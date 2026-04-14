# 패턴 B — 백엔드 전용 이미지 (docs/architecture.md §10.1).
# 빌드는 저장소 루트에서 (멀티모듈 libs/identity-events 포함):
#   docker build -f services/identity-service/Dockerfile -t identity-service:local .

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/services/identity-service
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY services/identity-service/gradlew services/identity-service/settings.gradle services/identity-service/build.gradle ./
COPY services/identity-service/gradle ./gradle
COPY services/identity-service/src ./src
COPY libs/identity-events /workspace/libs/identity-events
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /workspace/services/identity-service/build/libs/app.jar app.jar

USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
