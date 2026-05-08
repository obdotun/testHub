# ══════════════════════════════════════════════════════════════════════════════
# Stage 1 — Build Spring Boot
# ══════════════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# ══════════════════════════════════════════════════════════════════════════════
# Stage 2 — Image finale (Ubuntu 22.04 = Python 3.10 natif)
# ══════════════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# ── Installer Python 3.10, pip, git ──────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.10 \
    python3.10-venv \
    python3-pip \
    git \
    curl \
    && ln -sf python3.10 /usr/bin/python3 \
    && ln -sf python3.10 /usr/bin/python \
    && python3 -m pip install --upgrade pip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Vérification
RUN python3 --version

COPY --from=builder /build/target/*.jar app.jar

RUN mkdir -p /app/storage/projects /app/storage/reports /app/data

ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]