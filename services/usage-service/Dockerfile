# syntax=docker/dockerfile:1.7
# 패턴 B — usage-service 백엔드 전용 이미지 (docs/architecture.md §10.1).
# :usage-events 는 ../../libs/usage-events 이므로 build context 는 저장소 루트여야 한다.
# 빌드(저장소 루트에서):
#   docker build -f services/usage-service/Dockerfile -t usage-service:local .

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/services/usage-service
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY services/usage-service/gradlew services/usage-service/settings.gradle services/usage-service/build.gradle ./
COPY services/usage-service/gradle ./gradle
COPY libs/usage-events /workspace/libs/usage-events
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew dependencies --no-daemon

COPY services/usage-service/src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /workspace/services/usage-service/build/libs/app.jar app.jar

USER spring:spring

EXPOSE 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
