# Etapa 1: Compilar la aplicación Java
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 1. Copiar primero el pom y las librerías locales
COPY pom.xml .
COPY libs ./libs

# 2. Instalar manualmente la dependencia local de Getnet dentro del Maven de Docker
RUN mvn install:install-file \
    -Dfile=libs/POSIntegradoGetnet-1.0.0.jar \
    -DgroupId=posintegradogetnet \
    -DartifactId=POSIntegradoGetnet \
    -Dversion=1.0.0 \
    -Dpackaging=jar

# 3. Descargar el resto de las dependencias públicas de internet
RUN mvn dependency:go-offline -B

# 4. Copiar el código fuente y compilar
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen ligera para ejecutar el servicio
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]