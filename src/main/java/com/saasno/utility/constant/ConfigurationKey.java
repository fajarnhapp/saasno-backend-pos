package com.saasno.utility.constant;

public enum ConfigurationKey {

	FRONTEND_URL,

	// Daftar satuan produk, JSON: [{"value":"PCS","label":"Pcs"}, ...].
	PRODUCT_UNIT,

	// Biaya lain-lain pada penjualan (pajak, biaya layanan, ...), JSON:
	// [{"value":"PPN","label":"PPN","type":"PERCENT","amount":11,"inclusive":false}, ...]. Boleh kosong.
	SALE_CHARGE,

	// Batas diskon manual (persen dari subtotal setelah promo) yang boleh langsung; di atasnya perlu persetujuan.
	MANUAL_DISCOUNT_MAX_PERCENT,

	// Kategori biaya operasional, JSON: [{"value":"SUPPLIES","label":"Perlengkapan"}, ...].
	EXPENSE_CATEGORY,

	// Batas peringatan biaya operasional (rupiah); biaya di atasnya tetap diterima tetapi diberi peringatan.
	EXPENSE_WARNING_AMOUNT,

	// Stok di bawah atau sama dengan batas ini dianggap menipis di dashboard (bilangan bulat >= 0).
	LOW_STOCK_THRESHOLD,

	// Nomor WhatsApp admin untuk tombol lupa kata sandi dan daftar di halaman login (format 62xxxxxxxxxx, tanpa +).
	// Satu-satunya konfigurasi yang dibuka tanpa sesi lewat GET /api/public/contact.
	ADMIN_WHATSAPP,

	// Lama (hari) notifikasi yang sudah dibaca disimpan sebelum dihapus otomatis (bilangan bulat >= 1).
	NOTIFICATION_RETENTION_DAYS,

	// Keranjang yang ditahan lebih lama dari ini (jam) diingatkan ke kasir yang menahannya (bilangan bulat >= 1).
	HOLD_STALE_HOURS,

	// Jumlah login (atau kode OTP) salah berturut-turut sebelum akun dikunci sementara (bilangan bulat >= 1).
	MAX_LOGIN_ATTEMPTS,

	// Lama (menit) akun terkunci setelah melewati MAX_LOGIN_ATTEMPTS (bilangan bulat >= 1).
	LOGIN_LOCK_MINUTES;

}
