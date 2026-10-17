package com.saasno.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BranchScope;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.dashboard.DashboardDTO;
import com.saasno.utility.dto.dashboard.DashboardDTO.DayCount;
import com.saasno.utility.dto.dashboard.DashboardDTO.General;
import com.saasno.utility.dto.dashboard.DashboardDTO.HourCount;
import com.saasno.utility.dto.dashboard.DashboardDTO.LowStockItem;
import com.saasno.utility.dto.dashboard.DashboardDTO.StockStatus;
import com.saasno.utility.dto.dashboard.DashboardDTO.TopProduct;

import jakarta.persistence.EntityManager;

// Ringkasan beranda per cakupan cabang user. Hanya jumlah dan status, tanpa nilai rupiah sama sekali;
// uang ada di Laporan Kas dan Laporan Keuangan. Izin diperiksa di sini dengan tabel role_api yang sama dengan filter
// API, jadi data yang tidak berhak tidak pernah ikut dikirim.
@Service
public class DashboardService {

	private static final int TREND_DAYS = 7;
	private static final int TOP_PRODUCTS = 8;
	private static final int LOW_STOCK_ITEMS = 10;
	private static final int ARTICLES = 5;
	private static final long DEFAULT_LOW_STOCK = 5;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private PermissionService permissionService;

	@Autowired
	private ArticleService articleService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Transactional(readOnly = true)
	public com.saasno.utility.model.ResponseModel<DashboardDTO> get() {
		BranchScope scope = branchScopeService.current();
		Set<String> allowed = permissionService.apis();
		LocalDate today = LocalDate.now();

		General general = general(scope, today, allowed.contains("PUT_API_STOCK_TRANSFER_APPROVE"), allowed.contains("PUT_API_ORDER_APPROVE"),
				allowed.contains("PUT_API_SALE_APPROVE"));
		return new com.saasno.utility.model.ResponseModel<DashboardDTO>().success(new DashboardDTO(general));
	}

	// ---------------------------------------------------------------- umum

	private General general(BranchScope scope, LocalDate today, boolean canApproveTransfer, boolean canApproveOrder, boolean canApproveSale) {
		LocalDateTime dayStart = today.atStartOfDay();
		LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
		LocalDateTime trendStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();
		String sale = " AND s.status = 'COMPLETED' AND s.branch_id " + in(scope);
		String when = "COALESCE(s.decided_at, s.registered)";

		long todayCount = count("SELECT COUNT(*) FROM public.sale s WHERE " + when + " >= :a AND " + when + " < :b" + sale,
				scope, dayStart, tomorrow);
		long yesterdayCount = count("SELECT COUNT(*) FROM public.sale s WHERE " + when + " >= :a AND " + when + " < :b" + sale,
				scope, dayStart.minusDays(1), dayStart);
		long myTodayCount = ((Number) bind(entityManager.createNativeQuery("SELECT COUNT(*) FROM public.sale s WHERE "
				+ when + " >= :a AND " + when + " < :b" + sale + " AND s.created_by = :me"), scope, dayStart, tomorrow)
				.setParameter("me", sessionUtility.getUserId()).getSingleResult()).longValue();

		// Jumlah barang terjual bersih (dikurangi yang sudah diretur).
		BigDecimal todayQuantity = (BigDecimal) bind(entityManager.createNativeQuery(
				"SELECT COALESCE(SUM(i.quantity - i.returned_quantity), 0) FROM public.sale_item i JOIN public.sale s ON s.id = i.sale_id "
						+ "WHERE " + when + " >= :a AND " + when + " < :b" + sale), scope, dayStart, tomorrow).getSingleResult();

		// Jam ramai hari ini: 24 jam, jam tanpa transaksi bernilai 0.
		long[] perHour = new long[24];
		for (Object[] r : rows("SELECT CAST(EXTRACT(HOUR FROM " + when + ") AS int), COUNT(*) FROM public.sale s WHERE " + when
				+ " >= :a AND " + when + " < :b" + sale + " GROUP BY 1", scope, dayStart, tomorrow)) {
			perHour[((Number) r[0]).intValue()] = ((Number) r[1]).longValue();
		}
		List<HourCount> hourly = new ArrayList<>();
		for (int h = 0; h < 24; h++) {
			hourly.add(new HourCount(h, perHour[h]));
		}

		Map<LocalDate, Long> perDay = new TreeMap<>();
		for (int i = 0; i < TREND_DAYS; i++) {
			perDay.put(today.minusDays(TREND_DAYS - 1L - i), 0L);
		}
		for (Object[] r : rows("SELECT CAST(" + when + " AS date), COUNT(*) FROM public.sale s WHERE " + when + " >= :a AND "
				+ when + " < :b" + sale + " GROUP BY 1", scope, trendStart, tomorrow)) {
			perDay.put(((java.sql.Date) r[0]).toLocalDate(), ((Number) r[1]).longValue());
		}
		List<DayCount> daily = perDay.entrySet().stream().map(e -> new DayCount(e.getKey(), e.getValue())).toList();

		List<TopProduct> top = new ArrayList<>();
		for (Object[] r : rows("SELECT i.product_id, i.product_serial, i.product_name, SUM(i.quantity - i.returned_quantity) AS q "
				+ "FROM public.sale_item i JOIN public.sale s ON s.id = i.sale_id WHERE " + when + " >= :a AND " + when
				+ " < :b" + sale + " GROUP BY 1, 2, 3 HAVING SUM(i.quantity - i.returned_quantity) > 0 ORDER BY q DESC, 3 LIMIT "
				+ TOP_PRODUCTS, scope, trendStart, tomorrow)) {
			top.add(new TopProduct((String) r[0], (String) r[1], (String) r[2], (BigDecimal) r[3]));
		}

		long held = count("SELECT COUNT(*) FROM public.sale_hold h WHERE h.status = 'HELD' AND h.branch_id " + in(scope),
				scope);
		long oldHeld = ((Number) bind(entityManager.createNativeQuery("SELECT COUNT(*) FROM public.sale_hold h WHERE h.status = 'HELD' AND h.registered < :a AND h.branch_id "
				+ in(scope)), scope, dayStart, null).getSingleResult()).longValue();

		// Oper stok yang menunggu keputusan user ini: tujuan di cakupannya dan bukan pengajuannya sendiri.
		Long pendingTransfers = null;
		if (canApproveTransfer) {
			pendingTransfers = ((Number) bind(entityManager.createNativeQuery("SELECT COUNT(*) FROM public.stock_transfer t "
					+ "WHERE t.status = 'PENDING' AND t.requested_by <> :me AND t.destination_branch_id " + in(scope)), scope,
					null, null).setParameter("me", sessionUtility.getUserId()).getSingleResult()).longValue();
		}

		// Pesanan online yang menunggu persetujuan di cakupan user.
		Long pendingOrders = canApproveOrder ? count(
				"SELECT COUNT(*) FROM public.online_order o WHERE o.status = 'PENDING' AND o.branch_id " + in(scope), scope) : null;

		Long waitingApproval = canApproveSale ? count(
				"SELECT COUNT(*) FROM public.sale s WHERE s.status = 'WAITING_APPROVAL' AND s.branch_id " + in(scope), scope) : null;

		return new General(todayCount, yesterdayCount, todayQuantity, myTodayCount, hourly, daily, top, stock(scope), held,
				oldHeld, pendingTransfers, waitingApproval, pendingOrders, articleService.latest(scope, ARTICLES));
	}

	private StockStatus stock(BranchScope scope) {
		long threshold = lowStockThreshold();
		String base = "FROM public.stock_balance b JOIN public.product p ON p.id = b.product_id WHERE p.status <> 'DISABLED' AND b.branch_id "
				+ in(scope);
		long out = 0;
		long low = 0;
		long ok = 0;
		for (Object[] r : rows("SELECT CASE WHEN b.quantity <= 0 THEN 'OUT' WHEN b.quantity <= " + threshold
				+ " THEN 'LOW' ELSE 'OK' END, COUNT(*) " + base + " GROUP BY 1", scope, null, null)) {
			long n = ((Number) r[1]).longValue();
			switch ((String) r[0]) {
				case "OUT" -> out = n;
				case "LOW" -> low = n;
				default -> ok = n;
			}
		}
		List<Object[]> lowRows = rows("SELECT p.id, p.serial, p.name, b.branch_id, b.quantity " + base + " AND b.quantity <= "
				+ threshold + " ORDER BY b.quantity, p.name LIMIT " + LOW_STOCK_ITEMS, scope, null, null);
		Map<String, String> names = branchScopeService.names(lowRows.stream().map(r -> (String) r[3]).distinct().toList());
		List<LowStockItem> items = lowRows.stream().map(r -> new LowStockItem((String) r[0], (String) r[1], (String) r[2],
				(String) r[3], names.get((String) r[3]), (BigDecimal) r[4])).toList();
		return new StockStatus(ok, low, out, threshold, items);
	}

	private long lowStockThreshold() {
		String value = configurationRepository.get(ConfigurationKey.LOW_STOCK_THRESHOLD);
		try {
			return value == null ? DEFAULT_LOW_STOCK : Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_LOW_STOCK;
		}
	}

	// ---------------------------------------------------------------- bantu

	// Klausa cabang: user global tidak dibatasi.
	private String in(BranchScope scope) {
		return scope.isGlobal() ? "IS NOT NULL" : "IN (:branches)";
	}

	private jakarta.persistence.Query bind(jakarta.persistence.Query query, BranchScope scope, LocalDateTime a, LocalDateTime b) {
		if (!scope.isGlobal()) {
			query.setParameter("branches", scope.getIds());
		}
		if (a != null) {
			query.setParameter("a", Timestamp.valueOf(a));
		}
		if (b != null) {
			query.setParameter("b", Timestamp.valueOf(b));
		}
		return query;
	}

	private long count(String sql, BranchScope scope, LocalDateTime a, LocalDateTime b) {
		return ((Number) bind(entityManager.createNativeQuery(sql), scope, a, b).getSingleResult()).longValue();
	}

	private long count(String sql, BranchScope scope) {
		return count(sql, scope, null, null);
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> rows(String sql, BranchScope scope, LocalDateTime a, LocalDateTime b) {
		return bind(entityManager.createNativeQuery(sql), scope, a, b).getResultList();
	}

}
