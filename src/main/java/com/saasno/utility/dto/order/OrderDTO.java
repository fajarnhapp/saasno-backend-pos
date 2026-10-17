package com.saasno.utility.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Pesanan online untuk staf. Jumlah dalam satuan dasar produk (unit); unitLabel nama satuan untuk ditampilkan.
public record OrderDTO(String id, String serial, String branchId, String branchName, String status, String fulfillment,
		String paymentMethod, String customerName, String customerPhone, String address, String note, BigDecimal estimateTotal,
		BigDecimal confirmedTotal, String saleId, String saleSerial, String decidedByName, LocalDateTime decidedAt,
		String decisionNote, LocalDateTime registered, List<Item> items, List<Event> events) {

	public record Item(String productId, String productName, String unit, String unitLabel, BigDecimal quantity, BigDecimal unitPrice) {
	}

	public record Event(String status, String note, LocalDateTime at) {
	}

}
