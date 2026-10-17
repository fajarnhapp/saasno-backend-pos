-- Nomor WhatsApp admin untuk tombol "Lupa kata sandi" dan "Daftar" di halaman login.
-- Dibaca tanpa sesi lewat GET /api/public/contact (hanya nilai ini yang dibuka). Ubah lewat menu Configuration.
BEGIN;
INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('ADMIN_WHATSAPP', '6281383161853', now(), now());
COMMIT;
