package com.saasno.utility.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// field: PRICE (harga jual dasar) atau COST_PRICE (harga pokok). source: MANUAL (form produk), PURCHASE (penerimaan pembelian), IMPORT.
public record PriceHistoryDTO(String id, String field, BigDecimal oldValue, BigDecimal newValue, String source, String changedByName,
		LocalDateTime changedAt, String note) {
}
