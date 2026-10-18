package com.saasno.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
import com.saasno.entity.ProductEntity;
import com.saasno.entity.SaleEntity;
import com.saasno.entity.SaleItemEntity;
import com.saasno.entity.SaleReturnEntity;
import com.saasno.entity.SaleReturnItemEntity;
import com.saasno.entity.StockMovementEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.SaleRepository;
import com.saasno.repository.SaleReturnRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.sale.SaleReturnDTO;
import com.saasno.utility.dto.sale.SaleReturnItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;

// Retur penjualan: boleh sebagian per baris, tunai, dan mengembalikan stok (kecuali barang ditandai rusak). Hanya untuk transaksi yang sudah
// selesai di cabang tempat user berada. Nilai kembali dimulai dari bagian proporsional nilai akhir baris, lalu promo
// DIHITUNG ULANG terhadap barang yang tersisa: bila syarat promo tidak terpenuhi lagi, potongannya ditahan dari
// nilai kembali (tidak pernah menambah nilai kembali dan tidak pernah menagih customer). Retur terakhir untuk baris
// itu mengambil sisanya, jadi total yang dikembalikan selalu sama dengan yang dibayar.
@Service
public class SaleReturnService {

	@Autowired
	private ShiftService shiftService;

	@Autowired
	private LoyaltyService loyaltyService;

	@Autowired
	private ReceivableService receivableService;

	private static final String REFERENCE = "SALE_RETURN";
	private static final int MAX_ITEMS = 100;
	private static final int MAX_REASON = 500;
	private static final int SCALE = 6;

	@Autowired
	private SaleChargeService saleChargeService;

	@Autowired
	private SaleRepository saleRepository;

	@Autowired
	private SaleReturnRepository returnRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StockService stockService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private SessionUtility sessionUtility;

	// Hasil perhitungan satu retur: baris yang diretur, nilai kembali, dan bagian yang ditahan karena promo.
	private record Plan(SaleItemEntity item, BigDecimal quantity, BigDecimal refund, boolean restock) {
	}

	private record Calc(SaleEntity sale, List<Plan> plans, BigDecimal refundTotal, BigDecimal promoAdjustment,
			Map<String, ProductEntity> products) {
	}

	// Pratinjau nilai retur (promo dihitung ulang) tanpa mengubah apa pun, supaya kasir tahu jumlah uang yang akan
	// dikembalikan sebelum menyimpan.
	@Transactional(readOnly = true)
	public ResponseModel<SaleReturnDTO> preview(SaleReturnDTO request) {
		BranchScope scope = branchScopeService.current();
		Calc calc = calculate(scope, request, false);
		SaleReturnDTO dto = new SaleReturnDTO();
		dto.setSaleId(calc.sale().getId());
		dto.setSaleSerial(calc.sale().getSerial());
		dto.setBranchId(calc.sale().getBranchId());
		dto.setReason(request.getReason());
		dto.setRefundTotal(calc.refundTotal());
		dto.setPromoAdjustment(calc.promoAdjustment());
		dto.setItems(calc.plans().stream().map(p -> {
			SaleReturnItemDTO item = new SaleReturnItemDTO();
			item.setSaleItemId(p.item().getId());
			item.setProductId(p.item().getProductId());
			item.setProductSerial(p.item().getProductSerial());
			item.setProductName(p.item().getProductName());
			item.setUnit(p.item().getUnit());
			item.setQuantity(p.quantity());
			item.setRefund(p.refund());
			item.setRestock(p.restock());
			return item;
		}).collect(Collectors.toList()));
		return new ResponseModel<SaleReturnDTO>().success(dto);
	}

	@Transactional
	public ResponseModel<SaleReturnDTO> create(SaleReturnDTO request) {
		BranchScope scope = branchScopeService.current();
		Calc calc = calculate(scope, request, true);
		SaleEntity sale = calc.sale();
		String reason = request.getReason().trim();

		String userId = sessionUtility.getUserId();
		shiftService.assertOpen(userId, sale.getBranchId());
		BranchEntity branch = branchRepository.find(sale.getBranchId());
		SaleReturnEntity entity = new SaleReturnEntity();
		entity.setSaleId(sale.getId());
		entity.setBranchId(sale.getBranchId());
		entity.setReason(reason);
		entity.setRefundTotal(calc.refundTotal());
		// Transaksi tempo: refund mengurangi piutang yang masih terbuka lebih dulu; sisanya baru dikembalikan sebagai uang.
		entity.setCreditOffset(receivableService.applyReturn(sale, calc.refundTotal()));
		entity.setPromoAdjustment(calc.promoAdjustment());
		entity.setCreatedBy(userId);
		returnRepository.store(entity, branch.getSerial());
		returnRepository.getEntityManager().flush();
		loyaltyService.onReturned(sale, calc.refundTotal());

		for (Plan plan : calc.plans()) {
			plan.item().setReturnedQuantity(plan.item().getReturnedQuantity().add(plan.quantity()));
			plan.item().setReturnedAmount(plan.item().getReturnedAmount().add(plan.refund()));
			returnRepository.addItem(new SaleReturnItemEntity(entity.getId(), plan.item().getId(), plan.quantity(),
					plan.refund(), plan.restock()));
		}
		returnRepository.getEntityManager().flush();

		// Stok kembali ke cabang transaksi (hanya barang yang layak jual), dalam urutan tetap.
		for (Plan plan : calc.plans().stream().filter(Plan::restock)
				.sorted(Comparator.comparing(p -> p.item().getProductId())).toList()) {
			stockService.applyMovement(sale.getBranchId(), calc.products().get(plan.item().getProductId()), plan.quantity(),
					StockMovementEntity.RETURN, REFERENCE, entity.getId(), "Retur " + entity.getSerial(), userId, plan.item().getUnitCost());
		}
		return new ResponseModel<SaleReturnDTO>().success(dtos(List.of(entity)).get(0));
	}

	// Validasi dan hitung. lock = true saat menyimpan: transaksi dikunci supaya dua retur bersamaan tidak
	// melebihi jumlah yang dibeli.
	private Calc calculate(BranchScope scope, SaleReturnDTO request, boolean lock) {
		String saleId = request.getSaleId() == null ? null : request.getSaleId().trim();
		SaleEntity sale = saleId == null || saleId.isEmpty() ? null
				: (lock ? saleRepository.findLock(saleId) : saleRepository.find(saleId));
		if (sale == null || (!scope.isGlobal() && !sale.getBranchId().equals(scope.getBranchId()))) {
			throw new ApiException("404", "Transaksi tidak ditemukan");
		}
		String reason = request.getReason() == null ? null : request.getReason().trim();
		if (reason == null || reason.isEmpty()) {
			throw new ApiException("400", "Alasan tidak boleh kosong");
		}
		if (reason.length() > MAX_REASON) {
			throw new ApiException("400", "Alasan tidak boleh lebih dari " + MAX_REASON + " karakter");
		}
		if (!SaleEntity.COMPLETED.equals(sale.getStatus())) {
			throw new ApiException("409", "Hanya transaksi yang selesai yang dapat diretur, status transaksi ini: " + sale.getStatus());
		}
		List<SaleReturnItemDTO> requested = request.getItems() == null ? List.of() : request.getItems();
		if (requested.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (requested.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}

		List<SaleItemEntity> allItems = saleRepository.findItems(List.of(sale.getId()));
		Map<String, SaleItemEntity> items = new HashMap<>();
		allItems.forEach(i -> items.put(i.getId(), i));
		Map<String, ProductEntity> products = new HashMap<>();
		productRepository.findByIds(allItems.stream().map(SaleItemEntity::getProductId).collect(Collectors.toSet()))
				.forEach(p -> products.put(p.getId(), p));

		// Nilai kembali proporsional per baris (cara lama): bagian dari nilai akhir baris sesuai jumlah yang
		// diretur; retur terakhir untuk baris itu mengambil sisanya.
		List<Plan> proportional = new ArrayList<>();
		Map<String, BigDecimal> plannedQuantity = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for (SaleReturnItemDTO entry : requested) {
			String itemId = entry == null || entry.getSaleItemId() == null ? null : entry.getSaleItemId().trim();
			SaleItemEntity item = itemId == null ? null : items.get(itemId);
			if (item == null) {
				throw new ApiException("400", "Barang bukan bagian dari transaksi ini");
			}
			if (!seen.add(itemId)) {
				throw new ApiException("400", "Barang tidak boleh ganda");
			}
			BigDecimal quantity = entry.getQuantity();
			if (quantity == null || quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > SCALE) {
				throw new ApiException("400", "Jumlah retur tidak valid");
			}
			quantity = quantity.setScale(SCALE, RoundingMode.UNNECESSARY);
			BigDecimal remaining = item.getQuantity().subtract(item.getReturnedQuantity());
			if (quantity.compareTo(remaining) > 0) {
				throw new ApiException("409", "Jumlah retur melebihi yang terjual: " + item.getProductSerial() + " "
						+ item.getProductName());
			}
			BigDecimal left = item.getNetTotal().subtract(item.getReturnedAmount());
			BigDecimal amount = quantity.compareTo(remaining) == 0 ? left
					: item.getNetTotal().multiply(quantity).divide(item.getQuantity(), 0, RoundingMode.HALF_UP).min(left);
			boolean restock = entry.getRestock() == null || entry.getRestock();
			proportional.add(new Plan(item, quantity, amount.setScale(2, RoundingMode.HALF_UP), restock));
			plannedQuantity.put(itemId, quantity);
		}
		BigDecimal proportionalTotal = proportional.stream().map(Plan::refund).reduce(BigDecimal.ZERO, BigDecimal::add);

		// Hitung ulang promo: nilai kembali = yang masih dibayar customer dikurangi yang seharusnya dibayar untuk
		// barang yang tersisa, dibatasi antara 0 dan nilai proporsional (hitung ulang hanya bisa mengurangi, tidak
		// pernah menambah atau menagih customer).
		BigDecimal refundTotal = proportionalTotal;
		if (sale.getPromoScope() != null) {
			BigDecimal paidBefore = sale.getTotal().subtract(allItems.stream().map(SaleItemEntity::getReturnedAmount)
					.reduce(BigDecimal.ZERO, BigDecimal::add));
			BigDecimal candidate = paidBefore.subtract(entitledAfter(sale, allItems, plannedQuantity));
			refundTotal = candidate.max(BigDecimal.ZERO).min(proportionalTotal);
		}

		List<Plan> plans = proportional;
		if (refundTotal.compareTo(proportionalTotal) < 0) {
			// Bagi nilai kembali ke baris yang diretur sesuai bobot nilai proporsionalnya; tiap baris tidak melebihi
			// nilai proporsionalnya sendiri.
			plans = new ArrayList<>();
			BigDecimal assigned = BigDecimal.ZERO;
			for (int i = 0; i < proportional.size(); i++) {
				Plan plan = proportional.get(i);
				BigDecimal share;
				if (proportionalTotal.signum() == 0) {
					share = BigDecimal.ZERO;
				} else if (i == proportional.size() - 1) {
					share = refundTotal.subtract(assigned);
				} else {
					share = refundTotal.multiply(plan.refund()).divide(proportionalTotal, 0, RoundingMode.HALF_UP);
				}
				share = share.max(BigDecimal.ZERO).min(plan.refund());
				assigned = assigned.add(share);
				plans.add(new Plan(plan.item(), plan.quantity(), share.setScale(2, RoundingMode.HALF_UP), plan.restock()));
			}
			refundTotal = plans.stream().map(Plan::refund).reduce(BigDecimal.ZERO, BigDecimal::add);
		}
		BigDecimal promoAdjustment = proportionalTotal.subtract(refundTotal).max(BigDecimal.ZERO);
		return new Calc(sale, plans, refundTotal.setScale(2, RoundingMode.HALF_UP),
				promoAdjustment.setScale(2, RoundingMode.HALF_UP), products);
	}

	// Yang seharusnya dibayar customer untuk barang yang TERSISA setelah retur ini, dengan harga dan diskon yang
	// dibekukan saat transaksi dan promo dievaluasi ulang terhadap jumlah/nilai yang tersisa.
	private BigDecimal entitledAfter(SaleEntity sale, List<SaleItemEntity> items, Map<String, BigDecimal> planned) {
		BigDecimal originalLines = BigDecimal.ZERO;
		BigDecimal originalItemDiscounts = BigDecimal.ZERO;
		BigDecimal remainingLines = BigDecimal.ZERO;
		BigDecimal remainingGross = BigDecimal.ZERO;
		BigDecimal remainingQuantity = BigDecimal.ZERO;
		BigDecimal promoProductGross = BigDecimal.ZERO;
		BigDecimal promoProductQuantity = BigDecimal.ZERO;
		BigDecimal freeTotal = BigDecimal.ZERO;
		BigDecimal freeKept = BigDecimal.ZERO;
		for (SaleItemEntity item : items) {
			BigDecimal remaining = item.getQuantity().subtract(item.getReturnedQuantity())
					.subtract(planned.getOrDefault(item.getId(), BigDecimal.ZERO));
			if (item.isFree()) {
				freeTotal = freeTotal.add(item.getQuantity());
				freeKept = freeKept.add(remaining);
				continue;
			}
			BigDecimal gross = item.getUnitPrice().multiply(remaining);
			BigDecimal fraction = remaining.divide(item.getQuantity(), 10, RoundingMode.HALF_UP);
			remainingGross = remainingGross.add(gross);
			remainingQuantity = remainingQuantity.add(remaining);
			remainingLines = remainingLines.add(gross.subtract(item.getDiscount().multiply(fraction)));
			originalLines = originalLines.add(item.getLineTotal());
			originalItemDiscounts = originalItemDiscounts.add(item.getDiscount());
			if (item.getProductId().equals(sale.getPromoProductId())) {
				promoProductGross = promoProductGross.add(gross);
				promoProductQuantity = promoProductQuantity.add(remaining);
			}
		}

		// Diskon manual tingkat transaksi ikut mengecil sebanding dengan nilai barang yang tersisa.
		BigDecimal transactionDiscount = sale.getManualDiscount().add(sale.getRewardDiscount()).subtract(originalItemDiscounts).max(BigDecimal.ZERO);
		BigDecimal remainingTransactionDiscount = originalLines.signum() == 0 ? BigDecimal.ZERO
				: transactionDiscount.multiply(remainingLines).divide(originalLines, 2, RoundingMode.HALF_UP);

		boolean byProduct = "PRODUCT".equals(sale.getPromoScope());
		boolean byQuantity = "QUANTITY".equals(sale.getPromoConditionType());
		BigDecimal base = byProduct ? promoProductGross : remainingGross;
		BigDecimal measured = byQuantity ? (byProduct ? promoProductQuantity : remainingQuantity) : base;
		boolean met = measured.compareTo(sale.getPromoMinValue()) >= 0;

		BigDecimal promoDiscount = BigDecimal.ZERO;
		BigDecimal freeAdjustment = BigDecimal.ZERO;
		if ("FREE_PRODUCT".equals(sale.getPromoRewardType())) {
			// Syarat tidak terpenuhi lagi: barang gratis yang masih dibawa customer dihitung sebagai harus dibayar.
			if (!met && freeTotal.signum() > 0) {
				freeAdjustment = sale.getPromoValue().multiply(freeKept).divide(freeTotal, 2, RoundingMode.HALF_UP);
			}
		} else if (met) {
			promoDiscount = "DISCOUNT_FIXED".equals(sale.getPromoRewardType())
					? sale.getPromoDiscountValue().min(base)
					: base.multiply(sale.getPromoDiscountValue()).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
		}

		BigDecimal net = remainingLines.subtract(remainingTransactionDiscount).subtract(promoDiscount)
				.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
		BigDecimal total = net.add(saleChargeService.exclusiveFor(saleChargeService.read(sale.getCharges()), net));
		return total.add(freeAdjustment);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<SaleReturnDTO>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(find(new DataFilter(request), branchScopeService.current()), HttpStatus.OK);
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<SaleReturnDTO>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}

	// Isi read untuk satu filter dan cakupan cabang (dipakai juga oleh pengunduhan).
	@Transactional(readOnly = true)
	public DataResult<SaleReturnDTO> find(DataFilter requested, BranchScope scope) {
		DataFilter filter = scope.apply(requested, "branchId");
		if (filter.getOrder() == null) {
			filter.setOrder("registered");
			filter.setDescending(true);
		}
		DataResult<SaleReturnEntity> result = returnRepository.dataTable(filter);
		return new DataResult<>(dtos(result.getData()), result.getTotal(),
					result.getGrandTotal());
	}

	private List<SaleReturnDTO> dtos(List<SaleReturnEntity> returns) {
		Set<String> ids = returns.stream().map(SaleReturnEntity::getId).collect(Collectors.toSet());
		Map<String, List<SaleReturnItemEntity>> items = returnRepository.findItems(ids).stream()
				.collect(Collectors.groupingBy(SaleReturnItemEntity::getReturnId));
		Map<String, SaleItemEntity> saleItems = new HashMap<>();
		saleRepository.findItemsByIds(items.values().stream().flatMap(List::stream)
				.map(SaleReturnItemEntity::getSaleItemId).collect(Collectors.toSet()))
				.forEach(i -> saleItems.put(i.getId(), i));
		Map<String, String> saleSerials = new HashMap<>();
		for (String saleId : returns.stream().map(SaleReturnEntity::getSaleId).collect(Collectors.toSet())) {
			SaleEntity sale = saleRepository.find(saleId);
			saleSerials.put(saleId, sale == null ? null : sale.getSerial());
		}
		Map<String, String> branchNames = branchScopeService
				.names(returns.stream().map(SaleReturnEntity::getBranchId).collect(Collectors.toSet()));
		Map<String, String> usernames = userRepository
				.findUsernames(returns.stream().map(SaleReturnEntity::getCreatedBy).collect(Collectors.toSet()));

		return returns.stream().map(r -> {
			SaleReturnDTO dto = new SaleReturnDTO();
			dto.setId(r.getId());
			dto.setSerial(r.getSerial());
			dto.setSaleId(r.getSaleId());
			dto.setSaleSerial(saleSerials.get(r.getSaleId()));
			dto.setBranchId(r.getBranchId());
			dto.setBranchName(branchNames.get(r.getBranchId()));
			dto.setReason(r.getReason());
			dto.setRefundTotal(r.getRefundTotal());
			dto.setPromoAdjustment(r.getPromoAdjustment());
			dto.setCreatedBy(r.getCreatedBy());
			dto.setCreatedByName(usernames.get(r.getCreatedBy()));
			dto.setRegistered(r.getRegistered());
			dto.setItems(items.getOrDefault(r.getId(), List.of()).stream().map(i -> {
				SaleItemEntity saleItem = saleItems.get(i.getSaleItemId());
				SaleReturnItemDTO item = new SaleReturnItemDTO();
				item.setSaleItemId(i.getSaleItemId());
				item.setProductId(saleItem.getProductId());
				item.setProductSerial(saleItem.getProductSerial());
				item.setProductName(saleItem.getProductName());
				item.setUnit(saleItem.getUnit());
				item.setQuantity(i.getQuantity());
				item.setRefund(i.getRefund());
				item.setRestock(i.isRestock());
				return item;
			}).collect(Collectors.toList()));
			return dto;
		}).collect(Collectors.toList());
	}

}
