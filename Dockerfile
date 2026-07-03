# Legacy Dockerfile — use Dockerfile.benefits or Dockerfile.claims instead
# Kept for backward compatibility
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY benefits-service/ benefits-service/
COPY claims-service/ claims-service/
RUN chmod +x gradlew && ./gradlew :benefits-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/benefits-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
