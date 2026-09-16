# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runner
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: Create non-root user and group
RUN addgroup -S smartbus && adduser -S smartbus -G smartbus

COPY --from=build --chown=smartbus:smartbus /app/target/smartbus-backend-1.0.0.jar app.jar

USER smartbus:smartbus

ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O - http://localhost:${PORT}/api/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

