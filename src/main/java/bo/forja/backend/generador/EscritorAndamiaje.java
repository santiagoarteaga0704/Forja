package bo.forja.backend.generador;

import org.springframework.stereotype.Component;

/**
 * Archivos que no salen del diagrama pero sin los cuales el proyecto
 * generado no es un proyecto: el descriptor de Maven, la clase de arranque,
 * la configuracion y el README.
 * <p>
 * El objetivo es que lo generado se pueda ejecutar sin tocar nada. Un
 * generador que produce clases correctas pero exige media hora de
 * configuracion antes de arrancar no cumple lo que promete.
 */
@Component
public class EscritorAndamiaje {

    /** Version de Spring Boot: la misma que usa FORJA, para no documentar dos. */
    private static final String VERSION_BOOT = "4.1.1";
    private static final String VERSION_JAVA = "21";
    /**
     * Verificado contra Spring Boot 4.1.1 el 21 de septiembre de 2026: resuelve,
     * arranca y sirve /v3/api-docs y /swagger-ui.html. La numeracion 2.x sugiere
     * Boot 3 y por eso se comprobo corriendolo, no leyendo la documentacion.
     */
    private static final String VERSION_SPRINGDOC = "2.8.6";
    /**
     * Ni el 5432 ni los que usa FORJA. Una maquina de desarrollo suele tener ya
     * un Postgres en el puerto de siempre, y que el proyecto generado pelee por
     * el con otro proyecto es un problema que nadie deberia depurar.
     */
    private static final int PUERTO_BASE = 5442;

    public String pom(Plan.Proyecto proyecto) {
        String grupo = grupoDe(proyecto.paqueteBase());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>%s</version>
                        <relativePath/>
                    </parent>

                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <name>%s</name>
                    <description>Generado por FORJA desde un diagrama de clases UML</description>

                    <properties>
                        <java.version>%s</java.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-webmvc</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-data-jpa</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-validation</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                            <scope>runtime</scope>
                        </dependency>
                        <!--
                            Documenta la API y la deja probar desde el navegador,
                            en /swagger-ui.html. Se incluye porque un CRUD
                            generado sin forma de ejercitarlo obliga a instalar
                            otra herramienta antes de ver si funciona.
                        -->
                        <dependency>
                            <groupId>org.springdoc</groupId>
                            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                            <version>%s</version>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(VERSION_BOOT, grupo, proyecto.artefacto(),
                proyecto.nombreAplicacion(), VERSION_JAVA, VERSION_SPRINGDOC);
    }

    public String aplicacion(Plan.Proyecto proyecto) {
        String clase = Nombres.clase(proyecto.nombreAplicacion()) + "Aplicacion";
        return """
                package %s;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                /** Punto de arranque. Generado por FORJA. */
                @SpringBootApplication
                public class %s {

                    public static void main(String[] argumentos) {
                        SpringApplication.run(%s.class, argumentos);
                    }
                }
                """.formatted(proyecto.paqueteBase(), clase, clase);
    }

    public String nombreClaseAplicacion(Plan.Proyecto proyecto) {
        return Nombres.clase(proyecto.nombreAplicacion()) + "Aplicacion";
    }

    /**
     * Configuracion con {@code ddl-auto: update} a proposito.
     * <p>
     * En un sistema en produccion el esquema se versiona con migraciones y
     * esta opcion seria un error. Aqui el proyecto es el resultado de generar
     * un diagrama, y lo que se espera de el es poder ejecutarlo en el momento
     * para ver las tablas que salieron del modelo. Pedir migraciones escritas
     * a mano antes del primer arranque contradiria el proposito del generador.
     */
    public String configuracion(Plan.Proyecto proyecto) {
        return """
                spring:
                  application:
                    name: %s
                  datasource:
                    url: jdbc:postgresql://localhost:%d/%s
                    username: postgres
                    password: postgres
                  jpa:
                    hibernate:
                      ddl-auto: update
                    open-in-view: false
                    properties:
                      hibernate:
                        format_sql: true

                server:
                  port: 8081
                """.formatted(proyecto.artefacto(), PUERTO_BASE,
                proyecto.artefacto().replace('-', '_'));
    }

    /**
     * La base del proyecto, para que no haya que inventarla.
     * <p>
     * Sin esto, arrancar un proyecto generado obligaba a escribir a mano un
     * {@code docker run} con el nombre de base correcto -que no es evidente:
     * sale del artefacto- y a pelear el puerto 5432 con cualquier otro Postgres
     * de la maquina.
     */
    public String compose(Plan.Proyecto proyecto) {
        return """
                # Levanta la base que este proyecto espera:
                #
                #     docker compose up -d
                #
                # El puerto no es el 5432 a proposito: una maquina de desarrollo
                # suele tener ya un Postgres ahi, y no vale la pena pelearlo.
                services:
                  db:
                    image: postgres:17-alpine
                    container_name: %s-db
                    environment:
                      POSTGRES_DB: %s
                      POSTGRES_USER: postgres
                      POSTGRES_PASSWORD: postgres
                    ports:
                      - "%d:5432"
                    volumes:
                      - %s-datos:/var/lib/postgresql/data
                    healthcheck:
                      test: ["CMD-SHELL", "pg_isready -U postgres -d %s"]
                      interval: 5s
                      timeout: 5s
                      retries: 10

                volumes:
                  %s-datos:
                """.formatted(proyecto.artefacto(), proyecto.artefacto().replace('-', '_'),
                PUERTO_BASE, proyecto.artefacto(), proyecto.artefacto().replace('-', '_'),
                proyecto.artefacto());
    }

    /**
     * Permiso para que un frontend en otro puerto pueda llamar a la API.
     * <p>
     * Se genera porque su ausencia se manifiesta de la peor manera posible:
     * el navegador bloquea cada llamada con un mensaje que no nombra al
     * backend, asi que se lee como un error del frontend y se busca donde no
     * es. Y no aparece probando con un cliente de API -Postman no es un
     * navegador y no aplica la politica de origenes-, asi que el problema
     * espera agazapado hasta el primer {@code fetch}.
     * <p>
     * Los origenes son los puertos de desarrollo habituales. Antes de publicar
     * esto hay que reemplazarlos por el dominio real.
     */
    public String cors(Plan.Proyecto proyecto) {
        return """
                package %s.web;

                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.CorsRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

                /**
                 * Generado por FORJA.
                 *
                 * <p>Permite que un cliente servido desde otro puerto -Vite en 5173,
                 * React en 3000- consuma esta API durante el desarrollo. En produccion
                 * hay que cambiar estos origenes por el dominio real.
                 */
                @Configuration
                public class ConfiguracionCors implements WebMvcConfigurer {

                    @Override
                    public void addCorsMappings(CorsRegistry registro) {
                        registro.addMapping("/api/**")
                                .allowedOrigins(
                                        "http://localhost:5173",
                                        "http://localhost:4173",
                                        "http://localhost:3000")
                                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                                .allowedHeaders("*");
                    }
                }
                """.formatted(proyecto.paqueteBase());
    }

    public String readme(Plan.Proyecto proyecto) {
        StringBuilder entidades = new StringBuilder();
        for (Plan.Clase clase : proyecto.generables()) {
            entidades.append("| `").append(clase.nombreJava()).append("` | `")
                    .append(clase.tabla()).append("` | `/api/").append(clase.ruta())
                    .append("` |\n");
        }

        return """
                # %s

                Proyecto generado por **FORJA** a partir de un diagrama de clases UML.
                No se escribio a mano ninguna de sus clases.

                ## Como ejecutarlo

                Dos comandos. El proyecto trae su propia base y el wrapper de Maven,
                asi que no hace falta instalar nada mas que Docker y un JDK 21.

                ```
                docker compose up -d       # levanta la base, en el puerto %d
                ./mvnw spring-boot:run     # en Windows: mvnw.cmd spring-boot:run
                ```

                La aplicacion arranca en el puerto 8081 y crea las tablas del modelo
                en el primer arranque.

                > En Linux y macOS, si `./mvnw` no arranca, dale permiso de ejecucion:
                > `chmod +x mvnw`. El zip no puede guardar ese permiso.

                ## Como probarlo sin instalar nada

                Con la aplicacion corriendo, abri en el navegador:

                **<http://localhost:8081/swagger-ui.html>**

                Estan todos los recursos con su formulario: se elige uno, `Try it out`,
                y se manda. El JSON de ejemplo sale del modelo, asi que no hay que
                escribirlo a mano.

                **El orden importa.** Una entidad cuya asociacion tenia multiplicidad 1
                se genera con esa columna `NOT NULL`: hay que crear primero aquello de
                lo que depende. La restriccion no es del generador, es lo que decia el
                diagrama.

                ## Conectarle un frontend

                Ya viene con CORS habilitado para los puertos de desarrollo habituales
                -5173, 4173 y 3000-, en `web/ConfiguracionCors.java`. Sin eso el
                navegador bloquea cada llamada, con un error que parece del frontend.
                Antes de publicar, cambia esos origenes por el dominio real.

                ## Que se genero

                Cuatro capas por cada clase concreta del diagrama:

                1. `dominio` - la entidad JPA, con sus columnas y asociaciones
                2. `repositorio` - el acceso a datos sobre Spring Data JPA
                3. `servicio` - las operaciones, con las fronteras transaccionales
                4. `web` - la API REST

                | Entidad | Tabla | Recurso |
                |---|---|---|
                %s
                ## Decisiones que conviene conocer

                - **El esquema lo crea Hibernate** (`ddl-auto: update`). Sirve para ver
                  el modelo funcionando de inmediato; antes de llevar esto a produccion
                  hay que reemplazarlo por migraciones versionadas.
                - **La API expone las entidades directamente**, sin objetos de
                  transferencia. Es el punto de partida de un CRUD; en cuanto la API
                  deba dejar de reflejar el modelo interno, corresponde interponerlos.
                - **Las asociaciones hacia el padre son ansiosas** (`FetchType.EAGER`) y
                  **las colecciones no se serializan** (`@JsonIgnore`). Las dos cosas
                  salen de lo mismo: `open-in-view` esta en `false` -que es lo correcto-
                  y los controladores devuelven la entidad, asi que al escribir el JSON
                  la transaccion ya cerro y una asociacion perezosa falla. Y serializar
                  las dos puntas de una relacion no termina nunca. El precio es que
                  traer una fila trae tambien sus padres; con objetos de transferencia
                  se puede volver a `LAZY` y elegir que viaja.
                - **Los metodos declarados en el diagrama estan sin implementar**: se
                  genero su firma y lanzan `UnsupportedOperationException`. El diagrama
                  declara que operaciones existen, no que hacen.
                """.formatted(proyecto.nombreAplicacion(), PUERTO_BASE,
                entidades.toString());
    }

    /** Del paquete {@code com.ejemplo.clinica} sale el grupo {@code com.ejemplo}. */
    private String grupoDe(String paqueteBase) {
        int ultimoPunto = paqueteBase.lastIndexOf('.');
        return ultimoPunto < 0 ? paqueteBase : paqueteBase.substring(0, ultimoPunto);
    }
}
