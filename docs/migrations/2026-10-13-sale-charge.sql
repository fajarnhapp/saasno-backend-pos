-- Pajak tunggal (TAX_RATE_PERCENT/TAX_INCLUSIVE) diganti daftar biaya lain-lain (konfigurasi SALE_CHARGE) yang dihitung
-- satu per satu. Penjualan menyimpan salinan biayanya (sale.charges, JSON) dan jumlahnya (sale.charge_total).
-- Dijalankan manual (proyek tidak memakai Flyway). Semuanya dalam satu transaksi.
BEGIN;

ALTER TABLE public.sale ADD COLUMN charges text NOT NULL DEFAULT '[]';
ALTER TABLE public.sale ADD COLUMN charge_total numeric(19,2) NOT NULL DEFAULT 0;

-- Penjualan lama: pajaknya menjadi satu biaya "Pajak".
UPDATE public.sale SET charge_total = tax,
  charges = jsonb_build_array(jsonb_build_object('code', 'TAX', 'label', 'Pajak', 'type', 'PERCENT', 'rate', tax_rate,
      'inclusive', tax_inclusive, 'amount', tax, 'base', CASE WHEN tax_inclusive THEN total ELSE total - tax END))::text
 WHERE tax_rate > 0;

ALTER TABLE public.sale ALTER COLUMN charges DROP DEFAULT, ALTER COLUMN charge_total DROP DEFAULT;
ALTER TABLE public.sale ADD CONSTRAINT sale_charge_total_check CHECK (charge_total >= 0);
ALTER TABLE public.sale DROP COLUMN tax_rate, DROP COLUMN tax_inclusive, DROP COLUMN tax;

-- Konfigurasi: tarif lama dipindah menjadi satu item biaya (kosong bila tanpa pajak).
INSERT INTO public.configuration (id, value, modificated, registered)
SELECT 'SALE_CHARGE',
  CASE WHEN COALESCE(NULLIF((SELECT value FROM public.configuration WHERE id = 'TAX_RATE_PERCENT'), '')::numeric, 0) > 0
       THEN jsonb_build_array(jsonb_build_object('value', 'TAX', 'label', 'Pajak', 'type', 'PERCENT',
            'amount', (SELECT value FROM public.configuration WHERE id = 'TAX_RATE_PERCENT')::numeric,
            'inclusive', COALESCE((SELECT value FROM public.configuration WHERE id = 'TAX_INCLUSIVE'), 'false') = 'true'))::text
       ELSE '[]' END,
  now(), now();
DELETE FROM public.configuration WHERE id IN ('TAX_RATE_PERCENT', 'TAX_INCLUSIVE');

COMMIT;
