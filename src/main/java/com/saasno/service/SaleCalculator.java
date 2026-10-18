package com.saasno.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.CustomerEntity;
import com.saasno.entity.ProductEntity;
import com.saasno.entity.PromoEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.CustomerRepository;
import com.saasno.repository.ProductRepository;
import com.saasno.repository.PromoRepository;
import com.saasno.repository.StockRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.sale.SaleChargeDTO;
import com.saasno.utility.dto.product.ProductPriceResultDTO;
import com.saasno.utility.dto.sale.SaleRequestDTO;
import com.saasno.utility.dto.sale.SaleRequestItemDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.picker.PromoAudience;
import com.saasno.utility.picker.PromoCondition;
import com.saasno.utility.picker.PromoReward;
import com.saasno.utility.picker.PromoScope;

// Menghitung keranjang sepenuhnya di server: harga satuan (customer, tipe customer, dasar, bertingkat),
// SATU promo yang paling menguntungkan, diskon manual, pajak, pembulatan ke rupiah utuh, dan alokasi nilai
// akhir per baris (dipakai menghitung retur). Angka dari browser tidak pernah dipercaya.
@Service
public class SaleCalculator {

	private static final int MAX_ITEMS = 100;
	private static final int MAX_REASON = 500;
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

	public static class Line {
		public ProductEntity product;
		public BigDecimal quantity;
		public BigDecimal unitPrice;
		public String priceSource;
		public BigDecimal tierMinQuantity;
		public BigDecimal gross;
		public BigDecimal discount = BigDecimal.ZERO;
		public BigDecimal lineTotal;
		public BigDecimal netTotal = BigDecimal.ZERO;
		public boolean free;
	}

	public static class Cart {
		public String branchId;
		public CustomerEntity customer;
		public List<Line> lines = new ArrayList<>();
		public BigDecimal subtotal;
		public PromoEntity promo;
		public BigDecimal promoDiscount = BigDecimal.ZERO;
		// Nilai manfaat promo saat transaksi (potongan, atau harga barang gratis); dibekukan untuk hitung ulang retur.
		public BigDecimal promoValue = BigDecimal.ZERO;
		// Seluruh potongan di luar promo: diskon manual + reward (voucher dan poin). Dipakai menghitung nilai akhir dan alokasi per baris.
		public BigDecimal manualDiscount = BigDecimal.ZERO;
		// Hanya diskon manual (yang butuh alasan dan persetujuan bila melebihi batas).
		public BigDecimal manualOnly = BigDecimal.ZERO;
		public String discountReason;
		public BigDecimal rewardDiscount = BigDecimal.ZERO;
		public String voucherId;
		public String voucherCode;
		public long pointsUsed;
		// Biaya lain-lain (pajak dll.), dihitung satu per satu dari nilai akhir; exclusiveCharge = yang ditambahkan ke tagihan.
		public List<SaleChargeDTO> charges = new ArrayList<>();
		public BigDecimal chargeTotal = BigDecimal.ZERO;
		public BigDecimal exclusiveCharge = BigDecimal.ZERO;
		public BigDecimal total;
		public BigDecimal limitPercent;
		public boolean requiresApproval;
	}

	private record Candidate(PromoEntity promo, BigDecimal discount, ProductEntity freeProduct, BigDecimal freeQuantity,
			BigDecimal value) {
	}

	@Autowired
	private SaleChargeService saleChargeService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CustomerRepository customerRepository;

	@Autowired
	private PromoRepository promoRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private LoyaltyService loyaltyService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private StockService stockService;

	@Autowired
	private ProductService productService;

	@Transactional(readOnly = true)
	public Cart calculate(String branchId, SaleRequestDTO request) {
		List<SaleRequestItemDTO> items = request.getItems() == null ? List.of() : request.getItems();
		if (items.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (items.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}

		Cart cart = new Cart();
		cart.branchId = branchId;
		cart.customer = customer(branchId, request.getCustomerId());

		Set<String> seen = new HashSet<>();
		for (SaleRequestItemDTO item : items) {
			String productId = item == null || item.getProductId() == null ? null : item.getProductId().trim();
			if (productId == null || productId.isEmpty()) {
				throw new ApiException("400", "Produk tidak boleh kosong");
			}
			if (!seen.add(productId)) {
				throw new ApiException("400", "Produk tidak boleh ganda");
			}
			ProductEntity product = stockService.visibleProduct(branchId, productId);
			if ("DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
			BigDecimal quantity = stockService.toBase(product, item.getQuantity(), item.getUnit(), false);
			ProductPriceResultDTO price = productService.priceFor(product, cart.customer, quantity);

			Line line = new Line();
			line.product = product;
			line.quantity = quantity;
			line.unitPrice = price.getUnitPrice();
			line.priceSource = price.getPriceSource();
			line.tierMinQuantity = price.getTierMinQuantity();
			line.gross = line.unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
			line.discount = rupiah(item.getDiscount(), "Diskon barang");
			if (line.discount.compareTo(line.gross) > 0) {
				throw new ApiException("400", "Diskon barang melebihi nilai barang: " + product.getName());
			}
			line.lineTotal = line.gross.subtract(line.discount);
			cart.lines.add(line);
		}
		cart.subtotal = cart.lines.stream().map(l -> l.gross).reduce(BigDecimal.ZERO, BigDecimal::add);

		applyBestPromo(cart);

		BigDecimal transactionDiscount = rupiah(request.getDiscount(), "Diskon");
		cart.manualDiscount = cart.lines.stream().map(l -> l.discount).reduce(BigDecimal.ZERO, BigDecimal::add)
				.add(transactionDiscount);
		BigDecimal afterPromo = cart.subtotal.subtract(cart.promoDiscount);
		if (cart.manualDiscount.compareTo(afterPromo) > 0) {
			throw new ApiException("400", "Diskon manual melebihi jumlah yang harus dibayar");
		}
		if (cart.manualDiscount.signum() > 0) {
			String reason = request.getDiscountReason() == null ? null : request.getDiscountReason().trim();
			if (reason == null || reason.isEmpty()) {
				throw new ApiException("400", "Alasan diskon tidak boleh kosong");
			}
			if (reason.length() > MAX_REASON) {
				throw new ApiException("400", "Alasan diskon tidak boleh lebih dari " + MAX_REASON + " karakter");
			}
			cart.discountReason = reason;
		}
		cart.manualOnly = cart.manualDiscount;

		// Reward: voucher lalu poin, dikurangkan dari tagihan setelah promo dan diskon manual; tidak butuh persetujuan.
		BigDecimal base = afterPromo.subtract(cart.manualOnly);
		LoyaltyService.VoucherUse voucher = loyaltyService.resolveVoucher(request.getVoucherCode(), branchId, cart.customer, afterPromo, base);
		BigDecimal voucherDiscount = voucher == null ? BigDecimal.ZERO : voucher.discount();
		BigDecimal pointsDiscount = loyaltyService.resolvePoints(request.getRedeemPoints(), cart.customer, base.subtract(voucherDiscount));
		cart.rewardDiscount = voucherDiscount.add(pointsDiscount);
		if (voucher != null) {
			cart.voucherId = voucher.id();
			cart.voucherCode = voucher.code();
		}
		cart.pointsUsed = pointsDiscount.signum() > 0 ? request.getRedeemPoints() : 0;
		cart.manualDiscount = cart.manualOnly.add(cart.rewardDiscount);

		// Nilai yang dibayar dibulatkan ke rupiah utuh sebelum pajak.
		BigDecimal net = afterPromo.subtract(cart.manualDiscount).setScale(0, RoundingMode.HALF_UP);
		SaleChargeService.Result charged = saleChargeService.compute(net);
		cart.charges = charged.charges();
		cart.chargeTotal = charged.total();
		cart.exclusiveCharge = charged.exclusive();
		cart.total = net.add(charged.exclusive()).setScale(2);
		allocate(cart, net);

		cart.limitPercent = decimal(ConfigurationKey.MANUAL_DISCOUNT_MAX_PERCENT, new BigDecimal("10"));
		cart.requiresApproval = cart.manualOnly.signum() > 0
				&& cart.manualOnly.multiply(HUNDRED).compareTo(cart.limitPercent.multiply(afterPromo)) > 0;

		assertStock(cart);
		return cart;
	}

	// Transaksi offline: harga per satuan dasar ditentukan kasir saat itu (tanpa promo, diskon, voucher, atau poin). Pajak dan
	// alokasi per baris dihitung dengan aturan yang sama; stok diperiksa seperti biasa.
	@Transactional(readOnly = true)
	public Cart priceOffline(String branchId, String customerId, List<com.saasno.utility.dto.sale.SaleOfflineDTO.Item> items) {
		if (items == null || items.isEmpty()) {
			throw new ApiException("400", "Barang tidak boleh kosong");
		}
		if (items.size() > MAX_ITEMS) {
			throw new ApiException("400", "Barang tidak boleh lebih dari " + MAX_ITEMS);
		}
		Cart cart = new Cart();
		cart.branchId = branchId;
		cart.customer = customer(branchId, customerId);
		Set<String> seen = new HashSet<>();
		for (com.saasno.utility.dto.sale.SaleOfflineDTO.Item item : items) {
			String productId = item == null || item.productId() == null ? null : item.productId().trim();
			if (productId == null || productId.isEmpty() || !seen.add(productId)) {
				throw new ApiException("400", "Produk tidak boleh kosong atau ganda");
			}
			ProductEntity product = stockService.visibleProduct(branchId, productId);
			if ("DISABLED".equals(product.getStatus())) {
				throw new ApiException("409", "Produk nonaktif: " + product.getSerial() + " " + product.getName());
			}
			BigDecimal quantity = stockService.toBase(product, item.quantity(), null, false);
			BigDecimal unitPrice = item.unitPrice();
			if (unitPrice == null || unitPrice.signum() < 0 || unitPrice.stripTrailingZeros().scale() > 2 || unitPrice.compareTo(MAX_AMOUNT) > 0) {
				throw new ApiException("400", "Harga tidak valid: " + product.getName());
			}
			String source = item.priceSource() == null ? "BASE" : item.priceSource();
			if (!source.equals("BASE") && !source.equals("CUSTOMER") && !source.equals("CUSTOMER_TYPE")) {
				source = "BASE";
			}
			Line line = new Line();
			line.product = product;
			line.quantity = quantity;
			line.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
			line.priceSource = source;
			line.gross = line.unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
			line.lineTotal = line.gross;
			cart.lines.add(line);
		}
		cart.subtotal = cart.lines.stream().map(l -> l.gross).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal net = cart.subtotal.setScale(0, RoundingMode.HALF_UP);
		SaleChargeService.Result charged = saleChargeService.compute(net);
		cart.charges = charged.charges();
		cart.chargeTotal = charged.total();
		cart.exclusiveCharge = charged.exclusive();
		cart.total = net.add(charged.exclusive()).setScale(2);
		allocate(cart, net);
		cart.limitPercent = decimal(ConfigurationKey.MANUAL_DISCOUNT_MAX_PERCENT, new BigDecimal("10"));
		assertStock(cart);
		return cart;
	}

	// ---------------------------------------------------------------- promo

	// Hanya SATU promo yang dipakai: yang paling menguntungkan customer. Barang gratis dinilai dengan harganya.
	// Promo berlaku di cabang pemiliknya dan semua turunannya, jadi yang dihitung adalah promo milik cabang
	// transaksi dan induk-induknya.
	private void applyBestPromo(Cart cart) {
		Set<String> branchIds = branchRepository.findAncestorIds(cart.branchId);
		Candidate best = null;
		for (PromoEntity promo : promoRepository.findActive(branchIds, LocalDate.now())) {
			Candidate candidate = evaluate(promo, cart);
			if (candidate != null && candidate.value().signum() > 0
					&& (best == null || candidate.value().compareTo(best.value()) > 0)) {
				best = candidate;
			}
		}
		if (best == null) {
			return;
		}
		cart.promo = best.promo();
		cart.promoDiscount = best.discount().setScale(2);
		cart.promoValue = best.value().setScale(2, RoundingMode.HALF_UP);
		if (best.freeProduct() != null) {
			Line free = new Line();
			free.product = best.freeProduct();
			free.quantity = best.freeQuantity();
			free.unitPrice = BigDecimal.ZERO.setScale(2);
			free.priceSource = "PROMO";
			free.gross = BigDecimal.ZERO.setScale(2);
			free.lineTotal = BigDecimal.ZERO.setScale(2);
			free.free = true;
			cart.lines.add(free);
		}
	}

	private Candidate evaluate(PromoEntity promo, Cart cart) {
		PromoAudience audience = PromoAudience.fromString(promo.getAudience());
		CustomerEntity customer = cart.customer;
		if (audience == PromoAudience.TYPE && (customer == null || !customer.getType().equals(promo.getCustomerType()))) {
			return null;
		}
		if (audience == PromoAudience.CUSTOMER && (customer == null
				|| promo.getCustomers().stream().noneMatch(c -> c.getId().equals(customer.getId())))) {
			return null;
		}

		boolean byQuantity = PromoCondition.fromString(promo.getConditionType()) == PromoCondition.QUANTITY;
		BigDecimal base;
		BigDecimal measured;
		if (PromoScope.fromString(promo.getScope()) == PromoScope.PRODUCT) {
			Line line = cart.lines.stream().filter(l -> !l.free && l.product.getId().equals(promo.getProductId()))
					.findFirst().orElse(null);
			if (line == null) {
				return null;
			}
			base = line.gross;
			measured = byQuantity ? line.quantity : line.gross;
		} else {
			base = cart.subtotal;
			measured = byQuantity
					? cart.lines.stream().filter(l -> !l.free).map(l -> l.quantity).reduce(BigDecimal.ZERO, BigDecimal::add)
					: cart.subtotal;
		}
		if (measured.compareTo(promo.getMinValue()) < 0) {
			return null;
		}

		switch (PromoReward.fromString(promo.getRewardType())) {
		case DISCOUNT_FIXED: {
			BigDecimal discount = promo.getDiscountValue().min(base);
			return new Candidate(promo, discount, null, null, discount);
		}
		case DISCOUNT_PERCENT: {
			BigDecimal discount = base.multiply(promo.getDiscountValue()).divide(HUNDRED, 0, RoundingMode.HALF_UP);
			return new Candidate(promo, discount, null, null, discount);
		}
		default: {
			ProductEntity reward = promo.getRewardProductId() == null ? null
					: productRepository.find(promo.getRewardProductId());
			if (reward == null || "DISABLED".equals(reward.getStatus())
					|| !branchScopeService.subtree(cart.branchId).canSee(reward.getBranchId())) {
				return null;
			}
			BigDecimal quantity = promo.getRewardQuantity();
			// Stok harus cukup untuk barang gratis ditambah barang yang sama yang dibeli.
			BigDecimal bought = cart.lines.stream().filter(l -> !l.free && l.product.getId().equals(reward.getId()))
					.map(l -> l.quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
			if (stockRepository.quantity(cart.branchId, reward.getId()).compareTo(bought.add(quantity)) < 0) {
				return null;
			}
			BigDecimal value = productService.priceFor(reward, cart.customer, quantity).getUnitPrice()
					.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
			return new Candidate(promo, BigDecimal.ZERO, reward, quantity, value);
		}
		}
	}

	// ---------------------------------------------------------------- bantu

	// Nilai akhir tiap baris berbayar (dipakai menghitung retur). Potongan promo hanya dibagi ke baris yang berhak
	// (promo per produk -> hanya baris produk itu; promo total -> semua baris), diskon manual transaksi dibagi ke
	// semua baris sebanding dengan nilainya, dan pajak (bila belum termasuk harga) sebanding dengan nilai akhir.
	// Baris terakhir menanggung sisa pembulatan, jadi jumlahnya selalu sama dengan total.
	private void allocate(Cart cart, BigDecimal net) {
		List<Line> paid = cart.lines.stream().filter(l -> !l.free).toList();
		if (paid.isEmpty()) {
			return;
		}
		BigDecimal lineSum = paid.stream().map(l -> l.lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal itemDiscounts = paid.stream().map(l -> l.discount).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal transactionDiscount = cart.manualDiscount.subtract(itemDiscounts).max(BigDecimal.ZERO);

		// Baris yang berhak atas potongan promo.
		List<Line> eligible = paid;
		if (cart.promo != null && PromoScope.fromString(cart.promo.getScope()) == PromoScope.PRODUCT) {
			eligible = paid.stream().filter(l -> l.product.getId().equals(cart.promo.getProductId())).toList();
		}
		BigDecimal eligibleGross = eligible.stream().map(l -> l.gross).reduce(BigDecimal.ZERO, BigDecimal::add);

		Map<Line, BigDecimal> raw = new java.util.IdentityHashMap<>();
		BigDecimal promoLeft = cart.promoDiscount;
		BigDecimal manualLeft = transactionDiscount;
		for (int i = 0; i < paid.size(); i++) {
			Line line = paid.get(i);
			boolean last = i == paid.size() - 1;
			BigDecimal promoShare = BigDecimal.ZERO;
			if (eligible.contains(line) && eligibleGross.signum() > 0 && cart.promoDiscount.signum() > 0) {
				promoShare = line == eligible.get(eligible.size() - 1) ? promoLeft
						: cart.promoDiscount.multiply(line.gross).divide(eligibleGross, 2, RoundingMode.HALF_UP).min(promoLeft);
			}
			BigDecimal manualShare = last ? manualLeft
					: lineSum.signum() == 0 ? BigDecimal.ZERO
					: transactionDiscount.multiply(line.lineTotal).divide(lineSum, 2, RoundingMode.HALF_UP).min(manualLeft);
			promoLeft = promoLeft.subtract(promoShare);
			manualLeft = manualLeft.subtract(manualShare);
			raw.put(line, line.lineTotal.subtract(promoShare).subtract(manualShare).max(BigDecimal.ZERO));
		}

		// Bulatkan ke rupiah utuh; baris terakhir menyesuaikan supaya jumlah nilai akhir = net.
		BigDecimal netLeft = net;
		for (int i = 0; i < paid.size(); i++) {
			Line line = paid.get(i);
			BigDecimal share = i == paid.size() - 1 ? netLeft.max(BigDecimal.ZERO)
					: raw.get(line).setScale(0, RoundingMode.HALF_UP).min(netLeft).max(BigDecimal.ZERO);
			netLeft = netLeft.subtract(share);
			line.netTotal = share.setScale(2);
		}

		// Biaya yang belum termasuk harga ditambahkan ke tiap baris sebanding dengan nilai akhirnya.
		if (cart.exclusiveCharge.signum() > 0 && net.signum() > 0) {
			BigDecimal chargeLeft = cart.exclusiveCharge;
			for (int i = 0; i < paid.size(); i++) {
				Line line = paid.get(i);
				BigDecimal share = i == paid.size() - 1 ? chargeLeft
						: cart.exclusiveCharge.multiply(line.netTotal).divide(net, 0, RoundingMode.HALF_UP).min(chargeLeft);
				chargeLeft = chargeLeft.subtract(share);
				line.netTotal = line.netTotal.add(share).setScale(2);
			}
		}
	}

	private void assertStock(Cart cart) {
		Map<String, BigDecimal> needed = new HashMap<>();
		Map<String, ProductEntity> products = new HashMap<>();
		for (Line line : cart.lines) {
			needed.merge(line.product.getId(), line.quantity, BigDecimal::add);
			products.put(line.product.getId(), line.product);
		}
		needed.forEach((productId, quantity) -> {
			if (stockRepository.quantity(cart.branchId, productId).compareTo(quantity) < 0) {
				ProductEntity product = products.get(productId);
				throw new ApiException("409", "Stok tidak cukup untuk " + product.getSerial() + " " + product.getName());
			}
		});
	}

	private CustomerEntity customer(String branchId, String customerId) {
		String id = customerId == null ? null : customerId.trim();
		if (id == null || id.isEmpty()) {
			return null;
		}
		CustomerEntity customer = customerRepository.find(id);
		if (customer == null || !branchScopeService.subtree(branchId).contains(customer.getBranchId())) {
			throw new ApiException("404", "Customer tidak ditemukan");
		}
		if ("DISABLED".equals(customer.getStatus())) {
			throw new ApiException("409", "Customer nonaktif");
		}
		return customer;
	}

	// Rupiah utuh >= 0; kosong dianggap 0.
	private BigDecimal rupiah(BigDecimal value, String field) {
		if (value == null) {
			return BigDecimal.ZERO.setScale(2);
		}
		if (value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || value.setScale(2, RoundingMode.UNNECESSARY).compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", field + " tidak valid");
		}
		return value.setScale(2, RoundingMode.UNNECESSARY);
	}

	private BigDecimal decimal(ConfigurationKey key, BigDecimal fallback) {
		String value = configurationRepository.get(key);
		try {
			return value == null ? fallback : new BigDecimal(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}
