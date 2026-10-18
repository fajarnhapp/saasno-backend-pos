package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;
import static com.saasno.service.AnalyticsSupport.time;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.BranchEntity;
import com.saasno.repository.BranchRepository;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.shift.ShiftDTO;
import com.saasno.utility.dto.shift.ShiftRequestDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.generator.ID;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataResult;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

// Shift kasir (laci kas). Satu shift terbuka per kasir; selama SHIFT_REQUIRED, penjualan dan retur hanya bisa dibuat
// oleh kasir yang shiftnya terbuka di cabang itu. Uang yang seharusnya ada saat tutup:
//   modal awal + penjualan (selesai, menunggu persetujuan, atau kemudian di-void; yang diterima kasir ini) - retur yang
//   diproses kasir ini - void yang dilakukan kasir ini.
// Dikaitkan ke shift lewat kasir + cabang + rentang waktu (shift satu kasir tidak pernah tumpang tindih), jadi tidak ada
// kolom baru di sale/sale_return. Penjualan yang ditolak/dibatalkan dianggap uangnya dikembalikan di shift yang sama.
// Semua pembayaran sekarang tunai.
@Service
public class ShiftService {

	private static final int DEFAULT_MAX_HOURS = 16;
	private static final BigDecimal DEFAULT_LIMIT = new BigDecimal("5000");
	private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
	private static final int MAX_NOTE = 500;
	private static final int MAX_PAGE = 100;
	private static final DateTimeFormatter SERIAL_DAY = DateTimeFormatter.ofPattern("yyMMdd");

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	// cashSales = uang yang benar-benar diterima saat transaksi (tanpa bagian piutang); collections = pembayaran piutang yang diterima.
	private record Totals(BigDecimal cashSales, BigDecimal collections, BigDecimal refunds, BigDecimal voids, long saleCount) {
		BigDecimal expected(BigDecimal opening) {
			return opening.add(cashSales).add(collections).subtract(refunds).subtract(voids);
		}
	}

	// ------------------------------------------------------------------ penjaga penjualan

	// Dipanggil sebelum membuat penjualan/retur. Tanpa SHIFT_REQUIRED tidak ada pembatasan.
	public void assertOpen(String userId, String branchId) {
		if (!"true".equalsIgnoreCase(config(ConfigurationKey.SHIFT_REQUIRED, "true"))) {
			return;
		}
		List<Object[]> rows = rows("SELECT s.branch_id, s.opened_at FROM public.cash_shift s WHERE s.user_id = :user AND s.status = 'OPEN'",
				"user", userId);
		if (rows.isEmpty()) {
			throw new ApiException("409", "Buka shift kasir terlebih dahulu sebelum bertransaksi");
		}
		if (!branchId.equals(str(rows.get(0)[0]))) {
			throw new ApiException("409", "Shift Anda terbuka di cabang lain; tutup shift itu atau pindah ke cabang yang sama");
		}
		if (overdue(time(rows.get(0)[1]))) {
			throw new ApiException("409", "Shift sudah lebih dari " + maxHours() + " jam; tutup shift lalu buka yang baru");
		}
	}

	// Transaksi offline yang dikirim kemudian: harus terjadi selama shift yang sekarang masih terbuka (sudah dibuka sebelum transaksi).
	// Shift yang sudah ditutup tidak boleh bertambah transaksinya karena angkanya sudah dibekukan.
	public void assertOpenAt(String userId, String branchId, LocalDateTime at) {
		if (!"true".equalsIgnoreCase(config(ConfigurationKey.SHIFT_REQUIRED, "true"))) {
			return;
		}
		List<Object[]> rows = rows("SELECT s.branch_id, s.opened_at FROM public.cash_shift s WHERE s.user_id = :user AND s.status = 'OPEN'", "user", userId);
		if (rows.isEmpty()) {
			throw new ApiException("409", "Shift saat transaksi ini terjadi sudah ditutup; hubungi manajer untuk mencatatnya");
		}
		if (!branchId.equals(str(rows.get(0)[0]))) {
			throw new ApiException("409", "Shift Anda terbuka di cabang lain");
		}
		if (time(rows.get(0)[1]).withNano(0).isAfter(at)) {
			throw new ApiException("409", "Transaksi terjadi sebelum shift yang sedang terbuka dibuka; shift lamanya sudah ditutup");
		}
	}

	// ------------------------------------------------------------------ buka / tutup

	@Transactional(readOnly = true)
	public ResponseModel<ShiftDTO.State> current() {
		branchScopeService.current();
		List<ShiftDTO> list = find("s.user_id = :user AND s.status = 'OPEN'", Map.of("user", sessionUtility.getUserId()), 0, 1);
		boolean required = "true".equalsIgnoreCase(config(ConfigurationKey.SHIFT_REQUIRED, "true"));
		return new ResponseModel<ShiftDTO.State>().success(new ShiftDTO.State(required, list.isEmpty() ? null : list.get(0)));
	}

	@Transactional
	public ResponseModel<ShiftDTO> open(ShiftRequestDTO request) {
		BranchScope scope = branchScopeService.current();
		String userId = sessionUtility.getUserId();
		BigDecimal opening = amount(request.openingCash(), "Modal awal");
		String branchId = scope.isGlobal() ? clean(request.branchId()) : scope.getBranchId();
		if (branchId == null) {
			throw new ApiException("400", "Cabang tidak boleh kosong");
		}
		BranchEntity branch = branchRepository.find(branchId);
		if (branch == null || !scope.contains(branchId)) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		lock("cash_shift_open:" + userId);
		if (!rows("SELECT 1 FROM public.cash_shift s WHERE s.user_id = :user AND s.status = 'OPEN'", "user", userId).isEmpty()) {
			throw new ApiException("409", "Anda masih punya shift yang terbuka");
		}
		LocalDateTime now = LocalDateTime.now();
		String prefix = "SFT/" + branch.getSerial() + "/" + LocalDate.now().format(SERIAL_DAY) + "/";
		lock("cash_shift_serial:" + prefix);
		List<Object[]> latest = rows("SELECT MAX(s.serial) FROM public.cash_shift s WHERE s.serial LIKE :prefix", "prefix", prefix + "%");
		int sequence = latest.isEmpty() || latest.get(0)[0] == null ? 1 : Integer.parseInt(str(latest.get(0)[0]).substring(prefix.length())) + 1;
		String id = ID.uuid();
		execute("INSERT INTO public.cash_shift (id, serial, branch_id, user_id, status, opened_at, opening_cash, forced, modificated) "
				+ "VALUES (:id, :serial, :branch, :user, 'OPEN', :now, :opening, false, :now)",
				"id", id, "serial", prefix + String.format("%03d", sequence), "branch", branchId, "user", userId, "now", now,
				"opening", opening);
		return new ResponseModel<ShiftDTO>().success(one(id));
	}

	@Transactional
	public ResponseModel<ShiftDTO> close(ShiftRequestDTO request) {
		branchScopeService.current();
		String userId = sessionUtility.getUserId();
		List<Object[]> open = rows("SELECT s.id FROM public.cash_shift s WHERE s.user_id = :user AND s.status = 'OPEN' FOR UPDATE", "user", userId);
		if (open.isEmpty()) {
			throw new ApiException("409", "Tidak ada shift yang terbuka");
		}
		return new ResponseModel<ShiftDTO>().success(finish(str(open.get(0)[0]), request.countedCash(), request.note(), userId, false));
	}

	@Transactional
	public ResponseModel<ShiftDTO> forceClose(ShiftRequestDTO request) {
		BranchScope scope = branchScopeService.current();
		String id = clean(request.id());
		List<Object[]> open = id == null ? List.of()
				: rows("SELECT s.branch_id, s.user_id FROM public.cash_shift s WHERE s.id = :id AND s.status = 'OPEN' FOR UPDATE", "id", id);
		if (open.isEmpty() || !scope.contains(str(open.get(0)[0]))) {
			throw new ApiException("404", "Shift terbuka tidak ditemukan");
		}
		String actor = sessionUtility.getUserId();
		if (actor.equals(str(open.get(0)[1]))) {
			throw new ApiException("409", "Tutup shift Anda sendiri lewat tombol Tutup Shift");
		}
		String reason = clean(request.note());
		if (reason == null) {
			throw new ApiException("400", "Alasan penutupan paksa wajib diisi");
		}
		return new ResponseModel<ShiftDTO>().success(finish(id, request.countedCash(), reason, actor, true));
	}

	// Menutup shift: hitung ulang angka akhir di dalam transaksi lalu bekukan ke baris shift.
	private ShiftDTO finish(String id, BigDecimal countedRaw, String noteRaw, String closer, boolean forced) {
		BigDecimal counted = amount(countedRaw, "Uang hasil hitung");
		String note = clean(noteRaw);
		if (note != null && note.length() > MAX_NOTE) {
			throw new ApiException("400", "Catatan tidak boleh lebih dari " + MAX_NOTE + " karakter");
		}
		Object[] shift = rows("SELECT s.branch_id, s.user_id, s.opened_at, s.opening_cash FROM public.cash_shift s WHERE s.id = :id", "id", id).get(0);
		LocalDateTime now = LocalDateTime.now();
		BigDecimal opening = dec(shift[3]);
		Totals totals = totals(str(shift[1]), str(shift[0]), time(shift[2]), now);
		BigDecimal expected = totals.expected(opening);
		BigDecimal difference = counted.subtract(expected);
		if (difference.abs().compareTo(differenceLimit()) > 0 && note == null) {
			throw new ApiException("400", "Selisih uang " + difference.toPlainString() + " melebihi batas; isi catatan penyebabnya");
		}
		execute("UPDATE public.cash_shift SET status = 'CLOSED', closed_at = :now, closed_by = :closer, forced = :forced, expected_cash = :expected, "
				+ "counted_cash = :counted, difference = :difference, cash_sales = :cashSales, collections = :collections, refunds = :refunds, voids = :voids, "
				+ "sale_count = :saleCount, note = :note, modificated = :now WHERE id = :id",
				"id", id, "now", now, "closer", closer, "forced", forced, "expected", expected, "counted", counted,
				"difference", difference, "cashSales", totals.cashSales(), "collections", totals.collections(), "refunds", totals.refunds(), "voids", totals.voids(),
				"saleCount", (int) totals.saleCount(), "note", note);
		return one(id);
	}

	// ------------------------------------------------------------------ baca

	// Rincian satu shift: milik sendiri, atau shift cabang dalam cakupan bagi yang punya izin daftar shift.
	@Transactional(readOnly = true)
	public ResponseModel<ShiftDTO> summary(String id) {
		BranchScope scope = branchScopeService.current();
		ShiftDTO dto = id == null ? null : one(id.trim());
		if (dto == null || !(scope.contains(dto.branchId()) && (dto.userId().equals(sessionUtility.getUserId()) || canSeeAll()))) {
			throw new ApiException("404", "Shift tidak ditemukan");
		}
		return new ResponseModel<ShiftDTO>().success(dto);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ShiftDTO>> mine(String status, LocalDate from, LocalDate to, Integer offset, Integer limit) {
		branchScopeService.current();
		return page("s.user_id = :me", Map.of("me", sessionUtility.getUserId()), null, status, from, to, offset, limit);
	}

	@Transactional(readOnly = true)
	public ResponseEntity<DataResult<ShiftDTO>> all(String branchId, String userId, String status, LocalDate from, LocalDate to,
			Integer offset, Integer limit) {
		BranchScope scope = branchScopeService.current();
		String wanted = clean(branchId);
		if (wanted != null && !scope.contains(wanted)) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		String where = wanted != null ? "s.branch_id = :wanted" : scope.isGlobal() ? "1 = 1" : "s.branch_id IN (:scope)";
		Map<String, Object> params = new java.util.HashMap<>();
		if (wanted != null) {
			params.put("wanted", wanted);
		} else if (!scope.isGlobal()) {
			params.put("scope", scope.getIds());
		}
		return page(where, params, clean(userId), status, from, to, offset, limit);
	}

	private ResponseEntity<DataResult<ShiftDTO>> page(String where, Map<String, Object> base, String userId,
			String status, LocalDate from, LocalDate to, Integer offset, Integer limit) {
		StringBuilder sql = new StringBuilder(where);
		Map<String, Object> params = new java.util.HashMap<>(base);
		String wantedStatus = clean(status);
		if (wantedStatus != null) {
			if (!wantedStatus.equals("OPEN") && !wantedStatus.equals("CLOSED")) {
				throw new ApiException("400", "Status shift tidak valid");
			}
			sql.append(" AND s.status = :status");
			params.put("status", wantedStatus);
		}
		if (userId != null) {
			sql.append(" AND s.user_id = :uid");
			params.put("uid", userId);
		}
		if (from != null) {
			sql.append(" AND s.opened_at >= :from");
			params.put("from", from.atStartOfDay());
		}
		if (to != null) {
			sql.append(" AND s.opened_at < :to");
			params.put("to", to.plusDays(1).atStartOfDay());
		}
		int size = limit == null || limit < 1 ? 10 : Math.min(limit, MAX_PAGE);
		int start = offset == null || offset < 0 ? 0 : offset;
		long total = lng(rows("SELECT COUNT(*) FROM public.cash_shift s WHERE " + sql, params).get(0)[0]);
		List<ShiftDTO> list = find(sql.toString(), params, start, size);
		return ResponseEntity.ok(new DataResult<>(list, BigInteger.valueOf(total), BigInteger.valueOf(total)));
	}

	private boolean canSeeAll() {
		return !rows("SELECT 1 FROM public.role_api ra JOIN public.user_role ur ON ur.role_id = ra.role_id WHERE ur.user_id = :user AND ra.api_id = 'GET_API_SHIFT'",
				"user", sessionUtility.getUserId()).isEmpty();
	}

	// ------------------------------------------------------------------ hitung & susun

	private ShiftDTO one(String id) {
		List<ShiftDTO> list = find("s.id = :id", Map.of("id", id), 0, 1);
		return list.isEmpty() ? null : list.get(0);
	}

	private List<ShiftDTO> find(String where, Map<String, Object> params, int offset, int limit) {
		List<Object[]> rows = rows("SELECT s.id, s.serial, s.branch_id, b.name, s.user_id, u.username, s.status, s.opened_at, s.opening_cash, s.closed_at, "
				+ "s.closed_by, cu.username, s.forced, s.cash_sales, s.refunds, s.voids, s.sale_count, s.expected_cash, s.counted_cash, s.difference, s.note, s.collections "
				+ "FROM public.cash_shift s JOIN public.branch b ON b.id = s.branch_id JOIN public.\"user\" u ON u.id = s.user_id "
				+ "LEFT JOIN public.\"user\" cu ON cu.id = s.closed_by WHERE " + where + " ORDER BY s.opened_at DESC LIMIT " + limit + " OFFSET " + offset, params);
		int maxHours = maxHours();
		BigDecimal limitValue = differenceLimit();
		List<ShiftDTO> list = new ArrayList<>();
		for (Object[] r : rows) {
			boolean open = "OPEN".equals(str(r[6]));
			LocalDateTime opened = time(r[7]);
			BigDecimal opening = dec(r[8]);
			BigDecimal cashSales = r[13] == null ? null : dec(r[13]);
			BigDecimal refunds = r[14] == null ? null : dec(r[14]);
			BigDecimal voids = r[15] == null ? null : dec(r[15]);
			long saleCount = r[16] == null ? 0 : lng(r[16]);
			BigDecimal expected = r[17] == null ? null : dec(r[17]);
			BigDecimal collections = r[21] == null ? BigDecimal.ZERO : dec(r[21]);
			if (open) {
				Totals live = totals(str(r[4]), str(r[2]), opened, LocalDateTime.now());
				cashSales = live.cashSales();
				collections = live.collections();
				refunds = live.refunds();
				voids = live.voids();
				saleCount = live.saleCount();
				expected = live.expected(opening);
			}
			list.add(new ShiftDTO(str(r[0]), str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]), opened, opening,
					time(r[9]), str(r[10]), str(r[11]), Boolean.TRUE.equals(r[12]), cashSales, collections, refunds, voids, saleCount, expected,
					r[18] == null ? null : dec(r[18]), r[19] == null ? null : dec(r[19]), str(r[20]), open && overdue(opened), maxHours, limitValue));
		}
		return list;
	}

	private Totals totals(String userId, String branchId, LocalDateTime from, LocalDateTime to) {
		Object[] sales = rows("SELECT COALESCE(SUM(s.total - s.credit_amount), 0), COUNT(*) FROM public.sale s WHERE s.created_by = :user AND s.branch_id = :branch "
				+ "AND s.registered >= :from AND s.registered <= :to AND s.status IN ('COMPLETED', 'WAITING_APPROVAL', 'VOIDED')",
				"user", userId, "branch", branchId, "from", from, "to", to).get(0);
		Object[] returns = rows("SELECT COALESCE(SUM(r.refund_total - r.credit_offset), 0) FROM public.sale_return r WHERE r.created_by = :user AND r.branch_id = :branch "
				+ "AND r.registered >= :from AND r.registered <= :to", "user", userId, "branch", branchId, "from", from, "to", to).get(0);
		Object[] collected = rows("SELECT COALESCE(SUM(p.amount), 0) FROM public.receivable_payment p WHERE p.received_by = :user AND p.branch_id = :branch "
				+ "AND p.registered >= :from AND p.registered <= :to", "user", userId, "branch", branchId, "from", from, "to", to).get(0);
		Object[] voids = rows("SELECT COALESCE(SUM(s.total - s.credit_amount), 0) FROM public.sale s WHERE s.voided_by = :user AND s.branch_id = :branch "
				+ "AND s.voided_at >= :from AND s.voided_at <= :to", "user", userId, "branch", branchId, "from", from, "to", to).get(0);
		return new Totals(dec(sales[0]), dec(collected[0]), dec(returns[0]), dec(voids[0]), lng(sales[1]));
	}

	// ------------------------------------------------------------------ pengaturan & utilitas

	private boolean overdue(LocalDateTime openedAt) {
		return Duration.between(openedAt, LocalDateTime.now()).toHours() >= maxHours();
	}

	private int maxHours() {
		try {
			int value = Integer.parseInt(config(ConfigurationKey.SHIFT_MAX_HOURS, String.valueOf(DEFAULT_MAX_HOURS)).trim());
			return value < 1 ? DEFAULT_MAX_HOURS : value;
		} catch (NumberFormatException e) {
			return DEFAULT_MAX_HOURS;
		}
	}

	private BigDecimal differenceLimit() {
		try {
			BigDecimal value = new BigDecimal(config(ConfigurationKey.SHIFT_DIFFERENCE_LIMIT, DEFAULT_LIMIT.toPlainString()).trim());
			return value.signum() < 0 ? DEFAULT_LIMIT : value;
		} catch (NumberFormatException e) {
			return DEFAULT_LIMIT;
		}
	}

	private String config(ConfigurationKey key, String fallback) {
		String value = configurationRepository.get(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	private BigDecimal amount(BigDecimal value, String name) {
		if (value == null || value.signum() < 0 || value.stripTrailingZeros().scale() > 0 || value.compareTo(MAX_AMOUNT) > 0) {
			throw new ApiException("400", name + " harus berupa rupiah bulat, 0 atau lebih");
		}
		return value;
	}

	private static String clean(String value) {
		String trimmed = value == null ? null : value.trim();
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}

	private void lock(String key) {
		entityManager.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS locked")
				.setParameter("key", key).getSingleResult();
	}

	private List<Object[]> rows(String sql, Object... kv) {
		Map<String, Object> params = new java.util.HashMap<>();
		for (int i = 0; i + 1 < kv.length; i += 2) {
			params.put((String) kv[i], kv[i + 1]);
		}
		return rows(sql, params);
	}

	private List<Object[]> rows(String sql, Map<String, Object> params) {
		Query query = entityManager.createNativeQuery(sql);
		params.forEach((name, value) -> {
			if (sql.contains(":" + name)) {
				query.setParameter(name, value);
			}
		});
		List<?> result = query.getResultList();
		List<Object[]> rows = new ArrayList<>(result.size());
		for (Object row : result) {
			rows.add(row instanceof Object[] array ? array : new Object[] { row });
		}
		return rows;
	}

	private void execute(String sql, Object... kv) {
		Query query = entityManager.createNativeQuery(sql);
		for (int i = 0; i + 1 < kv.length; i += 2) {
			query.setParameter((String) kv[i], kv[i + 1]);
		}
		query.executeUpdate();
	}
}
