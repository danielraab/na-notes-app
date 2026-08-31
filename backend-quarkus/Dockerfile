# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --create-home --home-dir /app app
WORKDIR /app
COPY --from=build /src/target/quarkus-app/lib/ ./lib/
COPY --from=build /src/target/quarkus-app/*.jar ./
COPY --from=build /src/target/quarkus-app/app/ ./app/
COPY --from=build /src/target/quarkus-app/quarkus/ ./quarkus/
RUN mkdir -p /data && chown app:app /data
VOLUME ["/data"]
ENV DATABASE_URL=/data/notes.db
ENV LISTEN_ADDR=:8080
EXPOSE 8080
USER app
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
