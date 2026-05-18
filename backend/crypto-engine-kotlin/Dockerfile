# =============================================================
# crypto-engine-kotlin Dockerfile (Faz 1 spike)
#
# Multi-stage build:
#   1. egk-builder  → ElectionGuard-Kotlin-Multiplatform fat JAR'ı git'ten klonla,
#                     gradle ile derle (egklib-all.jar üret).
#   2. app-builder  → Spring Boot Kotlin app'i derle, EGK fat JAR'ı libs/'a kopyala.
#   3. runtime      → Distroless JRE 17, sadece app fat JAR'ı çalıştır.
#
# KMP build cache: Stage 1 layer'ı git repo değişmediği sürece Docker layer
# cache'inden gelir, sonraki rebuild'lerde 0 sn. Force rebuild için:
#   docker compose build --no-cache crypto-engine-kotlin
# =============================================================

# ---------- Stage 1: EGK fat JAR build ----------
FROM gradle:8.3-jdk17 AS egk-builder

WORKDIR /egk

# git'i image'da kur (gradle:8.3-jdk17 üzerinde apt mevcut)
USER root
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*

# KMP repo'yu shallow clone
RUN git clone --depth 1 https://github.com/votingworks/electionguard-kotlin-multiplatform.git .

# Tanılama: Gradle sürümünü ve fatJar task'ının gerçekten var olup olmadığını yaz
RUN gradle --version
RUN gradle tasks --no-daemon --all 2>&1 | grep -iE "fatjar|jar" | head -50 || true

# Adım 1: KMP egklib JVM-spesifik JAR'ı üret. fatJar bunu beklemiyor (eksik
# task dependency tanımı), bu yüzden ayrı bir invocation ile zorlamamız lazım.
RUN gradle :egklib:jvmJar --no-daemon -x test --stacktrace > /tmp/egk-step1.log 2>&1 ; \
    EC=$? ; \
    if [ $EC -ne 0 ] ; then \
        echo "===== EGK STEP 1 (jvmJar) FAILED ====="; \
        tail -400 /tmp/egk-step1.log ; \
        exit $EC ; \
    fi ; \
    echo "===== EGK STEP 1 OK =====" ; \
    ls -lh /egk/egklib/build/libs/

# Adım 2: Şimdi fatJar JAR'ları birleştirebilir.
RUN gradle :egkliball:fatJar --no-daemon -x test --stacktrace > /tmp/egk-step2.log 2>&1 ; \
    EC=$? ; \
    if [ $EC -ne 0 ] ; then \
        echo "===== EGK STEP 2 (fatJar) FAILED ====="; \
        tail -400 /tmp/egk-step2.log ; \
        exit $EC ; \
    fi ; \
    echo "===== EGK BUILD OK ====="

# Çıktıyı stabil bir yola kopyala — sonraki stage bunu çekecek
RUN mkdir -p /egk/out && \
    cp egkliball/build/libs/egklib-all.jar /egk/out/egklib-all.jar && \
    ls -lh /egk/out/

# Self-test pipeline için EGK'nın hazır test data'sını da paketle
# (commonTest/data/workflow/allAvailableJson — election initialized + manifest)
RUN cp -r /egk/egklib/src/commonTest/data /egk/out/test-data && \
    ls /egk/out/test-data/workflow/ | head -20

# ---------- Stage 2: Spring Boot Kotlin app build ----------
FROM gradle:8.3-jdk17 AS app-builder

WORKDIR /app

# EGK fat JAR'ı önce kopyala (build.gradle.kts files() ile bunu bekliyor)
RUN mkdir -p libs
COPY --from=egk-builder /egk/out/egklib-all.jar libs/egklib-all.jar

# Build dosyaları
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Dependency cache layer
RUN gradle dependencies --no-daemon || true

# Source kodu
COPY src/ ./src/

# Spring Boot fat JAR
RUN gradle bootJar --no-daemon -x test

# ---------- Stage 3: Runtime ----------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=app-builder /app/build/libs/*.jar /app/app.jar

# EGK test data — SelfTest pipeline'ı (POC v2) bu dizini okur
COPY --from=egk-builder /egk/out/test-data /app/egk-test-data

EXPOSE 50051

# Faz 3.13 — non-root çalıştır (container hardening). SelfTest çıktısı
# /tmp/selftest-output'a (non-root yazabilir), test-data /app altında
# world-readable → chown gerekmez. gRPC 50051>1024 root gerektirmez.
#
# Faz 4.15b regresyon fix: egklib-all.jar kendi logback.xml'ini classpath'e
# koyuyor; içinde CWD'ye `egkServer.log` yazan bir FILE appender var. WORKDIR
# /app root'a ait → non-root `app` yazamaz → Spring "Logging system failed to
# initialize" ile FATAL düşüyordu (crypto-engine hiç ayağa kalkmıyordu).
# Çözüm: app'in sahibi olduğu yazılabilir bir çalışma dizini ver, CWD onu
# yapsın; egkServer.log oraya düşer. app.jar/egk-test-data absolute yolda,
# etkilenmez. (egklib logback'inin bizim config'i ezmesi ayrı bir residual.)
RUN addgroup -S app && adduser -S app -G app
RUN mkdir -p /work && chown app:app /work
WORKDIR /work
USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
