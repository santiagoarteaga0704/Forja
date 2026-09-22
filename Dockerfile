# Imagen de FORJA para el despliegue.
#
# Una sola imagen con las dos mitades adentro: la API y el cliente web. La
# alternativa -dos servicios y dos dominios- obligaria a configurar CORS en
# produccion, a publicar dos direcciones y a que el canal colaborativo viaje por
# un origen distinto al de la pagina. Servir la web desde el mismo servidor
# elimina los tres problemas de raiz, y para una aplicacion de este tamano no
# hay nada que ganar del otro lado.
#
# Se construye en tres etapas para que la imagen final no cargue con Node ni con
# Maven: lo unico que queda es un JRE y el jar.

# ---------- 1. El cliente web -------------------------------------------------
FROM node:22-alpine AS web

WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/ ./
# Vacia a proposito: con VITE_API vacio, el cliente llama a rutas relativas y
# habla con el servidor que lo sirvio, sea cual sea el dominio. Si se pusiera
# aqui la direccion del despliegue, habria que reconstruir la imagen cada vez
# que cambie.
ENV VITE_API=""
RUN npm run build


# ---------- 2. La API ---------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS api

WORKDIR /app
# El pom primero y solo: mientras no cambie, esta capa se reutiliza y no hay que
# volver a bajar las dependencias en cada construccion.
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src/ ./src/
# La web construida entra como recurso estatico del jar. Spring Boot sirve
# solo lo que haya en classpath:/static.
COPY --from=web /web/dist/ ./src/main/resources/static/

# Las pruebas no corren aqui: necesitan un Postgres de verdad -es deliberado, la
# exclusion mutua y la bitacora las arbitra la base- y un contenedor de
# construccion no lo tiene. Se corren antes de construir la imagen.
RUN mvn -q -B -DskipTests package


# ---------- 3. Lo que se despliega -------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# No corre como root: si alguien se escapa del proceso, no es administrador de
# la maquina.
RUN addgroup -S forja && adduser -S forja -G forja
WORKDIR /app
COPY --from=api /app/target/*.jar app.jar
USER forja

EXPOSE 8080

# La instancia gratuita de AWS tiene 1 GB. Sin este limite, la JVM calcula su
# monton sobre la memoria de la maquina y el sistema la mata cuando ademas
# corre la base al lado.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
