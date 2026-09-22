# Front: Next.js com export estatico, servido depois pelo proprio Spring Boot.
FROM node:22-alpine AS frontend
WORKDIR /frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# Build do backend: dependencias em uma camada separada do codigo, para que
# alterar o src nao invalide o cache do Maven.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline

COPY backend/src/ src/
# O pom empacota ../frontend/out em static/; com WORKDIR=/build, o caminho e /frontend/out.
COPY --from=frontend /frontend/out/ /frontend/out/
RUN mvn -B -DskipTests package

# Runtime: apenas o JRE e o jar, com usuario sem privilegios.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S webchat && adduser -S webchat -G webchat && \
    mkdir -p /app/uploads && \
    chown -R webchat:webchat /app/uploads

COPY --from=build /build/target/web-chat-*.jar app.jar
USER webchat

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
