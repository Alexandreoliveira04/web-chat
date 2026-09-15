# Build: baixa as dependencias em uma camada separada do codigo, para que
# alterar o src nao invalide o cache do Maven.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B -DskipTests package

# Runtime: apenas o JRE e o jar, com usuario sem privilegios.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S webchat && adduser -S webchat -G webchat

COPY --from=build /build/target/web-chat-*.jar app.jar
USER webchat

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
