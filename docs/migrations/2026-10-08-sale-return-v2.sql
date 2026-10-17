-- Retur: promo dihitung ulang, pilihan kembali ke stok atau rusak, dan laporan kas harian.
BEGIN;

-- Aturan promo yang dipakai transaksi dibekukan supaya bisa dihitung ulang saat retur walau promonya diubah atau
-- dihapus kemudian. Transaksi lama (sebelum kolom ini) kosong dan dihitung proporsional seperti sebelumnya.
ALTER TABLE public.sale
    ADD COLUMN promo_scope           varchar(16),
    ADD COLUMN promo_product_id      varchar(64),
    ADD COLUMN promo_condition_type  varchar(16),
    ADD COLUMN promo_min_value       numeric(19,6),
    ADD COLUMN promo_reward_type     varchar(16),
    ADD COLUMN promo_discount_value  numeric(19,2),
    ADD COLUMN promo_value           numeric(19,2);

-- Potongan promo yang ditahan dari nilai kembali karena syarat promo tidak terpenuhi lagi setelah retur.
ALTER TABLE public.sale_return
    ADD COLUMN promo_adjustment numeric(19,2) NOT NULL DEFAULT 0
        CONSTRAINT sale_return_promo_adjustment_check CHECK (promo_adjustment >= 0);

-- restock = false: barang rusak, uang tetap dikembalikan tetapi stok tidak bertambah.
ALTER TABLE public.sale_return_item
    ADD COLUMN restock boolean NOT NULL DEFAULT true;

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('POST_API_SALE_RETURN_PREVIEW', 'Menghitung pratinjau nilai retur',     'POST', '/api/sale-return/preview', now(), now()),
 ('GET_API_SALE_CASH',            'Menampilkan laporan kas harian',       'GET',  '/api/sale/cash',           now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('SALE_CASH', 'Laporan Kas', 'papper', '/transaksi/kas', 4, 'SALES', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN ('POST_API_SALE_RETURN_PREVIEW','GET_API_SALE_CASH');
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'SALE_CASH', now());

COMMIT;
