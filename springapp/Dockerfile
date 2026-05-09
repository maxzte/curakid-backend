# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy as build
WORKDIR /app

# Copy gradle executable and wrapper
COPY gradlew .
COPY gradle gradle

# Copy build configuration files
COPY build.gradle.kts settings.gradle.kts ./
COPY springapp/build.gradle.kts springapp/
# Copy other modules if they are dependencies (as seen in springapp/build.gradle.kts)
COPY essential/ essential/
COPY feature/profile/ feature/profile/

# Pre-fetch dependencies to cache them in a Docker layer
RUN ./gradlew :springapp:dependencies --no-daemon || true

# Copy source code and build
COPY . .
RUN ./gradlew :springapp:bootJar --no-daemon

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-jammy as extract
WORKDIR /app
COPY --from=build /app/springapp/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Final image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Copy the layers from the extract stage
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./

EXPOSE 8080

# Use the JarLauncher for faster startup and better resource usage
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
