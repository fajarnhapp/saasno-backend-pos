-- Dashboard: endpoint ringkasan dan batas stok menipis.
-- Bagian umum untuk semua yang punya izin GET_API_DASHBOARD; bagian penjualan dan keuangan ikut izin
-- GET_API_SALE_CASH dan GET_API_FINANCE_REPORT yang sudah ada.
BEGIN;

INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('LOW_STOCK_THRESHOLD', '5', now(), now());

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_DASHBOARD', 'Menampilkan ringkasan dashboard', 'GET', '/api/dashboard', now(), now());

-- Bagian umum tanpa nilai rupiah: semua peran yang ada boleh melihat (peran baru mendapatkannya otomatis lewat
-- daftar API bawaan di RoleService).
INSERT INTO public.role_api (role_id, api_id, registered)
SELECT id, 'GET_API_DASHBOARD', now() FROM public.role;

COMMIT;
