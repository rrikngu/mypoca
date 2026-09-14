# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*

ARG SBT_VERSION=1.10.1
RUN curl -fsL "https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz" -o /tmp/sbt.tgz && \
    tar -xzf /tmp/sbt.tgz -C /usr/local && \
    ln -s /usr/local/sbt/bin/sbt /usr/local/bin/sbt && \
    rm /tmp/sbt.tgz

# Cache dependency resolution separately from source changes
COPY project project
COPY build.sbt .
RUN sbt update

COPY . .
RUN sbt assembly

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/scala-2.13/activation-lab.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
