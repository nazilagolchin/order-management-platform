# Build context is the repository root (see docker-compose.yml) so this
# stage can see the shared-kernel and event-contracts modules that
# inventory-service depends on.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# The root pom.xml's <modules> list includes order-service, so Maven needs
# that pom present to resolve the reactor even though -am won't build it;
# only its pom.xml (not src) is required for that.
COPY pom.xml .
COPY event-contracts/pom.xml event-contracts/pom.xml
COPY shared-kernel/pom.xml shared-kernel/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
RUN mvn -q -pl inventory-service -am dependency:go-offline

COPY event-contracts/src event-contracts/src
COPY shared-kernel/src shared-kernel/src
COPY inventory-service/src inventory-service/src
RUN mvn -q -pl inventory-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/inventory-service/target/inventory-service.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
