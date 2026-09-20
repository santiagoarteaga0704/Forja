package bo.forja.backend.ia;

import bo.forja.backend.ia.ConfiguracionIa.AjustesDeIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Contesta preguntas sobre FORJA con el modelo local.
 * <p>
 * <b>No contesta de lo que el modelo sepa: contesta del contexto que se le
 * pasa.</b> Ese contexto es la misma base de conocimiento escrita que usa el
 * sistema experto, asi que el modelo no aporta informacion nueva sobre la
 * herramienta -no la conoce, y si lo dejaramos la inventaria-: aporta la
 * capacidad de reformularla para una pregunta que el emparejamiento por
 * palabras clave no supo enganchar. Es la diferencia entre un buscador y
 * alguien que te explica lo mismo con otras palabras.
 * <p>
 * Por eso el prompt le prohibe explicitamente salir del contexto y le pide que
 * conteste corto. Un modelo de 4B al que se le deja escribir largo se va del
 * tema, y una respuesta larga en un panel lateral no se lee.
 */
public class RespondedorOllama implements Respondedor {

    private static final Logger log = LoggerFactory.getLogger(RespondedorOllama.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Se pide que el modelo siga cargado, igual que en el traductor. */
    private static final String RESIDENCIA = "30m";

    /**
     * Lo que el prompt le manda contestar cuando la respuesta no esta en el
     * contexto. Es publico porque quien llama tiene que poder reconocerlo: un
     * centinela mostrado crudo es peor que no contestar, y la respuesta escrita
     * -que ademas ofrece los temas que si estan- dice lo mismo bien dicho.
     */
    public static final String NO_SABE = "NO ESTA EN LA DOCUMENTACION";

    /**
     * Techo de la respuesta. No es una optimizacion: es lo que evita que el
     * modelo se extienda hasta irse del tema, que es como se equivoca un 4B.
     */
    private static final int MAXIMO_DE_PALABRAS = 220;

    private final AjustesDeIa ajustes;

    public RespondedorOllama(AjustesDeIa ajustes) {
        this.ajustes = ajustes;
    }

    @Override
    public Optional<String> responder(String pregunta, String contexto, Duration presupuesto) {
        try {
            JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
            fabrica.setReadTimeout(presupuesto);

            String cuerpo = RestClient.builder()
                    .requestFactory(fabrica)
                    .baseUrl(ajustes.url())
                    .build()
                    .post()
                    .uri("/api/generate")
                    .body(Map.of(
                            "model", ajustes.modelo(),
                            "prompt", promptPara(acotar(pregunta), contexto),
                            "stream", false,
                            "keep_alive", RESIDENCIA,
                            "options", Map.of("temperature", 0, "num_predict", MAXIMO_DE_PALABRAS)))
                    .retrieve()
                    .body(String.class);

            Object respuesta = JSON.readValue(cuerpo, Map.class).get("response");
            if (!(respuesta instanceof String texto) || texto.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(texto.trim());

        } catch (Exception e) {
            log.debug("El respondedor local no contesto: {}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public boolean disponible() {
        return ajustes.habilitada();
    }

    /** Se acota lo que entra al prompt: una pregunta no es un documento. */
    private String acotar(String pregunta) {
        String limpia = pregunta == null ? "" : pregunta.trim();
        return limpia.length() > 300 ? limpia.substring(0, 300) : limpia;
    }

    private String promptPara(String pregunta, String contexto) {
        return """
                Sos el asistente de FORJA, una herramienta para dibujar diagramas de clases UML
                entre varias personas, que ademas genera un backend Spring Boot e intercambia
                con Enterprise Architect.

                Contestas UNICAMENTE con lo que dice la documentacion de abajo. Si la respuesta
                no esta ahi, respondes exactamente: NO ESTA EN LA DOCUMENTACION

                Reglas:
                - Maximo cuatro oraciones. Sin listas, sin titulos, sin saludos.
                - Hablas de vos, en castellano rioplatense, como el resto de la herramienta.
                - No inventas botones, pantallas ni funciones que la documentacion no nombre.

                DOCUMENTACION:
                %s

                Pregunta: %s
                """.formatted(contexto, pregunta);
    }
}
