# Etapa 1: Compilar la aplicación Java
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Descargar dependencias primero (aprovecha la caché de capas de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copiar el código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen ligera para ejecutar el servicio
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
