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

COPY core/database/build.gradle.kts core/database/
COPY core/essential/build.gradle.kts core/essential/
COPY core/medical/build.gradle.kts core/medical/
COPY core/security/build.gradle.kts core/security/

COPY feature/assessments/build.gradle.kts feature/assessments/
COPY feature/auth/build.gradle.kts feature/auth/
COPY feature/chat/build.gradle.kts feature/chat/
COPY feature/children/build.gradle.kts feature/children/
COPY feature/development/build.gradle.kts feature/development/
COPY feature/event/build.gradle.kts feature/event/
COPY feature/fis/build.gradle.kts feature/fis/
COPY feature/knowledge/build.gradle.kts feature/knowledge/
COPY feature/measurements/build.gradle.kts feature/measurements/
COPY feature/reports/build.gradle.kts feature/reports/
COPY feature/notification/build.gradle.kts feature/notification/

# Copy all source code and build
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew :springapp:bootJar -x test --no-daemon

# Stage 2: Extract layers
FROM eclipse-temurin:21-jre-jammy AS extract
WORKDIR /app
COPY --from=build /app/springapp/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Final
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd -m springuser
USER springuser

COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom org.springframework.boot.loader.launch.JarLauncher"]
