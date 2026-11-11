FROM maven:3.9.9-eclipse-temurin-23 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:23-jre
RUN useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /build/target/cuentas-api-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
