package com.saasno.utility.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Ringkasan satu pesanan untuk daftar "Pesanan saya" di browser pembeli. Tanpa nama, nomor HP, alamat, atau isi pesanan.
public record PublicOrderSummaryDTO(String token, String serial, String branchName, String status, String fulfillment, BigDecimal total,
		boolean totalFinal, int itemCount, LocalDateTime registered, LocalDateTime updated) {
}
