# One Dockerfile for all seven services; docker-compose passes MODULE per service.
#
# This image expects the jars to already exist, so the workflow is:
#     ./mvnw package -DskipTests && docker compose up -d --build
#
# Deliberate: a self-contained multi-stage build would re-resolve the whole dependency
# tree once per service (seven times), turning a 40-second rebuild into several minutes.
# On a 2-3 week timeline the iteration loop matters more than build hermeticity.

FROM eclipse-temurin:21-jre-alpine

ARG MODULE
ENV MODULE=${MODULE}

# Run unprivileged: a container that does not need root should not have it.
RUN addgroup -S careerpilot && adduser -S careerpilot -G careerpilot

# curl is used by the compose healthchecks to poll the actuator endpoint.
RUN apk add --no-cache curl

WORKDIR /app
COPY ${MODULE}/target/*.jar app.jar
RUN chown -R careerpilot:careerpilot /app
USER careerpilot

# MaxRAMPercentage lets the JVM respect the container memory limit instead of the host's.
# Seven JVMs on a student laptop makes this a practical necessity, not a nicety.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
