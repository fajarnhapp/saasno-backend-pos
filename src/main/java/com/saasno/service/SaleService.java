package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

import com.saasno.entity.BranchEntity;
import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.SaleHoldEntity;
import com.saasno.entity.SaleEntity;
import com.saasno.entity.SaleItemEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.SaleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.sale.SaleDTO;
import com.saasno.utility.dto.sale.SaleItemDTO;
import com.saasno.utility.dto.sale.SaleRequestDTO;
import com.saasno.utility.dto.sale.SaleRequestItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Penjualan tunai. Cabang transaksi selalu cabang tempat kasir berada (user global memilih). Stok berkurang
// saat transaksi selesai; transaksi dengan diskon manual di atas batas menunggu persetujuan user lain dan
// stok baru berkurang saat disetujui. Transaksi tidak pernah dihapus.
@Service
public class SaleService {

	private static final String REFERENCE = "SALE";
	private static final String VOID_REFERENCE = "SALE_VOID";
	private static final int MAX_NOTE = 500;
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	@Autowired
	private SaleChargeService saleChargeService;

	@Autowired
	private SaleRepository saleRepository;

	@Autowired
	private SaleCalculator calculator;

	@Autowired
	private StockService stockService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private NotificationRecipients notificationRecipients;

	@Autowired
	private SaleHoldService holdService;

	@Autowired
	private SessionUtility sessionUtility;

	// ---------------------------------------------------------------- hitung & simpan

	// Pratinjau perhitungan keranjang tanpa menyimpan (dipakai layar kasir). paid boleh kosong.
	@Transactional(readOnly = true)
	public ResponseModel<SaleDTO> preview(SaleRequestDTO request) {
		BranchEntity branch = branch(branchScopeService.current(), request.getBranchId());
		SaleCalculator.Cart cart = calculator.calculate(branch.getId(), request);
		SaleDTO dto = cartDto(cart, branch);
		BigDecimal paid = request.getPaid();
		if (paid != null && paid.compareTo(cart.total) >= 0) {
			dto.setPaid(paid);
			dto.setChangeAmount(paid.subtract(cart.total));
		}
		return new ResponseModel<SaleDTO>().success(dto);
	}

	// allowDiscount: true hanya untuk endpoint khusus diskon manual (izinnya dipisah lewat role_api).
	@Transactional
	public ResponseModel<SaleDTO> create(SaleRequestDTO request, boolean allowDiscount) {
		BranchScope scope = branchScopeService.current();
		String userId = sessionUtility.getUserId();
		String key = clean(request.getRequestKey());
		if (key != null) {
			if (key.length() > 64) {
				throw new ApiException("400", "Request key tidak boleh lebih dari 64 karakter");
			}
			// Pengiriman ulang dengan kunci yang sama mengembalikan transaksi yang sudah ada.
			SaleEntity existing = saleRepository.findByRequestKey(userId, key);
			if (existing != null) {
				return new ResponseModel<SaleDTO>().success(dtos(List.of(existing)).get(0));
			}
		}
		if (!allowDiscount && hasDiscount(request)) {
			throw new ApiException("400", "Diskon manual tidak diizinkan di endpoint ini");
		}
		BranchEntity branch = branch(scope, request.getBranchId());
		// Keranjang tertahan yang dilanjutkan dikunci dulu: dua kasir yang membukanya tidak keduanya bisa membayar.
		String holdId = clean(request.getHoldId());
		SaleHoldEntity hold = holdId == null ? null : holdService.lockUsable(holdId, branch.getId());
		SaleCalculator.Cart cart = calculator.calculate(branch.getId(), request);

		BigDecimal paid = request.getPaid();
		if (paid == null || paid.signum() < 0 || paid.stripTrailingZeros().scale() > 0
				|| paid.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", "Jumlah bayar tidak valid");
		}
		paid = paid.setScale(2, RoundingMode.UNNECESSARY);
		if (paid.compareTo(cart.total) < 0) {
			throw new ApiException("400", "Jumlah bayar kurang dari total");
		}

		SaleEntity sale = new SaleEntity();
		sale.setBranchId(branch.getId());
		sale.setCustomerId(cart.customer == null ? null : cart.customer.getId());
		sale.setStatus(cart.requiresApproval ? SaleEntity.WAITING_APPROVAL : SaleEntity.COMPLETED);
		sale.setSubtotal(cart.subtotal);
		sale.setPromoId(cart.promo == null ? null : cart.promo.getId());
		sale.setPromoName(cart.promo == null ? null : cart.promo.getName());
		sale.setPromoDiscount(cart.promoDiscount);
		if (cart.promo != null) {
			// Aturan promo dibekukan supaya retur bisa menghitung ulang walau promonya diubah atau dihapus.
			sale.setPromoScope(cart.promo.getScope());
			sale.setPromoProductId(cart.promo.getProductId());
			sale.setPromoConditionType(cart.promo.getConditionType());
			sale.setPromoMinValue(cart.promo.getMinValue());
			sale.setPromoRewardType(cart.promo.getRewardType());
			sale.setPromoDiscountValue(cart.promo.getDiscountValue());
			sale.setPromoValue(cart.promoValue);
		}
		sale.setManualDiscount(cart.manualDiscount);
		sale.setManualDiscountReason(cart.discountReason);
		sale.setCharges(saleChargeService.write(cart.charges));
		sale.setChargeTotal(cart.chargeTotal);
		sale.setTotal(cart.total);
		sale.setPaid(paid);
		sale.setChangeAmount(paid.subtract(cart.total));
		sale.setRequestKey(key);
		sale.setCreatedBy(userId);
		saleRepository.store(sale, branch.getSerial());
		// Header harus tertulis sebelum baris (baris tidak punya relasi JPA ke header).
		saleRepository.getEntityManager().flush();

		for (SaleCalculator.Line line : cart.lines) {
			SaleItemEntity item = new SaleItemEntity();
			item.setSaleId(sale.getId());
			item.setProductId(line.product.getId());
			item.setProductSerial(line.product.getSerial());
			item.setProductName(line.product.getName());
			item.setUnit(line.product.getUnit());
			item.setQuantity(line.quantity);
			item.setUnitPrice(line.unitPrice);
			item.setPriceSource(line.priceSource);
			item.setTierMinQuantity(line.tierMinQuantity);
			item.setDiscount(line.discount);
			item.setLineTotal(line.lineTotal);
			item.setNetTotal(line.netTotal);
			item.setFree(line.free);
			// Harga pokok dibekukan per baris (kosong bila produk belum punya harga pokok).
			item.setUnitCost(line.product.getCostPrice());
			saleRepository.addItem(item);
		}
		saleRepository.getEntityManager().flush();
		if (hold != null) {
			holdService.markUsed(hold, sale.getId(), userId);
		}

		if (SaleEntity.COMPLETED.equals(sale.getStatus())) {
			deductStock(sale, saleRepository.findItems(List.of(sale.getId())), userId);
		} else if (SaleEntity.WAITING_APPROVAL.equals(sale.getStatus())) {
			notifyWaiting(sale, userId);
		}
		return new ResponseModel<SaleDTO>().success(dtos(List.of(sale)).get(0));
	}

	// ---------------------------------------------------------------- baca

	// Transaksi cabang user dan turunannya (hanya baca bagi induk).
	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<SaleDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<SaleDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<SaleDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<SaleEntity> result = saleRepository.dataTable(filter);
		return new DataResult<>(dtos(result.getData()), result.getTotal(),
					result.getGrandTotal());
	}

	// ---------------------------------------------------------------- putuskan

	// Setuju: user lain yang cabangnya mencakup cabang transaksi. Stok baru berkurang sekarang; kalau stok
	// sudah tidak cukup, ditolak 409 dan transaksi tetap menunggu.
	@Transactional
	public ResponseModel<SaleDTO> approve(String id, String note) {
		SaleEntity sale = lockForDecision(id);
		String decisionNote = clean(note);
		if (decisionNote != null && decisionNote.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		String userId = sessionUtility.getUserId();
		List<SaleItemEntity> items = saleRepository.findItems(List.of(sale.getId()));
		Map<String, ProductEntity> products = products(items);
		for (SaleItemEntity item : items) {
			ProductEntity product = products.get(item.getProductId());
			if ("DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
		}
		deductStock(sale, items, userId);
		sale.setStatus(SaleEntity.COMPLETED);
		decide(sale, userId, decisionNote);
		notifyDecided(sale, true, decisionNote);
		return new ResponseModel<SaleDTO>().success(dtos(List.of(sale)).get(0));
	}

	@Transactional
	public ResponseModel<SaleDTO> reject(String id, String note) {
		SaleEntity sale = lockForDecision(id);
		String reason = clean(note);
		if (reason == null) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (reason.length() > MAX_NOTE) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		sale.setStatus(SaleEntity.REJECTED);
		decide(sale, sessionUtility.getUserId(), reason);
		notifyDecided(sale, false, reason);
		return new ResponseModel<SaleDTO>().success(dtos(List.of(sale)).get(0));
	}

	// Hanya kasir pembuatnya yang bisa membatalkan transaksi yang masih menunggu persetujuan.
	@Transactional
	public ResponseModel<SaleDTO> cancel(String id) {
		branchScopeService.current();
		SaleEntity sale = id == null ? null : saleRepository.findLock(id.trim());
		if (sale == null) {
			throw new ApiException("404", "Transaksi tidak ditemukan");
		}
		String userId = sessionUtility.getUserId();
		if (!sale.getCreatedBy().equals(userId)) {
			throw new ApiException("403", "Hanya kasir yang membuat transaksi ini yang dapat membatalkannya");
		}
		assertWaiting(sale);
		sale.setStatus(SaleEntity.CANCELLED);
		decide(sale, userId, null);
		return new ResponseModel<SaleDTO>().success(dtos(List.of(sale)).get(0));
	}

	// Membatalkan transaksi yang sudah selesai (void): seluruh transaksi, belum ada retur, alasan wajib.
	// Hanya di cabang tempat transaksi dibuat. Stok kembali penuh.
	@Transactional
	public ResponseModel<SaleDTO> voidSale(String id, String reason) {
		BranchScope scope = branchScopeService.current();
		SaleEntity sale = id == null ? null : saleRepository.findLock(id.trim());
		if (sale == null || (!scope.isGlobal() && !sale.getBranchId().equals(scope.getBranchId()))) {
			throw new ApiException("404", "Transaksi tidak ditemukan");
		}
		String voidReason = clean(reason);
		if (voidReason == null) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (voidReason.length() > MAX_NOTE) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		if (!SaleEntity.COMPLETED.equals(sale.getStatus())) {
			throw new ApiException("409", "Hanya transaksi yang selesai yang dapat di-void, status transaksi ini: " + sale.getStatus());
		}
		List<SaleItemEntity> items = saleRepository.findItems(List.of(sale.getId()));
		if (items.stream().anyMatch(i -> i.getReturnedQuantity().signum() > 0)) {
			throw new ApiException("409", "Transaksi sudah memiliki retur sehingga tidak dapat di-void");
		}
		String userId = sessionUtility.getUserId();
		Map<String, ProductEntity> products = products(items);
		for (SaleItemEntity item : items.stream().sorted(Comparator.comparing(SaleItemEntity::getProductId)).toList()) {
			stockService.applyMovement(sale.getBranchId(), products.get(item.getProductId()), item.getQuantity(),
					StockMovementEntity.RETURN, VOID_REFERENCE, sale.getId(), "Void " + sale.getSerial(), userId, item.getUnitCost());
		}
		sale.setStatus(SaleEntity.VOIDED);
		sale.setVoidedBy(userId);
		sale.setVoidedAt(LocalDateTime.now());
		sale.setVoidReason(voidReason);
		saleRepository.update(sale);
		return new ResponseModel<SaleDTO>().success(dtos(List.of(sale)).get(0));
	}

	// ---------------------------------------------------------------- bantu

	// Yang boleh memutuskan: user lain yang cabangnya mencakup cabang transaksi. Baris dikunci supaya dua
	// keputusan bersamaan untuk transaksi yang sama tidak keduanya berhasil.
	private SaleEntity lockForDecision(String id) {
		BranchScope scope = branchScopeService.current();
		SaleEntity sale = id == null ? null : saleRepository.findLock(id.trim());
		if (sale == null || !scope.contains(sale.getBranchId())) {
			throw new ApiException("404", "Transaksi tidak ditemukan");
		}
		if (sale.getCreatedBy().equals(sessionUtility.getUserId())) {
			throw new ApiException("403", "Anda tidak dapat memutuskan transaksi Anda sendiri");
		}
		assertWaiting(sale);
		return sale;
	}

	// Yang berhak menyetujui di cabang transaksi (selain kasirnya) diberi tahu ada diskon yang menunggu.
	private void notifyWaiting(SaleEntity sale, String creatorId) {
		java.util.Set<String> approvers = notificationRecipients.withApi("PUT_API_SALE_APPROVE", sale.getBranchId());
		approvers.remove(creatorId);
		notificationPublisher.notify(approvers, "SALE_WAITING_APPROVAL", "Transaksi menunggu persetujuan diskon",
				sale.getSerial() + " dengan diskon manual " + rupiah(sale.getManualDiscount()) + " (total " + rupiah(sale.getTotal())
						+ "), dibuat oleh " + notificationRecipients.username(creatorId) + ".",
				"/transaction/history");
	}

	// Kasir pembuat diberi tahu hasil keputusan.
	private void notifyDecided(SaleEntity sale, boolean approved, String note) {
		String decider = notificationRecipients.username(sessionUtility.getUserId());
		notificationPublisher.notify(List.of(sale.getCreatedBy()), approved ? "SALE_APPROVED" : "SALE_REJECTED",
				approved ? "Transaksi disetujui" : "Transaksi ditolak",
				sale.getSerial() + (approved ? " disetujui oleh " : " ditolak oleh ") + decider
						+ (note == null ? "." : ". Catatan: " + note),
				"/transaction/history");
	}

	private String rupiah(java.math.BigDecimal value) {
		java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0",
				java.text.DecimalFormatSymbols.getInstance(java.util.Locale.forLanguageTag("id-ID")));
		return "Rp " + format.format(value == null ? java.math.BigDecimal.ZERO : value);
	}

	private void assertWaiting(SaleEntity sale) {
		if (!SaleEntity.WAITING_APPROVAL.equals(sale.getStatus())) {
			throw new ApiException("409", "Transaksi sudah " + sale.getStatus());
		}
	}

	private void decide(SaleEntity sale, String userId, String note) {
		sale.setDecidedBy(userId);
		sale.setDecidedAt(LocalDateTime.now());
		sale.setDecisionNote(note);
		saleRepository.update(sale);
	}

	// Mengurangi stok semua baris (termasuk barang gratis) dalam urutan tetap supaya dua transaksi bersamaan
	// tidak saling mengunci. Stok kurang -> 409 dan seluruh transaksi dibatalkan.
	private void deductStock(SaleEntity sale, List<SaleItemEntity> items, String userId) {
		Map<String, ProductEntity> products = products(items);
		List<SaleItemEntity> ordered = new ArrayList<>(items);
		ordered.sort(Comparator.comparing(SaleItemEntity::getProductId).thenComparing(SaleItemEntity::isFree));
		for (SaleItemEntity item : ordered) {
			stockService.applyMovement(sale.getBranchId(), products.get(item.getProductId()), item.getQuantity().negate(),
					StockMovementEntity.SALE, REFERENCE, sale.getId(), "Penjualan " + sale.getSerial(), userId, item.getUnitCost());
		}
	}

	private Map<String, ProductEntity> products(List<SaleItemEntity> items) {
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(items.stream().map(SaleItemEntity::getProductId).collect(Collectors.toSet()))
				.forEach(p -> products.put(p.getId(), p));
		return products;
	}

	// Asal cabang: user cabang selalu cabangnya sendiri (ditolak 403 bila menyebut yang lain); user global
	// harus menyebutkannya.
	private BranchEntity branch(BranchScope scope, String requested) {
		String id = clean(requested);
		if (!scope.isGlobal()) {
			if (id != null && !id.equals(scope.getBranchId())) {
				throw new ApiException("403", "Transaksi hanya dapat dibuat di cabang Anda sendiri");
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

	private boolean hasDiscount(SaleRequestDTO request) {
		if (request.getDiscount() != null && request.getDiscount().signum() != 0) {
			return true;
		}
		return request.getItems() != null && request.getItems().stream()
				.anyMatch(i -> i != null && i.getDiscount() != null && i.getDiscount().signum() != 0);
	}

	private SaleDTO cartDto(SaleCalculator.Cart cart, BranchEntity branch) {
		SaleDTO dto = new SaleDTO();
		dto.setBranchId(branch.getId());
		dto.setBranchName(branch.getName());
		if (cart.customer != null) {
			dto.setCustomerId(cart.customer.getId());
			dto.setCustomerName(cart.customer.getProfile().getName());
		}
		dto.setItems(cart.lines.stream().map(l -> {
			SaleItemDTO item = new SaleItemDTO();
			item.setProductId(l.product.getId());
			item.setProductSerial(l.product.getSerial());
			item.setProductName(l.product.getName());
			item.setUnit(l.product.getUnit());
			item.setQuantity(l.quantity);
			item.setUnitPrice(l.unitPrice);
			item.setPriceSource(l.priceSource);
			item.setTierMinQuantity(l.tierMinQuantity);
			item.setDiscount(l.discount);
			item.setLineTotal(l.lineTotal);
			item.setNetTotal(l.netTotal);
			item.setFree(l.free);
			return item;
		}).collect(Collectors.toList()));
		dto.setSubtotal(cart.subtotal);
		dto.setPromoId(cart.promo == null ? null : cart.promo.getId());
		dto.setPromoName(cart.promo == null ? null : cart.promo.getName());
		dto.setPromoDiscount(cart.promoDiscount);
		dto.setManualDiscount(cart.manualDiscount);
		dto.setManualDiscountReason(cart.discountReason);
		dto.setCharges(cart.charges);
		dto.setChargeTotal(cart.chargeTotal);
		dto.setTotal(cart.total);
		dto.setRequiresApproval(cart.requiresApproval);
		dto.setManualDiscountLimitPercent(cart.limitPercent);
		return dto;
	}

	private List<SaleDTO> dtos(List<SaleEntity> sales) {
		Set<String> saleIds = sales.stream().map(SaleEntity::getId).collect(Collectors.toSet());
		Map<String, List<SaleItemEntity>> items = saleRepository.findItems(saleIds).stream()
				.collect(Collectors.groupingBy(SaleItemEntity::getSaleId));
		Set<String> branchIds = sales.stream().map(SaleEntity::getBranchId).collect(Collectors.toSet());
		Map<String, String> branchNames = branchScopeService.names(branchIds);
		Set<String> customerIds = sales.stream().map(SaleEntity::getCustomerId).filter(c -> c != null)
				.collect(Collectors.toSet());
		Map<String, String> customerNames = new HashMap<>();
		for (CustomerEntity customer : customerRepository.findByIds(customerIds)) {
			customerNames.put(customer.getId(), customer.getProfile().getName());
		}
		Set<String> userIds = new HashSet<>();
		sales.forEach(s -> {
			userIds.add(s.getCreatedBy());
			if (s.getDecidedBy() != null) {
				userIds.add(s.getDecidedBy());
			}
			if (s.getVoidedBy() != null) {
				userIds.add(s.getVoidedBy());
			}
		});
		Map<String, String> usernames = userRepository.findUsernames(userIds);
		BigDecimal limit = null;

		return sales.stream().map(s -> {
			SaleDTO dto = new SaleDTO();
			dto.setId(s.getId());
			dto.setSerial(s.getSerial());
			dto.setBranchId(s.getBranchId());
			dto.setBranchName(branchNames.get(s.getBranchId()));
			dto.setCustomerId(s.getCustomerId());
			dto.setCustomerName(s.getCustomerId() == null ? null : customerNames.get(s.getCustomerId()));
			dto.setStatus(s.getStatus());
			dto.setItems(items.getOrDefault(s.getId(), List.of()).stream().map(i -> {
				SaleItemDTO item = new SaleItemDTO();
				item.setId(i.getId());
				item.setProductId(i.getProductId());
				item.setProductSerial(i.getProductSerial());
				item.setProductName(i.getProductName());
				item.setUnit(i.getUnit());
				item.setQuantity(i.getQuantity());
				item.setUnitPrice(i.getUnitPrice());
				item.setPriceSource(i.getPriceSource());
				item.setTierMinQuantity(i.getTierMinQuantity());
				item.setDiscount(i.getDiscount());
				item.setLineTotal(i.getLineTotal());
				item.setNetTotal(i.getNetTotal());
				item.setFree(i.isFree());
				item.setReturnedQuantity(i.getReturnedQuantity());
				item.setReturnedAmount(i.getReturnedAmount());
				return item;
			}).collect(Collectors.toList()));
			dto.setSubtotal(s.getSubtotal());
			dto.setPromoId(s.getPromoId());
			dto.setPromoName(s.getPromoName());
			dto.setPromoDiscount(s.getPromoDiscount());
			dto.setManualDiscount(s.getManualDiscount());
			dto.setManualDiscountReason(s.getManualDiscountReason());
			dto.setCharges(saleChargeService.read(s.getCharges()));
			dto.setChargeTotal(s.getChargeTotal());
			dto.setTotal(s.getTotal());
			dto.setPaid(s.getPaid());
			dto.setChangeAmount(s.getChangeAmount());
			dto.setManualDiscountLimitPercent(limit);
			dto.setCreatedBy(s.getCreatedBy());
			dto.setCreatedByName(usernames.get(s.getCreatedBy()));
			dto.setRegistered(s.getRegistered());
			dto.setDecidedBy(s.getDecidedBy());
			dto.setDecidedByName(s.getDecidedBy() == null ? null : usernames.get(s.getDecidedBy()));
			dto.setDecidedAt(s.getDecidedAt());
			dto.setDecisionNote(s.getDecisionNote());
			dto.setVoidedBy(s.getVoidedBy());
			dto.setVoidedByName(s.getVoidedBy() == null ? null : usernames.get(s.getVoidedBy()));
			dto.setVoidedAt(s.getVoidedAt());
			dto.setVoidReason(s.getVoidReason());
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
