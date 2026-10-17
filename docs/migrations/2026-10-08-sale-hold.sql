-- Tahan transaksi: keranjang disimpan sementara (customer lupa dompet) tanpa menjadi transaksi.
-- Tidak ada pembatalan otomatis; stok tidak dikunci; harga dihitung ulang saat dilanjutkan.
BEGIN;

-- Status: HELD (ditahan) -> USED (sudah dibayar menjadi transaksi) | DISCARDED (dibuang, alasan wajib).
CREATE TABLE public.sale_hold (
    id             varchar(64)  PRIMARY KEY,
    serial         varchar(40)  NOT NULL UNIQUE,
    branch_id      varchar(64)  NOT NULL REFERENCES public.branch(id),
    label          varchar(200),
    -- Keranjang apa adanya (JSON: customerId, items[productId, quantity, unit, discount], discount, discountReason).
    -- Bukan catatan keuangan: harga dan promo tidak dibekukan, dihitung ulang saat dilanjutkan.
    cart           text         NOT NULL,
    status         varchar(12)  NOT NULL CONSTRAINT sale_hold_status_check CHECK (status IN ('HELD','USED','DISCARDED')),
    created_by     varchar(64)  NOT NULL REFERENCES public."user"(id),
    registered     timestamp    NOT NULL,
    modificated    timestamp    NOT NULL,
    sale_id        varchar(64)  REFERENCES public.sale(id),
    used_by        varchar(64)  REFERENCES public."user"(id),
    used_at        timestamp,
    discarded_by   varchar(64)  REFERENCES public."user"(id),
    discarded_at   timestamp,
    discard_reason varchar(500),
    CONSTRAINT sale_hold_used_check CHECK ((status = 'USED') = (sale_id IS NOT NULL AND used_by IS NOT NULL AND used_at IS NOT NULL)),
    CONSTRAINT sale_hold_discard_check CHECK ((status = 'DISCARDED') = (discarded_by IS NOT NULL AND discarded_at IS NOT NULL
                                                                         AND discard_reason IS NOT NULL))
);
CREATE INDEX sale_hold_branch_idx ON public.sale_hold(branch_id, status, registered);

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('POST_API_SALE_HOLD',         'Menahan transaksi (menyimpan keranjang sementara)', 'POST', '/api/sale-hold',         now(), now()),
 ('GET_API_SALE_HOLD',          'Menampilkan transaksi yang ditahan',                'GET',  '/api/sale-hold',         now(), now()),
 ('PUT_API_SALE_HOLD_DISCARD',  'Membuang transaksi yang ditahan',                   'PUT',  '/api/sale-hold/discard', now(), now());

INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN ('POST_API_SALE_HOLD','GET_API_SALE_HOLD','PUT_API_SALE_HOLD_DISCARD');

COMMIT;
