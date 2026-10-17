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
import com.saasno.utility.dto.finance.FinanceReportDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.persistence.EntityManager;

// Laporan keuangan harian per cabang, dasar akrual sederhana: penjualan bersih tanpa pajak, harga pokok barang terjual
// (dari harga pokok yang dibekukan di mutasi stok), laba kotor, biaya operasional (biaya kas + nilai pemakaian stok
// sendiri), dan hasil operasional. Biaya yang dibatalkan tidak dihitung. Baris yang harga pokoknya belum diketahui
// dihitung Rp 0 dan dilaporkan di unknownCostLines supaya laba kotor yang tidak lengkap terlihat.
@Service
public class FinanceReportService {

	private static final int MAX_DAYS = 366;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private BranchScopeService branchScopeService;

	@Transactional(readOnly = true)
	public ResponseModel<List<FinanceReportDTO>> report(LocalDate from, LocalDate to, String branchId) {
		return report(from, to, branchId, branchScopeService.current());
	}

	// Dengan cakupan cabang yang diberikan (pengunduhan berjalan tanpa sesi).
	@Transactional(readOnly = true)
	public ResponseModel<List<FinanceReportDTO>> report(LocalDate from, LocalDate to, String branchId, BranchScope scope) {
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
		String filter = wanted != null ? " AND x.branch_id = :branch" : scope.isGlobal() ? "" : " AND x.branch_id IN (:branches)";
		Map<String, FinanceReportDTO> rows = new TreeMap<>();

		// Penjualan selesai (termasuk yang kemudian di-void) pada hari selesai, tanpa pajak.
		for (Object[] r : query("SELECT x.branch_id, CAST(COALESCE(x.decided_at, x.registered) AS date), SUM(x.total - x.charge_total) "
				+ "FROM public.sale x WHERE x.status IN ('COMPLETED','VOIDED') AND COALESCE(x.decided_at, x.registered) >= :from "
				+ "AND COALESCE(x.decided_at, x.registered) < :to" + filter + " GROUP BY 1, 2", start, end, wanted, scope)) {
			row(rows, r).setRevenue(row(rows, r).getRevenue().add((BigDecimal) r[2]));
		}
		// Void mengurangi penjualan pada hari void.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.voided_at AS date), SUM(x.total - x.charge_total) FROM public.sale x "
				+ "WHERE x.status = 'VOIDED' AND x.voided_at >= :from AND x.voided_at < :to" + filter + " GROUP BY 1, 2",
				start, end, wanted, scope)) {
			row(rows, r).setRevenue(row(rows, r).getRevenue().subtract((BigDecimal) r[2]));
		}
		// Retur mengurangi penjualan pada hari retur (bagian pajaknya dikeluarkan sebanding dengan transaksinya).
		for (Object[] r : query("SELECT x.branch_id, CAST(x.registered AS date), SUM(x.refund_total * (1 - CASE WHEN s.total = 0 THEN 0 "
				+ "ELSE s.charge_total / s.total END)) FROM public.sale_return x JOIN public.sale s ON s.id = x.sale_id "
				+ "WHERE x.registered >= :from AND x.registered < :to" + filter + " GROUP BY 1, 2", start, end, wanted, scope)) {
			row(rows, r).setRevenue(row(rows, r).getRevenue().subtract((BigDecimal) r[2]));
		}
		// Harga pokok barang terjual: penjualan menambah, retur/void yang kembali ke stok mengurangi (harga pokok yang sama
		// dengan saat dijual). Barang retur yang rusak tidak kembali ke stok, jadi harga pokoknya tetap terhitung.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.registered AS date), SUM(-x.quantity * COALESCE(x.unit_cost, 0)), "
				+ "SUM(CASE WHEN x.unit_cost IS NULL THEN 1 ELSE 0 END) FROM public.stock_movement x "
				+ "WHERE (x.type = 'SALE' OR (x.type = 'RETURN' AND x.reference_type IN ('SALE_RETURN','SALE_VOID'))) "
				+ "AND x.registered >= :from AND x.registered < :to" + filter + " GROUP BY 1, 2", start, end, wanted, scope)) {
			FinanceReportDTO dto = row(rows, r);
			dto.setCogs(dto.getCogs().add((BigDecimal) r[2]));
			dto.setUnknownCostLines(dto.getUnknownCostLines() + ((Number) r[3]).longValue());
		}
		// Biaya operasional yang masih berlaku: biaya kas dan nilai pemakaian stok sendiri.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.registered AS date), "
				+ "SUM(CASE WHEN x.kind = 'CASH' THEN x.amount ELSE 0 END), SUM(CASE WHEN x.kind = 'STOCK_USE' THEN x.amount ELSE 0 END), "
				+ "SUM(CASE WHEN x.kind = 'STOCK_USE' AND x.unit_cost IS NULL THEN 1 ELSE 0 END) FROM public.store_expense x "
				+ "WHERE x.status = 'RECORDED' AND x.registered >= :from AND x.registered < :to" + filter + " GROUP BY 1, 2",
				start, end, wanted, scope)) {
			FinanceReportDTO dto = row(rows, r);
			dto.setCashExpense(dto.getCashExpense().add((BigDecimal) r[2]));
			dto.setStockUseExpense(dto.getStockUseExpense().add((BigDecimal) r[3]));
			dto.setUnknownCostLines(dto.getUnknownCostLines() + ((Number) r[4]).longValue());
		}
		// Isi kas kecil pada harinya.
		for (Object[] r : query("SELECT x.branch_id, CAST(x.registered AS date), SUM(x.amount) FROM public.petty_cash_entry x "
				+ "WHERE x.type = 'TOPUP' AND x.registered >= :from AND x.registered < :to" + filter + " GROUP BY 1, 2",
				start, end, wanted, scope)) {
			row(rows, r).setPettyCashTopUp((BigDecimal) r[2]);
		}

		List<FinanceReportDTO> result = new ArrayList<>(rows.values());
		Map<String, String> names = branchScopeService.names(result.stream().map(FinanceReportDTO::getBranchId).toList());
		result.forEach(d -> {
			d.setBranchName(names.get(d.getBranchId()));
			d.setGrossProfit(d.getRevenue().subtract(d.getCogs()));
			d.setOperatingResult(d.getGrossProfit().subtract(d.getCashExpense()).subtract(d.getStockUseExpense()));
		});
		result.sort(Comparator.comparing(FinanceReportDTO::getDate).reversed().thenComparing(FinanceReportDTO::getBranchName));
		return new ResponseModel<List<FinanceReportDTO>>().success(result);
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

	private FinanceReportDTO row(Map<String, FinanceReportDTO> rows, Object[] r) {
		String branchId = (String) r[0];
		LocalDate date = ((Date) r[1]).toLocalDate();
		return rows.computeIfAbsent(branchId + "|" + date, key -> {
			FinanceReportDTO dto = new FinanceReportDTO();
			dto.setBranchId(branchId);
			dto.setDate(date);
			dto.setRevenue(BigDecimal.ZERO);
			dto.setCogs(BigDecimal.ZERO);
			dto.setCashExpense(BigDecimal.ZERO);
			dto.setStockUseExpense(BigDecimal.ZERO);
			dto.setPettyCashTopUp(BigDecimal.ZERO);
			dto.setUnknownCostLines(0L);
			return dto;
		});
	}

}
