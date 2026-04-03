# 패턴 B — 백엔드 전용 이미지 (docs/architecture.md §10.1).
# 빌드: 이 디렉터리에서 docker build -t identity-service:local .
# Gradle 은 컨테이너 안에서 실행되므로 사전 bootJar 가 필수는 아니다.

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /app/build/libs/app.jar app.jar

USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
