package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.percent;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.dto.analytics.AnalyticsDTO.BreakEven;
import com.saasno.utility.dto.analytics.AnalyticsDTO.DayPoint;
import com.saasno.utility.dto.analytics.AnalyticsDTO.ExpenseCategory;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Finance;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Projection;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Step;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Totals;
import com.saasno.utility.dto.finance.FinanceReportDTO;
import com.saasno.utility.model.ResponseModel;

// Analitik keuangan: seluruh angka berasal dari FinanceReportService (definisi omzet, harga pokok, dan biaya yang sama
// dengan Laporan Keuangan), hanya dijumlah per hari lintas cabang dan dibandingkan dengan periode sebelumnya.
@Service
public class AnalyticsFinanceService {

	@Autowired
	private AnalyticsSupport support;

	@Autowired
	private FinanceReportService financeReportService;

	@Autowired
	private ExpenseService expenseService;

	@Transactional(readOnly = true)
	public ResponseModel<Finance> get(LocalDate from, LocalDate to, String branchId) {
		Ctx c = support.ctx(from, to, branchId);
		List<FinanceReportDTO> rows = report(c);
		Totals summary = totals(rows);
		Totals previous = totals(report(c.previous()));

		Map<LocalDate, List<FinanceReportDTO>> byDay = rows.stream()
				.collect(Collectors.groupingBy(FinanceReportDTO::getDate, TreeMap::new, Collectors.toList()));
		List<DayPoint> daily = new ArrayList<>();
		for (LocalDate day = c.start(); !day.isAfter(c.end()); day = day.plusDays(1)) {
			Totals t = totals(byDay.getOrDefault(day, List.of()));
			daily.add(new DayPoint(day, t.revenue(), t.cogs(), t.grossProfit(), t.cashExpense().add(t.stockUseExpense()),
					t.operatingResult()));
		}

		List<Step> waterfall = List.of(
				new Step("Omzet", summary.revenue(), "TOTAL"),
				new Step("Harga pokok", summary.cogs().negate(), "DELTA"),
				new Step("Laba kotor", summary.grossProfit(), "TOTAL"),
				new Step("Biaya kas", summary.cashExpense().negate(), "DELTA"),
				new Step("Pemakaian stok", summary.stockUseExpense().negate(), "DELTA"),
				new Step("Hasil operasional", summary.operatingResult(), "TOTAL"));

		long unknown = rows.stream().mapToLong(r -> r.getUnknownCostLines() == null ? 0 : r.getUnknownCostLines()).sum();
		return new ResponseModel<Finance>().success(new Finance(summary, previous, daily, waterfall,
				expenseByCategory(c, summary.revenue()), breakEven(c, summary), projection(c), unknown));
	}

	private List<FinanceReportDTO> report(Ctx c) {
		return financeReportService.report(c.start(), c.end(), c.branch(), c.scope()).getData();
	}

	private Totals totals(List<FinanceReportDTO> rows) {
		BigDecimal revenue = BigDecimal.ZERO;
		BigDecimal cogs = BigDecimal.ZERO;
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal use = BigDecimal.ZERO;
		for (FinanceReportDTO r : rows) {
			revenue = revenue.add(r.getRevenue());
			cogs = cogs.add(r.getCogs());
			cash = cash.add(r.getCashExpense());
			use = use.add(r.getStockUseExpense());
		}
		BigDecimal gross = revenue.subtract(cogs);
		return new Totals(revenue, cogs, gross, cash, use, gross.subtract(cash).subtract(use), percent(gross, revenue));
	}

	private List<ExpenseCategory> expenseByCategory(Ctx c, BigDecimal revenue) {
		Map<String, String> labels = expenseService.categories().stream()
				.collect(Collectors.toMap(ExpenseService.Category::value, ExpenseService.Category::label, (a, b) -> a));
		List<ExpenseCategory> result = new ArrayList<>();
		for (Object[] r : support.rows("SELECT x.category, SUM(x.amount) FROM public.store_expense x WHERE x.status = 'RECORDED' "
				+ "AND x.registered >= :from AND x.registered < :to" + c.in("x.branch_id") + " GROUP BY 1 ORDER BY 2 DESC", c)) {
			String code = str(r[0]);
			BigDecimal amount = dec(r[1]);
			result.add(new ExpenseCategory(code, labels.getOrDefault(code, code), amount, percent(amount, revenue)));
		}
		return result;
	}

	// Titik impas harian: biaya per hari dibagi margin kotor. Tidak bisa dihitung (0) bila margin kotor tidak positif.
	private BreakEven breakEven(Ctx c, Totals t) {
		BigDecimal days = BigDecimal.valueOf(c.days());
		BigDecimal avgExpense = t.cashExpense().add(t.stockUseExpense()).divide(days, 0, RoundingMode.HALF_UP);
		BigDecimal avgRevenue = t.revenue().divide(days, 0, RoundingMode.HALF_UP);
		BigDecimal required = BigDecimal.ZERO;
		if (t.grossMarginPercent() > 0) {
			required = avgExpense.multiply(BigDecimal.valueOf(100))
					.divide(BigDecimal.valueOf(t.grossMarginPercent()), 0, RoundingMode.HALF_UP);
		}
		return new BreakEven(avgExpense, t.grossMarginPercent(), required, avgRevenue,
				t.grossMarginPercent() > 0 && avgRevenue.compareTo(required) >= 0);
	}

	// Proyeksi akhir bulan berjalan dengan laju rata-rata sejak tanggal 1, terlepas dari rentang yang dipilih.
	private Projection projection(Ctx c) {
		LocalDate today = LocalDate.now();
		LocalDate monthStart = today.withDayOfMonth(1);
		Ctx month = new Ctx(monthStart, today, c.branch(), c.scope());
		Totals t = totals(report(month));
		int elapsed = today.getDayOfMonth();
		int length = today.lengthOfMonth();
		BigDecimal factor = BigDecimal.valueOf(length).divide(BigDecimal.valueOf(elapsed), 6, RoundingMode.HALF_UP);
		return new Projection(monthStart, elapsed, length, t.revenue(), t.operatingResult(),
				t.revenue().multiply(factor).setScale(0, RoundingMode.HALF_UP),
				t.operatingResult().multiply(factor).setScale(0, RoundingMode.HALF_UP));
	}
}
