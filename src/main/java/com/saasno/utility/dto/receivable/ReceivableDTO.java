package com.saasno.utility.dto.receivable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Piutang dari satu penjualan tempo. outstanding = amount - paidAmount - returnedAmount. overdue = masih terbuka dan lewat jatuh tempo.
public record ReceivableDTO(String id, String serial, String saleId, String saleSerial, String customerId, String customerName, String phone,
		String branchId, String branchName, BigDecimal amount, BigDecimal paidAmount, BigDecimal returnedAmount, BigDecimal outstanding, LocalDate dueDate,
		String status, boolean overdue, long daysOverdue, String note, String createdByName, LocalDateTime registered, LocalDateTime closedAt,
		List<Payment> payments) {

	public record Payment(String id, BigDecimal amount, String note, String receivedByName, LocalDateTime registered) {
	}

	// Ringkasan untuk kartu di halaman: total piutang berjalan dan yang sudah lewat jatuh tempo.
	public record Summary(long openCount, BigDecimal outstanding, long overdueCount, BigDecimal overdueOutstanding) {
	}

	public record Page(List<ReceivableDTO> data, long total, Summary summary) {
	}

	public record PayRequest(String id, BigDecimal amount, String note) {
	}

	public record WriteOffRequest(String id, String reason) {
	}
}
