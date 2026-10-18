package com.saasno.service;

import static com.saasno.service.AnalyticsSupport.date;
import static com.saasno.service.AnalyticsSupport.dec;
import static com.saasno.service.AnalyticsSupport.lng;
import static com.saasno.service.AnalyticsSupport.percent;
import static com.saasno.service.AnalyticsSupport.str;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.service.AnalyticsSupport.Ctx;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Approvals;
import com.saasno.utility.dto.analytics.AnalyticsDTO.CashierDiscount;
import com.saasno.utility.dto.analytics.AnalyticsDTO.Promo;
import com.saasno.utility.dto.analytics.AnalyticsDTO.PromoBaseline;
import com.saasno.utility.dto.analytics.AnalyticsDTO.PromoRow;
import com.saasno.utility.dto.analytics.AnalyticsDTO.ReasonCount;
import com.saasno.utility.model.ResponseModel;

// Analitik promo dan diskon manual. "Selama/sebelum" membandingkan omzet harian seluruh toko pada masa berlaku promo
// (dipotong ke rentang yang dipilih) dengan masa sebelumnya yang sama panjang; ini petunjuk, bukan bukti sebab-akibat.
@Service
public class AnalyticsPromoService {

	private static final String WHEN = "COALESCE(s.decided_at, s.registered)";
	private static final String COMPLETED = "s.status = 'COMPLETED' AND " + WHEN + " >= :from AND " + WHEN + " < :to";
	private static final int TOP_PROMOS = 20;

	@Autowired
	private AnalyticsSupport support;

	@Transactional(readOnly = true)
	public ResponseModel<Promo> get(LocalDate from, LocalDate to, String branchId) {
		Ctx c = support.ctx(from, to, branchId);
		return new ResponseModel<Promo>().success(new Promo(promos(c), baseline(c), cashiers(c), reasons(c), approvals(c)));
	}

	private List<PromoRow> promos(Ctx c) {
		List<PromoRow> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.promo_id, MAX(s.promo_name), COUNT(*), SUM(s.promo_discount), SUM(s.total - s.charge_total), "
				+ "AVG(s.total - s.charge_total), SUM(s.subtotal) FROM public.sale s WHERE " + COMPLETED + " AND s.promo_id IS NOT NULL"
				+ c.in("s.branch_id") + " GROUP BY 1 ORDER BY 4 DESC LIMIT " + TOP_PROMOS, c)) {
			String id = str(r[0]);
			BigDecimal avgDuring = null;
			BigDecimal avgBefore = null;
			Double change = null;
			List<Object[]> validity = support.rows("SELECT valid_from, valid_until FROM public.promo WHERE id = :promoId", c, "promoId", id);
			if (!validity.isEmpty()) {
				LocalDate validFrom = date(validity.get(0)[0]);
				LocalDate validUntil = date(validity.get(0)[1]);
				LocalDate duringStart = validFrom == null || validFrom.isBefore(c.start()) ? c.start() : validFrom;
				LocalDate duringEnd = validUntil == null || validUntil.isAfter(c.end()) ? c.end() : validUntil;
				if (!duringEnd.isBefore(duringStart)) {
					Ctx during = new Ctx(duringStart, duringEnd, c.branch(), c.scope());
					Ctx before = new Ctx(duringStart.minusDays(during.days()), duringStart.minusDays(1), c.branch(), c.scope());
					avgDuring = dailyRevenue(during);
					avgBefore = dailyRevenue(before);
					change = avgBefore.signum() == 0 ? null : percent(avgDuring.subtract(avgBefore), avgBefore);
				}
			}
			list.add(new PromoRow(id, str(r[1]), lng(r[2]), dec(r[3]), dec(r[4]), dec(r[5]).setScale(0, RoundingMode.HALF_UP),
					percent(dec(r[3]), dec(r[6])), avgDuring, avgBefore, change));
		}
		return list;
	}

	private BigDecimal dailyRevenue(Ctx w) {
		List<Object[]> rows = support.rows("SELECT COALESCE(SUM(s.total - s.charge_total), 0) FROM public.sale s WHERE " + COMPLETED + w.in("s.branch_id"), w);
		return dec(rows.get(0)[0]).divide(BigDecimal.valueOf(w.days()), 0, RoundingMode.HALF_UP);
	}

	private PromoBaseline baseline(Ctx c) {
		List<Object[]> rows = support.rows("SELECT AVG(s.total - s.charge_total), AVG(CASE WHEN s.promo_id IS NOT NULL THEN s.total - s.charge_total END), "
				+ "AVG(CASE WHEN s.promo_id IS NULL THEN s.total - s.charge_total END), COUNT(*), SUM(CASE WHEN s.promo_id IS NOT NULL THEN 1 ELSE 0 END) "
				+ "FROM public.sale s WHERE " + COMPLETED + c.in("s.branch_id"), c);
		Object[] r = rows.get(0);
		return new PromoBaseline(dec(r[0]).setScale(0, RoundingMode.HALF_UP), dec(r[1]).setScale(0, RoundingMode.HALF_UP),
				dec(r[2]).setScale(0, RoundingMode.HALF_UP), AnalyticsSupport.ratio(lng(r[4]), lng(r[3])));
	}

	// Diskon manual yang diminta per kasir (termasuk yang masih menunggu atau ditolak), terhadap subtotal penjualannya.
	private List<CashierDiscount> cashiers(Ctx c) {
		List<CashierDiscount> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT s.created_by, u.username, COUNT(*), SUM(CASE WHEN s.manual_discount > 0 THEN 1 ELSE 0 END), "
				+ "SUM(s.manual_discount), SUM(s.subtotal) FROM public.sale s JOIN public.\"user\" u ON u.id = s.created_by "
				+ "WHERE s.status <> 'CANCELLED' AND s.registered >= :from AND s.registered < :to" + c.in("s.branch_id")
				+ " GROUP BY 1, 2 HAVING SUM(s.manual_discount) > 0 ORDER BY 5 DESC LIMIT 20", c)) {
			list.add(new CashierDiscount(str(r[0]), str(r[1]), lng(r[2]), lng(r[3]), dec(r[4]), percent(dec(r[4]), dec(r[5]))));
		}
		return list;
	}

	private List<ReasonCount> reasons(Ctx c) {
		List<ReasonCount> list = new ArrayList<>();
		for (Object[] r : support.rows("SELECT COALESCE(NULLIF(TRIM(s.manual_discount_reason), ''), '(tanpa alasan)'), COUNT(*), SUM(s.manual_discount) "
				+ "FROM public.sale s WHERE s.manual_discount > 0 AND s.status <> 'CANCELLED' AND s.registered >= :from AND s.registered < :to"
				+ c.in("s.branch_id") + " GROUP BY 1 ORDER BY 3 DESC LIMIT 10", c)) {
			list.add(new ReasonCount(str(r[0]), lng(r[1]), dec(r[2])));
		}
		return list;
	}

	private Approvals approvals(Ctx c) {
		List<Object[]> rows = support.rows("SELECT SUM(CASE WHEN s.status = 'WAITING_APPROVAL' THEN 1 ELSE 0 END), "
				+ "SUM(CASE WHEN s.status IN ('COMPLETED', 'VOIDED') AND s.decided_by IS NOT NULL THEN 1 ELSE 0 END), "
				+ "SUM(CASE WHEN s.status = 'REJECTED' THEN 1 ELSE 0 END), "
				+ "AVG(CASE WHEN s.decided_by IS NOT NULL THEN EXTRACT(EPOCH FROM (s.decided_at - s.registered)) / 60 END) "
				+ "FROM public.sale s WHERE s.registered >= :from AND s.registered < :to" + c.in("s.branch_id"), c);
		Object[] r = rows.get(0);
		Double minutes = r[3] == null ? null : Math.round(dec(r[3]).doubleValue() * 10) / 10.0;
		return new Approvals(lng(r[0]), lng(r[1]), lng(r[2]), minutes);
	}
}
