package com.saasno.utility.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

// Isi katalog yang dibaca tanpa login. Hanya data yang memang untuk umum: tidak ada angka stok, harga pokok, atau id cabang.
public record PublicCatalogDTO(String branchName, String slug, String whatsapp, long total, List<Product> products) {

	public record Entry(String slug, String branchName) {
	}

	// available: sisa yang bisa dipesan minimal 1 (stok dikurangi yang dikunci pesanan, dibulatkan ke bawah). limited/maxOrder hanya terisi
	// saat sisanya sedikit (10 atau kurang); stok yang banyak tidak pernah ditampilkan.
	public record Product(String id, String serial, String name, String description, BigDecimal price, String unit,
			String unitLabel, boolean available, boolean limited, Integer maxOrder, Long imageVersion) {
	}

}
