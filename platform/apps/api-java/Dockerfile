FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/api-java-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 4001
HEALTHCHECK --interval=10s --timeout=5s --retries=6 CMD curl --fail --silent http://127.0.0.1:4001/health || exit 1
# Sem memória, a JVM segue viva mas pode perder threads essenciais (no teste de carga de 11/11 o
# Tomcat perdeu a que aceita conexões e a API ficou surda até reiniciar à mão). Saindo, o
# `restart: unless-stopped` do compose a sobe de novo sozinho.
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
