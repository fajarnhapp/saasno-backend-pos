-- Pesanan online dari katalog publik: pembeli tanpa akun memesan, staf cabang menyetujui dan memprosesnya, pembeli melacak lewat
-- tautan berkode acak. Stok dikunci saat disetujui (tersedia di katalog = stok - yang dikunci) dan baru berkurang saat
-- pesanan diselesaikan, yaitu saat transaksi penjualan sungguhan dibuat.
BEGIN;

CREATE TABLE public.online_order (
    id             varchar(64)  PRIMARY KEY,
    -- Nomor yang dibaca manusia (ONL-yyMMdd-0001), bukan kunci akses.
    serial         varchar(24)  NOT NULL UNIQUE,
    -- Kunci akses halaman pelacakan pembeli: acak 160 bit, tidak bisa ditebak.
    token          varchar(40)  NOT NULL UNIQUE,
    branch_id      varchar(64)  NOT NULL REFERENCES public.branch(id),
    status         varchar(16)  NOT NULL CONSTRAINT online_order_status_check
                   CHECK (status IN ('PENDING','APPROVED','READY','COMPLETED','REJECTED','CANCELLED')),
    fulfillment    varchar(10)  NOT NULL CONSTRAINT online_order_fulfillment_check CHECK (fulfillment IN ('PICKUP','DELIVERY')),
    payment_method varchar(10)  NOT NULL CONSTRAINT online_order_payment_check CHECK (payment_method IN ('CASH','TRANSFER')),
    customer_name  varchar(80)  NOT NULL,
    customer_phone varchar(20)  NOT NULL,
    address        varchar(300),
    note           varchar(300),
    -- Perkiraan saat memesan (harga dasar katalog) dan total pasti saat disetujui (dihitung mesin penjualan: promo, pajak).
    estimate_total numeric(19,2) NOT NULL,
    confirmed_total numeric(19,2),
    sale_id        varchar(64)  REFERENCES public.sale(id),
    decided_by     varchar(64)  REFERENCES public."user"(id),
    decided_at     timestamp,
    decision_note  varchar(300),
    registered     timestamp    NOT NULL,
    modificated    timestamp    NOT NULL,
    CONSTRAINT online_order_address_check CHECK (fulfillment <> 'DELIVERY' OR address IS NOT NULL),
    CONSTRAINT online_order_sale_check CHECK ((status = 'COMPLETED') = (sale_id IS NOT NULL))
);
CREATE INDEX online_order_branch_idx ON public.online_order(branch_id, status, registered DESC);
CREATE INDEX online_order_phone_idx ON public.online_order(branch_id, customer_phone) WHERE status = 'PENDING';

-- Jumlah dalam satuan dasar produk. Nama dan harga dibekukan saat memesan.
CREATE TABLE public.online_order_item (
    order_id     varchar(64)   NOT NULL REFERENCES public.online_order(id),
    product_id   varchar(64)   NOT NULL REFERENCES public.product(id),
    product_name varchar(256)  NOT NULL,
    unit         varchar(32)   NOT NULL,
    quantity     numeric(19,6) NOT NULL CONSTRAINT online_order_item_quantity_check CHECK (quantity > 0),
    unit_price   numeric(19,2) NOT NULL,
    CONSTRAINT online_order_item_pkey PRIMARY KEY (order_id, product_id)
);
CREATE INDEX online_order_item_product_idx ON public.online_order_item(product_id);

-- Riwayat status untuk pelacakan. actor kosong = pembeli atau sistem. note tampil ke pembeli.
CREATE TABLE public.online_order_event (
    id        varchar(64) PRIMARY KEY,
    order_id  varchar(64) NOT NULL REFERENCES public.online_order(id),
    status    varchar(16) NOT NULL,
    note      varchar(300),
    actor     varchar(64) REFERENCES public."user"(id),
    at        timestamp   NOT NULL
);
CREATE INDEX online_order_event_idx ON public.online_order_event(order_id, at);

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_ORDER',          'Menampilkan pesanan online',            'GET', '/api/order',          now(), now()),
 ('PUT_API_ORDER_APPROVE',  'Menyetujui pesanan online',             'PUT', '/api/order/approve',  now(), now()),
 ('PUT_API_ORDER_REJECT',   'Menolak pesanan online',                'PUT', '/api/order/reject',   now(), now()),
 ('PUT_API_ORDER_READY',    'Menandai pesanan online siap',          'PUT', '/api/order/ready',    now(), now()),
 ('PUT_API_ORDER_COMPLETE', 'Menyelesaikan pesanan online (jual)',   'PUT', '/api/order/complete', now(), now()),
 ('PUT_API_ORDER_CANCEL',   'Membatalkan pesanan online',            'PUT', '/api/order/cancel',   now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('SALE_ORDER', 'Pesanan Online', 'cashier', '/transaction/order', 5, 'SALES', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id LIKE '%\_API\_ORDER%' ESCAPE '\';
INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'SALE_ORDER', now());

COMMIT;
