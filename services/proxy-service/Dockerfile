# syntax=docker/dockerfile:1.7
# CI 또는 로컬에서 ./gradlew build 로 생성한 app.jar 를 포함한 컨텍스트로 빌드합니다.
# (proxy-service 디렉터리에서 실행: ../../libs/usage-events 는 settings.gradle 참고)

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY build/libs/app.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
