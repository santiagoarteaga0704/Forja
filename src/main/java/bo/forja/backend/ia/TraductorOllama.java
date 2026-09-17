package bo.forja.backend.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Traduce con un modelo que corre en la misma maquina, por Ollama.
 * <p>
 * Ollama habla HTTP y JSON, asi que no hace falta ninguna dependencia nueva:
 * alcanza con el RestClient que ya trae Spring. El backend no hacia ninguna
 * llamada saliente hasta ahora, y esta es a localhost.
 * <p>
 * Tres decisiones que no son detalles:
 * <ul>
 *   <li><b>Nada se propaga.</b> Todo el cuerpo esta dentro de un try que
 *       devuelve vacio. Un traductor que lanza rompe el dictado, que es
 *       exactamente lo que este bloque no puede hacer.
 *   <li><b>Sin reintentos.</b> El presupuesto es el presupuesto; con un cliente
 *       que reintenta, tres segundos se convierten en nueve de reloj y del otro
 *       lado hay alguien esperando.
 *   <li><b>La salida es texto plano, una frase por linea, y no JSON.</b> Un
 *       modelo de 4B emitiendo JSON agrega un modo de falla -JSON mal formado, y
 *       se pierde la respuesta entera- encima del que ya se maneja sin drama:
 *       una linea que no parsea, que se descarta sola. Las lineas se degradan de
 *       a una; el JSON se degrada de golpe.
 * </ul>
 */
public class TraductorOllama implements Traductor {

    private static final Logger log = LoggerFactory.getLogger(TraductorOllama.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Vinetas y numeracion que los modelos chicos agregan aunque se les pida que no. */
    private static final String ADORNOS = "^\\s*(?:[-*•]|\\d+[.)])\\s*";

    private final ConfiguracionIa.AjustesDeIa ajustes;

    public TraductorOllama(ConfiguracionIa.AjustesDeIa ajustes) {
        this.ajustes = ajustes;
    }

    @Override
    public List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas,
                                         Duration presupuesto) {
        try {
            // El timeout va en la fabrica porque los dos presupuestos son
            // distintos: tres segundos para el dictado en vivo, treinta para un
            // pedido explicito. Por eso el cliente se arma por llamada.
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
                            "prompt", promptPara(pedido, clasesConocidas),
                            "stream", false,
                            "options", Map.of("temperature", 0)))
                    .retrieve()
                    .body(String.class);

            return lineasDe(JSON.readValue(cuerpo, Map.class).get("response"));

        } catch (Exception e) {
            log.debug("El traductor local no contesto: {}", e.toString());
            return List.of();
        }
    }

    private List<String> lineasDe(Object respuesta) {
        if (!(respuesta instanceof String texto)) {
            return List.of();
        }
        return Arrays.stream(texto.split("\\R"))
                .map(linea -> linea.replaceAll(ADORNOS, "").trim())
                .filter(linea -> !linea.isBlank())
                .toList();
    }

    /**
     * Las formas que se listan son formas que la gramatica realmente acepta.
     * La fuente de verdad es compartido/corpus-voz.json: si alguna vez se agrega
     * una forma aca que la gramatica no entiende, el modelo va a proponerla y
     * todas esas lineas se van a descartar en silencio.
     */
    private String promptPara(String pedido, List<String> clasesConocidas) {
        String existentes = clasesConocidas == null || clasesConocidas.isEmpty()
                ? "(el diagrama esta vacio)"
                : String.join(", ", clasesConocidas);

        return """
                Convertis un pedido en instrucciones para una herramienta de diagramas de clases UML.

                Respondes SOLO con instrucciones, una por linea. Sin numerar, sin vinetas, sin
                explicar y sin saludar. Si el pedido no se puede expresar con las formas de abajo,
                no respondes nada.

                Formas que entiende la herramienta, y son las unicas que podes usar:
                crea la clase NOMBRE
                crea la clase NOMBRE con los atributos UNO de tipo texto y OTRO de tipo entero
                a NOMBRE agregale el atributo UNO de tipo texto
                a NOMBRE agregale el atributo UNO de tipo texto obligatorio
                a NOMBRE agregale el metodo HACER que devuelve entero
                NOMBRE hereda de OTRO
                NOMBRE tiene muchas OTROS
                NOMBRE se compone de muchas OTROS
                marca NOMBRE como abstracta

                Tipos que existen: texto, entero, decimal, booleano, fecha, fechayhora.
                Los nombres de clase van en singular y con la primera letra en mayuscula.

                Clases que ya estan en el diagrama, usalas en vez de crearlas de nuevo: %s

                Pedido: %s
                """.formatted(existentes, pedido);
    }

    @Override
    public boolean disponible() {
        return ajustes.habilitada();
    }
}
