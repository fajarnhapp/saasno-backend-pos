-- Notifikasi berbasis kejadian: oper stok, persetujuan diskon, artikel terbit, stok menipis/habis.
-- Tetap di PostgreSQL (status baca berubah, dan notifikasi dibuat dalam transaksi yang sama dengan kejadiannya).
BEGIN;

-- Jenis notifikasi dan tautan tujuan (path relatif di frontend).
ALTER TABLE public.notification_message
    ADD COLUMN type varchar(32) NOT NULL DEFAULT 'INFO',
    ADD COLUMN link varchar(256);

-- Daftar notifikasi per user (terbaru dulu) dan pembersihan notifikasi lama.
CREATE INDEX notification_user_idx ON public.notification(user_id, registered DESC);
CREATE INDEX notification_cleanup_idx ON public.notification(status, registered);

-- Lama penyimpanan notifikasi yang sudah dibaca (hari); yang belum dibaca tidak dihapus otomatis.
INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('NOTIFICATION_RETENTION_DAYS', '30', now(), now());

COMMIT;
