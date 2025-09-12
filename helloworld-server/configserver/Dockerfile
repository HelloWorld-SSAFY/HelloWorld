# ===== Build stage =====
FROM gradle:8.10-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle clean bootJar --no-daemon

# ===== Runtime stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
