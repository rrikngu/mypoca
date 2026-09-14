# --- build stage ---
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.3_9_1.10.1_2.13.14 AS build
WORKDIR /app
COPY . .
RUN sbt assembly

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/scala-2.13/activation-lab.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
