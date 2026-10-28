# Multi-stage Docker build for Quizora Spring Boot Backend
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and dependency sources
COPY pom.xml .
COPY src ./src

# Build production executable jar without running tests during image creation
RUN mvn clean package -DskipTests

# Run stage with lightweight Eclipse Temurin JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Render sets PORT dynamically (defaults to 8080)
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
