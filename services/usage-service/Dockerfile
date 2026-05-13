# syntax=docker/dockerfile:1.7
# 패턴 B — usage-service 백엔드 전용 이미지 (docs/architecture.md §10.1).
# :usage-events, :identity-events 는 ../../libs/... 이므로 build context 는 저장소 루트여야 한다.
# 빌드(저장소 루트에서):
#   docker build -f services/usage-service/Dockerfile -t usage-service:local .

# --- Builder: 의존성 해결 레이어와 소스·컴파일 레이어 분리 (소스 변경 시 deps 재다운로드 방지)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/services/usage-service
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"

COPY services/usage-service/gradlew services/usage-service/settings.gradle services/usage-service/build.gradle ./
COPY services/usage-service/gradle ./gradle
COPY libs/usage-events /workspace/libs/usage-events
COPY libs/identity-events /workspace/libs/identity-events

RUN chmod +x gradlew && sed -i 's/\r$//' gradlew

RUN --mount=type=cache,id=gradle-usage-service,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

COPY services/usage-service/src ./src

RUN --mount=type=cache,id=gradle-usage-service,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

# --- Runner: JRE 만 포함, 비루트 실행 (build.gradle 의 bootJar archiveFileName = app.jar)
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder --chown=spring:spring /workspace/services/usage-service/build/libs/app.jar /app/app.jar

USER spring:spring

EXPOSE 8092
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
