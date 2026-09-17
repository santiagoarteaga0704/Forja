package bo.forja.backend.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Que traductor se registra. Sin configuracion, el nulo.
 * <p>
 * La condicion es que este habilitado explicitamente, y no que Ollama conteste:
 * comprobar el modelo al arrancar retrasaria el arranque y ataria la aplicacion
 * a que un servicio externo este vivo antes que ella. Si esta habilitado pero
 * Ollama no corre, el traductor devuelve vacio en cada llamada, que es una
 * respuesta legitima y no un error.
 */
@Configuration
public class ConfiguracionIa {

    /**
     * @param modelo el nombre tal como lo conoce Ollama, con su etiqueta de
     *               tamano. Cambiar de 4B a 12B es cambiar esta cadena
     */
    public record AjustesDeIa(boolean habilitada, String url, String modelo) {
    }

    @Bean
    Traductor traductor(
            @Value("${forja.ia.local.habilitada:false}") boolean habilitada,
            @Value("${forja.ia.local.url:http://localhost:11434}") String url,
            @Value("${forja.ia.local.modelo:gemma3:4b}") String modelo) {

        // La rama que habla llega en el commit siguiente. Hasta entonces la
        // configuracion existe, se lee, y no cambia nada: es exactamente el
        // estado en el que el bloque de IA esta recortado.
        return new TraductorNulo();
    }
}
