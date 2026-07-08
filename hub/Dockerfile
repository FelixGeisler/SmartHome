# syntax=docker/dockerfile:1

# Stage 1: build the self-contained fat jar (Spring Boot backend + the React SPA baked in).
#
# Pinned to the native build platform (BUILDPLATFORM) on purpose: the jar is pure bytecode plus
# static assets, so it is identical for every target architecture. Building it natively means a
# cross-built arm64 image (for a Raspberry Pi) never runs Maven or npm under slow QEMU emulation;
# only the runtime stage below is pulled per target architecture.
FROM --platform=$BUILDPLATFORM eclipse-temurin:26-jdk AS build
WORKDIR /build

# The .dockerignore keeps target/, node_modules/, dist/, data/ and .git out of the context, so this
# copies just the sources and the Maven wrapper.
COPY . .

# -DskipTests skips the backend unit tests (ci.yml is the quality gate for those). The frontend
# still lints, tests, and builds here (its Maven plugin runs regardless of -DskipTests) and MUST
# run so the SPA is bundled into the jar, so do not pass -Dskip.frontend=true. chmod keeps the
# wrapper executable even from a Windows checkout.
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# Stage 2: minimal runtime. This FROM resolves per TARGETPLATFORM, so each published image carries
# its own architecture's Java 26 runtime and the end user installs nothing.
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app

# Run unprivileged. /app/data is the only path the app writes (the H2 database and settings).
RUN groupadd --system smarthome \
    && useradd --system --gid smarthome --home-dir /app smarthome \
    && mkdir -p /app/data \
    && chown -R smarthome:smarthome /app

# The glob resolves to the repackaged fat jar; the .jar.original thin jar is left behind.
COPY --from=build /build/target/smarthome-*.jar /app/app.jar

USER smarthome

# WORKDIR /app makes H2's relative ./data resolve to /app/data, which the volume persists across
# restarts and image upgrades.
VOLUME ["/app/data"]
EXPOSE 8080

# exec so the JVM is PID 1 and receives SIGTERM directly for Spring Boot's graceful shutdown.
# JAVA_OPTS is passed through for tuning (e.g. a heap cap on a small Raspberry Pi); "$@" forwards
# any container run arguments to the app (e.g. --server.port=9000).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
