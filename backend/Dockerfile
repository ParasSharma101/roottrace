# -------------------------------------------------------------
# Stage 1: Build Application with Maven and Eclipse Temurin JDK 21
# -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-26-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable fat jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# -------------------------------------------------------------
# Stage 2: Hardened Runtime with Eclipse Temurin JRE 21
# -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Security: run as non-root user
RUN addgroup -S -g 1000 roottrace && adduser -S -u 1000 roottrace -G roottrace

# Install curl for healthcheck
RUN apk add --no-cache curl

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R roottrace:roottrace /app

USER roottrace:roottrace

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
