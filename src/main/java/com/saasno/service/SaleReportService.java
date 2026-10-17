package com.saasno.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.utility.BranchScope;
import com.saasno.utility.dto.sale.SaleCashDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Laporan kas harian per cabang. Tanpa shift, laporan ini dasar mencocokkan uang di laci: uang masuk dari
// penjualan selesai, dikurangi uang keluar untuk void dan retur, masing-masing pada harinya sendiri.
@Service
public class SaleReportService {

	private static final int MAX_DAYS = 366;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional(readOnly = true)
	public ResponseModel<List<SaleCashDTO>> cash(LocalDate from, LocalDate to, String branchId) {
		return cash(from, to, branchId, branchScopeService.current());
	}

	// Dengan cakupan cabang yang diberikan (pengunduhan berjalan tanpa sesi).
	@Transactional(readOnly = true)
	public ResponseModel<List<SaleCashDTO>> cash(LocalDate from, LocalDate to, String branchId, BranchScope scope) {
		LocalDate end = to == null ? LocalDate.now() : to;
		LocalDate start = from == null ? end : from;
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

		// Cabang yang dilaporkan: yang diminta, atau seluruh cakupan user (global = semua).
		String branchFilter = wanted != null ? " AND x.branch_id = :branch"
				: scope.isGlobal() ? "" : " AND x.branch_id IN (:branches)";
		Map<String, SaleCashDTO> rows = new TreeMap<>();

		// Penjualan selesai (termasuk yang kemudian di-void): pada hari selesai.
		for (Object[] r : query("SELECT x.branch_id, CAST(COALESCE(x.decided_at, x.registered) AS date), COUNT(*), "
				+ "SUM(x.total), SUM(x.promo_discount), SUM(x.manual_discount), SUM(x.charge_total) FROM public.sale x "
				+ "WHERE x.status IN ('COMPLETED','VOIDED') AND COALESCE(x.decided_at, x.registered) >= :from "
				+ "AND COALESCE(x.decided_at, x.registered) < :to" + branchFilter + " GROUP BY 1, 2", start, end, wanted, scope)) {
			SaleCashDTO dto = row(rows, r);
			dto.setSaleCount(((Number) r[2]).longValue());
			dto.setSalesTotal((BigDecimal) r[3]);
			dto.setPromoDiscount((BigDecimal) r[4]);
			dto.setManualDiscount((BigDecimal) r[5]);
			dto.setChargeTotal((BigDecimal) r[6]);
		}
		// Void: uang keluar pada hari void.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.voided_at AS date), COUNT(*), SUM(x.total) FROM public.sale x "
				+ "WHERE x.status = 'VOIDED' AND x.voided_at >= :from AND x.voided_at < :to" + branchFilter
				+ " GROUP BY 1, 2", start, end, wanted, scope)) {
			SaleCashDTO dto = row(rows, r);
			dto.setVoidCount(((Number) r[2]).longValue());
			dto.setVoidTotal((BigDecimal) r[3]);
		}
		// Retur: uang keluar pada hari retur.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.registered AS date), COUNT(*), SUM(x.refund_total) "
				+ "FROM public.sale_return x WHERE x.registered >= :from AND x.registered < :to" + branchFilter
				+ " GROUP BY 1, 2", start, end, wanted, scope)) {
			SaleCashDTO dto = row(rows, r);
			dto.setReturnCount(((Number) r[2]).longValue());
			dto.setReturnTotal((BigDecimal) r[3]);
		}

		List<SaleCashDTO> result = new ArrayList<>(rows.values());
		Map<String, String> names = branchScopeService.names(result.stream().map(SaleCashDTO::getBranchId).toList());
		result.forEach(d -> {
			d.setBranchName(names.get(d.getBranchId()));
			d.setNet(d.getSalesTotal().subtract(d.getVoidTotal()).subtract(d.getReturnTotal()));
		});
		result.sort(Comparator.comparing(SaleCashDTO::getDate).reversed().thenComparing(SaleCashDTO::getBranchName));
		return new ResponseModel<List<SaleCashDTO>>().success(result);
	}

	@SuppressWarnings("unchecked")
	private List<Object[]> query(String sql, LocalDate start, LocalDate end, String branch, BranchScope scope) {
		var query = entityManager.createNativeQuery(sql)
				.setParameter("from", start.atStartOfDay())
				.setParameter("to", end.plusDays(1).atStartOfDay());
		if (branch != null) {
			query.setParameter("branch", branch);
		} else if (!scope.isGlobal()) {
			query.setParameter("branches", scope.getIds());
		}
		return query.getResultList();
	}

	// Satu baris per (cabang, tanggal); kolom yang tidak ada kejadian bernilai 0.
	private SaleCashDTO row(Map<String, SaleCashDTO> rows, Object[] r) {
		String branchId = (String) r[0];
		LocalDate date = ((Date) r[1]).toLocalDate();
		return rows.computeIfAbsent(branchId + "|" + date, key -> {
			SaleCashDTO dto = new SaleCashDTO();
			dto.setBranchId(branchId);
			dto.setDate(date);
			dto.setSaleCount(0L);
			dto.setSalesTotal(BigDecimal.ZERO);
			dto.setVoidCount(0L);
			dto.setVoidTotal(BigDecimal.ZERO);
			dto.setReturnCount(0L);
			dto.setReturnTotal(BigDecimal.ZERO);
			dto.setPromoDiscount(BigDecimal.ZERO);
			dto.setManualDiscount(BigDecimal.ZERO);
			dto.setChargeTotal(BigDecimal.ZERO);
			return dto;
		});
	}

}
