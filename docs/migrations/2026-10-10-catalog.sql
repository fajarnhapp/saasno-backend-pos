-- Katalog publik per cabang: halaman tanpa login berisi produk yang ditandai tampil, dengan foto dan deskripsi, dan
-- pemesanan lewat WhatsApp. Pengaturan katalog per cabang (alamat, nomor WhatsApp, aktif) dan data katalog per produk
-- dipisah dari tabel branch dan product supaya keduanya tidak berubah.
BEGIN;

CREATE TABLE public.branch_catalog (
    branch_id   varchar(64) PRIMARY KEY REFERENCES public.branch(id) ON DELETE CASCADE,
    -- Alamat publik: /katalog/<slug>
    slug        varchar(40) NOT NULL UNIQUE
                CONSTRAINT branch_catalog_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) >= 3),
    whatsapp    varchar(20) NOT NULL,
    enabled     boolean     NOT NULL DEFAULT false,
    modificated timestamp   NOT NULL,
    registered  timestamp   NOT NULL
);

-- Dibuat saat produk pertama kali diatur untuk katalog. image_updated kosong = belum ada foto.
CREATE TABLE public.product_catalog (
    product_id    varchar(64)  PRIMARY KEY REFERENCES public.product(id) ON DELETE CASCADE,
    visible       boolean      NOT NULL DEFAULT false,
    description   varchar(1000),
    image_updated timestamp,
    modificated   timestamp    NOT NULL,
    registered    timestamp    NOT NULL
);
CREATE INDEX product_catalog_visible_idx ON public.product_catalog(product_id) WHERE visible;

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_CATALOG_SETTING',            'Menampilkan pengaturan katalog',        'GET',    '/api/catalog/setting',       now(), now()),
 ('PUT_API_CATALOG_SETTING',            'Mengubah pengaturan katalog',           'PUT',    '/api/catalog/setting',       now(), now()),
 ('GET_API_CATALOG_PRODUCT',            'Menampilkan produk untuk katalog',      'GET',    '/api/catalog/product',       now(), now()),
 ('PUT_API_CATALOG_PRODUCT',            'Mengatur produk di katalog',            'PUT',    '/api/catalog/product',       now(), now()),
 ('POST_API_CATALOG_PRODUCT_IMAGE',     'Mengunggah foto produk katalog',        'POST',   '/api/catalog/product/image', now(), now()),
 ('GET_API_CATALOG_PRODUCT_IMAGE',      'Menampilkan foto produk katalog',       'GET',    '/api/catalog/product/image', now(), now()),
 ('DELETE_API_CATALOG_PRODUCT_IMAGE',   'Menghapus foto produk katalog',         'DELETE', '/api/catalog/product/image', now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('CATALOG', 'Katalog', 'product', '/master/catalog', 5, 'MASTER', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id LIKE '%\_API\_CATALOG\_%' ESCAPE '\';
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'CATALOG', now());

COMMIT;
