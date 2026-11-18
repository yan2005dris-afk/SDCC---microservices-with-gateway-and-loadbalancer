# Stage 1: Build JAR with Maven & Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY pom.xml ./
COPY eureka-server/pom.xml ./eureka-server/
COPY api-gateway/pom.xml ./api-gateway/
COPY order-service/pom.xml ./order-service/
COPY inventory-service/pom.xml ./inventory-service/

RUN apt-get update && apt-get install -y maven && mvn dependency:go-offline -B -pl api-gateway || true

COPY api-gateway/src ./api-gateway/src
RUN mvn clean package -DskipTests -pl api-gateway

# Stage 2: Minimal Runtime with JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
