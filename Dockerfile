FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY launcher-shared/ launcher-shared/
COPY launcher-backend/ launcher-backend/
COPY settings-docker.gradle.kts settings.gradle.kts
COPY build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN gradle :launcher-backend:installDist --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/launcher-backend/build/install/launcher-backend/ .
EXPOSE 8080
ENV DB_DRIVER=postgres
ENV DB_HOST=dpg-d10r8spdbsen06u8u0ig-a.oregon-postgres.render.com
ENV DB_PORT=5432
ENV DB_NAME=nightshift
ENV DB_USER=nightshift
ENV DB_PASSWORD=eRNDQBPlwLkTgRLgJSPjFr7hd2iguAmD
ENV ADMIN_TOKEN=
ENV SESSION_SECRET=nightshift-secret-2026-key
ENV PORT=8080
ENV MIN_CLIENT_VERSION=0.0.0
CMD ["./bin/launcher-backend"]
