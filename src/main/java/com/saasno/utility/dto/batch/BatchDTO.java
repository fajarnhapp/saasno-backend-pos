package com.saasno.utility.dto.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Satu batch stok berkedaluwarsa. status: EXPIRED (sudah lewat), SOON (dalam batas peringatan), OK. value = jumlah x harga pokok produk.
public record BatchDTO(String id, String branchId, String branchName, String productId, String serial, String productName, String unit,
		String batchNo, LocalDate expiryDate, BigDecimal quantity, BigDecimal value, long daysLeft, String status) {

	// Ringkasan untuk kartu/peringatan; warningDays = batas hari "segera kedaluwarsa" yang berlaku.
	public record Summary(long expiredBatches, BigDecimal expiredQuantity, BigDecimal expiredValue, long soonBatches, BigDecimal soonValue,
			int warningDays) {
	}

	public record Page(List<BatchDTO> data, long total, Summary summary) {
	}
}
