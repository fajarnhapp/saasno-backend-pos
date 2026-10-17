-- Katalog publik diambil dari stok cabang: produk aktif yang ada stoknya di cabang itu tampil otomatis, tanpa ditandai satu per satu.
-- Menu Katalog tinggal untuk MENYEMBUNYIKAN produk tertentu (hidden), deskripsi, dan foto. Kolom visible lama tidak lagi dipakai
-- (dibiarkan agar backend versi lama tetap jalan sampai di-restart; boleh dihapus kemudian).
BEGIN;
ALTER TABLE public.product_catalog ADD COLUMN hidden boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN public.product_catalog.visible IS 'Tidak dipakai lagi (katalog diambil dari stok); diganti hidden.';
COMMIT;
