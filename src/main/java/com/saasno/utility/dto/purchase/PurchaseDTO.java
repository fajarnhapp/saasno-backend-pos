package com.saasno.utility.dto.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Pesanan pembelian. Jumlah dalam satuan dasar produk, harga per satuan dasar. items dan receipts hanya terisi pada rincian.
public record PurchaseDTO(String id, String serial, String branchId, String branchName, String supplierId, String supplierName,
		String status, LocalDate expectedDate, String note, BigDecimal total, String createdByName, LocalDateTime registered,
		LocalDateTime orderedAt, LocalDateTime closedAt, String closeNote, long lineCount, List<Item> items,
		List<Receipt> receipts) {

	public record Item(String productId, String serial, String name, String unit, BigDecimal quantity, BigDecimal unitCost,
			BigDecimal receivedQuantity, BigDecimal lineTotal) {
	}

	public record ReceiptItem(String name, String unit, BigDecimal quantity, BigDecimal unitCost) {
	}

	public record Receipt(String serial, String invoiceNo, String note, BigDecimal total, String receivedByName,
			LocalDateTime registered, List<ReceiptItem> items) {
	}
}
