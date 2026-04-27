FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /workspace

COPY services/billing-service/ /workspace/services/billing-service/
COPY libs/usage-events/ /workspace/libs/usage-events/
WORKDIR /workspace/services/billing-service
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/services/billing-service/build/libs/app.jar /app/app.jar

EXPOSE 8095
ENTRYPOINT ["java","-jar","/app/app.jar"]
