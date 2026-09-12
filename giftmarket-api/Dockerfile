# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

RUN jfr configure \
    --input profile.jfc \
    --output /app/startup-diagnostics.jfc \
    class-loading=true \
    allocation-profiling=low \
    'jdk.InitialEnvironmentVariable#enabled=false' \
    'jdk.InitialSystemProperty#enabled=false' \
    'jdk.JVMInformation#enabled=false' \
    'jdk.SystemProcess#enabled=false'

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon -x test


# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder /app/startup-diagnostics.jfc startup-diagnostics.jfc

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
