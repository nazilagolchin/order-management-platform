# Build context is the repository root (see docker-compose.yml) so this
# stage can see the shared-kernel module that order-service depends on.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY shared-kernel/pom.xml shared-kernel/pom.xml
COPY order-service/pom.xml order-service/pom.xml
RUN mvn -q -pl order-service -am dependency:go-offline

COPY shared-kernel/src shared-kernel/src
COPY order-service/src order-service/src
RUN mvn -q -pl order-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/order-service/target/order-service.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
