# Stage 1: Build JAR with Maven & Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy parent and module pom.xml files for dependency caching
COPY pom.xml ./
COPY eureka-server/pom.xml ./eureka-server/
COPY api-gateway/pom.xml ./api-gateway/
COPY order-service/pom.xml ./order-service/
COPY inventory-service/pom.xml ./inventory-service/

# Download dependencies in offline-friendly way
RUN apt-get update && apt-get install -y maven && mvn dependency:go-offline -B -pl eureka-server || true

# Copy source code and build only eureka-server
COPY eureka-server/src ./eureka-server/src
RUN mvn clean package -DskipTests -pl eureka-server

# Stage 2: Minimal Runtime with JRE 21
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/eureka-server/target/*.jar app.jar

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
