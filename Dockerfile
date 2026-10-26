# Container image for hosts such as Railway, which build it straight from this repository.
# Configuration comes from environment variables (DB_URL, DB_USERNAME, DB_PASSWORD, SESSION_COOKIE_SECURE, ...).

# Build: compile the Spring Boot jar with the Maven wrapper. Tests are run separately with ./mvnw test.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Run: only the JRE and the jar.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/bumberoos-*.jar app.jar
# The host sets PORT and application.properties binds to it (8080 when unset).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
