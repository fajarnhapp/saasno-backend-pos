package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Basket;
import com.saasno.utility.dto.analytics.AnalyticsDTO.BasketDay;
import com.saasno.utility.dto.analytics.AnalyticsDTO.HeatCell;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Holds;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Online;
import com.saasno.utility.dto.analytics.AnalyticsDTO.ProductPair;
import com.saasno.utility.dto.analytics.AnalyticsDTO.ReasonCount;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Sales;
import com.saasno.utility.dto.analytics.AnalyticsDTO.StatusCount;
import com.saasno.utility.dto.analytics.AnalyticsDTO.TopItem;
import com.saasno.utility.model.ResponseModel;

// Analitik perilaku penjualan: jam ramai, keranjang, produk yang dibeli bersamaan, transaksi batal/void/tahan, dan pesanan
// online. Omzet di sini = total tanpa pajak dari penjualan selesai pada hari selesai.
@Service
public class AnalyticsSalesService {

	private static final String WHEN = "COALESCE(s.decided_at, s.registered)";
	private static final String COMPLETED = "s.status = 'COMPLETED' AND " + WHEN + " >= :from AND " + WHEN + " < :to";
	private static final int TOP_PAIRS = 20;
	private static final int MIN_PAIR = 2;

	@Autowired
	private AnalyticsSupport support;

	@Transactional(readOnly = true)
	public ResponseModel<Sales> get(LocalDate from, LocalDate to, String branchId) {
		Ctx c = support.ctx(from, to, branchId);
		return new ResponseModel<Sales>().success(new Sales(heatmap(c), basket(c), pairs(c), statuses(c), voidReasons(c), holds(c),
				online(c)));
	}

	private List<HeatCell> heatmap(Ctx c) {
		List<HeatCell> cells = new ArrayList<>();
		for (Object[] r : support.rows("SELECT CAST(EXTRACT(ISODOW FROM " + WHEN + ") AS int), CAST(EXTRACT(HOUR FROM " + WHEN + ") AS int), "
				+ "COUNT(*), SUM(s.total - s.charge_total) FROM public.sale s WHERE " + COMPLETED + c.in("s.branch_id") + " GROUP BY 1, 2", c)) {
			cells.add(new HeatCell(((Number) r[0]).intValue(), ((Number) r[1]).intValue(), lng(r[2]), dec(r[3])));
		}
		return cells;
	}

	private Basket basket(Ctx c) {
		List<BasketDay> daily = new ArrayList<>();
		long transactions = 0;
		BigDecimal value = BigDecimal.ZERO;
		double items = 0;
		for (Object[] r : support.rows("SELECT CAST(" + WHEN + " AS date), COUNT(*), AVG(s.total - s.charge_total), AVG(n.c) FROM public.sale s "
				+ "JOIN (SELECT sale_id, COUNT(*) AS c FROM public.sale_item GROUP BY sale_id) n ON n.sale_id = s.id WHERE " + COMPLETED
				+ c.in("s.branch_id") + " GROUP BY 1 ORDER BY 1", c)) {
			long count = lng(r[1]);
			BigDecimal avg = dec(r[2]).setScale(0, RoundingMode.HALF_UP);
			double avgItems = Math.round(dec(r[3]).doubleValue() * 10) / 10.0;
			daily.add(new BasketDay(date(r[0]), count, avg, avgItems));
			transactions += count;
			value = value.add(dec(r[2]).multiply(BigDecimal.valueOf(count)));
			items += avgItems * count;
		}
		BigDecimal avgValue = transactions == 0 ? BigDecimal.ZERO : value.divide(BigDecimal.valueOf(transactions), 0, RoundingMode.HALF_UP);
		return new Basket(transactions, avgValue, transactions == 0 ? 0 : Math.round(items / transactions * 10) / 10.0, daily);
	}

	// Pasangan produk yang muncul di transaksi yang sama (minimal MIN_PAIR kali). support = porsi transaksi yang memuat
	// keduanya; confidence = dari transaksi yang memuat A, berapa persen memuat B juga; lift > 1 berarti saling menarik.
	private List<ProductPair> pairs(Ctx c) {
		String base = "SELECT si.sale_id, si.product_id FROM public.sale_item si JOIN public.sale s ON s.id = si.sale_id WHERE " + COMPLETED
				+ " AND NOT si.is_free" + c.in("s.branch_id") + " GROUP BY si.sale_id, si.product_id";
		long total = 0;
		Map<String, long[]> perProduct = new HashMap<>();
		Map<String, String> names = new HashMap<>();
		for (Object[] r : support.rows("SELECT p.id, p.name, COUNT(*) FROM (" + base + ") b JOIN public.product p ON p.id = b.product_id GROUP BY 1, 2", c)) {
			perProduct.put(str(r[0]), new long[] { lng(r[2]) });
			names.put(str(r[0]), str(r[1]));
		}
		List<Object[]> totalRows = support.rows("SELECT COUNT(DISTINCT sale_id) FROM (" + base + ") b", c);
		total = totalRows.isEmpty() ? 0 : lng(totalRows.get(0)[0]);
		List<ProductPair> pairs = new ArrayList<>();
		if (total == 0) {
			return pairs;
		}
		for (Object[] r : support.rows("SELECT a.product_id, b.product_id, COUNT(*) FROM (" + base + ") a JOIN (" + base
				+ ") b ON a.sale_id = b.sale_id AND a.product_id < b.product_id GROUP BY 1, 2 HAVING COUNT(*) >= " + MIN_PAIR
				+ " ORDER BY 3 DESC LIMIT 100", c)) {
			String a = str(r[0]);
			String b = str(r[1]);
			long together = lng(r[2]);
			long countA = perProduct.getOrDefault(a, new long[] { 0 })[0];
			long countB = perProduct.getOrDefault(b, new long[] { 0 })[0];
			if (countA == 0 || countB == 0) {
				continue;
			}
			double lift = (double) together * total / ((double) countA * countB);
			pairs.add(new ProductPair(names.get(a), names.get(b), together, Math.round(together * 1000.0 / total) / 10.0,
					Math.round(together * 1000.0 / countA) / 10.0, Math.round(lift * 100) / 100.0));
		}
		pairs.sort((x, y) -> x.together() != y.together() ? Long.compare(y.together(), x.together()) : Double.compare(y.lift(), x.lift()));
		return pairs.size() > TOP_PAIRS ? pairs.subList(0, TOP_PAIRS) : pairs;
	}

	private List<StatusCount> statuses(Ctx c) {
		List<StatusCount> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.status, COUNT(*), SUM(s.total - s.charge_total) FROM public.sale s WHERE s.registered >= :from "
				+ "AND s.registered < :to" + c.in("s.branch_id") + " GROUP BY 1 ORDER BY 2 DESC", c)) {
			list.add(new StatusCount(str(r[0]), lng(r[1]), dec(r[2])));
		}
		return list;
	}

	private List<ReasonCount> voidReasons(Ctx c) {
		List<ReasonCount> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT COALESCE(NULLIF(TRIM(s.void_reason), ''), '(tanpa alasan)'), COUNT(*), SUM(s.total - s.charge_total) "
				+ "FROM public.sale s WHERE s.status = 'VOIDED' AND s.voided_at >= :from AND s.voided_at < :to" + c.in("s.branch_id")
				+ " GROUP BY 1 ORDER BY 2 DESC, 3 DESC LIMIT 10", c)) {
			list.add(new ReasonCount(str(r[0]), lng(r[1]), dec(r[2])));
		}
		return list;
	}

	private Holds holds(Ctx c) {
		long used = 0;
		long discarded = 0;
		long held = 0;
		for (Object[] r : support.rows("SELECT h.status, COUNT(*) FROM public.sale_hold h WHERE h.registered >= :from AND h.registered < :to"
				+ c.in("h.branch_id") + " GROUP BY 1", c)) {
			switch (str(r[0])) {
			case "USED" -> used = lng(r[1]);
			case "DISCARDED" -> discarded = lng(r[1]);
			default -> held = lng(r[1]);
			}
		}
		return new Holds(used + discarded + held, used, discarded, held);
	}

	private Online online(Ctx c) {
		List<StatusCount> funnel = new ArrayList<>();
		for (Object[] r : support.rows("SELECT o.status, COUNT(*), SUM(COALESCE(o.confirmed_total, o.estimate_total)) FROM public.online_order o "
				+ "WHERE o.registered >= :from AND o.registered < :to" + c.in("o.branch_id") + " GROUP BY 1 ORDER BY 2 DESC", c)) {
			funnel.add(new StatusCount(str(r[0]), lng(r[1]), dec(r[2])));
		}
		Double decisionMinutes = null;
		List<Object[]> avg = support.rows("SELECT AVG(EXTRACT(EPOCH FROM (o.decided_at - o.registered)) / 60) FROM public.online_order o "
				+ "WHERE o.decided_at IS NOT NULL AND o.registered >= :from AND o.registered < :to" + c.in("o.branch_id"), c);
		if (!avg.isEmpty() && avg.get(0)[0] != null) {
			decisionMinutes = Math.round(dec(avg.get(0)[0]).doubleValue() * 10) / 10.0;
		}
		BigDecimal onlineRevenue = BigDecimal.ZERO;
		BigDecimal allRevenue = BigDecimal.ZERO;
		for (Object[] r : support.rows("SELECT CASE WHEN EXISTS (SELECT 1 FROM public.online_order o WHERE o.sale_id = s.id) THEN 1 ELSE 0 END, "
				+ "SUM(s.total - s.charge_total) FROM public.sale s WHERE " + COMPLETED + c.in("s.branch_id") + " GROUP BY 1", c)) {
			BigDecimal amount = dec(r[1]);
			allRevenue = allRevenue.add(amount);
			if (((Number) r[0]).intValue() == 1) {
				onlineRevenue = amount;
			}
		}
		List<TopItem> top = new ArrayList<>();
		for (Object[] r : support.rows("SELECT i.product_name, SUM(i.quantity) FROM public.online_order_item i JOIN public.online_order o ON o.id = i.order_id "
				+ "WHERE o.status = 'COMPLETED' AND o.registered >= :from AND o.registered < :to" + c.in("o.branch_id")
				+ " GROUP BY 1 ORDER BY 2 DESC LIMIT 5", c)) {
			top.add(new TopItem(str(r[0]), dec(r[1])));
		}
		return new Online(funnel, decisionMinutes, onlineRevenue, allRevenue.subtract(onlineRevenue),
				AnalyticsSupport.percent(onlineRevenue, allRevenue), top);
	}
}
