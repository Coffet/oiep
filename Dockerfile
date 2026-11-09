# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Fetch dependencies first so ordinary source edits do not re-download the world.
COPY pom.xml ./
RUN mvn -B -ntp -Dmaven.test.skip=true dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

# ---------- runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL org.opencontainers.image.title="OIEP" \
      org.opencontainers.image.description="Open Interview Experience Platform"

# curl = healthcheck; sqlite3 = online backups; ca-certificates = outbound HTTPS.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl sqlite3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 oiep \
    && useradd --system --uid 10001 --gid oiep --home-dir /app --shell /usr/sbin/nologin oiep

WORKDIR /app
COPY --from=build --chown=oiep:oiep /workspace/target/oiep.war /app/oiep.war

# SQLite lives on a volume in production. The app creates the parent directory,
# but Docker volume mounts need the mountpoint to exist before the first run.
RUN mkdir -p /data && chown oiep:oiep /data

ENV TZ=Asia/Hong_Kong \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    OIEP_DB_PATH=/data/oiep.db \
    OIEP_DEMO_DATA=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

USER oiep
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/ >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/oiep.war"]
