package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;
import static com.saasno.service.SqlHelper.clean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.CustomerEntity;
import com.saasno.entity.SaleEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.loyalty.LoyaltyDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

// Poin loyalitas dan voucher.
//  - Poin: customer terdaftar mendapat floor((total tanpa pajak) / LOYALTY_EARN_PER_RUPIAH) poin saat transaksi SELESAI; poin bisa dipakai
//    sebagai potongan LOYALTY_POINT_VALUE rupiah per poin, paling banyak LOYALTY_MAX_REDEEM_PERCENT dari tagihan.
//  - Voucher: kode potongan tetap atau persen (opsional dibatasi customer, masa berlaku, belanja minimal, dan jumlah pakai).
// Potongan keduanya (reward) dikurangkan setelah promo dan diskon manual, tanpa persetujuan. Voucher dan poin yang dipakai diambil
// saat transaksi dibuat dan dikembalikan bila transaksi ditolak, dibatalkan, atau di-void. Poin yang didapat dicabut saat void dan
// sebanding saat retur.
@Service
public class LoyaltyService {

	private static final int MAX_PAGE = 100;
	private static final BigDecimal HUNDRED = new BigDecimal("100");

	@Autowired
	private SqlHelper sql;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ReceivableService receivableService;

	@Autowired
	private PermissionService permissionService;

	public record VoucherUse(String id, String code, BigDecimal discount) {
	}

	// ------------------------------------------------------------------ pengaturan

	public boolean enabled() {
		return "true".equalsIgnoreCase(String.valueOf(configurationRepository.get(ConfigurationKey.LOYALTY_ENABLED)).trim());
	}

	public BigDecimal pointValue() {
		return decimal(ConfigurationKey.LOYALTY_POINT_VALUE, new BigDecimal("100"));
	}

	private BigDecimal earnPerRupiah() {
		BigDecimal value = decimal(ConfigurationKey.LOYALTY_EARN_PER_RUPIAH, new BigDecimal("10000"));
		return value.signum() <= 0 ? new BigDecimal("10000") : value;
	}

	public int maxRedeemPercent() {
		BigDecimal value = decimal(ConfigurationKey.LOYALTY_MAX_REDEEM_PERCENT, new BigDecimal("50"));
		return Math.max(0, Math.min(100, value.intValue()));
	}

	private BigDecimal decimal(ConfigurationKey key, BigDecimal fallback) {
		try {
			return new BigDecimal(String.valueOf(configurationRepository.get(key)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	// ------------------------------------------------------------------ dipakai SaleCalculator (hanya baca)

	// Memeriksa voucher untuk transaksi ini dan menghitung potongannya terhadap `base` (tagihan setelah promo dan diskon manual).
	public VoucherUse resolveVoucher(String codeRaw, String branchId, CustomerEntity customer, BigDecimal subtotalAfterPromo, BigDecimal base) {
		String code = clean(codeRaw) == null ? null : codeRaw.trim().toUpperCase();
		if (code == null) {
			return null;
		}
		List<Object[]> rows = sql.rows("SELECT v.id, v.branch_id, v.type, v.value, v.max_discount, v.min_purchase, v.max_uses, v.used_count, v.customer_id, v.valid_from, "
				+ "v.valid_until, v.status FROM public.voucher v WHERE v.code = :code", "code", code);
		if (rows.isEmpty() || !branchRepository.findAncestorIds(branchId).contains(str(rows.get(0)[1]))) {
			throw new ApiException("404", "Voucher " + code + " tidak ditemukan");
		}
		Object[] v = rows.get(0);
		LocalDate today = LocalDate.now();
		if (!"ACTIVE".equals(str(v[11]))) {
			throw new ApiException("409", "Voucher " + code + " tidak aktif");
		}
		if ((v[9] != null && date(v[9]).isAfter(today)) || (v[10] != null && date(v[10]).isBefore(today))) {
			throw new ApiException("409", "Voucher " + code + " di luar masa berlaku");
		}
		if (v[6] != null && ((Number) v[7]).intValue() >= ((Number) v[6]).intValue()) {
			throw new ApiException("409", "Voucher " + code + " sudah habis dipakai");
		}
		if (v[8] != null && (customer == null || !customer.getId().equals(str(v[8])))) {
			throw new ApiException("409", "Voucher " + code + " khusus untuk customer tertentu");
		}
		if (subtotalAfterPromo.compareTo(dec(v[5])) < 0) {
			throw new ApiException("409", "Voucher " + code + " butuh belanja minimal Rp " + dec(v[5]).stripTrailingZeros().toPlainString());
		}
		BigDecimal discount = "FIXED".equals(str(v[2])) ? dec(v[3]) : base.multiply(dec(v[3])).divide(HUNDRED, 0, RoundingMode.HALF_UP);
		if ("PERCENT".equals(str(v[2])) && v[4] != null) {
			discount = discount.min(dec(v[4]));
		}
		discount = discount.min(base).max(BigDecimal.ZERO);
		return new VoucherUse(str(v[0]), code, discount);
	}

	// Potongan dari poin yang dipakai, terhadap `base` (tagihan setelah voucher). Mengembalikan 0 bila tidak memakai poin.
	public BigDecimal resolvePoints(Long points, CustomerEntity customer, BigDecimal base) {
		if (points == null || points == 0) {
			return BigDecimal.ZERO;
		}
		if (points < 0) {
			throw new ApiException("400", "Poin tidak valid");
		}
		if (!enabled()) {
			throw new ApiException("409", "Program poin belum diaktifkan");
		}
		if (customer == null) {
			throw new ApiException("400", "Pilih customer terdaftar untuk memakai poin");
		}
		long balance = balance(customer.getId());
		if (points > balance) {
			throw new ApiException("409", "Poin customer tidak cukup (saldo " + balance + ")");
		}
		BigDecimal discount = pointValue().multiply(BigDecimal.valueOf(points));
		BigDecimal cap = base.multiply(BigDecimal.valueOf(maxRedeemPercent())).divide(HUNDRED, 0, RoundingMode.DOWN);
		if (discount.compareTo(cap) > 0) {
			long maxPoints = pointValue().signum() == 0 ? 0 : cap.divide(pointValue(), 0, RoundingMode.DOWN).longValue();
			throw new ApiException("409", "Poin yang dipakai maksimal " + maxPoints + " (" + maxRedeemPercent() + "% dari tagihan)");
		}
		return discount;
	}

	public long balance(String customerId) {
		List<Object[]> rows = sql.rows("SELECT p.balance FROM public.customer_point p WHERE p.customer_id = :c", "c", customerId);
		return rows.isEmpty() ? 0 : lng(rows.get(0)[0]);
	}

	// ------------------------------------------------------------------ dipanggil SaleService / SaleReturnService

	// Setelah transaksi tersimpan: ambil voucher (satu pemakaian) dan poin yang dipakai.
	public void onCreated(SaleEntity sale) {
		if (sale.getVoucherId() != null) {
			int changed = sql.execute("UPDATE public.voucher SET used_count = used_count + 1, modificated = :now WHERE id = :id AND (max_uses IS NULL OR used_count < max_uses)",
					"id", sale.getVoucherId(), "now", LocalDateTime.now());
			if (changed == 0) {
				throw new ApiException("409", "Voucher " + sale.getVoucherCode() + " sudah habis dipakai");
			}
		}
		if (sale.getPointsUsed() > 0) {
			change(sale.getCustomerId(), -sale.getPointsUsed(), "REDEEM", sale, "Dipakai pada " + sale.getSerial());
		}
	}

	// Saat transaksi selesai (langsung atau setelah disetujui): customer terdaftar mendapat poin.
	public void onCompleted(SaleEntity sale) {
		if (!enabled() || sale.getCustomerId() == null || sale.getPointsEarned() > 0) {
			return;
		}
		long points = sale.getTotal().subtract(sale.getChargeTotal()).divide(earnPerRupiah(), 0, RoundingMode.DOWN).longValue();
		if (points <= 0) {
			return;
		}
		change(sale.getCustomerId(), points, "EARN", sale, "Belanja " + sale.getSerial());
		sale.setPointsEarned(points);
	}

	// Transaksi ditolak/dibatalkan (belum selesai) atau di-void: kembalikan voucher dan poin yang dipakai, cabut poin yang didapat.
	public void onReversed(SaleEntity sale, String why) {
		if (sale.getVoucherId() != null) {
			sql.execute("UPDATE public.voucher SET used_count = GREATEST(used_count - 1, 0), modificated = :now WHERE id = :id", "id", sale.getVoucherId(), "now", LocalDateTime.now());
		}
		if (sale.getPointsUsed() > 0) {
			change(sale.getCustomerId(), sale.getPointsUsed(), "REFUND", sale, why + " " + sale.getSerial());
		}
		if (sale.getPointsEarned() > 0) {
			long take = Math.min(sale.getPointsEarned(), balance(sale.getCustomerId()));
			if (take > 0) {
				change(sale.getCustomerId(), -take, "REVERSE", sale, why + " " + sale.getSerial());
			}
		}
	}

	// Retur: poin yang didapat dicabut sebanding dengan nilai yang dikembalikan.
	public void onReturned(SaleEntity sale, BigDecimal refundTotal) {
		if (sale.getPointsEarned() <= 0 || sale.getTotal().signum() <= 0) {
			return;
		}
		long take = BigDecimal.valueOf(sale.getPointsEarned()).multiply(refundTotal).divide(sale.getTotal(), 0, RoundingMode.DOWN).longValue();
		take = Math.min(take, balance(sale.getCustomerId()));
		if (take > 0) {
			change(sale.getCustomerId(), -take, "REVERSE", sale, "Retur " + sale.getSerial());
		}
	}

	private void change(String customerId, long delta, String kind, SaleEntity sale, String note) {
		record2(customerId, delta, kind, sale == null ? null : sale.getId(), note, sessionUtility.getUserId());
	}

	private void record2(String customerId, long delta, String kind, String saleId, String note, String userId) {
		sql.rows("SELECT 1 FROM public.customer WHERE id = :c FOR UPDATE", "c", customerId);
		sql.execute("INSERT INTO public.customer_point (customer_id, balance, modificated) VALUES (:c, 0, :now) ON CONFLICT (customer_id) DO NOTHING",
				"c", customerId, "now", LocalDateTime.now());
		long balance = lng(sql.rows("SELECT p.balance FROM public.customer_point p WHERE p.customer_id = :c FOR UPDATE", "c", customerId).get(0)[0]);
		long after = balance + delta;
		if (after < 0) {
			throw new ApiException("409", "Poin customer tidak cukup");
		}
		sql.execute("UPDATE public.customer_point SET balance = :b, modificated = :now WHERE customer_id = :c", "b", after, "c", customerId, "now", LocalDateTime.now());
		sql.execute("INSERT INTO public.point_ledger (id, customer_id, delta, balance_after, kind, sale_id, note, created_by, registered) VALUES (:id, :c, :d, :after, :kind, :sale, :note, :user, :now)",
				SqlHelper.map("id", ID.uuid(), "c", customerId, "d", delta, "after", after, "kind", kind, "sale", saleId, "note", note, "user", userId, "now", LocalDateTime.now()));
	}

	// ------------------------------------------------------------------ baca & koreksi (halaman Poin Customer, layar kasir)

	@Transactional(readOnly = true)
	public ResponseModel<LoyaltyDTO.CustomerPoints> customer(String customerId) {
		BranchScope scope = branchScopeService.current();
		List<Object[]> rows = customerId == null ? List.of()
				: sql.rows("SELECT c.id, c.pic, c.type, c.branch_id FROM public.customer c WHERE c.id = :id", "id", customerId.trim());
		if (rows.isEmpty() || !scope.canSee(str(rows.get(0)[3]))) {
			throw new ApiException("404", "Customer tidak ditemukan");
		}
		return new ResponseModel<LoyaltyDTO.CustomerPoints>().success(new LoyaltyDTO.CustomerPoints(str(rows.get(0)[0]), str(rows.get(0)[1]), str(rows.get(0)[2]),
				balance(str(rows.get(0)[0])), enabled(), pointValue(), maxRedeemPercent(), receivableService.creditLimit(),
				receivableService.outstanding(str(rows.get(0)[0])), permissionService.has("POST_API_SALE_CREDIT")));
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<LoyaltyDTO.CustomerPoints>> customers(String name, Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		StringBuilder where = new StringBuilder("1 = 1");
		Map<String, Object> params = new HashMap<>();
		if (!scope.isGlobal()) {
			where.append(" AND c.branch_id IN (:scope)");
			params.put("scope", scope.getIds());
		}
		if (clean(name) != null) {
			where.append(" AND (LOWER(c.pic) LIKE :name OR LOWER(c.serial) LIKE :name)");
			params.put("name", "%" + name.trim().toLowerCase() + "%");
		}
		long total = lng(sql.rows("SELECT COUNT(*) FROM public.customer c WHERE " + where, params).get(0)[0]);
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		boolean on = enabled();
		BigDecimal value = pointValue();
		int max = maxRedeemPercent();
		List<LoyaltyDTO.CustomerPoints> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT c.id, c.pic, c.type, COALESCE(p.balance, 0) FROM public.customer c LEFT JOIN public.customer_point p ON p.customer_id = c.id WHERE " + where
				+ " ORDER BY COALESCE(p.balance, 0) DESC, c.pic LIMIT " + size + " OFFSET " + start, params)) {
			list.add(new LoyaltyDTO.CustomerPoints(str(r[0]), str(r[1]), str(r[2]), lng(r[3]), on, value, max, BigDecimal.ZERO, BigDecimal.ZERO, false));
		}
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}

	@Transactional(readOnly = true)
	public ResponseModel<List<LoyaltyDTO.LedgerEntry>> ledger(String customerId) {
		customer(customerId);
		List<LoyaltyDTO.LedgerEntry> list = new ArrayList<>();
		for (Object[] r : sql.rows("SELECT l.id, l.delta, l.balance_after, l.kind, s.serial, l.note, u.username, l.registered FROM public.point_ledger l "
				+ "LEFT JOIN public.sale s ON s.id = l.sale_id LEFT JOIN public.\"user\" u ON u.id = l.created_by WHERE l.customer_id = :c ORDER BY l.registered DESC LIMIT 50",
				"c", customerId.trim())) {
			list.add(new LoyaltyDTO.LedgerEntry(str(r[0]), lng(r[1]), lng(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), time(r[7])));
		}
		return new ResponseModel<List<LoyaltyDTO.LedgerEntry>>().success(list);
	}

	// Koreksi manual (bonus atau pengurangan); catatan wajib, saldo tidak boleh minus.
	@Transactional
	public ResponseModel<LoyaltyDTO.CustomerPoints> adjust(LoyaltyDTO.AdjustRequest request) {
		BranchScope scope = branchScopeService.current();
		List<Object[]> rows = request.customerId() == null ? List.of()
				: sql.rows("SELECT c.id, c.branch_id FROM public.customer c WHERE c.id = :id", "id", request.customerId().trim());
		if (rows.isEmpty() || !scope.contains(str(rows.get(0)[1]))) {
			throw new ApiException("404", "Customer tidak ditemukan");
		}
		if (request.points() == 0 || Math.abs(request.points()) > 1_000_000) {
			throw new ApiException("400", "Jumlah poin harus bukan nol dan maksimal 1.000.000");
		}
		String note = clean(request.note());
		if (note == null || note.length() > 255) {
			throw new ApiException("400", "Catatan wajib diisi (maksimal 255 karakter)");
		}
		record2(request.customerId().trim(), request.points(), "ADJUST", null, note, sessionUtility.getUserId());
		return customer(request.customerId());
	}
}
