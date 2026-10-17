-- Stok per cabang, mutasi stok (ledger, tidak bisa diubah), dan oper stok dengan persetujuan.
-- Dijalankan manual (proyek tidak memakai Flyway). Aman dijalankan sekali; semuanya dalam satu transaksi.
BEGIN;

-- Saldo stok: satu baris per cabang dan produk, dalam satuan dasar produk. Tidak boleh minus.
CREATE TABLE public.stock_balance (
    branch_id   varchar(64)    NOT NULL REFERENCES public.branch(id),
    product_id  varchar(64)    NOT NULL REFERENCES public.product(id),
    quantity    numeric(19,6)  NOT NULL CONSTRAINT stock_balance_quantity_check CHECK (quantity >= 0),
    modificated timestamp      NOT NULL,
    CONSTRAINT stock_balance_pkey PRIMARY KEY (branch_id, product_id)
);
CREATE INDEX stock_balance_product_idx ON public.stock_balance(product_id);

-- Mutasi stok: setiap perubahan saldo dicatat sebagai baris baru. quantity = selisih bertanda
-- (+ masuk, - keluar); balance_after = saldo setelah perubahan.
CREATE TABLE public.stock_movement (
    id             varchar(64)    PRIMARY KEY,
    branch_id      varchar(64)    NOT NULL REFERENCES public.branch(id),
    product_id     varchar(64)    NOT NULL REFERENCES public.product(id),
    type           varchar(24)    NOT NULL CONSTRAINT stock_movement_type_check
                   CHECK (type IN ('RECEIVE','ADJUST','TRANSFER_OUT','TRANSFER_IN','SALE','RETURN')),
    quantity       numeric(19,6)  NOT NULL CONSTRAINT stock_movement_quantity_check CHECK (quantity <> 0),
    balance_after  numeric(19,6)  NOT NULL CONSTRAINT stock_movement_balance_check CHECK (balance_after >= 0),
    reference_type varchar(24),
    reference_id   varchar(64),
    note           varchar(500),
    created_by     varchar(64)    NOT NULL REFERENCES public."user"(id),
    registered     timestamp      NOT NULL
);
CREATE INDEX stock_movement_branch_product_idx ON public.stock_movement(branch_id, product_id, registered DESC);
CREATE INDEX stock_movement_reference_idx ON public.stock_movement(reference_type, reference_id);

-- Ledger tidak boleh diubah atau dihapus oleh siapa pun, termasuk lewat SQL langsung.
CREATE FUNCTION public.stock_movement_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movement is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER stock_movement_immutable
    BEFORE UPDATE OR DELETE ON public.stock_movement
    FOR EACH ROW EXECUTE FUNCTION public.stock_movement_immutable();

-- Oper stok: diajukan cabang asal, berlaku setelah disetujui cabang tujuan.
CREATE TABLE public.stock_transfer (
    id                    varchar(64)  PRIMARY KEY,
    serial                varchar(16)  NOT NULL UNIQUE,
    source_branch_id      varchar(64)  NOT NULL REFERENCES public.branch(id),
    destination_branch_id varchar(64)  NOT NULL REFERENCES public.branch(id),
    status                varchar(16)  NOT NULL CONSTRAINT stock_transfer_status_check
                          CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    note                  varchar(500),
    requested_by          varchar(64)  NOT NULL REFERENCES public."user"(id),
    registered            timestamp    NOT NULL,
    decided_by            varchar(64)  REFERENCES public."user"(id),
    decided_at            timestamp,
    decision_note         varchar(500),
    modificated           timestamp    NOT NULL,
    CONSTRAINT stock_transfer_branches_check CHECK (source_branch_id <> destination_branch_id),
    -- Keputusan (setuju/tolak/batal) selalu mencatat siapa dan kapan; PENDING belum punya keputusan.
    CONSTRAINT stock_transfer_decision_check CHECK (
        (status = 'PENDING') = (decided_by IS NULL AND decided_at IS NULL))
);
CREATE INDEX stock_transfer_source_idx ON public.stock_transfer(source_branch_id);
CREATE INDEX stock_transfer_destination_idx ON public.stock_transfer(destination_branch_id);

CREATE TABLE public.stock_transfer_item (
    transfer_id varchar(64)    NOT NULL REFERENCES public.stock_transfer(id),
    product_id  varchar(64)    NOT NULL REFERENCES public.product(id),
    quantity    numeric(19,6)  NOT NULL CONSTRAINT stock_transfer_item_quantity_check CHECK (quantity > 0),
    CONSTRAINT stock_transfer_item_pkey PRIMARY KEY (transfer_id, product_id)
);

-- Pendaftaran endpoint (api), menu, dan izin untuk role Super User.
INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_STOCK',               'Menampilkan saldo stok',            'GET',  '/api/stock',                  now(), now()),
 ('POST_API_STOCK_IN',           'Mencatat stok masuk',               'POST', '/api/stock/in',               now(), now()),
 ('POST_API_STOCK_ADJUST',       'Mengoreksi stok (opname)',          'POST', '/api/stock/adjust',           now(), now()),
 ('GET_API_STOCK_MOVEMENT',      'Menampilkan mutasi stok',           'GET',  '/api/stock/movement',         now(), now()),
 ('GET_API_STOCK_TRANSFER',      'Menampilkan daftar oper stok',      'GET',  '/api/stock-transfer',         now(), now()),
 ('POST_API_STOCK_TRANSFER',     'Mengajukan oper stok',              'POST', '/api/stock-transfer',         now(), now()),
 ('PUT_API_STOCK_TRANSFER_APPROVE', 'Menyetujui oper stok',           'PUT',  '/api/stock-transfer/approve', now(), now()),
 ('PUT_API_STOCK_TRANSFER_REJECT',  'Menolak oper stok',              'PUT',  '/api/stock-transfer/reject',  now(), now()),
 ('PUT_API_STOCK_TRANSFER_CANCEL',  'Membatalkan pengajuan oper stok','PUT',  '/api/stock-transfer/cancel',  now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('STOCK',          'Stok',        'stock',    '#',               5, NULL,    now(), now()),
 ('STOCK_BALANCE',  'Saldo Stok',  'stock',    '/stock/saldo',    1, 'STOCK', now(), now()),
 ('STOCK_MOVEMENT', 'Mutasi Stok', 'history',  '/stock/mutasi',   2, 'STOCK', now(), now()),
 ('STOCK_TRANSFER', 'Oper Stok',   'transfer', '/stock/oper',     3, 'STOCK', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN (
 'GET_API_STOCK','POST_API_STOCK_IN','POST_API_STOCK_ADJUST','GET_API_STOCK_MOVEMENT','GET_API_STOCK_TRANSFER',
 'POST_API_STOCK_TRANSFER','PUT_API_STOCK_TRANSFER_APPROVE','PUT_API_STOCK_TRANSFER_REJECT','PUT_API_STOCK_TRANSFER_CANCEL');

INSERT INTO public.role_menu (role_id, menu_id, registered)
SELECT 'SUPER', id, now() FROM public.menu WHERE id IN ('STOCK','STOCK_BALANCE','STOCK_MOVEMENT','STOCK_TRANSFER');

COMMIT;
