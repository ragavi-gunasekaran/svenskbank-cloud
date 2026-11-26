# ---------- Stage 1: build the jar ----------
# Downloads dependencies from Maven Central inside the container.
# Build stage runs on the native CPU (a .jar runs on any CPU)
FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a normal user, not root (security best practice)
RUN groupadd --system --gid 1001 app && useradd --system --uid 1001 --gid app app
COPY --from=build /app/target/transaction-service.jar app.jar
USER 1001
EXPOSE 8082
# Use up to 75% of the container memory limit for the Java heap
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
