-- Transaksi penjualan (tunai), pembatalan, retur, dan pengaturannya.
-- Dijalankan manual (proyek tidak memakai Flyway); satu transaksi, aman dijalankan sekali.
BEGIN;

-- Header transaksi. Tidak pernah dihapus atau diubah selain perpindahan status.
-- Status: COMPLETED | WAITING_APPROVAL | REJECTED | CANCELLED | VOIDED.
CREATE TABLE public.sale (
    id                     varchar(64)   PRIMARY KEY,
    serial                 varchar(40)   NOT NULL UNIQUE,
    branch_id              varchar(64)   NOT NULL REFERENCES public.branch(id),
    customer_id            varchar(64)   REFERENCES public.customer(id),
    status                 varchar(20)   NOT NULL CONSTRAINT sale_status_check
                           CHECK (status IN ('COMPLETED','WAITING_APPROVAL','REJECTED','CANCELLED','VOIDED')),
    subtotal               numeric(19,2) NOT NULL CONSTRAINT sale_subtotal_check CHECK (subtotal >= 0),
    promo_id               varchar(64),
    promo_name             varchar(256),
    promo_discount         numeric(19,2) NOT NULL DEFAULT 0 CONSTRAINT sale_promo_discount_check CHECK (promo_discount >= 0),
    manual_discount        numeric(19,2) NOT NULL DEFAULT 0 CONSTRAINT sale_manual_discount_check CHECK (manual_discount >= 0),
    manual_discount_reason varchar(500),
    tax_rate               numeric(7,4)  NOT NULL DEFAULT 0,
    tax_inclusive          boolean       NOT NULL DEFAULT false,
    tax                    numeric(19,2) NOT NULL DEFAULT 0 CONSTRAINT sale_tax_check CHECK (tax >= 0),
    total                  numeric(19,2) NOT NULL CONSTRAINT sale_total_check CHECK (total >= 0),
    paid                   numeric(19,2) NOT NULL,
    change_amount          numeric(19,2) NOT NULL,
    request_key            varchar(64),
    created_by             varchar(64)   NOT NULL REFERENCES public."user"(id),
    registered             timestamp     NOT NULL,
    decided_by             varchar(64)   REFERENCES public."user"(id),
    decided_at             timestamp,
    decision_note          varchar(500),
    voided_by              varchar(64)   REFERENCES public."user"(id),
    voided_at              timestamp,
    void_reason            varchar(500),
    modificated            timestamp     NOT NULL,
    CONSTRAINT sale_paid_check CHECK (paid >= total AND change_amount = paid - total),
    CONSTRAINT sale_manual_reason_check CHECK (manual_discount = 0 OR manual_discount_reason IS NOT NULL)
);
CREATE INDEX sale_branch_idx ON public.sale(branch_id, registered DESC);
CREATE INDEX sale_customer_idx ON public.sale(customer_id);
-- Anti dobel: satu kunci permintaan per kasir hanya menghasilkan satu transaksi.
CREATE UNIQUE INDEX sale_request_key_idx ON public.sale(created_by, request_key) WHERE request_key IS NOT NULL;

-- Baris transaksi. Harga, nama, dan serial produk dibekukan saat transaksi.
-- net_total = jumlah akhir baris (setelah promo, diskon manual, dan pajak dialokasikan proporsional),
-- dipakai menghitung nilai retur.
CREATE TABLE public.sale_item (
    id                varchar(64)   PRIMARY KEY,
    sale_id           varchar(64)   NOT NULL REFERENCES public.sale(id),
    product_id        varchar(64)   NOT NULL REFERENCES public.product(id),
    product_serial    varchar(16)   NOT NULL,
    product_name      varchar(256)  NOT NULL,
    unit              varchar(32)   NOT NULL,
    quantity          numeric(19,6) NOT NULL CONSTRAINT sale_item_quantity_check CHECK (quantity > 0),
    unit_price        numeric(19,2) NOT NULL CONSTRAINT sale_item_price_check CHECK (unit_price >= 0),
    price_source      varchar(16)   NOT NULL CONSTRAINT sale_item_source_check
                      CHECK (price_source IN ('BASE','CUSTOMER','CUSTOMER_TYPE','PROMO')),
    tier_min_quantity numeric(19,6),
    discount          numeric(19,2) NOT NULL DEFAULT 0 CONSTRAINT sale_item_discount_check CHECK (discount >= 0),
    line_total        numeric(19,2) NOT NULL CONSTRAINT sale_item_line_check CHECK (line_total >= 0),
    net_total         numeric(19,2) NOT NULL CONSTRAINT sale_item_net_check CHECK (net_total >= 0),
    is_free           boolean       NOT NULL DEFAULT false,
    returned_quantity numeric(19,6) NOT NULL DEFAULT 0,
    returned_amount   numeric(19,2) NOT NULL DEFAULT 0,
    CONSTRAINT sale_item_returned_check CHECK (returned_quantity >= 0 AND returned_quantity <= quantity
                                               AND returned_amount >= 0 AND returned_amount <= net_total),
    CONSTRAINT sale_item_unique UNIQUE (sale_id, product_id, is_free)
);
CREATE INDEX sale_item_product_idx ON public.sale_item(product_id);

-- Retur: boleh sebagian per baris, tunai, dan mengembalikan stok.
CREATE TABLE public.sale_return (
    id           varchar(64)   PRIMARY KEY,
    serial       varchar(40)   NOT NULL UNIQUE,
    sale_id      varchar(64)   NOT NULL REFERENCES public.sale(id),
    branch_id    varchar(64)   NOT NULL REFERENCES public.branch(id),
    reason       varchar(500)  NOT NULL,
    refund_total numeric(19,2) NOT NULL CONSTRAINT sale_return_refund_check CHECK (refund_total >= 0),
    created_by   varchar(64)   NOT NULL REFERENCES public."user"(id),
    registered   timestamp     NOT NULL
);
CREATE INDEX sale_return_sale_idx ON public.sale_return(sale_id);
CREATE INDEX sale_return_branch_idx ON public.sale_return(branch_id, registered DESC);

CREATE TABLE public.sale_return_item (
    return_id    varchar(64)   NOT NULL REFERENCES public.sale_return(id),
    sale_item_id varchar(64)   NOT NULL REFERENCES public.sale_item(id),
    quantity     numeric(19,6) NOT NULL CONSTRAINT sale_return_item_quantity_check CHECK (quantity > 0),
    refund       numeric(19,2) NOT NULL CONSTRAINT sale_return_item_refund_check CHECK (refund >= 0),
    CONSTRAINT sale_return_item_pkey PRIMARY KEY (return_id, sale_item_id)
);

-- Pengaturan (tabel configuration).
INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('TAX_RATE_PERCENT',            '0',     now(), now()),
 ('TAX_INCLUSIVE',               'false', now(), now()),
 ('MANUAL_DISCOUNT_MAX_PERCENT', '10',    now(), now());

-- Endpoint (api), menu, dan izin untuk role Super User.
INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('POST_API_SALE_PREVIEW',  'Menghitung pratinjau transaksi',                    'POST', '/api/sale/preview',  now(), now()),
 ('POST_API_SALE',          'Membuat transaksi penjualan (tanpa diskon manual)', 'POST', '/api/sale',          now(), now()),
 ('POST_API_SALE_DISCOUNT', 'Membuat transaksi penjualan dengan diskon manual',  'POST', '/api/sale/discount', now(), now()),
 ('GET_API_SALE',           'Menampilkan riwayat transaksi',                     'GET',  '/api/sale',          now(), now()),
 ('PUT_API_SALE_APPROVE',   'Menyetujui transaksi dengan diskon di atas batas',  'PUT',  '/api/sale/approve',  now(), now()),
 ('PUT_API_SALE_REJECT',    'Menolak transaksi dengan diskon di atas batas',     'PUT',  '/api/sale/reject',   now(), now()),
 ('PUT_API_SALE_CANCEL',    'Membatalkan transaksi yang menunggu persetujuan',   'PUT',  '/api/sale/cancel',   now(), now()),
 ('PUT_API_SALE_VOID',      'Membatalkan transaksi yang sudah selesai (void)',   'PUT',  '/api/sale/void',     now(), now()),
 ('POST_API_SALE_RETURN',   'Membuat retur penjualan',                           'POST', '/api/sale-return',   now(), now()),
 ('GET_API_SALE_RETURN',    'Menampilkan daftar retur penjualan',                'GET',  '/api/sale-return',   now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('SALES',        'Transaksi',         'cashier', '#',                  6, NULL,    now(), now()),
 ('SALE_POS',     'Kasir',             'cashier', '/transaksi/kasir',   1, 'SALES', now(), now()),
 ('SALE_HISTORY', 'Riwayat Transaksi', 'history', '/transaksi/riwayat', 2, 'SALES', now(), now()),
 ('SALE_RETURN',  'Retur',             'return',  '/transaksi/retur',   3, 'SALES', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN (
 'POST_API_SALE_PREVIEW','POST_API_SALE','POST_API_SALE_DISCOUNT','GET_API_SALE','PUT_API_SALE_APPROVE',
 'PUT_API_SALE_REJECT','PUT_API_SALE_CANCEL','PUT_API_SALE_VOID','POST_API_SALE_RETURN','GET_API_SALE_RETURN');

INSERT INTO public.role_menu (role_id, menu_id, registered)
SELECT 'SUPER', id, now() FROM public.menu WHERE id IN ('SALES','SALE_POS','SALE_HISTORY','SALE_RETURN');

COMMIT;
