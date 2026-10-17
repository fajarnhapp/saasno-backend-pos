-- Artikel (info dan berita) yang tampil di dashboard dan halaman Artikel.
-- Terlihat turun dari cabang pembuat ke seluruh turunannya (seperti produk); hanya cabang pembuat dan induknya
-- (lewat cakupan) yang boleh mengubah. Membaca: semua peran; menulis: izin POST/PUT/DELETE_API_ARTICLE.
BEGIN;

CREATE TABLE public.article (
    id          varchar(64)  PRIMARY KEY,
    branch_id   varchar(64)  NOT NULL REFERENCES public.branch(id),
    title       varchar(200) NOT NULL,
    summary     varchar(500),
    content     text         NOT NULL,
    status      varchar(12)  NOT NULL CONSTRAINT article_status_check CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    pinned      boolean      NOT NULL DEFAULT false,
    published_at timestamp,
    expires_at  timestamp,
    created_by  varchar(64)  NOT NULL REFERENCES public."user"(id),
    updated_by  varchar(64)  NOT NULL REFERENCES public."user"(id),
    registered  timestamp    NOT NULL,
    modificated timestamp    NOT NULL,
    CONSTRAINT article_published_check CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL),
    CONSTRAINT article_pinned_check CHECK (NOT pinned OR status = 'PUBLISHED')
);
CREATE INDEX article_branch_idx ON public.article(branch_id, status, published_at DESC);

INSERT INTO public.api (id, name, method, path, modificated, registered) VALUES
 ('GET_API_ARTICLE',        'Menampilkan artikel',                         'GET',    '/api/article',        now(), now()),
 ('GET_API_ARTICLE_ACCESS', 'Mengecek izin menulis artikel',               'GET',    '/api/article/access', now(), now()),
 ('POST_API_ARTICLE',       'Membuat artikel',                             'POST',   '/api/article',        now(), now()),
 ('PUT_API_ARTICLE',        'Mengubah, menerbitkan, atau mengarsipkan artikel', 'PUT', '/api/article',       now(), now()),
 ('DELETE_API_ARTICLE',     'Menghapus artikel yang belum terbit',         'DELETE', '/api/article',        now(), now());

INSERT INTO public.menu (id, name, icon, url, order_index, parent_id, modificated, registered) VALUES
 ('ARTICLE', 'Artikel', 'papper', '/article', 2, NULL, now(), now());

-- Membaca dan menu untuk semua peran yang ada (peran baru otomatis lewat daftar bawaan di RoleService);
-- menulis hanya untuk Super User sampai diberikan ke peran lain.
INSERT INTO public.role_api (role_id, api_id, registered)
SELECT id, 'GET_API_ARTICLE', now() FROM public.role;
INSERT INTO public.role_api (role_id, api_id, registered)
SELECT id, 'GET_API_ARTICLE_ACCESS', now() FROM public.role;
INSERT INTO public.role_menu (role_id, menu_id, registered)
SELECT id, 'ARTICLE', now() FROM public.role;
INSERT INTO public.role_api (role_id, api_id, registered)
SELECT 'SUPER', id, now() FROM public.api WHERE id IN ('POST_API_ARTICLE','PUT_API_ARTICLE','DELETE_API_ARTICLE');

COMMIT;
