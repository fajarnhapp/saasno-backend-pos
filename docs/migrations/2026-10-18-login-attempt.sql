-- Batas percobaan login gagal: akun dikunci sementara setelah MAX_LOGIN_ATTEMPTS kali salah berturut-turut.
ALTER TABLE public."user" ADD COLUMN failed_login_count integer NOT NULL DEFAULT 0;
ALTER TABLE public."user" ADD COLUMN locked_until timestamp;

INSERT INTO public.configuration (id, value, modificated, registered) VALUES
 ('MAX_LOGIN_ATTEMPTS', '5', now(), now()),
 ('LOGIN_LOCK_MINUTES', '15', now(), now());
