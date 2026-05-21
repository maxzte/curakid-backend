# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
RUN chmod +x gradlew

# Build configs
COPY build.gradle.kts settings.gradle.kts ./
COPY build-logic/ build-logic/

# Module configs
COPY springapp/build.gradle.kts springapp/
COPY core/essential/build.gradle.kts core/essential/
COPY core/security/build.gradle.kts core/security/
COPY core/database/build.gradle.kts core/database/
COPY feature/auth/build.gradle.kts feature/auth/
COPY feature/children/build.gradle.kts feature/children/
COPY feature/development/build.gradle.kts feature/development/
COPY feature/measurements/build.gradle.kts feature/measurements/
COPY feature/knowledge/build.gradle.kts feature/knowledge/
COPY feature/reports/build.gradle.kts feature/reports/
COPY feature/fis/build.gradle.kts feature/fis/
COPY feature/vaccine/build.gradle.kts feature/vaccine/

# Pre-fetch dependencies (кешується як окремий шар)
RUN ./gradlew :springapp:dependencies --no-daemon || true

# Copy all source code and build
COPY . .
RUN ./gradlew :springapp:bootJar --no-daemon

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /app
COPY --from=build /app/springapp/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Final
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]