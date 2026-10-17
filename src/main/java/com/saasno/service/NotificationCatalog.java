package com.saasno.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Daftar jenis notifikasi dan kategorinya. User mematikan notifikasi per KATEGORI (bukan per jenis), jadi
// "Oper stok" mencakup pengajuan sekaligus hasil keputusannya.
public final class NotificationCatalog {

	public record Category(String id, String label, String description) {
	}

	public static final List<Category> CATEGORIES = List.of(
			new Category("STOCK_TRANSFER", "Oper stok", "Pengajuan oper stok yang perlu Anda putuskan dan hasil pengajuan Anda."),
			new Category("STOCK_OPNAME", "Stok opname", "Pengajuan stok opname yang perlu Anda putuskan dan hasil pengajuan Anda."),
			new Category("SALE_APPROVAL", "Persetujuan diskon", "Transaksi yang menunggu persetujuan diskon dan hasil keputusannya."),
			new Category("STOCK_ALERT", "Stok menipis atau habis", "Saat stok produk melewati batas menipis atau habis."),
			new Category("HOLD", "Transaksi tertahan", "Pengingat keranjang yang ditahan terlalu lama."),
			new Category("FINANCE", "Peringatan keuangan", "Biaya melebihi batas peringatan dan saldo kas kecil minus."),
			new Category("ORDER", "Pesanan online", "Pesanan baru dari katalog dan pesanan yang dibatalkan pembeli."),
			new Category("ARTICLE", "Artikel baru", "Artikel baru yang terbit untuk cabang Anda."),
			new Category("DOWNLOAD", "Unduhan", "Unduhan data yang sudah selesai atau gagal."));

	private static final Map<String, String> TYPE_CATEGORY = new LinkedHashMap<>();

	static {
		for (String type : List.of("STOCK_TRANSFER_REQUEST", "STOCK_TRANSFER_APPROVED", "STOCK_TRANSFER_REJECTED")) {
			TYPE_CATEGORY.put(type, "STOCK_TRANSFER");
		}
		for (String type : List.of("STOCK_OPNAME_REQUEST", "STOCK_OPNAME_APPROVED", "STOCK_OPNAME_REJECTED")) {
			TYPE_CATEGORY.put(type, "STOCK_OPNAME");
		}
		for (String type : List.of("SALE_WAITING_APPROVAL", "SALE_APPROVED", "SALE_REJECTED")) {
			TYPE_CATEGORY.put(type, "SALE_APPROVAL");
		}
		TYPE_CATEGORY.put("STOCK_LOW", "STOCK_ALERT");
		TYPE_CATEGORY.put("STOCK_OUT", "STOCK_ALERT");
		TYPE_CATEGORY.put("HOLD_STALE", "HOLD");
		TYPE_CATEGORY.put("EXPENSE_OVER_LIMIT", "FINANCE");
		TYPE_CATEGORY.put("PETTY_CASH_NEGATIVE", "FINANCE");
		TYPE_CATEGORY.put("ORDER_NEW", "ORDER");
		TYPE_CATEGORY.put("ORDER_CANCELLED", "ORDER");
		TYPE_CATEGORY.put("ARTICLE_PUBLISHED", "ARTICLE");
		TYPE_CATEGORY.put("DOWNLOAD_DONE", "DOWNLOAD");
		TYPE_CATEGORY.put("DOWNLOAD_FAILED", "DOWNLOAD");
	}

	private NotificationCatalog() {
	}

	// Kategori sebuah jenis; null bila jenis tidak dikenal (tidak bisa dimatikan).
	public static String categoryOf(String type) {
		return TYPE_CATEGORY.get(type);
	}

	public static boolean isCategory(String id) {
		return CATEGORIES.stream().anyMatch(c -> c.id().equals(id));
	}

}
