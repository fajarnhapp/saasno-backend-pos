# Saasno Backend

Backend API for **Saasno** — a role-based access management system covering APIs, menus, roles, and users. Built with Spring Boot 3.5 / Java 21.

Pairs with [`saasno-frontend`](../saasno-frontend), a Next.js app. The frontend never calls this service from the browser — it proxies every request through its own Next.js API routes.

## Tech Stack

- **Framework:** Spring Boot 3.5.7, Java 21
- **Database:** PostgreSQL + Hibernate/JPA (`ddl-auto=validate`)
- **Session store:** Redis (session tokens, looked up per request — not JWT)
- **Messaging:** RabbitMQ (async notification delivery)
- **Password hashing:** Argon2 (`spring-security-crypto`)
- **Mapping:** MapStruct (entity ↔ DTO)

## Prerequisites

- Java 21 (JDK)
- Maven
- PostgreSQL, Redis, and RabbitMQ reachable (defaults below assume `localhost` with services running)

## Getting Started

```bash
mvn spring-boot:run
```

The server starts on port `8000`. Every config value below has a `localhost` default, so no environment variables are required to run locally, as long as Postgres/Redis/RabbitMQ are up on their standard ports.

## Environment Variables

All are optional locally — see the defaults in `src/main/resources/application.properties`.

| Variable | Default | Description |
|---|---|---|
| `SAASNO_DATABASE_URL` | `jdbc:postgresql://localhost:5432/saasno` | PostgreSQL JDBC URL |
| `SAASNO_DATABASE_USERNAME` | `database` | PostgreSQL user |
| `SAASNO_DATABASE_PASSWORD` | `database` | PostgreSQL password |
| `SAASNO_REDIS_HOST` | `localhost` | Redis host (port fixed at `6379`) |
| `SAASNO_REDIS_PASSWORD` | `admin` | Redis password |
| `SAASNO_RABBITMQ_HOST` | `localhost` | RabbitMQ host (port fixed at `5672`) |
| `SAASNO_RABBITMQ_USERNAME` | `admin` | RabbitMQ user |
| `SAASNO_RABBITMQ_PASSWORD` | `admin123` | RabbitMQ password |

## Modules

Each module below is a matching Entity + Repository + Service + Controller, following the same generic CRUD + pagination/filter pattern (`DataFilter`/`DataTable`, see below):

| Module | Endpoint base | Notes |
|---|---|---|
| Api | `/api/api` | Registry of protected endpoints (id, name, method, path) — the permission catalog itself |
| Menu | `/api/menu` | Sidebar menu tree (parent/child via `parentId`) |
| Role | `/api/role` | On creation, automatically grants a baseline set of self-service API permissions (own profile, notifications, password change, menu-access check) and the `DASHBOARD` menu — see `RoleService.create()` |
| RoleApi | `/api/role-api` | Role ↔ API permission assignment |
| RoleMenu | `/api/role-menu` | Role ↔ Menu assignment, plus `GET /api/role-menu/access` to check the current session's access to a menu |
| User | `/api/user` | Includes `/me`, `/reset-password` (admin-initiated), `/change-password` (self-service) |
| Configuration | `/api/configuration` | Simple key/value app settings |
| Notification / NotificationUnread | `/api/notification`, `/api/notification-unread` | Per-user notification list + unread counter; delivery fanned out via RabbitMQ |
| Authentication | `/api/login`, `/api/logout` | Session-based (not JWT) — see below |

> **Note:** the frontend also has Position and Position-Role screens (`/api/position`, `/api/position-role`), but there is currently no matching entity/controller for either on this backend — those calls will 404 until implemented here.

## Authorization Model

This isn't just "logged in or not" — every non-public request is checked against the **exact** `method` + `path` pair recorded in the `api` table for one of the caller's roles (`SessionService.check`, backed by `role_api`). That's why the `Api` module exists: it's a literal registry of every protected endpoint, and a role only gets access to an endpoint once someone links it via `role_api`. Endpoints that take an id as a path variable (rather than in the query string or body) can't be matched this way — keep new endpoints consistent with the existing `?id=`/body-based pattern rather than `/resource/{id}`.

Sessions live in Redis, keyed by a bearer token issued at login; there is no JWT — every request does a Redis lookup via `SessionFilter`.

## Generic Query Layer

Most `read()` endpoints accept arbitrary query params, which `DataFilter` (`utility/model/data/DataFilter.java`) turns into `ILIKE` filters automatically — any request param that isn't `limit`/`offset`/`order`/`sorting`/`group`/`custom`/`startDate`/`endDate` becomes a filter on that entity field, including dotted paths for auto-joined relations (e.g. `role.id`). No per-field filter code is needed on the backend when the frontend adds a new filter input.

## Building

```bash
mvn clean package -DskipTests
java -jar target/saasno-backend-*.jar
```

## Deployment

`Dockerfile`/`docker-compose.yml`/`run.sh` build and run the packaged jar in a two-stage Ubuntu image. **Known issue:** `Dockerfile` and `run.sh` now use `saasno-backend` paths and jar name, but `docker-compose.yml` still uses an unrelated prior project's container, network and log-path naming (`annisadua-backend`) — update those to `saasno-backend` before relying on it for a real deployment.

## License

All rights reserved — see [`LICENSE`](./LICENSE).

## CI/CD dan Deployment

Pipeline ada di [`.github/workflows/ghcr.yml`](.github/workflows/ghcr.yml). Image di-build dari `Dockerfile` dan disimpan di GHCR:
`ghcr.io/fajarnhapp/saasno-backend-pos`

Alur job: `build` (cek Dockerfile, tanpa push; otomatis saat push ke `main`/`UAT`) → `push` (push image ke GHCR, **manual**) → `deploy-prod` atau `deploy-uat` (**manual**, bisa tanpa push).

### Ketentuan branch

| Branch | Image tag (saat push manual) | Environment | Build otomatis saat push | Push image dan deploy |
|---|---|---|---|---|
| `main` | `latest` dan `sha-<commit>` | production | ya (hanya build) | manual |
| `UAT` | `uat` dan `sha-<commit>` | uat | ya (hanya build) | manual |
| selain `main` dan `UAT` | - | - | tidak | **tidak ada push image dan deployment** |

- Nama branch `UAT` harus huruf besar persis (case-sensitive).
- Push ke branch hanya menjalankan `build` untuk memastikan Dockerfile berhasil. Image **tidak** di-push dan tidak ada deployment otomatis.
- Push image dan deploy hanya manual: buka **Actions → Build, push to GHCR and deploy to VPS → Run workflow**, pilih branch `main` (production) atau `UAT`, lalu centang opsi:
  - **push**: push image ke GHCR (default dicentang).
  - **deploy**: deploy ke VPS (default tidak dicentang). Deploy memakai image yang **sudah ada** di GHCR (`latest` untuk `main`, `uat` untuk `UAT`).
  - Push dan deploy berdiri sendiri. Untuk rilis baru, centang keduanya: push dijalankan dulu, deploy menunggu push selesai. Untuk deploy ulang image lama atau rollback, centang deploy saja. Untuk hanya menyimpan image, centang push saja.
- Kalau keduanya tidak dicentang, workflow hanya menjalankan `build`.
- Deploy ke VPS: login ke ghcr.io, lalu `docker compose pull` dan `docker compose up -d --no-build` di folder aplikasi. Compose membaca tag dari variabel `IMAGE_TAG` (default `latest`), yang diisi workflow: `latest` untuk production dan `uat` untuk UAT.
- Job `deploy-prod` memakai Environment `production`, dan `deploy-uat` memakai Environment `uat` (bisa dipasang required reviewers).

### Secret dan variable GitHub

Credential VPS production dan UAT **dibedakan**. Isi di Settings → Secrets and variables → Actions (level repository atau di dalam Environment masing-masing).

| Production | UAT | Jenis | Keterangan |
|---|---|---|---|
| `SAASNO_VPS_PROD_HOST` | `SAASNO_VPS_UAT_HOST` | secret | IP atau domain VPS |
| `SAASNO_VPS_PROD_USER` | `SAASNO_VPS_UAT_USER` | secret | user SSH, harus bisa menjalankan docker |
| `SAASNO_VPS_PROD_SSH_KEY` | `SAASNO_VPS_UAT_SSH_KEY` | secret | opsional. Isi private key: file `.ppk` (Biznet) boleh ditempel apa adanya, atau OpenSSH `.pem`. Kalau kosong, dipakai password |
| `SAASNO_VPS_PROD_SSH_KEY_PASSPHRASE` | `SAASNO_VPS_UAT_SSH_KEY_PASSPHRASE` | secret | opsional, hanya jika key memakai passphrase |
| `SAASNO_VPS_PROD_PASSWORD` | `SAASNO_VPS_UAT_PASSWORD` | secret | opsional. Password user SSH, dipakai hanya jika `SSH_KEY` kosong. Salah satu dari key atau password wajib diisi |
| `SAASNO_VPS_PROD_PORT` | `SAASNO_VPS_UAT_PORT` | secret | opsional, default `22` |
| `SAASNO_VPS_PROD_APP_DIR` | `SAASNO_VPS_UAT_APP_DIR` | variable | opsional, default `/home/application/data/apps/fajarnhapp/saasno-backend-pos` |

Catatan keamanan:
- Yang ditempel ke secret adalah **isi** file key, bukan file-nya. Jangan commit file `.ppk` atau `.pem` ke repo.
- Workflow mengonversi `.ppk` ke OpenSSH otomatis saat deploy (memakai `puttygen`).
- Login GHCR di VPS memakai `GITHUB_TOKEN` sementara dari workflow, tidak perlu PAT di server.

### Syarat di VPS

- Docker dan Docker Compose terpasang, dan user SSH boleh menjalankan `docker`.
- Folder aplikasi berisi `docker-compose.yml` dan `.env` (variabel `SAASNO_*`).
- Network docker yang dipakai compose sudah dibuat (`external: true`). Lihat catatan "Known issue" di atas: compose backend masih memakai nama `annisadua-backend`.
- UAT dan production sebaiknya di server terpisah. Kalau satu server, container name dan port di compose harus dibedakan.

## Perubahan database

Setiap perubahan database (tabel, kolom, data referensi seperti `api`, `menu_api`, `role_menu`) **wajib dicatat** di repo `postgres`, folder `query`:
- `structure.sql` untuk DDL.
- `record.sql` untuk data/record.

Backend memakai `spring.jpa.hibernate.ddl-auto=validate`, jadi kolom atau tabel baru harus sudah ada di database **sebelum** versi backend yang memakainya di-deploy. Jalankan SQL di UAT dulu, baru di production.
