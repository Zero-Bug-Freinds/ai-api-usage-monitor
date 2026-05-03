# syntax=docker/dockerfile:1.7

FROM gradle:8.14.3-jdk21-alpine AS builder
WORKDIR /workspace/services/agent-service

COPY services/agent-service/settings.gradle services/agent-service/build.gradle ./
COPY services/agent-service/src ./src
COPY libs/identity-events /workspace/libs/identity-events
COPY libs/usage-events /workspace/libs/usage-events

RUN --mount=type=cache,id=gradle-agent-service,target=/home/gradle/.gradle \
    gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /workspace/services/agent-service/build/libs/app.jar app.jar

USER spring:spring

EXPOSE 8096
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
