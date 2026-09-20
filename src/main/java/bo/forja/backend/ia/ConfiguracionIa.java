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

        if (!habilitada) {
            return new TraductorNulo();
        }
        return new TraductorOllama(new AjustesDeIa(true, url, modelo));
    }

    /**
     * Quien contesta cuando la base de reglas del agente no alcanza.
     * <p>
     * Va detras de la MISMA bandera que el traductor: el bloque de IA se prende
     * y se apaga entero, y apagado la aplicacion es la de siempre. Son dos
     * beans y no uno porque son dos capacidades distintas -traducir a
     * instrucciones y redactar una explicacion- y cada una se puede recortar
     * sin la otra.
     */
    @Bean
    Respondedor respondedor(
            @Value("${forja.ia.local.habilitada:false}") boolean habilitada,
            @Value("${forja.ia.local.url:http://localhost:11434}") String url,
            @Value("${forja.ia.local.modelo:gemma3:4b}") String modelo) {

        if (!habilitada) {
            return new RespondedorNulo();
        }
        return new RespondedorOllama(new AjustesDeIa(true, url, modelo));
    }
}
