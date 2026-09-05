package bo.forja.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FORJA :: herramienta CASE colaborativa para la fase de diseno.
 * <p>
 * La planificacion de tareas se habilita para el barrido periodico de
 * bloqueos vencidos del lienzo colaborativo.
 */
@SpringBootApplication
@EnableScheduling
public class ForjaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForjaBackendApplication.class, args);
    }
}
