# Build and test image for TrinoMyDriver
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy project
COPY pom.xml .
COPY src ./src

# Build and run tests
RUN mvn -B clean test


# Final stage with built JAR
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/trino-my-driver-*.jar ./trino-my-driver.jar
