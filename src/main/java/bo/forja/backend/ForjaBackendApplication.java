package bo.forja.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

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

    /**
     * El reloj como dependencia y no como llamada estatica, para que lo que
     * caduca con el tiempo se pueda probar sin esperar. En UTC a proposito: la
     * aplicacion guarda instantes, y la zona es cosa de quien los muestra.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
