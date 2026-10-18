package com.saasno.utility.dto.shift;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Satu shift kasir. Untuk shift yang masih terbuka, cashSales/refunds/voids/saleCount/expectedCash dihitung langsung
// dari transaksi sampai saat ini; countedCash dan difference baru terisi setelah ditutup. overdue = sudah melewati
// SHIFT_MAX_HOURS (penjualan baru ditolak sampai shift ditutup).
public record ShiftDTO(String id, String serial, String branchId, String branchName, String userId, String username,
		String status, LocalDateTime openedAt, BigDecimal openingCash, LocalDateTime closedAt, String closedBy,
		String closedByName, boolean forced, BigDecimal cashSales, BigDecimal collections, BigDecimal refunds, BigDecimal voids, long saleCount,
		BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference, String note, boolean overdue,
		int maxHours, BigDecimal differenceLimit) {

	// Keadaan shift user saat ini: apakah shift wajib, dan shift yang terbuka (null bila belum ada).
	public record State(boolean required, ShiftDTO shift) {
	}
}
