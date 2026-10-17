package com.saasno.utility.dto.order;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Pesanan dari pembeli di katalog publik. Harga tidak pernah diterima dari pembeli; dihitung dari katalog.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicOrderRequest(String name, String phone, String fulfillment, String paymentMethod, String address, String note,
		List<Item> items) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String productId, BigDecimal quantity) {
	}

}
