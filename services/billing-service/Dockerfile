# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/services/billing-service
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY services/billing-service/gradlew services/billing-service/settings.gradle services/billing-service/build.gradle ./
COPY services/billing-service/gradle ./gradle
COPY services/billing-service/gradle.properties ./gradle.properties
COPY libs/usage-events /workspace/libs/usage-events
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew dependencies --no-daemon

COPY services/billing-service/src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/services/billing-service/build/libs/app.jar /app/app.jar

EXPOSE 8095
ENTRYPOINT ["java","-jar","/app/app.jar"]
