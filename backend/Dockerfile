# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY settings-docker.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -s settings-docker.xml -DskipTests \
    -Daether.connector.connectTimeout=30000 \
    -Daether.connector.requestTimeout=120000 \
    -Dmaven.wagon.http.retryHandler.count=3 \
    package

FROM eclipse-temurin:21.0.7_6-jre-alpine
WORKDIR /app
COPY --from=build /build/target/medsim-backend-1.0.0.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD wget -q -O - http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
