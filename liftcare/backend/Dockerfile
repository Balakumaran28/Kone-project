# ==============================================================================
# LiftCare Spring Boot Backend — Multi-Stage Production Dockerfile
# Optimized for on-premise building server deployment over local area network
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Application Artifact
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and dependencies specification
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source files and package executable JAR
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Hardened, Minimal JRE Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install lightweight diagnostic tools for Docker health probes
RUN apk add --no-cache wget curl

# Non-root security profile for local building appliance
RUN addgroup -S liftcare && adduser -S liftcare -G liftcare

# Copy compiled artifact
COPY --from=builder --chown=liftcare:liftcare /workspace/target/*.jar app.jar

USER liftcare:liftcare

EXPOSE 8080

ENV SERVER_PORT=8080 \
    LIFTCARE_ENV=production-local \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Native Docker Healthcheck against LiftCare health probe
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
