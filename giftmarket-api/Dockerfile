# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew bootJar --no-daemon -x test
RUN cp build/libs/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar \
    extract --layers --destination /app/extracted


# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

RUN java \
    -XX:ArchiveClassesAtExit=application.jsa \
    -Xlog:cds=error \
    -Dspring.context.exit=onRefresh \
    -jar application.jar \
    --server.port=0 \
    --spring.datasource.url='jdbc:mysql://127.0.0.1:9/cds?connectTimeout=100' \
    --spring.datasource.username=cds \
    --spring.datasource.password=cds \
    --spring.jpa.hibernate.ddl-auto=none \
    --spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect \
    --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
    --spring.security.oauth2.client.registration.google.client-id=cds-client \
    --spring.security.oauth2.client.registration.google.client-secret=cds-secret \
    --app.frontend-url=http://127.0.0.1 \
    --app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
    --app.jwt.refresh-token-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
    --app.jwt.access-token-expiration-seconds=1800 \
    --app.jwt.refresh-token-expiration-seconds=1209600 \
    --app.jwt.refresh-cookie-name=gift_refresh_token \
    --minio.endpoint=http://127.0.0.1:9000 \
    --minio.access-key=cds-access \
    --minio.secret-key=cds-secret-key \
    --minio.bucket=cds \
    --payment.toss.secret-key=cds-secret \
    || { echo 'AppCDS archive creation failed; runtime will start without it.'; rm -f application.jsa; }

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
