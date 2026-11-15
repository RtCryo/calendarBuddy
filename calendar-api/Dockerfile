FROM maven:3.10-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.version="${VERSION}" org.opencontainers.image.revision="${REVISION}"
WORKDIR /app
COPY --from=build /build/target/calendar-api.jar app.jar
RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
