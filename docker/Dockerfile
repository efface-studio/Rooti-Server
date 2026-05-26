# syntax=docker/dockerfile:1.7
# --- builder ---
FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /app

# 1) deps only — better layer caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle gradle
RUN gradle --no-daemon dependencies || true

# 2) sources
COPY src src
RUN gradle --no-daemon bootJar -x test

# --- runtime ---
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache fontconfig ttf-dejavu \
    && addgroup -S rooti && adduser -S rooti -G rooti

WORKDIR /opt/rooti
COPY --from=builder /app/build/libs/*.jar /opt/rooti/app.jar

USER rooti
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseG1GC","-XX:MaxRAMPercentage=75","-jar","/opt/rooti/app.jar"]
