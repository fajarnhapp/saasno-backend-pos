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
	LOGIN_LOCK_MINUTES,

	// "true" = kasir wajib membuka shift sebelum membuat penjualan atau retur.
	SHIFT_REQUIRED,

	// Shift yang terbuka lebih lama dari ini (jam) harus ditutup dulu sebelum bisa bertransaksi (bilangan bulat >= 1).
	SHIFT_MAX_HOURS,

	// Selisih uang shift (rupiah) di atas ini wajib diberi catatan saat menutup shift (rupiah utuh >= 0).
	SHIFT_DIFFERENCE_LIMIT,

	// "true" = toko buka 24 jam; aturan "transaksi di luar jam buka" di Analitik dimatikan.
	STORE_24_HOURS,

	// Jam buka dan tutup toko (0-24) untuk aturan "transaksi di luar jam buka" di Analitik; bawaan 6 dan 22.
	STORE_OPEN_HOUR,
	STORE_CLOSE_HOUR,

	// Cara harga pokok produk diperbarui saat barang pembelian diterima: AVERAGE (rata-rata tertimbang), LAST, atau NONE.
	PURCHASE_COST_METHOD,

	// Cadangan database: aktifkan harian (true/false), jam jalan (0-23), jumlah berkas yang disimpan, folder tujuan, dan lokasi pg_dump.
	BACKUP_ENABLED,
	BACKUP_HOUR,
	BACKUP_KEEP_COUNT,
	BACKUP_DIR,
	BACKUP_PG_DUMP_PATH,

	// Batch yang kedaluwarsa dalam sekian hari dianggap "segera kedaluwarsa" (bilangan bulat >= 1, bawaan 30).
	EXPIRY_WARNING_DAYS,

	// Program poin: aktifkan (true/false), rupiah belanja per 1 poin, nilai rupiah 1 poin saat dipakai, dan batas persen tagihan yang boleh dibayar dengan poin.
	LOYALTY_ENABLED,
	LOYALTY_EARN_PER_RUPIAH,
	LOYALTY_POINT_VALUE,
	LOYALTY_MAX_REDEEM_PERCENT,

	// Penjualan tempo: batas total piutang berjalan per customer (rupiah; 0 = dimatikan) dan jatuh tempo bawaan (hari).
	CREDIT_LIMIT,
	CREDIT_DEFAULT_DAYS;

}
