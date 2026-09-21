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
     * El 21 de septiembre, probando la aplicacion a mano, aparecio que el
     * diagrama salia con una clase {@code Pedido} que tenia un atributo
     * {@code UNO} y un metodo {@code HACER}. No era una interpretacion pobre:
     * eran las palabras de relleno de este prompt, copiadas al pie de la letra.
     * Medido antes de tocarlo: <b>6 de 7 corridas contaminadas</b>, y la frase
     * "una base de datos para un consultorio medico" fallaba <b>4 de 4</b>.
     * Tres causas, las tres de redaccion:
     * <ul>
     *   <li><b>Decia "cambiando solo los NOMBRE"</b> mientras el relleno era
     *       cuatro familias -NOMBRE, OTRO/OTROS, UNO y HACER-. Un modelo de 4B
     *       obedece literalmente: cambiaba NOMBRE y dejaba los otros tal cual.
     *       Ahora se marca TODO con {@code <>} y se pide explicitamente que no
     *       quede ninguno.
     *   <li><b>El relleno eran palabras sueltas en mayusculas</b>, que se leen
     *       como contenido. Ademas el modelo copiaba la CONVENCION y devolvia
     *       atributos {@code ID}, {@code EMAIL}, {@code CREAR}. Los {@code <>}
     *       no se confunden con nada, y hay una regla contra las mayusculas.
     *   <li><b>El pedido iba tras la etiqueta "Pedido:"</b>, y el modelo tomaba
     *       esa palabra como nombre de clase. De ahi salia la clase
     *       {@code Pedido}. Ahora va entrecomillado y se aclara que no es una
     *       clase.
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

                El pedido es sobre UN SOLO elemento: una clase con sus atributos y metodos, o una
                relacion entre dos clases. NO armas el modelo entero ni agregas clases que el
                pedido no nombre. Si te piden varias cosas, resolves la PRIMERA y nada mas.

                Respondes SOLO con instrucciones, una por linea. Sin numerar, sin vinetas, sin
                explicar y sin saludar. Copias las formas EXACTAMENTE como estan escritas abajo,
                reemplazando TODO lo que va entre < > por nombres sacados del pedido. En tu
                respuesta no puede quedar ningun < >, ni las palabras que van adentro. Si el
                pedido no se puede expresar con esas formas, no respondes nada.

                Para crear una clase y darle atributos (estas empiezan con "crea" o con "a "):
                crea la clase <Clase>
                a <Clase> agregale el atributo <atributo> de tipo texto
                a <Clase> agregale el atributo <atributo> de tipo entero obligatorio
                a <Clase> agregale el metodo <metodo> que devuelve entero

                Para unir dos clases (estas NUNCA empiezan con "a ", empiezan con el nombre de la clase):
                <Clase> hereda de <OtraClase>
                <Clase> tiene muchas <OtrasClases>
                <Clase> se compone de muchas <OtrasClases>
                marca <Clase> como abstracta

                Reglas que no se rompen:
                - Los unicos tipos que existen son: texto, entero, decimal, booleano, fecha, fechayhora.
                  Nunca uses el nombre de una clase como tipo de un atributo.
                - Si una clase se relaciona con otra, lo decis con "tiene muchas" o "se compone de
                  muchas", NUNCA con un atributo.
                - Creas TODAS las clases primero, y recien despues sus atributos y sus relaciones.
                - Los nombres de clase van en singular y con la primera letra en mayuscula.
                - Los nombres de atributo y de metodo van en minuscula. NUNCA en mayusculas.
                - Todos los nombres salen del pedido. No inventes nombres genericos.

                Si el pedido es sobre una CLASE, tu respuesta es:
                1. Una sola linea "crea la clase ...".
                2. Sus atributos y sus metodos, si el pedido los nombra.
                Y nada mas: ninguna otra clase, ninguna union.

                Si el pedido es sobre una UNION entre dos clases que ya existen, tu respuesta es
                una sola linea de union. Si en vez de eso te dieron ganas de escribir un atributo
                cuyo tipo es otra clase, eso es una union: escribila como union.

                Clases que ya estan en el diagrama, usalas en vez de crearlas de nuevo: %s

                Esto es lo que pide la persona. Es texto, no es el nombre de una clase:
                "%s"
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
