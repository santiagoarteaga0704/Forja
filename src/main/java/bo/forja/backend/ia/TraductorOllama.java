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

    /**
     * Cuanto tiempo Ollama mantiene el modelo en memoria despues de contestar.
     * <p>
     * Por omision son cinco minutos, y eso no alcanza: entre que alguien abre la
     * aplicacion, arma algo a mano y recien despues prueba un pedido pasan mas,
     * y la llamada vuelve a pagar el arranque en frio, que cuesta mas de un
     * minuto y por lo tanto se pierde entera.
     */
    private static final String RESIDENCIA = "30m";

    /** Cargar el modelo a memoria la primera vez, que es lo lento de verdad. */
    private static final Duration ESPERA_DE_CARGA = Duration.ofMinutes(5);

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
                            "keep_alive", RESIDENCIA,
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
     * <p>
     * Esta version salio de medir contra Gemma 3 4B de verdad el 17 de
     * septiembre, y cada regla arregla algo que el modelo hacia mal con la
     * anterior:
     * <ul>
     *   <li><b>Las formas estan agrupadas por como empiezan.</b> Con una lista
     *       sola el modelo le ponia "a " adelante a todo, y escribia
     *       "a Mascota hereda de Dueno", que no parsea. Ninguna relacion
     *       entraba.
     *   <li><b>Prohibir una clase como tipo de atributo.</b> Proponia
     *       "el atributo dueno de tipo Dueno", que se descarta, y encima usaba
     *       eso EN LUGAR de una relacion: el diagrama salia sin ninguna.
     *   <li><b>Crear todas las clases antes de usarlas.</b> Nombraba una clase
     *       que recien creaba mas abajo, y esa linea no resolvia sus extremos.
     * </ul>
     * Medido asi, las relaciones aparecen en todas las corridas. Lo que no se
     * arregla con el prompt es que el modelo conteste distinto cada vez -pasa
     * igual con temperatura cero y semilla fija- y por eso lo que propone se
     * revisa antes de aplicarlo.
     */
    private String promptPara(String pedido, List<String> clasesConocidas) {
        String existentes = clasesConocidas == null || clasesConocidas.isEmpty()
                ? "(el diagrama esta vacio)"
                : String.join(", ", clasesConocidas);

        return """
                Convertis un pedido en instrucciones para una herramienta de diagramas de clases UML.

                Respondes SOLO con instrucciones, una por linea. Sin numerar, sin vinetas, sin
                explicar y sin saludar. Copias las formas EXACTAMENTE como estan escritas abajo,
                cambiando solo los NOMBRE. Si el pedido no se puede expresar con esas formas, no
                respondes nada.

                Para crear una clase y darle atributos (estas empiezan con "crea" o con "a "):
                crea la clase NOMBRE
                a NOMBRE agregale el atributo UNO de tipo texto
                a NOMBRE agregale el atributo UNO de tipo entero obligatorio
                a NOMBRE agregale el metodo HACER que devuelve entero

                Para unir dos clases (estas NUNCA empiezan con "a ", empiezan con el nombre de la clase):
                NOMBRE hereda de OTRO
                NOMBRE tiene muchas OTROS
                NOMBRE se compone de muchas OTROS
                marca NOMBRE como abstracta

                Reglas que no se rompen:
                - Los unicos tipos que existen son: texto, entero, decimal, booleano, fecha, fechayhora.
                  Nunca uses el nombre de una clase como tipo de un atributo.
                - Si una clase se relaciona con otra, lo decis con "tiene muchas" o "se compone de
                  muchas", NUNCA con un atributo.
                - Creas TODAS las clases primero, y recien despues sus atributos y sus relaciones.
                - Los nombres de clase van en singular y con la primera letra en mayuscula.

                Clases que ya estan en el diagrama, usalas en vez de crearlas de nuevo: %s

                Pedido: %s
                """.formatted(existentes, pedido);
    }

    @Override
    public void precalentar() {
        try {
            JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
            fabrica.setReadTimeout(ESPERA_DE_CARGA);

            RestClient.builder()
                    .requestFactory(fabrica)
                    .baseUrl(ajustes.url())
                    .build()
                    .post()
                    .uri("/api/generate")
                    .body(Map.of(
                            "model", ajustes.modelo(),
                            "prompt", "",
                            "stream", false,
                            "keep_alive", RESIDENCIA))
                    .retrieve()
                    .body(String.class);

            log.info("Modelo {} cargado y listo para traducir", ajustes.modelo());

        } catch (Exception e) {
            // Que Ollama no este es normal y no es un error: el traductor va a
            // devolver vacio en cada llamada, que es una respuesta legitima.
            log.debug("No se pudo precalentar el modelo: {}", e.toString());
        }
    }

    @Override
    public boolean disponible() {
        return ajustes.habilitada();
    }
}
