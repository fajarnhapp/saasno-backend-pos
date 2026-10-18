package com.saasno.utility.dto.sale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Transaksi yang dicatat kasir saat offline. Jumlah dalam satuan dasar produk dan harga per satuan dasar (harga yang dipakai kasir
// saat itu). offlineId unik per transaksi (kunci idempotensi). total dihitung ulang server dan harus sama dengan yang dibayar pelanggan.
public record SaleOfflineDTO(String offlineId, String branchId, String customerId, LocalDateTime createdAt, BigDecimal paid, BigDecimal total,
		List<Item> items) {

	public record Item(String productId, BigDecimal quantity, BigDecimal unitPrice, String priceSource) {
	}
}
