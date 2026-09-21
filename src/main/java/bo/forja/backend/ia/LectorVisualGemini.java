package bo.forja.backend.ia;

import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Lectura de la foto por Gemini.
 * <p>
 * Esta clase existe por una limitacion que no se arregla ajustando parametros:
 * el reconocimiento de caracteres lee TEXTO, y en un diagrama dibujado las
 * relaciones son FLECHAS. Medido el 21 de septiembre de 2026 sobre un diagrama
 * de Enterprise Architect de nueve clases:
 * <pre>
 *   reconocimiento en el navegador  ~4 s   lineas que cruzaban tres cajas
 *   Gemma 3 4B, local               50 s   10 clases (una inventada), 0 de 12 relaciones
 *   Gemini 3.1 Flash Lite          3,2 s   9 clases, 21 atributos, 4 operaciones, 8 de 10 relaciones
 * </pre>
 * <p>
 * <b>Que NO hace.</b> No emite comandos ni toca el diagrama: devuelve el texto
 * en la notacion de pizarra y de ahi sigue el camino de siempre -la persona
 * revisa, el {@code ParserPizarra} determinista interpreta-. La IA transcribe;
 * el parser decide. Es la misma division que en el dictado, y es lo que permite
 * que una transcripcion mala se corrija en vez de aplicarse.
 * <p>
 * <b>Donde corre.</b> En el servidor y no en el navegador, porque la clave es
 * de pago: puesta en el cliente, cualquiera que abra la aplicacion la lee. El
 * precio es que por esta via la imagen SI sale de la maquina, y por eso la
 * pantalla lo dice antes de que se elija.
 */
public class LectorVisualGemini implements LectorVisual {

    private static final Logger log = LoggerFactory.getLogger(LectorVisualGemini.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Se le pide la notacion que el parser ya entiende en lugar de un formato
     * propio: lo que el modelo devuelve entra por el mismo camino que una
     * pizarra escrita a mano, y no hay un segundo interprete que mantener.
     */
    private static final String PEDIDO = """
            Transcribi este diagrama de clases UML a texto plano, con esta notacion exacta:

            Para cada clase, su nombre en una linea y debajo sus miembros, uno por linea:
            NombreDeLaClase
            + atributo: Tipo
            + operacion(): Tipo

            Una linea en blanco separa una clase de la siguiente. Si una clase tiene un
            estereotipo, va en la linea de arriba de su nombre, asi: <<interface>>

            Al final, una linea por cada relacion, con estos conectores:
            A --|> B        (herencia: A hereda de B)
            A ..|> B        (realizacion: A implementa la interfaz B)
            A 1 *-- 0..* B  (composicion, con multiplicidades)
            A 1 -- 0..* B   (asociacion, con multiplicidades)

            Reglas: no expliques nada, no uses markdown, no inventes clases, atributos ni
            relaciones que no esten en la imagen. Respondé solo con el texto en esa notacion.""";

    private final ConfiguracionIa.AjustesDeVision ajustes;

    public LectorVisualGemini(ConfiguracionIa.AjustesDeVision ajustes) {
        this.ajustes = ajustes;
    }

    @Override
    public String aNotacionDePizarra(byte[] imagen, String tipoMime, Duration presupuesto) {
        if (!disponible()) {
            return "";
        }
        try {
            // Igual que en el traductor: el timeout va en la fabrica y el
            // cliente se arma por llamada, porque el presupuesto lo pone quien
            // llama y no es el mismo en todos lados.
            JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
            fabrica.setReadTimeout(presupuesto);

            String cuerpo = RestClient.builder()
                    .requestFactory(fabrica)
                    .baseUrl(ajustes.url())
                    .build()
                    .post()
                    .uri("/v1beta/models/{modelo}:generateContent", ajustes.modelo())
                    // En la cabecera y no en la direccion: en la direccion
                    // quedaria en los registros de cualquier intermediario.
                    .header("x-goog-api-key", ajustes.clave())
                    .body(Map.of(
                            "contents", List.of(Map.of("parts", List.of(
                                    Map.of("text", PEDIDO),
                                    Map.of("inline_data", Map.of(
                                            "mime_type", tipoMime,
                                            "data", Base64.getEncoder().encodeToString(imagen)))))),
                            "generationConfig", Map.of("temperature", 0)))
                    .retrieve()
                    .body(String.class);

            return sinComillasDeCodigo(textoDe(cuerpo));

        } catch (Exception e) {
            // Medido: en una misma sesion el servicio devolvio 404 por un
            // modelo retirado y 503 por alta demanda. Que la pantalla siga
            // viva no es una hipotesis.
            log.debug("La lectura por IA no contesto: {}", e.toString());
            return "";
        }
    }

    @Override
    public boolean disponible() {
        return ajustes.habilitada() && ajustes.clave() != null && !ajustes.clave().isBlank();
    }

    @SuppressWarnings("unchecked")
    private String textoDe(String cuerpo) throws Exception {
        Map<String, Object> raiz = JSON.readValue(cuerpo, Map.class);
        List<Map<String, Object>> candidatos = (List<Map<String, Object>>) raiz.get("candidates");
        if (candidatos == null || candidatos.isEmpty()) {
            return "";
        }
        Map<String, Object> contenido = (Map<String, Object>) candidatos.get(0).get("content");
        if (contenido == null) {
            return "";
        }
        List<Map<String, Object>> partes = (List<Map<String, Object>>) contenido.get("parts");
        if (partes == null) {
            return "";
        }
        StringBuilder texto = new StringBuilder();
        for (Map<String, Object> parte : partes) {
            if (parte.get("text") instanceof String t) {
                texto.append(t);
            }
        }
        return texto.toString().trim();
    }

    /**
     * Se le pide que no use markdown y lo usa igual. Sin sacarlas, la primera y
     * la ultima linea entrarian al parser como lineas que no entiende.
     */
    private String sinComillasDeCodigo(String texto) {
        String limpio = texto.strip();
        if (!limpio.startsWith("```")) {
            return limpio;
        }
        int primerSalto = limpio.indexOf('\n');
        if (primerSalto < 0) {
            return "";
        }
        limpio = limpio.substring(primerSalto + 1);
        int cierre = limpio.lastIndexOf("```");
        return (cierre < 0 ? limpio : limpio.substring(0, cierre)).strip();
    }
}
