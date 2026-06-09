FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY launcher-shared/ launcher-shared/
COPY launcher-backend/ launcher-backend/
COPY settings-docker.gradle.kts settings.gradle.kts
COPY build.gradle.kts gradle.properties libs.versions.toml ./
COPY gradle/ gradle/
RUN gradle :launcher-backend:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/launcher-backend/build/install/launcher-backend/ .
EXPOSE 8080
ENV DB_DRIVER=postgres
ENV PORT=8080
CMD ["./bin/launcher-backend"]
