# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/target/azure-function.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
