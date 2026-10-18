package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.percent;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.dto.analytics.AnalyticsDTO.CustomerType;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Customers;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Lapsing;
import com.saasno.utility.dto.analytics.AnalyticsDTO.NewReturningDay;
import com.saasno.utility.dto.analytics.AnalyticsDTO.RfmRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Segment;
import com.saasno.utility.model.ResponseModel;

// Analitik customer: RFM (recency, frequency, monetary) pada rentang yang dipilih, customer yang mulai jarang datang
// (seluruh riwayat), customer baru lawan berulang, dan pengaruh tipe customer. Penjualan tanpa customer disebut "UMUM".
@Service
public class AnalyticsCustomerService {

	private static final String WHEN = "COALESCE(s.decided_at, s.registered)";
	private static final String COMPLETED = "s.status = 'COMPLETED'";
	private static final String IN_RANGE = " AND " + WHEN + " >= :from AND " + WHEN + " < :to";
	private static final int TOP_RFM = 100;
	private static final int TOP_LAPSING = 30;
	private static final double LAPSE_FACTOR = 1.5;
	private static final long LAPSE_MIN_DAYS = 14;

	@Autowired
	private AnalyticsSupport support;

	private record Buyer(String id, String serial, String name, String type, LocalDate last, long frequency, BigDecimal monetary) {
	}

	@Transactional(readOnly = true)
	public ResponseModel<Customers> get(LocalDate from, LocalDate to, String branchId) {
		Ctx c = support.ctx(from, to, branchId);
		List<RfmRow> rfm = rfm(c);
		Map<String, long[]> bySegment = new LinkedHashMap<>();
		Map<String, BigDecimal> segmentMoney = new HashMap<>();
		for (RfmRow r : rfm) {
			bySegment.computeIfAbsent(r.segment(), k -> new long[1])[0]++;
			segmentMoney.merge(r.segment(), r.monetary(), BigDecimal::add);
		}
		List<Segment> segments = bySegment.entrySet().stream()
				.map(e -> new Segment(e.getKey(), e.getValue()[0], segmentMoney.get(e.getKey())))
				.sorted(Comparator.comparing(Segment::monetary).reversed()).toList();

		List<CustomerType> byType = byType(c);
		long anonymous = byType.stream().filter(t -> "UMUM".equals(t.type())).mapToLong(CustomerType::transactions).sum();
		long identified = byType.stream().filter(t -> !"UMUM".equals(t.type())).mapToLong(CustomerType::transactions).sum();
		return new ResponseModel<Customers>().success(new Customers(segments,
				rfm.size() > TOP_RFM ? rfm.subList(0, TOP_RFM) : rfm, lapsing(c), newVsReturning(c), byType, anonymous, identified));
	}

	// ------------------------------------------------------------------ RFM

	private List<RfmRow> rfm(Ctx c) {
		List<Buyer> buyers = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.customer_id, cu.serial, cu.pic, cu.type, MAX(CAST(" + WHEN + " AS date)), COUNT(*), "
				+ "SUM(s.total - s.charge_total) FROM public.sale s JOIN public.customer cu ON cu.id = s.customer_id WHERE " + COMPLETED
				+ IN_RANGE + c.in("s.branch_id") + " GROUP BY 1, 2, 3, 4", c)) {
			buyers.add(new Buyer(str(r[0]), str(r[1]), str(r[2]), str(r[3]), date(r[4]), lng(r[5]), dec(r[6])));
		}
		Map<String, Integer> recencyScore = score(buyers, b -> ChronoUnit.DAYS.between(b.last(), c.end()), true);
		Map<String, Integer> frequencyScore = score(buyers, b -> b.frequency(), false);
		Map<String, Integer> monetaryScore = score(buyers, b -> b.monetary().doubleValue(), false);
		List<RfmRow> rows = new ArrayList<>();
		for (Buyer b : buyers) {
			int r = recencyScore.get(b.id());
			int f = frequencyScore.get(b.id());
			int m = monetaryScore.get(b.id());
			rows.add(new RfmRow(b.id(), b.serial(), b.name(), b.type(), ChronoUnit.DAYS.between(b.last(), c.end()), b.frequency(),
					b.monetary(), r, f, m, segment(r, f, m, b.frequency())));
		}
		rows.sort(Comparator.comparing(RfmRow::monetary).reversed());
		return rows;
	}

	// Skor 1-5 menurut peringkat: 5 = terbaik (recency terkecil, frequency/monetary terbesar). Nilai sama mendapat skor sama.
	private Map<String, Integer> score(List<Buyer> buyers, ToDoubleFunction<Buyer> value, boolean smallerIsBetter) {
		List<Buyer> sorted = new ArrayList<>(buyers);
		sorted.sort(Comparator.comparingDouble(value));
		if (!smallerIsBetter) {
			java.util.Collections.reverse(sorted);
		}
		Map<String, Integer> scores = new HashMap<>();
		int n = sorted.size();
		int firstOfTie = 0;
		for (int i = 0; i < n; i++) {
			if (i > 0 && value.applyAsDouble(sorted.get(i)) != value.applyAsDouble(sorted.get(i - 1))) {
				firstOfTie = i;
			}
			scores.put(sorted.get(i).id(), 5 - (int) Math.floor(firstOfTie * 5.0 / n));
		}
		return scores;
	}

	private String segment(int r, int f, int m, long frequency) {
		double fm = (f + m) / 2.0;
		if (r >= 4 && frequency == 1) {
			return "Baru";
		}
		if (r >= 4 && fm >= 4) {
			return "Juara";
		}
		if (r >= 3 && fm >= 4) {
			return "Setia";
		}
		if (r >= 4) {
			return "Potensial";
		}
		if (r <= 2 && fm >= 3) {
			return "Berisiko";
		}
		if (r <= 2) {
			return "Hilang";
		}
		return "Perlu perhatian";
	}

	// ------------------------------------------------------------------ mulai jarang datang

	// Seluruh riwayat dalam cakupan: customer dengan minimal 2 pembelian yang sudah lebih lama tidak datang daripada
	// LAPSE_FACTOR x jeda rata-ratanya (dan minimal LAPSE_MIN_DAYS hari).
	private List<Lapsing> lapsing(Ctx c) {
		LocalDate today = LocalDate.now();
		List<Lapsing> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.customer_id, cu.serial, cu.pic, cu.type, MIN(CAST(" + WHEN + " AS date)), MAX(CAST(" + WHEN + " AS date)), COUNT(*), "
				+ "SUM(s.total - s.charge_total) FROM public.sale s JOIN public.customer cu ON cu.id = s.customer_id WHERE " + COMPLETED
				+ c.in("s.branch_id") + " GROUP BY 1, 2, 3, 4 HAVING COUNT(*) >= 2", c)) {
			LocalDate first = date(r[4]);
			LocalDate last = date(r[5]);
			long purchases = lng(r[6]);
			double interval = ChronoUnit.DAYS.between(first, last) / (double) (purchases - 1);
			long since = ChronoUnit.DAYS.between(last, today);
			if (since > Math.max(LAPSE_MIN_DAYS, interval * LAPSE_FACTOR)) {
				list.add(new Lapsing(str(r[0]), str(r[1]), str(r[2]), str(r[3]), last, since, purchases,
						Math.round(interval * 10) / 10.0, dec(r[7])));
			}
		}
		list.sort(Comparator.comparing(Lapsing::monetary).reversed());
		return list.size() > TOP_LAPSING ? list.subList(0, TOP_LAPSING) : list;
	}

	// ------------------------------------------------------------------ baru lawan berulang

	private List<NewReturningDay> newVsReturning(Ctx c) {
		Map<String, LocalDate> firstPurchase = new HashMap<>();
		for (Object[] r : support.rows("SELECT s.customer_id, CAST(MIN(" + WHEN + ") AS date) FROM public.sale s WHERE " + COMPLETED
				+ " AND s.customer_id IS NOT NULL" + c.in("s.branch_id") + " GROUP BY 1", c)) {
			firstPurchase.put(str(r[0]), date(r[1]));
		}
		Map<LocalDate, Set<String>> visits = new TreeMap<>();
		Map<LocalDate, Long> anonymous = new HashMap<>();
		for (Object[] r : support.rows("SELECT CAST(" + WHEN + " AS date), s.customer_id, COUNT(*) FROM public.sale s WHERE " + COMPLETED + IN_RANGE
				+ c.in("s.branch_id") + " GROUP BY 1, 2", c)) {
			LocalDate day = date(r[0]);
			if (r[1] == null) {
				anonymous.merge(day, lng(r[2]), Long::sum);
			} else {
				visits.computeIfAbsent(day, k -> new HashSet<>()).add(str(r[1]));
			}
		}
		List<NewReturningDay> days = new ArrayList<>();
		for (LocalDate cursor = c.start(); !cursor.isAfter(c.end()); cursor = cursor.plusDays(1)) {
			final LocalDate day = cursor;
			Set<String> ids = visits.getOrDefault(day, Set.of());
			long fresh = ids.stream().filter(id -> day.equals(firstPurchase.get(id))).count();
			days.add(new NewReturningDay(day, fresh, ids.size() - fresh, anonymous.getOrDefault(day, 0L)));
		}
		return days;
	}

	// ------------------------------------------------------------------ tipe customer

	private List<CustomerType> byType(Ctx c) {
		Map<String, Object[]> sales = new LinkedHashMap<>();
		for (Object[] r : support.rows("SELECT COALESCE(cu.type, 'UMUM'), COUNT(DISTINCT s.customer_id), COUNT(*), SUM(s.total - s.charge_total) "
				+ "FROM public.sale s LEFT JOIN public.customer cu ON cu.id = s.customer_id WHERE " + COMPLETED + IN_RANGE
				+ c.in("s.branch_id") + " GROUP BY 1 ORDER BY 4 DESC", c)) {
			sales.put(str(r[0]), r);
		}
		Map<String, Object[]> lines = new HashMap<>();
		for (Object[] r : support.rows("SELECT COALESCE(cu.type, 'UMUM'), SUM(si.net_total), SUM(si.quantity * COALESCE(si.unit_cost, 0)), COUNT(*), "
				+ "SUM(CASE WHEN si.price_source IN ('CUSTOMER', 'CUSTOMER_TYPE') THEN 1 ELSE 0 END) FROM public.sale_item si "
				+ "JOIN public.sale s ON s.id = si.sale_id LEFT JOIN public.customer cu ON cu.id = s.customer_id WHERE " + COMPLETED + IN_RANGE
				+ c.in("s.branch_id") + " GROUP BY 1", c)) {
			lines.put(str(r[0]), r);
		}
		List<CustomerType> list = new ArrayList<>();
		for (Map.Entry<String, Object[]> e : sales.entrySet()) {
			Object[] s = e.getValue();
			Object[] l = lines.getOrDefault(e.getKey(), new Object[] { null, 0, 0, 0, 0 });
			long transactions = lng(s[2]);
			BigDecimal revenue = dec(s[3]);
			BigDecimal net = dec(l[1]);
			list.add(new CustomerType(e.getKey(), lng(s[1]), transactions, revenue,
					transactions == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(transactions), 0, RoundingMode.HALF_UP),
					percent(net.subtract(dec(l[2])), net), AnalyticsSupport.ratio(lng(l[4]), lng(l[3]))));
		}
		return list;
	}
}
