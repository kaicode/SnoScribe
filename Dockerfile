# Build stage
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

# Runtime: JRE + curl for Compose healthchecks
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/snoscribe-*.jar /app/snoscribe.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/snoscribe.jar"]
