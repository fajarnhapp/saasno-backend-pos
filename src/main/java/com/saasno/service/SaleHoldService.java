package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saasno.entity.BranchEntity;
import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.SaleHoldEntity;
import com.saasno.filter.ColumnFilter;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.SaleHoldRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.sale.SaleHoldDTO;
import com.saasno.utility.dto.sale.SaleHoldItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Tahan transaksi: customer lupa dompet dan kembali beberapa menit kemudian. Keranjang disimpan apa adanya
// supaya antrean tetap jalan. Tidak ada pembatalan otomatis, stok tidak dikunci (dicek lagi saat dibayar), dan
// harga serta promo dihitung ulang saat dilanjutkan. Terlihat dan bisa dilanjutkan atau dibuang oleh kasir
// mana pun di cabang yang sama; keranjang menjadi terpakai saat transaksinya tersimpan.
@Service
public class SaleHoldService {

	private static final int MAX_ITEMS = 100;
	private static final int MAX_LABEL = 200;
	private static final int MAX_REASON = 500;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	// Bentuk keranjang yang disimpan (JSON di kolom cart).
	private record StoredItem(String productId, BigDecimal quantity, String unit, BigDecimal discount) {
	}

	private record StoredCart(String customerId, List<StoredItem> items, BigDecimal discount, String discountReason) {
	}

	@Autowired
	private SaleHoldRepository holdRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ObjectMapper objectMapper;

	// Menahan keranjang baru, atau memperbarui yang sudah ada bila id diisi (kasir menahan ulang setelah mengubah).
	@Transactional
	public ResponseModel<SaleHoldDTO> save(SaleHoldDTO request) {
		BranchScope scope = branchScopeService.current();
		BranchEntity branch = branch(scope, request.getBranchId());
		String label = clean(request.getLabel());
		if (label != null && label.length() > MAX_LABEL) {
			throw new ApiException("400", "Label tidak boleh lebih dari " + MAX_LABEL + " karakter");
		}
		String cart = serialize(validate(branch.getId(), request));

		SaleHoldEntity hold;
		String id = clean(request.getId());
		if (id == null) {
			hold = new SaleHoldEntity();
			hold.setBranchId(branch.getId());
			hold.setCreatedBy(sessionUtility.getUserId());
			hold.setLabel(label);
			hold.setCart(cart);
			holdRepository.store(hold, branch.getSerial());
		} else {
			hold = holdRepository.findLock(id);
			if (hold == null || !hold.getBranchId().equals(branch.getId())) {
				throw new ApiException("404", "Transaksi ditahan tidak ditemukan");
			}
			assertHeld(hold);
			hold.setLabel(label);
			hold.setCart(cart);
			holdRepository.update(hold);
		}
		return new ResponseModel<SaleHoldDTO>().success(dtos(List.of(hold)).get(0));
	}

	// Keranjang yang sedang ditahan di cabang user (user global melihat semua), yang tertua dulu.
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<SaleHoldDTO>> read(HttpServletRequest request) {
		try {
			BranchScope scope = branchScopeService.current();
			DataFilter filter = new DataFilter(request);
			if (!scope.isGlobal()) {
				filter.restrict("branchId", List.of(scope.getBranchId()));
			}
			filter.getColumn().removeIf(c -> "status".equals(c.getColumn()));
			filter.getColumn().add(new ColumnFilter("status", SaleHoldEntity.HELD, "="));
			if (filter.getOrder() == null) {
				filter.setOrder("registered");
				filter.setDescending(false);
			}
			DataResult<SaleHoldEntity> result = holdRepository.dataTable(filter);
			return new ResponseEntity<>(new DataResult<>(dtos(result.getData()), result.getTotal(),
					result.getGrandTotal()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<SaleHoldDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Membuang keranjang tertahan: kasir mana pun di cabang itu, alasan wajib.
	@Transactional
	public ResponseModel<SaleHoldDTO> discard(String id, String reason) {
		BranchScope scope = branchScopeService.current();
		SaleHoldEntity hold = id == null ? null : holdRepository.findLock(id.trim());
		if (hold == null || (!scope.isGlobal() && !hold.getBranchId().equals(scope.getBranchId()))) {
			throw new ApiException("404", "Transaksi ditahan tidak ditemukan");
		}
		String text = clean(reason);
		if (text == null) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (text.length() > MAX_REASON) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_REASON + " karakter");
		}
		assertHeld(hold);
		hold.setStatus(SaleHoldEntity.DISCARDED);
		hold.setDiscardedBy(sessionUtility.getUserId());
		hold.setDiscardedAt(LocalDateTime.now());
		hold.setDiscardReason(text);
		holdRepository.update(hold);
		return new ResponseModel<SaleHoldDTO>().success(dtos(List.of(hold)).get(0));
	}

	// Dipakai SaleService saat keranjang tertahan dibayar: mengunci baris supaya dua kasir yang membuka keranjang
	// yang sama tidak keduanya berhasil membayar.
	@Transactional
	public SaleHoldEntity lockUsable(String holdId, String branchId) {
		SaleHoldEntity hold = holdRepository.findLock(holdId.trim());
		if (hold == null || !hold.getBranchId().equals(branchId)) {
			throw new ApiException("404", "Transaksi ditahan tidak ditemukan");
		}
		assertHeld(hold);
		return hold;
	}

	@Transactional
	public void markUsed(SaleHoldEntity hold, String saleId, String userId) {
		hold.setStatus(SaleHoldEntity.USED);
		hold.setSaleId(saleId);
		hold.setUsedBy(userId);
		hold.setUsedAt(LocalDateTime.now());
		holdRepository.update(hold);
	}

	// ---------------------------------------------------------------- bantu

	private void assertHeld(SaleHoldEntity hold) {
		if (!SaleHoldEntity.HELD.equals(hold.getStatus())) {
			throw new ApiException("409", "Transaksi ditahan sudah " + hold.getStatus());
		}
	}

	// Pemeriksaan ringan: barang ada, terlihat oleh cabang, aktif, dan jumlah/satuan sah. Stok dan harga sengaja
	// tidak diperiksa di sini; keduanya dihitung saat dilanjutkan.
	private StoredCart validate(String branchId, SaleHoldDTO request) {
		List<SaleHoldItemDTO> items = request.getItems() == null ? List.of() : request.getItems();
		if (items.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (items.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}
		Set<String> seen = new HashSet<>();
		List<StoredItem> stored = new java.util.ArrayList<>();
		for (SaleHoldItemDTO item : items) {
			String productId = item == null ? null : clean(item.getProductId());
			if (productId == null) {
				throw new ApiException("400", "Produk tidak boleh kosong");
			}
			if (!seen.add(productId)) {
				throw new ApiException("400", "Produk tidak boleh ganda");
			}
			ProductEntity product = stockService.visibleProduct(branchId, productId);
			if ("DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
			stockService.toBase(product, item.getQuantity(), item.getUnit(), false);
			String unit = clean(item.getUnit());
			stored.add(new StoredItem(productId, item.getQuantity(), unit == null || unit.equals(product.getUnit()) ? null : unit,
					rupiah(item.getDiscount(), "Diskon barang")));
		}
		String customerId = clean(request.getCustomerId());
		if (customerId != null) {
			CustomerEntity customer = customerRepository.find(customerId);
			if (customer == null || !branchScopeService.subtree(branchId).contains(customer.getBranchId())) {
				throw new ApiException("404", "Customer tidak ditemukan");
			}
		}
		String reason = clean(request.getDiscountReason());
		if (reason != null && reason.length() > MAX_REASON) {
			throw new ApiException("400", "Alasan diskon tidak boleh lebih dari " + MAX_REASON + " karakter");
		}
		return new StoredCart(customerId, stored, rupiah(request.getDiscount(), "Diskon"), reason);
	}

	private BigDecimal rupiah(BigDecimal value, String field) {
		if (value == null) {
			return null;
		}
		if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0
				|| value.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", field + " tidak valid");
		}
		return value.signum() == 0 ? null : value;
	}

	private String serialize(StoredCart cart) {
		try {
			return objectMapper.writeValueAsString(cart);
		} catch (JsonProcessingException e) {
			throw new ApiException("500", "Transaksi ditahan tidak dapat disimpan");
		}
	}

	private StoredCart parse(String cart) {
		try {
			return objectMapper.readValue(cart, StoredCart.class);
		} catch (JsonProcessingException e) {
			throw new ApiException("500", "Transaksi ditahan tidak dapat dibaca");
		}
	}

	// Asal cabang: user cabang selalu cabangnya sendiri (403 bila menyebut yang lain); user global menyebutkannya.
	private BranchEntity branch(BranchScope scope, String requested) {
		String id = clean(requested);
		if (!scope.isGlobal()) {
			if (id != null && !id.equals(scope.getBranchId())) {
				throw new ApiException("403", "Transaksi hanya dapat ditahan di cabang Anda sendiri");
			}
			id = scope.getBranchId();
		}
		if (id == null) {
			throw new ApiException("400", "Cabang tidak boleh kosong");
		}
		BranchEntity branch = branchRepository.find(id);
		if (branch == null) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		return branch;
	}

	private List<SaleHoldDTO> dtos(List<SaleHoldEntity> holds) {
		Map<String, StoredCart> carts = new HashMap<>();
		Set<String> productIds = new HashSet<>();
		Set<String> customerIds = new HashSet<>();
		holds.forEach(h -> {
			StoredCart cart = parse(h.getCart());
			carts.put(h.getId(), cart);
			cart.items().forEach(i -> productIds.add(i.productId()));
			if (cart.customerId() != null) {
				customerIds.add(cart.customerId());
			}
		});
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(productIds).forEach(p -> products.put(p.getId(), p));
		Map<String, String> customerNames = new HashMap<>();
		for (CustomerEntity customer : customerRepository.findByIds(customerIds)) {
			customerNames.put(customer.getId(), customer.getProfile().getName());
		}
		Map<String, String> branchNames = branchScopeService
				.names(holds.stream().map(SaleHoldEntity::getBranchId).collect(Collectors.toSet()));
		Map<String, String> usernames = userRepository
				.findUsernames(holds.stream().map(SaleHoldEntity::getCreatedBy).collect(Collectors.toSet()));

		return holds.stream().map(h -> {
			StoredCart cart = carts.get(h.getId());
			SaleHoldDTO dto = new SaleHoldDTO();
			dto.setId(h.getId());
			dto.setSerial(h.getSerial());
			dto.setBranchId(h.getBranchId());
			dto.setBranchName(branchNames.get(h.getBranchId()));
			dto.setLabel(h.getLabel());
			dto.setCustomerId(cart.customerId());
			dto.setCustomerName(cart.customerId() == null ? null : customerNames.get(cart.customerId()));
			dto.setDiscount(cart.discount());
			dto.setDiscountReason(cart.discountReason());
			dto.setStatus(h.getStatus());
			dto.setCreatedBy(h.getCreatedBy());
			dto.setCreatedByName(usernames.get(h.getCreatedBy()));
			dto.setRegistered(h.getRegistered());
			dto.setItems(cart.items().stream().map(i -> {
				ProductEntity product = products.get(i.productId());
				SaleHoldItemDTO item = new SaleHoldItemDTO();
				item.setProductId(i.productId());
				item.setProductSerial(product == null ? null : product.getSerial());
				item.setProductName(product == null ? null : product.getName());
				item.setQuantity(i.quantity());
				item.setUnit(i.unit() != null ? i.unit() : product == null ? null : product.getUnit());
				item.setDiscount(i.discount());
				return item;
			}).collect(Collectors.toList()));
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
