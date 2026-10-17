package com.saasno.utility.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.saasno.utility.dto.article.ArticleDTO;

// Ringkasan dashboard: hanya jumlah dan status, tanpa nilai rupiah sama sekali. Angka uang ada di Laporan Kas
// dan Laporan Keuangan.
public record DashboardDTO(General general) {

	public record General(
			long todayCount,
			long yesterdayCount,
			BigDecimal todayQuantity,
			long myTodayCount,
			List<HourCount> hourly,
			List<DayCount> daily,
			List<TopProduct> topProducts,
			StockStatus stock,
			long heldCount,
			long oldHeldCount,
			// null kalau user tidak berhak menyetujui oper stok.
			Long pendingTransferCount,
			// null kalau user tidak berhak menyetujui transaksi.
			Long waitingApprovalCount,
			// null kalau user tidak berhak menyetujui pesanan online.
			Long pendingOrderCount,
			// Artikel terbit terbaru (tanpa isi), yang disematkan di atas.
			List<ArticleDTO> articles) {
	}

	public record HourCount(int hour, long count) {
	}

	public record DayCount(LocalDate date, long count) {
	}

	public record TopProduct(String productId, String serial, String name, BigDecimal quantity) {
	}

	public record StockStatus(long ok, long low, long out, long threshold, List<LowStockItem> items) {
	}

	public record LowStockItem(String productId, String serial, String name, String branchId, String branchName,
			BigDecimal quantity) {
	}


}
