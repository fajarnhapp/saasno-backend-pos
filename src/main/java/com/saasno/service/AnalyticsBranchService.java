package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.percent;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Anomaly;
import com.saasno.utility.dto.analytics.AnalyticsDTO.BranchRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Branches;
import com.saasno.utility.dto.analytics.AnalyticsDTO.CashierRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Flow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.PettyCash;
import com.saasno.utility.dto.finance.FinanceReportDTO;
import com.saasno.utility.model.ResponseModel;

// Peringkat cabang, kinerja kasir, arus oper stok, dan pengendalian (void, diskon, transaksi di luar jam buka, kas kecil).
// Aturan kejanggalan sederhana dan terbuka: ambang dibandingkan dengan rata-rata seluruh kasir pada rentang yang sama,
// jadi hanya petunjuk untuk diperiksa, bukan tuduhan.
@Service
public class AnalyticsBranchService {

	private static final int DEFAULT_OPEN_HOUR = 6;
	private static final int DEFAULT_CLOSE_HOUR = 22;
	private static final double OUTLIER_FACTOR = 2.0;
	private static final long MIN_SAMPLE = 3;
	private static final long OFF_HOURS_HIGH = 10;

	@Autowired
	private AnalyticsSupport support;

	@Autowired
	private FinanceReportService financeReportService;

	@Autowired
	private BranchScopeService branchScopeService;

	@Autowired
	private ConfigurationRepository configurationRepository;

	// Jam buka toko dari pengaturan (STORE_OPEN_HOUR/STORE_CLOSE_HOUR 0-23); STORE_24_HOURS mematikan aturan luar jam buka.
	private record Hours(int open, int close, boolean open24) {
		// Jam dikirim sebagai angka yang sudah divalidasi, bukan teks bebas, karena dirangkai ke SQL.
		String openSql() {
			return open24 ? "0" : String.valueOf(open);
		}

		String closeSql() {
			return open24 ? "24" : String.valueOf(close);
		}
	}

	private Hours hours() {
		boolean h24 = "true".equalsIgnoreCase(String.valueOf(configurationRepository.get(ConfigurationKey.STORE_24_HOURS)).trim());
		return new Hours(hour(configurationRepository.get(ConfigurationKey.STORE_OPEN_HOUR), DEFAULT_OPEN_HOUR),
				hour(configurationRepository.get(ConfigurationKey.STORE_CLOSE_HOUR), DEFAULT_CLOSE_HOUR), h24);
	}

	private static int hour(String raw, int fallback) {
		try {
			int value = raw == null ? fallback : Integer.parseInt(raw.trim());
			return value < 0 || value > 24 ? fallback : value;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static final class Acc {
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal gross = BigDecimal.ZERO;
		BigDecimal result = BigDecimal.ZERO;
		long transactions;
		BigDecimal sales = BigDecimal.ZERO;
		BigDecimal refunds = BigDecimal.ZERO;
		long voids;
		BigDecimal opname = BigDecimal.ZERO;
	}

	@Transactional(readOnly = true)
	public ResponseModel<Branches> get(LocalDate from, LocalDate to, String branchId) {
		Ctx c = support.ctx(from, to, branchId);
		Hours hours = hours();
		List<CashierRow> cashiers = cashiers(c, hours);
		List<PettyCash> petty = pettyCash(c);
		List<BranchRow> branches = branches(c);
		return new ResponseModel<Branches>().success(new Branches(branches, cashiers, flows(c), anomalies(cashiers, petty, hours), petty,
				hours.open(), hours.close(), hours.open24()));
	}

	// ------------------------------------------------------------------ cabang

	private List<BranchRow> branches(Ctx c) {
		Map<String, Acc> acc = new HashMap<>();
		for (FinanceReportDTO r : financeReportService.report(c.start(), c.end(), c.branch(), c.scope()).getData()) {
			Acc a = acc.computeIfAbsent(r.getBranchId(), k -> new Acc());
			a.revenue = a.revenue.add(r.getRevenue());
			a.gross = a.gross.add(r.getGrossProfit());
			a.result = a.result.add(r.getOperatingResult());
		}
		for (Object[] r : support.rows("SELECT s.branch_id, COUNT(*), SUM(s.total - s.charge_total), SUM(s.total) FROM public.sale s WHERE s.status = 'COMPLETED' "
				+ "AND COALESCE(s.decided_at, s.registered) >= :from AND COALESCE(s.decided_at, s.registered) < :to" + c.in("s.branch_id") + " GROUP BY 1", c)) {
			Acc a = acc.computeIfAbsent(str(r[0]), k -> new Acc());
			a.transactions = lng(r[1]);
			a.sales = dec(r[3]);
			if (a.revenue.signum() == 0) {
				a.revenue = dec(r[2]);
			}
		}
		for (Object[] r : support.rows("SELECT x.branch_id, SUM(x.refund_total) FROM public.sale_return x WHERE x.registered >= :from AND x.registered < :to"
				+ c.in("x.branch_id") + " GROUP BY 1", c)) {
			acc.computeIfAbsent(str(r[0]), k -> new Acc()).refunds = dec(r[1]);
		}
		for (Object[] r : support.rows("SELECT s.branch_id, COUNT(*) FROM public.sale s WHERE s.status = 'VOIDED' AND s.voided_at >= :from AND s.voided_at < :to"
				+ c.in("s.branch_id") + " GROUP BY 1", c)) {
			acc.computeIfAbsent(str(r[0]), k -> new Acc()).voids = lng(r[1]);
		}
		for (Object[] r : support.rows("SELECT o.branch_id, SUM(ABS(i.counted_quantity - i.system_quantity) * COALESCE(p.cost_price, 0)) FROM public.stock_opname_item i "
				+ "JOIN public.stock_opname o ON o.id = i.opname_id JOIN public.product p ON p.id = i.product_id WHERE o.status = 'APPROVED' "
				+ "AND o.decided_at >= :from AND o.decided_at < :to" + c.in("o.branch_id") + " GROUP BY 1", c)) {
			acc.computeIfAbsent(str(r[0]), k -> new Acc()).opname = dec(r[1]);
		}
		Map<String, String> names = branchScopeService.names(acc.keySet());
		List<BranchRow> rows = new ArrayList<>();
		for (Map.Entry<String, Acc> e : acc.entrySet()) {
			Acc a = e.getValue();
			rows.add(new BranchRow(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()), a.revenue, a.gross, percent(a.gross, a.revenue),
					a.result, a.transactions,
					a.transactions == 0 ? BigDecimal.ZERO : a.revenue.divide(BigDecimal.valueOf(a.transactions), 0, RoundingMode.HALF_UP),
					percent(a.refunds, a.sales), a.voids, a.opname));
		}
		rows.sort(Comparator.comparing(BranchRow::revenue).reversed());
		return rows;
	}

	// ------------------------------------------------------------------ kasir

	private List<CashierRow> cashiers(Ctx c, Hours hours) {
		Map<String, Long> returns = new HashMap<>();
		for (Object[] r : support.rows("SELECT x.created_by, COUNT(*) FROM public.sale_return x WHERE x.registered >= :from AND x.registered < :to"
				+ c.in("x.branch_id") + " GROUP BY 1", c)) {
			returns.put(str(r[0]), lng(r[1]));
		}
		List<CashierRow> rows = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.created_by, u.username, "
				+ "SUM(CASE WHEN s.status IN ('COMPLETED', 'VOIDED') THEN 1 ELSE 0 END), "
				+ "SUM(CASE WHEN s.status IN ('COMPLETED', 'VOIDED') THEN s.total - s.charge_total ELSE 0 END), "
				+ "SUM(CASE WHEN s.status = 'VOIDED' THEN 1 ELSE 0 END), "
				+ "SUM(CASE WHEN s.status <> 'CANCELLED' THEN s.manual_discount ELSE 0 END), "
				+ "SUM(CASE WHEN s.status <> 'CANCELLED' THEN s.subtotal ELSE 0 END), "
				+ "SUM(CASE WHEN s.status IN ('COMPLETED', 'VOIDED') AND (EXTRACT(HOUR FROM s.registered) < " + hours.openSql()
				+ " OR EXTRACT(HOUR FROM s.registered) >= " + hours.closeSql() + ") THEN 1 ELSE 0 END) "
				+ "FROM public.sale s JOIN public.\"user\" u ON u.id = s.created_by WHERE s.registered >= :from AND s.registered < :to"
				+ c.in("s.branch_id") + " GROUP BY 1, 2", c)) {
			long transactions = lng(r[2]);
			BigDecimal revenue = dec(r[3]);
			long voids = lng(r[4]);
			rows.add(new CashierRow(str(r[0]), str(r[1]), transactions, revenue,
					transactions == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(transactions), 0, RoundingMode.HALF_UP), voids,
					AnalyticsSupport.ratio(voids, transactions), dec(r[5]), percent(dec(r[5]), dec(r[6])), lng(r[7]),
					returns.getOrDefault(str(r[0]), 0L)));
		}
		rows.sort(Comparator.comparing(CashierRow::revenue).reversed());
		return rows;
	}

	// ------------------------------------------------------------------ oper stok

	private List<Flow> flows(Ctx c) {
		String scope;
		if (c.branch() != null) {
			scope = " AND (t.source_branch_id = :branch OR t.destination_branch_id = :branch)";
		} else if (c.scope().isGlobal()) {
			scope = "";
		} else {
			scope = " AND (t.source_branch_id IN (:branches) OR t.destination_branch_id IN (:branches))";
		}
		List<Object[]> rows = support.rows("SELECT t.source_branch_id, t.destination_branch_id, COUNT(DISTINCT t.id), SUM(i.quantity), "
				+ "SUM(i.quantity * COALESCE(p.cost_price, 0)) FROM public.stock_transfer t JOIN public.stock_transfer_item i ON i.transfer_id = t.id "
				+ "JOIN public.product p ON p.id = i.product_id WHERE t.status = 'APPROVED' AND t.decided_at >= :from AND t.decided_at < :to" + scope
				+ " GROUP BY 1, 2 ORDER BY 5 DESC LIMIT 20", c);
		Set<String> ids = new HashSet<>();
		rows.forEach(r -> {
			ids.add(str(r[0]));
			ids.add(str(r[1]));
		});
		Map<String, String> names = ids.isEmpty() ? Map.of() : branchScopeService.names(ids);
		List<Flow> flows = new ArrayList<>();
		for (Object[] r : rows) {
			flows.add(new Flow(str(r[0]), names.getOrDefault(str(r[0]), str(r[0])), str(r[1]), names.getOrDefault(str(r[1]), str(r[1])),
					lng(r[2]), dec(r[3]), dec(r[4])));
		}
		return flows;
	}

	// ------------------------------------------------------------------ kas kecil

	// Buku kas kecil bertanda: pengeluaran bernilai negatif dan pembatalannya positif, jadi pengeluaran bersih = -SUM.

	private List<PettyCash> pettyCash(Ctx c) {
		Map<String, BigDecimal[]> amounts = new HashMap<>();
		for (Object[] r : support.rows("SELECT x.branch_id, SUM(CASE WHEN x.type = 'TOPUP' THEN x.amount ELSE 0 END), "
				+ "SUM(CASE WHEN x.type IN ('EXPENSE', 'EXPENSE_VOID') THEN -x.amount ELSE 0 END) "
				+ "FROM public.petty_cash_entry x WHERE x.registered >= :from AND x.registered < :to" + c.in("x.branch_id") + " GROUP BY 1", c)) {
			amounts.put(str(r[0]), new BigDecimal[] { dec(r[1]), dec(r[2]) });
		}
		Map<String, Long> over = new HashMap<>();
		for (Object[] r : support.rows("SELECT x.branch_id, COUNT(*) FROM public.store_expense x WHERE x.over_threshold AND x.status = 'RECORDED' "
				+ "AND x.registered >= :from AND x.registered < :to" + c.in("x.branch_id") + " GROUP BY 1", c)) {
			over.put(str(r[0]), lng(r[1]));
		}
		Map<String, BigDecimal> balances = new HashMap<>();
		for (Object[] r : support.rows("SELECT x.branch_id, x.balance FROM public.petty_cash_balance x WHERE 1 = 1" + c.in("x.branch_id"), c)) {
			balances.put(str(r[0]), dec(r[1]));
		}
		Set<String> ids = new HashSet<>(amounts.keySet());
		ids.addAll(over.keySet());
		ids.addAll(balances.keySet());
		Map<String, String> names = ids.isEmpty() ? Map.of() : branchScopeService.names(ids);
		List<PettyCash> list = new ArrayList<>();
		for (String id : ids) {
			BigDecimal[] a = amounts.getOrDefault(id, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
			list.add(new PettyCash(id, names.getOrDefault(id, id), a[0], a[1], over.getOrDefault(id, 0L), balances.getOrDefault(id, BigDecimal.ZERO)));
		}
		list.sort(Comparator.comparing(PettyCash::expense).reversed());
		return list;
	}

	// ------------------------------------------------------------------ kejanggalan

	private List<Anomaly> anomalies(List<CashierRow> cashiers, List<PettyCash> petty, Hours hours) {
		List<Anomaly> list = new ArrayList<>();
		long allTx = cashiers.stream().mapToLong(CashierRow::transactions).sum();
		long allVoids = cashiers.stream().mapToLong(CashierRow::voids).sum();
		double voidRate = AnalyticsSupport.ratio(allVoids, allTx);
		BigDecimal allDiscount = cashiers.stream().map(CashierRow::manualDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);
		double avgDiscount = cashiers.isEmpty() ? 0 : cashiers.stream().mapToDouble(CashierRow::discountPercentOfSubtotal).average().orElse(0);

		for (CashierRow k : cashiers) {
			if (k.voids() >= MIN_SAMPLE && k.voidRatePercent() >= Math.max(voidRate * OUTLIER_FACTOR, 5)) {
				list.add(new Anomaly("VOID_TINGGI", "HIGH", k.username(),
						k.voids() + " void dari " + k.transactions() + " transaksi (" + k.voidRatePercent() + "%), rata-rata " + voidRate + "%"));
			}
			if (allDiscount.signum() > 0 && k.transactions() >= MIN_SAMPLE && k.discountPercentOfSubtotal() > 0
					&& k.discountPercentOfSubtotal() >= Math.max(avgDiscount * OUTLIER_FACTOR, 1)) {
				list.add(new Anomaly("DISKON_TINGGI", "MEDIUM", k.username(),
						"Diskon manual " + k.discountPercentOfSubtotal() + "% dari subtotal, rata-rata kasir " + Math.round(avgDiscount * 10) / 10.0 + "%"));
			}
			if (!hours.open24() && k.offHours() >= MIN_SAMPLE) {
				list.add(new Anomaly("LUAR_JAM_BUKA", k.offHours() >= OFF_HOURS_HIGH ? "HIGH" : "MEDIUM", k.username(),
						k.offHours() + " transaksi di luar jam " + hours.open() + ":00-" + hours.close() + ":00"));
			}
		}
		for (PettyCash p : petty) {
			if (p.overThreshold() > 0) {
				list.add(new Anomaly("BIAYA_MELEBIHI_BATAS", "MEDIUM", p.name(), p.overThreshold() + " biaya melebihi batas peringatan"));
			}
			if (p.balance().signum() < 0) {
				list.add(new Anomaly("KAS_KECIL_MINUS", "HIGH", p.name(), "Saldo kas kecil minus"));
			}
		}
		list.sort(Comparator.comparing(Anomaly::severity));
		return list;
	}
}
