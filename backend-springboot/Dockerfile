# Spring Boot backend. Build context is the REPOSITORY ROOT (see docker-compose.yml), because
# the jar must contain ../bpmn-models (BPMN, DMN and forms are deployed to Camunda on startup).

# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Download dependencies first so they are cached when only the code changes
COPY backend-springboot/pom.xml backend-springboot/pom.xml
RUN mvn -B -q -f backend-springboot/pom.xml dependency:go-offline
COPY bpmn-models bpmn-models
COPY backend-springboot/src backend-springboot/src
RUN mvn -B -q -f backend-springboot/pom.xml package -DskipTests

# ---- Stage 2: run it on a small Java 21 runtime image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/backend-springboot/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
