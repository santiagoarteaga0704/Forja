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
                proyecto.nombreAplicacion(), VERSION_JAVA);
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
                    url: jdbc:postgresql://localhost:5432/%s
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
                """.formatted(proyecto.artefacto(), proyecto.artefacto().replace('-', '_'));
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

                Hace falta un PostgreSQL escuchando en el puerto 5432 con una base
                llamada `%s`:

                ```
                createdb %s
                ./mvnw spring-boot:run
                ```

                La aplicacion arranca en el puerto 8081 y crea las tablas del modelo
                en el primer arranque.

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
                """.formatted(proyecto.nombreAplicacion(), proyecto.artefacto(),
                proyecto.artefacto(), entidades.toString());
    }

    /** Del paquete {@code com.ejemplo.clinica} sale el grupo {@code com.ejemplo}. */
    private String grupoDe(String paqueteBase) {
        int ultimoPunto = paqueteBase.lastIndexOf('.');
        return ultimoPunto < 0 ? paqueteBase : paqueteBase.substring(0, ultimoPunto);
    }
}
