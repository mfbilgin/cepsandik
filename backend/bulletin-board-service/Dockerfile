FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
# Faz 3.13 — non-root çalıştır (container hardening)
RUN addgroup -S app && adduser -S app -G app
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
