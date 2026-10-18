package com.saasno.utility.dto.purchase;

import java.math.BigDecimal;

// Saran pesan ulang: stok + pesanan yang belum datang dibanding kebutuhan sampai SUGGEST_DAYS hari ke depan.
public record SuggestionDTO(String productId, String serial, String name, String unit, BigDecimal stock, BigDecimal incoming,
		BigDecimal avgDaily, Double daysCover, BigDecimal suggestedQuantity, BigDecimal lastCost, String lastSupplierId,
		String lastSupplierName) {
}
