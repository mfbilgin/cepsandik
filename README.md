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
npx expo run:android       # debug build on an emulator (API: http://10.0.2.2:8088/v1)
```

Production builds are produced with **EAS Build** (cloud) and distributed **only through Google Play**:

```bash
npm install -g eas-cli
eas login
eas init                                          # once: links the project, writes extra.eas.projectId
eas build --platform android --profile production # signed .aab, EAS-managed upload key
eas submit --platform android --profile production # uploads to the Play "internal" track
```

- The backend URL comes from `EXPO_PUBLIC_API_URL` (set per profile in `mobile/eas.json`). Release builds fall back to `https://api.cepsandik.com/v1` if it is missing.
- `mobile/.easignore` replaces `.gitignore` for EAS uploads so that `modules/electionguard/android/libs/egklib-all.jar` (not in git) is included.
- Device integrity (Google Play Integrity) is enabled by setting `EXPO_PUBLIC_PLAY_INTEGRITY_PROJECT_NUMBER` in the production profile and `PLAY_INTEGRITY_ENABLED=true` on the server (see `infra/.env.example`).

## Running an end-to-end acceptance test

The thesis describes an end-to-end acceptance test using three independent emulators that walks through a distributed key ceremony, multi-voter encrypted ballot submission, and threshold tally. To reproduce it locally:

1. Bring up the full local stack as above.
2. Launch three Android emulators and install the debug APK on each.
3. Register three accounts, create a community, define an election with three guardian slots, opt-in each emulator as a guardian.
4. Publish the election, complete the three-round key ceremony, cast ballots from each device, close the election, and run the distributed tally.

Logs in `bulletin-board-service` and `election-service` are tagged with `X-Request-Id` so individual ballot flows can be followed end-to-end.

## Independent verification (standalone verifier)

Cep Sandık ships a **standalone audit-record verifier** that any third party can run to confirm an election's results without trusting the production system. It re-runs every cryptographic check from scratch using the public audit record:

1. Re-builds the ElectionGuard context (manifest, joint public key, extended base hash) from `cryptoContext`.
2. Re-verifies every encrypted ballot's Chaum-Pedersen ZKP and confirmation code with EGK's `VerifyEncryptedBallots`.
3. Parses the `DecryptedTallyOrBallot` tally proof and checks that every contest selection carries a non-empty Chaum-Pedersen decryption proof.
4. Recomputes the entire bulletin board SHA-256 hash chain locally and rejects any tamper.

Build and run from a clean checkout:

```bash
cd backend/crypto-engine-kotlin
# Build the fat-jar (~7 MB, contains EGK runtime)
gradle auditVerifierJar
# Verify a live election directly from the gateway
java -jar build/libs/audit-verifier-*.jar \
    --url http://localhost/api/v1/elections/18/audit-record
# Or verify an offline copy
curl -s http://localhost/api/v1/elections/18/audit-record > audit-18.json
java -jar build/libs/audit-verifier-*.jar --file audit-18.json
```

Exit code is `0` when every check passes and `1` otherwise, so the verifier can be wired into CI or election-observation tooling. The verifier never contacts the system except (optionally) to fetch the audit record — every cryptographic check happens locally.

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
