FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl default-mysql-client \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home campus \
    && mkdir -p /app /data/campus-charity/upload /data/campus-charity/backup \
    && chown -R campus:campus /app /data/campus-charity
WORKDIR /app
COPY --from=build /build/target/campus-charity-backend-1.0.0.jar app.jar
USER campus
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
