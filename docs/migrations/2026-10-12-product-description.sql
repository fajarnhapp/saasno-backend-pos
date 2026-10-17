-- Deskripsi produk dipindah ke tabel product (diinput di halaman Produk, dipakai katalog publik). Deskripsi yang sebelumnya
-- diisi lewat menu Katalog ikut disalin. product_catalog.description tidak dipakai lagi (dibiarkan agar backend lama tetap jalan).
BEGIN;
ALTER TABLE public.product ADD COLUMN description varchar(1000);
UPDATE public.product p SET description = c.description FROM public.product_catalog c WHERE c.product_id = p.id AND c.description IS NOT NULL;
COMMENT ON COLUMN public.product_catalog.description IS 'Tidak dipakai lagi; diganti product.description.';
COMMIT;
