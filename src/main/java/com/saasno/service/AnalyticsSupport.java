package com.saasno.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.saasno.utility.BranchScope;
import com.saasno.utility.exception.ApiException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

// Bahan bersama semua halaman Analitik: rentang tanggal, cakupan cabang, dan pembacaan kolom hasil query native.
// Setiap query analitik wajib memakai Ctx.in(kolomCabang) supaya hasilnya tidak keluar dari cakupan cabang user.
@Component
public class AnalyticsSupport {

	public static final int MAX_DAYS = 366;
	private static final int DEFAULT_DAYS = 30;
	// ":branch" yang berdiri sendiri (bukan awal ":branches").
	private static final java.util.regex.Pattern BRANCH_PARAM = java.util.regex.Pattern.compile(":branch(?![a-z_])");

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchScopeService branchScopeService;

	// Rentang tanggal inklusif [start, end] beserta cabang yang dipilih (null = seluruh cakupan).
	public record Ctx(LocalDate start, LocalDate end, String branch, BranchScope scope) {

		public LocalDateTime from() {
			return start.atStartOfDay();
		}

		// Batas atas eksklusif.
		public LocalDateTime to() {
			return end.plusDays(1).atStartOfDay();
		}

		public long days() {
			return ChronoUnit.DAYS.between(start, end) + 1;
		}

		// Potongan WHERE untuk kolom cabang pada alias tertentu.
		public String in(String column) {
			if (branch != null) {
				return " AND " + column + " = :branch";
			}
			return scope.isGlobal() ? "" : " AND " + column + " IN (:branches)";
		}

		// Rentang sebelumnya dengan panjang sama, untuk perbandingan periode.
		public Ctx previous() {
			long length = days();
			return new Ctx(start.minusDays(length), start.minusDays(1), branch, scope);
		}
	}

	public Ctx ctx(LocalDate from, LocalDate to, String branchId) {
		BranchScope scope = branchScopeService.current();
		LocalDate end = to == null ? LocalDate.now() : to;
		LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
		if (end.isBefore(start)) {
			throw new ApiException("400", "Tanggal akhir tidak boleh sebelum tanggal mulai");
		}
		if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
			throw new ApiException("400", "Rentang tanggal tidak boleh lebih dari " + MAX_DAYS + " hari");
		}
		String wanted = branchId == null || branchId.isBlank() ? null : branchId.trim();
		if (wanted != null && !scope.contains(wanted)) {
			throw new ApiException("404", "Cabang tidak ditemukan");
		}
		return new Ctx(start, end, wanted, scope);
	}

	// Menjalankan query native; :from, :to, :branch, :branches diisi hanya bila dipakai di SQL. Parameter tambahan
	// berpasangan nama, nilai.
	public List<Object[]> rows(String sql, Ctx c, Object... extra) {
		Query query = entityManager.createNativeQuery(sql);
		if (sql.contains(":from")) {
			query.setParameter("from", c.from());
		}
		if (sql.contains(":to")) {
			query.setParameter("to", c.to());
		}
		if (sql.contains(":branches")) {
			query.setParameter("branches", c.scope().getIds());
		}
		if (BRANCH_PARAM.matcher(sql).find()) {
			query.setParameter("branch", c.branch());
		}
		for (int i = 0; i + 1 < extra.length; i += 2) {
			query.setParameter((String) extra[i], extra[i + 1]);
		}
		// Query satu kolom mengembalikan nilai tunggal, bukan Object[]; diseragamkan agar pemanggil selalu membaca r[0].
		List<?> result = query.getResultList();
		List<Object[]> rows = new java.util.ArrayList<>(result.size());
		for (Object row : result) {
			rows.add(row instanceof Object[] array ? array : new Object[] { row });
		}
		return rows;
	}

	public static BigDecimal dec(Object value) {
		if (value == null) {
			return BigDecimal.ZERO;
		}
		return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
	}

	public static long lng(Object value) {
		return value == null ? 0 : ((Number) value).longValue();
	}

	public static String str(Object value) {
		return value == null ? null : value.toString();
	}

	public static LocalDate date(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Date d) {
			return d.toLocalDate();
		}
		if (value instanceof Timestamp t) {
			return t.toLocalDateTime().toLocalDate();
		}
		return LocalDate.parse(value.toString().substring(0, 10));
	}

	public static LocalDateTime time(Object value) {
		return value == null ? null : ((Timestamp) value).toLocalDateTime();
	}

	// Persen a terhadap b dengan 1 desimal; 0 bila b nol.
	public static double percent(BigDecimal a, BigDecimal b) {
		if (b == null || b.signum() == 0) {
			return 0;
		}
		return a.multiply(BigDecimal.valueOf(100)).divide(b, 1, RoundingMode.HALF_UP).doubleValue();
	}

	public static double ratio(long a, long b) {
		return b == 0 ? 0 : Math.round(a * 1000.0 / b) / 10.0;
	}

	public static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
