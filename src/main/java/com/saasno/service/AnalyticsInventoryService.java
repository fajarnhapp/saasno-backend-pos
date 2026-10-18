package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.percent;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.dto.analytics.AnalyticsDTO.AbcClass;
import com.saasno.utility.dto.analytics.AnalyticsDTO.AbcRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.CoverRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.DeadStock;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Inventory;
import com.saasno.utility.dto.analytics.AnalyticsDTO.MatrixPoint;
import com.saasno.utility.dto.analytics.AnalyticsDTO.OpnameRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.ReturnRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Turnover;
import com.saasno.utility.model.ResponseModel;

// Analitik produk dan stok. Penjualan produk dihitung dari baris penjualan selesai (sale_item.quantity sudah dalam satuan
// dasar dan unit_cost per satuan dasar), sebelum retur; retur punya bagian sendiri.
@Service
public class AnalyticsInventoryService {

	private static final int TOP_ABC = 100;
	private static final int TOP_MATRIX = 120;
	private static final int TOP_DEAD = 100;
	private static final int TOP_COVER = 50;
	private static final int TOP_OPNAME = 30;
	private static final int TOP_RETURNS = 20;
	private static final int REORDER_DAYS = 14;
	private static final BigDecimal A_LIMIT = BigDecimal.valueOf(80);
	private static final BigDecimal B_LIMIT = BigDecimal.valueOf(95);

	@Autowired
	private AnalyticsSupport support;

	private record Sold(String id, String serial, String name, BigDecimal quantity, BigDecimal revenue, BigDecimal cost,
			long unknownCost) {
	}

	@Transactional(readOnly = true)
	public ResponseModel<Inventory> get(LocalDate from, LocalDate to, String branchId, Integer deadDaysParam) {
		Ctx c = support.ctx(from, to, branchId);
		int deadDays = deadDaysParam == null || deadDaysParam < 1 ? 30 : Math.min(deadDaysParam, 365);

		List<Sold> sold = sold(c);
		List<AbcRow> abc = new ArrayList<>();
		List<AbcClass> classes = abcClasses(sold, abc);
		BigDecimal[] medians = new BigDecimal[2];
		List<MatrixPoint> matrix = matrix(sold, medians);
		List<DeadStock> dead = dead(c, deadDays);
		BigDecimal deadValue = dead.stream().map(DeadStock::value).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal cogs = sold.stream().map(Sold::cost).reduce(BigDecimal.ZERO, BigDecimal::add);

		return new ResponseModel<Inventory>().success(new Inventory(classes,
				abc.size() > TOP_ABC ? abc.subList(0, TOP_ABC) : abc, matrix, medians[0], medians[1].doubleValue(), deadDays,
				dead, deadValue, cover(c), turnover(c, cogs), opname(c), returns(c)));
	}

	private List<Sold> sold(Ctx c) {
		List<Sold> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT p.id, p.serial, p.name, SUM(si.quantity), SUM(si.net_total), "
				+ "SUM(si.quantity * COALESCE(si.unit_cost, 0)), SUM(CASE WHEN si.unit_cost IS NULL THEN 1 ELSE 0 END) "
				+ "FROM public.sale_item si JOIN public.sale s ON s.id = si.sale_id JOIN public.product p ON p.id = si.product_id "
				+ "WHERE s.status = 'COMPLETED' AND COALESCE(s.decided_at, s.registered) >= :from "
				+ "AND COALESCE(s.decided_at, s.registered) < :to" + c.in("s.branch_id") + " GROUP BY 1, 2, 3", c)) {
			list.add(new Sold(str(r[0]), str(r[1]), str(r[2]), dec(r[3]), dec(r[4]), dec(r[5]), lng(r[6])));
		}
		return list;
	}

	// Kelas A menyumbang sampai 80% omzet, B sampai 95%, sisanya C. Produk yang melewati batas tetap di kelas sebelumnya.
	private List<AbcClass> abcClasses(List<Sold> sold, List<AbcRow> out) {
		List<Sold> sorted = new ArrayList<>(sold);
		sorted.sort(Comparator.comparing(Sold::revenue).reversed());
		BigDecimal total = sorted.stream().map(Sold::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal running = BigDecimal.ZERO;
		long[] counts = new long[3];
		BigDecimal[] revenue = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
		for (Sold s : sorted) {
			double before = percent(running, total);
			running = running.add(s.revenue());
			int index = before < A_LIMIT.doubleValue() ? 0 : before < B_LIMIT.doubleValue() ? 1 : 2;
			counts[index]++;
			revenue[index] = revenue[index].add(s.revenue());
			out.add(new AbcRow(s.id(), s.serial(), s.name(), s.revenue(), percent(s.revenue(), total), percent(running, total),
					"ABC".substring(index, index + 1)));
		}
		List<AbcClass> classes = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			classes.add(new AbcClass("ABC".substring(i, i + 1), counts[i], revenue[i], percent(revenue[i], total)));
		}
		return classes;
	}

	// Matriks margin lawan volume; produk yang harga pokoknya belum lengkap tidak ikut (margin-nya menyesatkan).
	private List<MatrixPoint> matrix(List<Sold> sold, BigDecimal[] medians) {
		List<Sold> usable = sold.stream().filter(s -> s.unknownCost() == 0 && s.revenue().signum() > 0)
				.sorted(Comparator.comparing(Sold::revenue).reversed()).limit(TOP_MATRIX).toList();
		List<BigDecimal> quantities = usable.stream().map(Sold::quantity).sorted().toList();
		List<Double> margins = usable.stream().map(s -> percent(s.revenue().subtract(s.cost()), s.revenue())).sorted().toList();
		BigDecimal medianQty = quantities.isEmpty() ? BigDecimal.ZERO : quantities.get(quantities.size() / 2);
		double medianMargin = margins.isEmpty() ? 0 : margins.get(margins.size() / 2);
		medians[0] = medianQty;
		medians[1] = BigDecimal.valueOf(medianMargin);
		List<MatrixPoint> points = new ArrayList<>();
		for (Sold s : usable) {
			BigDecimal margin = s.revenue().subtract(s.cost());
			double marginPercent = percent(margin, s.revenue());
			boolean popular = s.quantity().compareTo(medianQty) >= 0;
			boolean thick = marginPercent >= medianMargin;
			String quadrant = popular ? (thick ? "STAR" : "COW") : (thick ? "QUESTION" : "DOG");
			points.add(new MatrixPoint(s.id(), s.serial(), s.name(), s.quantity(), s.revenue(), margin, marginPercent, quadrant));
		}
		return points;
	}

	// Ada stok tetapi tidak terjual dalam deadDays hari terakhir (atau belum pernah). Nilai = stok x harga pokok produk.
	private List<DeadStock> dead(Ctx c, int deadDays) {
		LocalDateTime cut = LocalDate.now().minusDays(deadDays).atStartOfDay();
		LocalDate today = LocalDate.now();
		List<DeadStock> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT p.id, p.serial, p.name, SUM(b.quantity), SUM(b.quantity * COALESCE(p.cost_price, 0)), "
				+ "MAX(l.last_sold) FROM public.stock_balance b JOIN public.product p ON p.id = b.product_id "
				+ "LEFT JOIN (SELECT si.product_id, MAX(COALESCE(s.decided_at, s.registered)) AS last_sold FROM public.sale_item si "
				+ "JOIN public.sale s ON s.id = si.sale_id WHERE s.status = 'COMPLETED'" + c.in("s.branch_id")
				+ " GROUP BY si.product_id) l ON l.product_id = b.product_id WHERE b.quantity > 0" + c.in("b.branch_id")
				+ " GROUP BY p.id, p.serial, p.name HAVING MAX(l.last_sold) IS NULL OR MAX(l.last_sold) < :cut "
				+ "ORDER BY 5 DESC LIMIT " + TOP_DEAD, c, "cut", cut)) {
			LocalDate last = date(r[5]);
			list.add(new DeadStock(str(r[0]), str(r[1]), str(r[2]), dec(r[3]), dec(r[4]), last,
					last == null ? null : ChronoUnit.DAYS.between(last, today)));
		}
		return list;
	}

	// Sisa hari stok dari laju jual rata-rata rentang yang dipilih; saran pesan ulang menutup REORDER_DAYS hari ke depan.
	private List<CoverRow> cover(Ctx c) {
		BigDecimal days = BigDecimal.valueOf(Math.max(1, c.days()));
		LocalDate today = LocalDate.now();
		List<CoverRow> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT p.id, p.serial, p.name, COALESCE(st.q, 0), sold.q FROM (SELECT si.product_id, SUM(si.quantity) AS q "
				+ "FROM public.sale_item si JOIN public.sale s ON s.id = si.sale_id WHERE s.status = 'COMPLETED' "
				+ "AND COALESCE(s.decided_at, s.registered) >= :from AND COALESCE(s.decided_at, s.registered) < :to"
				+ c.in("s.branch_id") + " GROUP BY si.product_id) sold JOIN public.product p ON p.id = sold.product_id "
				+ "LEFT JOIN (SELECT b.product_id, SUM(b.quantity) AS q FROM public.stock_balance b WHERE 1 = 1" + c.in("b.branch_id")
				+ " GROUP BY b.product_id) st ON st.product_id = p.id WHERE sold.q > 0", c)) {
			BigDecimal stock = dec(r[3]);
			BigDecimal avgDaily = dec(r[4]).divide(days, 4, RoundingMode.HALF_UP);
			Double cover = avgDaily.signum() == 0 ? null : stock.divide(avgDaily, 1, RoundingMode.HALF_UP).doubleValue();
			LocalDate stockout = cover == null ? null : today.plusDays((long) Math.floor(cover));
			BigDecimal reorder = avgDaily.multiply(BigDecimal.valueOf(REORDER_DAYS)).subtract(stock)
					.max(BigDecimal.ZERO).setScale(0, RoundingMode.CEILING);
			list.add(new CoverRow(str(r[0]), str(r[1]), str(r[2]), stock, avgDaily.setScale(2, RoundingMode.HALF_UP), cover,
					stockout, reorder));
		}
		list.sort(Comparator.comparing(CoverRow::daysCover, Comparator.nullsLast(Comparator.naturalOrder())));
		return list.size() > TOP_COVER ? list.subList(0, TOP_COVER) : list;
	}

	// Perputaran persediaan setahun (harga pokok periode, diannualkan) terhadap nilai persediaan saat ini.
	private Turnover turnover(Ctx c, BigDecimal cogs) {
		List<Object[]> rows = support.rows("SELECT COALESCE(SUM(b.quantity * COALESCE(p.cost_price, 0)), 0) FROM public.stock_balance b "
				+ "JOIN public.product p ON p.id = b.product_id WHERE b.quantity > 0" + c.in("b.branch_id"), c);
		BigDecimal value = rows.isEmpty() ? BigDecimal.ZERO : dec(rows.get(0)[0]);
		double annualFactor = 365.0 / Math.max(1, c.days());
		double turns = value.signum() == 0 ? 0 : Math.round(cogs.doubleValue() * annualFactor / value.doubleValue() * 10) / 10.0;
		Double inventoryDays = cogs.signum() == 0 ? null
				: Math.round(value.doubleValue() / (cogs.doubleValue() / Math.max(1, c.days())) * 10) / 10.0;
		return new Turnover(cogs, value, turns, inventoryDays);
	}

	private List<OpnameRow> opname(Ctx c) {
		List<OpnameRow> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT p.id, p.serial, p.name, COUNT(*), SUM(i.counted_quantity - i.system_quantity), "
				+ "SUM(ABS(i.counted_quantity - i.system_quantity)), SUM((i.counted_quantity - i.system_quantity) * COALESCE(p.cost_price, 0)) "
				+ "FROM public.stock_opname_item i JOIN public.stock_opname o ON o.id = i.opname_id JOIN public.product p ON p.id = i.product_id "
				+ "WHERE o.status = 'APPROVED' AND o.decided_at >= :from AND o.decided_at < :to" + c.in("o.branch_id")
				+ " GROUP BY 1, 2, 3 HAVING SUM(ABS(i.counted_quantity - i.system_quantity)) > 0 ORDER BY 6 DESC LIMIT " + TOP_OPNAME, c)) {
			list.add(new OpnameRow(str(r[0]), str(r[1]), str(r[2]), lng(r[3]), dec(r[4]), dec(r[5]), dec(r[6])));
		}
		return list;
	}

	private List<ReturnRow> returns(Ctx c) {
		List<ReturnRow> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT p.id, p.serial, p.name, SUM(si.quantity), SUM(si.returned_quantity) FROM public.sale_item si "
				+ "JOIN public.sale s ON s.id = si.sale_id JOIN public.product p ON p.id = si.product_id WHERE s.status = 'COMPLETED' "
				+ "AND COALESCE(s.decided_at, s.registered) >= :from AND COALESCE(s.decided_at, s.registered) < :to" + c.in("s.branch_id")
				+ " GROUP BY 1, 2, 3 HAVING SUM(si.returned_quantity) > 0", c)) {
			BigDecimal sold = dec(r[3]);
			BigDecimal returned = dec(r[4]);
			list.add(new ReturnRow(str(r[0]), str(r[1]), str(r[2]), sold, returned, percent(returned, sold)));
		}
		list.sort(Comparator.comparingDouble(ReturnRow::ratePercent).reversed());
		return list.size() > TOP_RETURNS ? list.subList(0, TOP_RETURNS) : list;
	}
}
