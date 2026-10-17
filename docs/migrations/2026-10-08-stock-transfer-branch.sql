-- Endpoint daftar cabang tujuan oper stok (semua cabang: id, serial, nama).
BEGIN;
INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_STOCK_TRANSFER_BRANCH', 'Menampilkan daftar cabang tujuan oper stok', 'GET', '/api/stock-transfer/branch', now(), now());
INSERT INTO public.role_api (role_id, api_id, registered) VALUES ('SUPER', 'GET_API_STOCK_TRANSFER_BRANCH', now());
COMMIT;
