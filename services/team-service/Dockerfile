FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /workspace

COPY services/team-service/ /workspace/
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/app.jar /app/app.jar

EXPOSE 8093
ENTRYPOINT ["java","-jar","/app/app.jar"]
