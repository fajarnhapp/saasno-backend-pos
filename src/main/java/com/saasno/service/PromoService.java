package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.PromoEntity;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.PromoRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.promo.PromoCustomerDTO;
import com.saasno.utility.dto.promo.PromoDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.picker.CustomerType;
import com.saasno.utility.picker.PromoAudience;
import com.saasno.utility.picker.PromoCondition;
import com.saasno.utility.picker.PromoReward;
import com.saasno.utility.picker.PromoScope;
import com.saasno.utility.picker.PromoStatus;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class PromoService {

	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
	private static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999999999.999999");
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final int QUANTITY_SCALE = 6;
	private static final int MAX_CUSTOMERS = 500;

	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional
	public ResponseModel<PromoDTO> create(PromoDTO request) {
		BranchScope scope = branchScopeService.current();
		PromoEntity entity = new PromoEntity();
		entity.setBranchId(branchScopeService.resolveOwner(scope, request.getBranchId()));
		apply(entity, request);
		promoRepository.store(entity);
		return new ResponseModel<PromoDTO>().success(dtos(List.of(entity)).get(0));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<PromoDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<DataResult<PromoDTO>>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<PromoDTO>>(
					new DataResult<PromoDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Dipakai read() dan export download.
	@Transactional(readOnly = true)
	public DataResult<PromoDTO> find(DataFilter filter, BranchScope scope) {
		DataResult<PromoEntity> result = promoRepository.dataTable(scope.apply(filter, "branchId"));
		return new DataResult<>(dtos(result.getData()), result.getTotal(), result.getGrandTotal());
	}

	// Update menggantikan seluruh data promo (bukan parsial), supaya kolom opsional seperti periode
	// dan hadiah yang tidak relevan bisa dikosongkan dengan jelas.
	@Transactional
	public ResponseModel<PromoDTO> update(PromoDTO request) {
		BranchScope scope = branchScopeService.current();
		PromoEntity entity = request.getId() == null ? null : promoRepository.find(request.getId());
		if (entity == null || !scope.contains(entity.getBranchId())) {
			throw new ApiException("404", "Promo tidak ditemukan");
		}
		branchScopeService.assertSameOwner(entity.getBranchId(), request.getBranchId());
		apply(entity, request);
		promoRepository.update(entity);
		return new ResponseModel<PromoDTO>().success(dtos(List.of(entity)).get(0));
	}

	@Transactional
	public ResponseModel<PromoDTO> delete(String id) {
		BranchScope scope = branchScopeService.current();
		PromoEntity entity = id == null ? null : promoRepository.find(id);
		if (entity == null || !scope.contains(entity.getBranchId())) {
			throw new ApiException("404", "Promo tidak ditemukan");
		}
		promoRepository.delete(entity);
		return new ResponseModel<PromoDTO>().success();
	}

	private void apply(PromoEntity entity, PromoDTO r) {
		// Customer yang dirujuk harus milik cabang promo ini atau turunannya; produk boleh juga milik induknya.
		BranchScope referenceScope = branchScopeService.subtree(entity.getBranchId());
		String name = clean(r.getName());
		if (name == null) {
			throw new ApiException("400", "Nama tidak boleh kosong");
		}
		if (name.length() > 256) {
			throw new ApiException("400", "Nama tidak boleh lebih dari 256 karakter");
		}
		PromoStatus status = r.getStatus() == null || r.getStatus().isBlank() ? PromoStatus.ACTIVE
				: PromoStatus.fromString(r.getStatus().trim());

		// Syarat
		PromoScope scope = PromoScope.fromString(r.getScope());
		String productId = null;
		if (scope == PromoScope.PRODUCT) {
			productId = clean(r.getProductId());
			if (productId == null) {
				throw new ApiException("400", "Produk tidak boleh kosong");
			}
			assertProductExists(productId, referenceScope);
		}
		PromoCondition condition = PromoCondition.fromString(r.getConditionType());
		BigDecimal minValue = condition == PromoCondition.QUANTITY
				? quantity(r.getMinValue(), "Jumlah minimum")
				: amount(r.getMinValue(), "Nilai belanja minimum");

		// Hadiah; field yang tidak relevan untuk jenis hadiahnya dikosongkan.
		PromoReward reward = PromoReward.fromString(r.getRewardType());
		BigDecimal discountValue = null;
		String rewardProductId = null;
		BigDecimal rewardQuantity = null;
		switch (reward) {
		case DISCOUNT_FIXED -> {
			discountValue = amount(r.getDiscountValue(), "Diskon");
			if (condition == PromoCondition.AMOUNT && discountValue.compareTo(minValue) >= 0) {
				throw new ApiException("400", "Potongan harus lebih kecil dari nilai belanja minimum");
			}
		}
		case DISCOUNT_PERCENT -> {
			BigDecimal percent = r.getDiscountValue();
			if (percent == null || percent.signum() <= 0 || percent.compareTo(HUNDRED) > 0
					|| percent.stripTrailingZeros().scale() > 2) {
				throw new ApiException("400", "Persentase potongan harus antara 0 dan 100");
			}
			discountValue = percent.setScale(2, RoundingMode.UNNECESSARY);
		}
		case FREE_PRODUCT -> {
			rewardProductId = clean(r.getRewardProductId());
			if (rewardProductId == null) {
				throw new ApiException("400", "Produk hadiah tidak boleh kosong");
			}
			assertProductExists(rewardProductId, referenceScope);
			rewardQuantity = quantity(r.getRewardQuantity(), "Jumlah hadiah");
		}
		}

		// Sasaran: semua customer, satu tipe customer, atau customer tertentu.
		PromoAudience audience = r.getAudience() == null || r.getAudience().isBlank() ? PromoAudience.ALL
				: PromoAudience.fromString(r.getAudience().trim());
		String customerType = null;
		List<CustomerEntity> customers = new java.util.ArrayList<>();
		if (audience == PromoAudience.TYPE) {
			customerType = CustomerType.fromString(r.getCustomerType()).name();
		} else if (audience == PromoAudience.CUSTOMER) {
			List<PromoCustomerDTO> requested = r.getCustomers() == null ? List.of() : r.getCustomers();
			if (requested.isEmpty()) {
				throw new ApiException("400", "Customer tidak boleh kosong");
			}
			if (requested.size() > MAX_CUSTOMERS) {
				throw new ApiException("400", "Customer tidak boleh lebih dari " + MAX_CUSTOMERS);
			}
			Set<String> seen = new HashSet<>();
			for (PromoCustomerDTO item : requested) {
				String id = item == null ? null : clean(item.getId());
				if (id == null) {
					throw new ApiException("400", "Customer tidak boleh kosong");
				}
				if (!seen.add(id)) {
					throw new ApiException("400", "Customer tidak boleh ganda");
				}
				CustomerEntity customer = customerRepository.find(id);
				if (customer == null || !referenceScope.contains(customer.getBranchId())) {
					throw new ApiException("404", "Customer tidak ditemukan");
				}
				customers.add(customer);
			}
		}

		// Periode
		if (r.getValidFrom() != null && r.getValidUntil() != null && r.getValidUntil().isBefore(r.getValidFrom())) {
			throw new ApiException("400", "Tanggal akhir tidak boleh sebelum tanggal mulai");
		}

		entity.setName(name);
		entity.setStatus(status.name());
		entity.setScope(scope.name());
		entity.setProductId(productId);
		entity.setConditionType(condition.name());
		entity.setMinValue(minValue);
		entity.setRewardType(reward.name());
		entity.setDiscountValue(discountValue);
		entity.setRewardProductId(rewardProductId);
		entity.setRewardQuantity(rewardQuantity);
		entity.setAudience(audience.name());
		entity.setCustomerType(customerType);
		entity.getCustomers().clear();
		entity.getCustomers().addAll(customers);
		entity.setValidFrom(r.getValidFrom());
		entity.setValidUntil(r.getValidUntil());
	}

	private void assertProductExists(String productId, BranchScope referenceScope) {
		ProductEntity product = productRepository.find(productId);
		if (product == null || !referenceScope.canSee(product.getBranchId())) {
			throw new ApiException("404", "Produk tidak ditemukan");
		}
	}

	// Jumlah: > 0, maksimal 6 desimal.
	private BigDecimal quantity(BigDecimal value, String field) {
		if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > QUANTITY_SCALE
				|| value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY).compareTo(MAX_QUANTITY) > 0) {
			throw new ApiException("400", field + " tidak valid");
		}
		return value.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
	}

	// Rupiah utuh > 0.
	private BigDecimal amount(BigDecimal value, String field) {
		if (value == null || value.signum() <= 0 || value.stripTrailingZeros().scale() > 0
				|| value.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", field + " tidak valid");
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private List<PromoDTO> dtos(List<PromoEntity> entities) {
		Set<String> productIds = new HashSet<>();
		entities.forEach(e -> {
			if (e.getProductId() != null) {
				productIds.add(e.getProductId());
			}
			if (e.getRewardProductId() != null) {
				productIds.add(e.getRewardProductId());
			}
		});
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(productIds).forEach(p -> products.put(p.getId(), p));
		Map<String, String> branchNames = branchScopeService
				.names(entities.stream().map(PromoEntity::getBranchId).collect(Collectors.toSet()));

		return entities.stream().map(e -> {
			PromoDTO dto = new PromoDTO();
			dto.setId(e.getId());
			dto.setBranchId(e.getBranchId());
			dto.setBranchName(branchNames.get(e.getBranchId()));
			dto.setSerial(e.getSerial());
			dto.setName(e.getName());
			dto.setStatus(e.getStatus());
			dto.setScope(e.getScope());
			dto.setProductId(e.getProductId());
			ProductEntity product = products.get(e.getProductId());
			dto.setProductName(product == null ? null : product.getName());
			dto.setProductUnit(product == null ? null : product.getUnit());
			dto.setConditionType(e.getConditionType());
			dto.setMinValue(e.getMinValue());
			dto.setRewardType(e.getRewardType());
			dto.setDiscountValue(e.getDiscountValue());
			dto.setRewardProductId(e.getRewardProductId());
			ProductEntity rewardProduct = products.get(e.getRewardProductId());
			dto.setRewardProductName(rewardProduct == null ? null : rewardProduct.getName());
			dto.setRewardProductUnit(rewardProduct == null ? null : rewardProduct.getUnit());
			dto.setRewardQuantity(e.getRewardQuantity());
			dto.setAudience(e.getAudience());
			dto.setCustomerType(e.getCustomerType());
			dto.setCustomers(e.getCustomers().stream().map(c -> {
				PromoCustomerDTO item = new PromoCustomerDTO();
				item.setId(c.getId());
				item.setSerial(c.getSerial());
				item.setName(c.getProfile().getName());
				return item;
			}).collect(Collectors.toList()));
			dto.setValidFrom(e.getValidFrom());
			dto.setValidUntil(e.getValidUntil());
			dto.setModificated(e.getModificated());
			dto.setRegistered(e.getRegistered());
			return dto;
		}).collect(Collectors.toList());
	}

	private String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
