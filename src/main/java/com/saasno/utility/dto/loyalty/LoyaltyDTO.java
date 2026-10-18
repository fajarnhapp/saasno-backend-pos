package com.saasno.utility.dto.loyalty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class LoyaltyDTO {

	private LoyaltyDTO() {
	}

	// Voucher. Pada permintaan dipakai: branchId (pemilik, saat dibuat), code, name, type (FIXED|PERCENT), value, maxDiscount, minPurchase,
	// maxUses, customerId, validFrom, validUntil, status.
	public record Voucher(String id, String branchId, String branchName, String code, String name, String type, BigDecimal value,
			BigDecimal maxDiscount, BigDecimal minPurchase, Integer maxUses, int usedCount, String customerId, String customerName,
			LocalDate validFrom, LocalDate validUntil, String status) {
	}

	// Saldo poin satu customer; enabled/pointValue/maxRedeemPercent dari pengaturan, untuk layar kasir.
	// creditLimit/creditOutstanding/canCredit: info penjualan tempo untuk layar kasir (batas piutang, piutang berjalan, dan izin user).
	public record CustomerPoints(String customerId, String name, String type, long balance, boolean enabled, BigDecimal pointValue,
			int maxRedeemPercent, BigDecimal creditLimit, BigDecimal creditOutstanding, boolean canCredit) {
	}

	public record LedgerEntry(String id, long delta, long balanceAfter, String kind, String saleSerial, String note, String createdByName,
			LocalDateTime registered) {
	}

	public record AdjustRequest(String customerId, long points, String note) {
	}
}
