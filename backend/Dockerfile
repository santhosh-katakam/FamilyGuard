FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp verify
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S familyguard && adduser -S familyguard -G familyguard
WORKDIR /app
COPY --from=build /src/target/familyguard-api-1.0.0.jar app.jar
USER familyguard
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
