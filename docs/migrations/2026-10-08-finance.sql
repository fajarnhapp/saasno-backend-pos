-- Biaya operasional toko, kas kecil, pemakaian stok sendiri, dan harga pokok produk (laba kotor).
BEGIN;

-- Harga pokok produk (opsional). Dibekukan per transaksi (sale_item.unit_cost) dan per mutasi stok
-- (stock_movement.unit_cost), jadi mengubah harga pokok tidak mengubah laporan yang sudah lewat.
ALTER TABLE public.product
    ADD COLUMN cost_price numeric(19,2) CONSTRAINT product_cost_price_check CHECK (cost_price IS NULL OR cost_price >= 0);
ALTER TABLE public.sale_item ADD COLUMN unit_cost numeric(19,2);
ALTER TABLE public.stock_movement ADD COLUMN unit_cost numeric(19,2);

-- Jenis mutasi baru USE: pemakaian stok sendiri oleh toko (bukan penjualan).
ALTER TABLE public.stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE public.stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('RECEIVE','ADJUST','TRANSFER_OUT','TRANSFER_IN','SALE','RETURN','USE'));

-- Catatan biaya operasional. kind: CASH = beli perlengkapan dengan uang kas kecil; STOCK_USE = memakai stok sendiri
-- (tidak menyentuh kas kecil; nilainya jumlah x harga pokok). Tidak dihapus, hanya dibatalkan (VOIDED) dengan alasan.
CREATE TABLE public.store_expense (
    id              varchar(64)   PRIMARY KEY,
    serial          varchar(40)   NOT NULL UNIQUE,
    branch_id       varchar(64)   NOT NULL REFERENCES public.branch(id),
    kind            varchar(12)   NOT NULL CONSTRAINT store_expense_kind_check CHECK (kind IN ('CASH','STOCK_USE')),
    category        varchar(32)   NOT NULL,
    description     varchar(256)  NOT NULL,
    amount          numeric(19,2) NOT NULL CONSTRAINT store_expense_amount_check CHECK (amount >= 0),
    receipt_no      varchar(64),
    reason          varchar(500)  NOT NULL,
    approved_by     varchar(128),
    product_id      varchar(64)   REFERENCES public.product(id),
    quantity        numeric(19,6),
    unit_cost       numeric(19,2),
    over_threshold  boolean       NOT NULL DEFAULT false,
    status          varchar(12)   NOT NULL CONSTRAINT store_expense_status_check CHECK (status IN ('RECORDED','VOIDED')),
    created_by      varchar(64)   NOT NULL REFERENCES public."user"(id),
    registered      timestamp     NOT NULL,
    voided_by       varchar(64)   REFERENCES public."user"(id),
    voided_at       timestamp,
    void_reason     varchar(500),
    modificated     timestamp     NOT NULL,
    CONSTRAINT store_expense_cash_check CHECK (kind <> 'CASH' OR amount > 0),
    CONSTRAINT store_expense_use_check CHECK ((kind = 'STOCK_USE') = (product_id IS NOT NULL AND quantity IS NOT NULL AND quantity > 0)),
    CONSTRAINT store_expense_void_check CHECK ((status = 'VOIDED') = (voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL))
);
CREATE INDEX store_expense_branch_idx ON public.store_expense(branch_id, registered DESC);

-- Kas kecil per cabang: saldo boleh minus (peringatan, bukan penolakan); ledger tidak bisa diubah atau dihapus.
CREATE TABLE public.petty_cash_balance (
    branch_id   varchar(64)   PRIMARY KEY REFERENCES public.branch(id),
    balance     numeric(19,2) NOT NULL,
    modificated timestamp     NOT NULL
);

-- amount bertanda: + isi kas / pembatalan biaya, - biaya. balance_after = saldo setelah perubahan.
CREATE TABLE public.petty_cash_entry (
    id            varchar(64)   PRIMARY KEY,
    branch_id     varchar(64)   NOT NULL REFERENCES public.branch(id),
    type          varchar(16)   NOT NULL CONSTRAINT petty_cash_entry_type_check CHECK (type IN ('TOPUP','EXPENSE','EXPENSE_VOID')),
    amount        numeric(19,2) NOT NULL CONSTRAINT petty_cash_entry_amount_check CHECK (amount <> 0),
    balance_after numeric(19,2) NOT NULL,
    expense_id    varchar(64)   REFERENCES public.store_expense(id),
    note          varchar(500),
    created_by    varchar(64)   NOT NULL REFERENCES public."user"(id),
    registered    timestamp     NOT NULL
);
CREATE INDEX petty_cash_entry_branch_idx ON public.petty_cash_entry(branch_id, registered DESC);

CREATE FUNCTION public.petty_cash_entry_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'petty_cash_entry is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER petty_cash_entry_immutable
    BEFORE UPDATE OR DELETE ON public.petty_cash_entry
    FOR EACH ROW EXECUTE FUNCTION public.petty_cash_entry_immutable();

-- Pengaturan: kategori biaya (JSON seperti PRODUCT_UNIT) dan batas peringatan biaya (rupiah).
INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('EXPENSE_CATEGORY', '[{"value":"SUPPLIES","label":"Perlengkapan"},{"value":"CLEANING","label":"Kebersihan"},{"value":"UTILITY","label":"Listrik/Air"},{"value":"TRANSPORT","label":"Transportasi"},{"value":"REPAIR","label":"Perbaikan"},{"value":"OTHER","label":"Lainnya"}]', now(), now()),
 ('EXPENSE_WARNING_AMOUNT', '500000', now(), now());

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('POST_API_EXPENSE',             'Mencatat biaya operasional (uang kas kecil)',  'POST', '/api/expense',            now(), now()),
 ('GET_API_EXPENSE',              'Menampilkan biaya operasional',                'GET',  '/api/expense',            now(), now()),
 ('PUT_API_EXPENSE_VOID',         'Membatalkan catatan biaya operasional',        'PUT',  '/api/expense/void',       now(), now()),
 ('POST_API_STOCK_USE',           'Mencatat pemakaian stok sendiri oleh toko',    'POST', '/api/stock/use',          now(), now()),
 ('POST_API_PETTY_CASH_TOPUP',    'Mengisi kas kecil',                            'POST', '/api/petty-cash/topup',   now(), now()),
 ('GET_API_PETTY_CASH',           'Menampilkan riwayat kas kecil',                'GET',  '/api/petty-cash',         now(), now()),
 ('GET_API_PETTY_CASH_BALANCE',   'Menampilkan saldo kas kecil',                  'GET',  '/api/petty-cash/balance', now(), now()),
 ('GET_API_FINANCE_REPORT',       'Menampilkan laporan keuangan harian',          'GET',  '/api/finance/report',     now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('FINANCE',         'Keuangan',           'finance', '#',                   7, NULL,      now(), now()),
 ('FINANCE_EXPENSE', 'Biaya Operasional',  'expense', '/keuangan/biaya',     1, 'FINANCE', now(), now()),
 ('FINANCE_PETTY',   'Kas Kecil',          'petty',   '/keuangan/kas-kecil', 2, 'FINANCE', now(), now()),
 ('FINANCE_REPORT',  'Laporan Keuangan',   'papper',  '/keuangan/laporan',   3, 'FINANCE', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN (
 'POST_API_EXPENSE','GET_API_EXPENSE','PUT_API_EXPENSE_VOID','POST_API_STOCK_USE','POST_API_PETTY_CASH_TOPUP',
 'GET_API_PETTY_CASH','GET_API_PETTY_CASH_BALANCE','GET_API_FINANCE_REPORT');
INSERT INTO public.role_menu (role_id, menu_id, registered)
SELECT 'SUPER', id, now() FROM public.menu WHERE id IN ('FINANCE','FINANCE_EXPENSE','FINANCE_PETTY','FINANCE_REPORT');

COMMIT;
