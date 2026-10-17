package com.saasno.utility.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Yang dilihat pembeli lewat tautan pelacakan. Nomor HP disamarkan; tidak ada id internal, id staf, atau stok.
public record PublicOrderDTO(String token, String serial, String branchName, String whatsapp, String status, String fulfillment,
		String paymentMethod, String customerName, String phone, String address, String note, BigDecimal total, boolean totalFinal,
		boolean canCancel, LocalDateTime registered, List<Item> items, List<Event> events) {

	public record Item(String productName, String unitLabel, BigDecimal quantity, BigDecimal unitPrice) {
	}

	public record Event(String status, String note, LocalDateTime at) {
	}

}
