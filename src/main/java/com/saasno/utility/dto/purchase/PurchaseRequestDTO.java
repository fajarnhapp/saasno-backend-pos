package com.saasno.utility.dto.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Membuat/mengubah pesanan (id kosong = baru; order = langsung dipesan, bukan draf), menerima barang (id, invoiceNo, note,
// items), atau tindakan sederhana (id, note). unit boleh kosong (satuan dasar) atau satuan konversi produk; unitCost per unit itu.
public record PurchaseRequestDTO(String id, String branchId, String supplierId, LocalDate expectedDate, String note,
		boolean order, String invoiceNo, List<Item> items) {

	// expiryDate dan batchNo hanya dipakai saat menerima barang produk yang dilacak kedaluwarsanya.
	public record Item(String productId, BigDecimal quantity, String unit, BigDecimal unitCost, LocalDate expiryDate, String batchNo) {
	}
}
