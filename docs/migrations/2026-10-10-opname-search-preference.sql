-- Stok opname dengan persetujuan, pencarian global, preferensi notifikasi per user, dan pengingat transaksi tertahan.
-- Dijalankan manual (proyek tidak memakai Flyway). Semuanya dalam satu transaksi.
BEGIN;

-- Stok opname: user menghitung stok fisik, hasilnya berlaku setelah disetujui orang lain. Jumlah sistem dicatat saat
-- diajukan; saat disetujui, selisih (hitung - sistem) diterapkan ke saldo saat itu sebagai mutasi ADJUST, jadi
-- penjualan yang terjadi selama penghitungan tidak terhapus.
CREATE TABLE public.stock_opname (
    id            varchar(64)  PRIMARY KEY,
    serial        varchar(16)  NOT NULL UNIQUE,
    branch_id     varchar(64)  NOT NULL REFERENCES public.branch(id),
    status        varchar(16)  NOT NULL CONSTRAINT stock_opname_status_check
                  CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    note          varchar(500),
    requested_by  varchar(64)  NOT NULL REFERENCES public."user"(id),
    registered    timestamp    NOT NULL,
    decided_by    varchar(64)  REFERENCES public."user"(id),
    decided_at    timestamp,
    decision_note varchar(500),
    modificated   timestamp    NOT NULL,
    CONSTRAINT stock_opname_decision_check CHECK (
        (status = 'PENDING') = (decided_by IS NULL AND decided_at IS NULL))
);
CREATE INDEX stock_opname_branch_idx ON public.stock_opname(branch_id, registered DESC);

-- Semua jumlah dalam satuan dasar produk. difference = counted_quantity - system_quantity.
CREATE TABLE public.stock_opname_item (
    opname_id       varchar(64)    NOT NULL REFERENCES public.stock_opname(id),
    product_id      varchar(64)    NOT NULL REFERENCES public.product(id),
    system_quantity numeric(19,6)  NOT NULL CONSTRAINT stock_opname_item_system_check CHECK (system_quantity >= 0),
    counted_quantity numeric(19,6) NOT NULL CONSTRAINT stock_opname_item_counted_check CHECK (counted_quantity >= 0),
    CONSTRAINT stock_opname_item_pkey PRIMARY KEY (opname_id, product_id)
);

-- Pengingat transaksi tertahan terlalu lama: dikirim sekali per keranjang.
ALTER TABLE public.sale_hold ADD COLUMN stale_notified_at timestamp;
INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('HOLD_STALE_HOURS', '12', now(), now());

-- Preferensi notifikasi: kategori yang dimatikan user. Tidak ada baris = diterima.
CREATE TABLE public.notification_mute (
    user_id    varchar(64) NOT NULL REFERENCES public."user"(id),
    category   varchar(32) NOT NULL,
    registered timestamp   NOT NULL,
    CONSTRAINT notification_mute_pkey PRIMARY KEY (user_id, category)
);

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_STOCK_OPNAME',              'Menampilkan daftar stok opname',     'GET',  '/api/stock-opname',          now(), now()),
 ('POST_API_STOCK_OPNAME',             'Mengajukan stok opname',             'POST', '/api/stock-opname',          now(), now()),
 ('PUT_API_STOCK_OPNAME_APPROVE',      'Menyetujui stok opname',             'PUT',  '/api/stock-opname/approve',  now(), now()),
 ('PUT_API_STOCK_OPNAME_REJECT',       'Menolak stok opname',                'PUT',  '/api/stock-opname/reject',   now(), now()),
 ('PUT_API_STOCK_OPNAME_CANCEL',       'Membatalkan pengajuan stok opname',  'PUT',  '/api/stock-opname/cancel',   now(), now()),
 ('GET_API_SEARCH',                    'Pencarian global',                   'GET',  '/api/search',                now(), now()),
 ('GET_API_NOTIFICATION_PREFERENCE',   'Menampilkan preferensi notifikasi',  'GET',  '/api/notification/preference', now(), now()),
 ('PUT_API_NOTIFICATION_PREFERENCE',   'Mengubah preferensi notifikasi',     'PUT',  '/api/notification/preference', now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('STOCK_OPNAME', 'Stok Opname', 'stock', '/stock/opname', 4, 'STOCK', now(), now());

-- Super User mendapat semuanya. Pencarian dan preferensi notifikasi milik sendiri: semua peran yang ada boleh
-- (peran baru mendapatkannya lewat daftar API bawaan di RoleService).
INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN (
 'GET_API_STOCK_OPNAME','POST_API_STOCK_OPNAME','PUT_API_STOCK_OPNAME_APPROVE','PUT_API_STOCK_OPNAME_REJECT',
 'PUT_API_STOCK_OPNAME_CANCEL');

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT r.id, a.id, now() FROM public.role r CROSS JOIN public.api a
WHERE a.id IN ('GET_API_SEARCH','GET_API_NOTIFICATION_PREFERENCE','PUT_API_NOTIFICATION_PREFERENCE');

INSERT INTO public.role_menu (role_id, menu_id, registered) VALUES ('SUPER', 'STOCK_OPNAME', now());

COMMIT;
