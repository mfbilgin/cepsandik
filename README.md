# Cep Sandık

End-to-end verifiable mobile voting system based on Microsoft's **ElectionGuard Kotlin Multiplatform (EGK)** protocol. Cep Sandık combines homomorphic encryption, distributed key ceremony, and a threshold tally pipeline so that every ballot can be individually tracked by the voter and the whole election can be universally verified by any third party — without revealing plaintext votes.

This repository accompanies the undergraduate thesis *"Cep Sandık: ElectionGuard ile Güvenli ve Uçtan Uca Doğrulanabilir Dijital Seçim Sistemi"* (Gümüşhane University, Department of Software Engineering, 2026).

## Architecture at a glance

- **Mobile (React Native + Expo + native Kotlin module)** — runs EGK on-device so the plaintext ballot never leaves the phone.
- **Backend (Java 21 / Spring Boot 5 services)** — `user-service`, `community-service`, `election-service`, `bulletin-board-service`, `notification-service`.
- **Crypto engine (Kotlin / JVM)** — stateless gRPC service exposing EGK primitives (key ceremony, ballot encryption, threshold tally).
- **Data layer** — PostgreSQL (per-service schemas), MongoDB (Bulletin Board), Redis (cache), RabbitMQ (async events + DLQ).
- **Edge** — OpenResty / Nginx API Gateway with Let's Encrypt TLS termination.

A detailed architectural breakdown, sequence diagrams, and protocol description are available in [the thesis PDF](./cep_sandik_tez_muhammet_bilgin.pdf).

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Five Spring Boot microservices (Java 21, Maven) |
| `backend/crypto-engine-kotlin/` | Stateless Kotlin gRPC crypto engine |
| `mobile/` | React Native app + Kotlin native module for EGK |
| `infra/` | Docker Compose (local / staging / prod), Nginx gateway, backup container, observability stubs |
| `article/` | JISA submission TeX sources and figures |
| `cep_sandik_tez_muhammet_bilgin.{docx,pdf}` | Thesis document |

## Prerequisites

- Docker Engine **24+** and Docker Compose **v2**
- Git
- (Mobile development only) Node.js 20+, EAS CLI, an Android emulator or device
- (Backend development only) JDK 21 and Maven 3.9+

## Quick start (local stack)

```bash
git clone https://github.com/mfbilgin/cepsandik.git
cd cepsandik/infra
cp .env.example .env
# Open .env and replace every CHANGE_ME_* placeholder with a strong, unique value.
docker compose up -d
```

When the stack is healthy you can reach:

- **API Gateway** — `http://localhost` (or `http://localhost:8088` if port 80 is busy)
- **Swagger UI** — `http://localhost/swagger/{service}` for each backend service
- **RabbitMQ management** — `http://localhost:15672` (default `cepsandik_mq` / from `.env`)
- **PostgreSQL** — internal only, exposed on `postgres:5432` to other containers

Tear down the stack and remove volumes with:

```bash
docker compose down -v
```

## Mobile app

```bash
cd mobile
npm install
npx expo run:android       # debug build on an emulator
# or, to produce a release APK pointing to prod backend:
eas build --platform android --profile production
```

The mobile app reads its backend base URL from `mobile/app.config.ts`; switch between `local`, `uat`, and `prod` via the `EXPO_PUBLIC_API_ENV` environment variable.

## Running an end-to-end acceptance test

The thesis describes an end-to-end acceptance test using three independent emulators that walks through a distributed key ceremony, multi-voter encrypted ballot submission, and threshold tally. To reproduce it locally:

1. Bring up the full local stack as above.
2. Launch three Android emulators and install the debug APK on each.
3. Register three accounts, create a community, define an election with three guardian slots, opt-in each emulator as a guardian.
4. Publish the election, complete the three-round key ceremony, cast ballots from each device, close the election, and run the distributed tally.

Logs in `bulletin-board-service` and `election-service` are tagged with `X-Request-Id` so individual ballot flows can be followed end-to-end.

## Database backups

The `backup` container takes a daily `pg_dump` of all three PostgreSQL databases. Restore a backup into an isolated container:

```bash
docker run --rm -d --name pg_restore_test --network cepsandik-network \
  -e POSTGRES_PASSWORD=testpass -e POSTGRES_USER=cepsandik_user postgres:16-alpine
docker exec pg_restore_test psql -U cepsandik_user -d postgres -c "CREATE DATABASE userdb;"
docker cp cepsandik-backup:/backups/userdb_<timestamp>.sql.gz /tmp/
docker cp /tmp/userdb_<timestamp>.sql.gz pg_restore_test:/tmp/
docker exec pg_restore_test bash -c "gunzip -c /tmp/userdb_<timestamp>.sql.gz | psql -U cepsandik_user -d userdb"
```

## License

Cep Sandık is released under the **MIT License** — see [LICENSE](./LICENSE).

Microsoft ElectionGuard, on which the crypto engine is built, is also distributed under the MIT License.

## Citation

If you use this work academically, please cite the thesis:

> Bilgin, M.F. *Cep Sandık: ElectionGuard ile Güvenli ve Uçtan Uca Doğrulanabilir Dijital Seçim Sistemi*. Lisans Bitirme Tezi, Gümüşhane Üniversitesi, Mühendislik ve Doğa Bilimleri Fakültesi, Yazılım Mühendisliği Bölümü, 2026.
