# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/services/team-service
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false"
COPY services/team-service/gradlew services/team-service/settings.gradle services/team-service/build.gradle ./
COPY services/team-service/gradle ./gradle
COPY libs/identity-events /workspace/libs/identity-events
RUN --mount=type=cache,id=gradle-team-service,target=/root/.gradle \
    chmod +x gradlew && sed -i 's/\r$//' gradlew && ./gradlew dependencies --no-daemon

COPY services/team-service/src ./src
RUN --mount=type=cache,id=gradle-team-service,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/services/team-service/build/libs/app.jar /app/app.jar

EXPOSE 8093
ENTRYPOINT ["java","-jar","/app/app.jar"]
