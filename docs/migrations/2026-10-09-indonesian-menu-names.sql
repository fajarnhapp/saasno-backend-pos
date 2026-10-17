-- Nama menu yang masih berbahasa Inggris diganti ke bahasa Indonesia (hanya nama tampilan; id dan url tetap).
BEGIN;
UPDATE public.menu SET name = 'Sistem',     modificated = now() WHERE id = 'SYSTEM'        AND name = 'System';
UPDATE public.menu SET name = 'Konfigurasi', modificated = now() WHERE id = 'CONFIGURATION' AND name = 'Configuration';
COMMIT;
