-- Endpoint daftar kategori biaya dan batas peringatan untuk formulir (tanpa perlu izin menu Configuration).
BEGIN;
INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_EXPENSE_CATEGORY', 'Menampilkan kategori biaya dan batas peringatan', 'GET', '/api/expense/category', now(), now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_EXPENSE_CATEGORY', now());
COMMIT;
