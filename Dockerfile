# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Java4Shell (CVE-2026-114938): this stage runs an affected OpenJDK 17 build as well. It only
# compiles this repository and fetches dependencies over TLS, so it is not an internet-facing
# attack surface, but disabling the vulnerable Base64 decode intrinsic costs nothing here.
# The mitigation that matters for untrusted input is on the runtime stage below.
ENV MAVEN_OPTS="-XX:-UseBASE64Intrinsics"

# Fetch dependencies first so ordinary source edits do not re-download the world.
COPY pom.xml ./
RUN mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL org.opencontainers.image.title="OIEP" \
      org.opencontainers.image.description="Open Interview Experience Platform" \
      org.opencontainers.image.source="https://github.com/Coffet/oiep"

# Runtime package set is deliberately minimal: curl = healthcheck; sqlite3 = online backups;
# ca-certificates = outbound HTTPS. Review this list before adding anything. Base images are
# tag-pinned for reproducible builds; pin them by digest in a production build pipeline.
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl sqlite3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 oiep \
    && useradd --system --uid 10001 --gid oiep --home-dir /app --shell /usr/sbin/nologin oiep

WORKDIR /app
COPY --from=build --chown=oiep:oiep /workspace/target/oiep.war /app/oiep.war

# SQLite lives on a volume in production. The app creates the parent directory,
# but Docker volume mounts need the mountpoint to exist before the first run. Mode 0700 keeps
# the database (including moderation-hidden notes) private to the oiep user; the app runs as
# UID/GID 10001 and restore.sh writes the restored file with the same ownership.
RUN mkdir -p /data && chown oiep:oiep /data && chmod 700 /data

# Java4Shell (CVE-2026-114938) interim mitigation. As of 2026-11-18 the current
# eclipse-temurin:17-jre-jammy image supplies Temurin 17.0.21+9, an affected OpenJDK build, and
# no fixed Temurin 17 release has been published yet. Disabling HotSpot's Base64 intrinsic
# removes the vulnerable AVX-512 VBMI decode stub; this app decodes one ~30-byte pagination
# cursor per read-API request, so the performance cost is irrelevant. Unlike -XX:UseAVX=2
# (which an ARM JVM rejects), the vendor guidance documents -XX:-UseBASE64Intrinsics for every
# architecture. Remove it once the runtime image supplies a fixed build identifier (Temurin
# 17.0.21.1+2 or later; rebuild with --pull so the floating tag refreshes), and keep the strict
# cursor parsing in ReadApiCursor: see security/java4shell.md.
ENV TZ=Asia/Hong_Kong \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    OIEP_DB_PATH=/data/oiep.db \
    OIEP_DEMO_DATA=false \
    JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

USER oiep
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/ >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/oiep.war"]
