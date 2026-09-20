package bo.forja.backend.agente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de lo que el agente sabe responder.
 * <p>
 * Que la busqueda sea por palabras clave sobre un catalogo escrito y no un
 * modelo de lenguaje tiene una consecuencia comprobable: la misma pregunta da
 * siempre la misma respuesta. Eso es exactamente lo que se verifica aqui, y es
 * lo que no se podria verificar de la otra forma.
 */
@DisplayName("Preguntas al agente guia")
class PreguntasTest {

    private final Preguntas preguntas = new Preguntas();

    @ParameterizedTest(name = "\"{0}\" responde sobre {1}")
    @DisplayName("cada pregunta habitual encuentra su respuesta")
    @CsvSource({
            "'¿Cómo exporto a Enterprise Architect?',   respuesta-exportar-xmi",
            "'como exporto a enterprise architect',     respuesta-exportar-xmi",
            "'diferencia entre agregacion y composicion', respuesta-agregacion-vs-composicion",
            "'¿Qué significa el rombo lleno?',          respuesta-agregacion-vs-composicion",
            "'como invito a alguien',                   respuesta-invitar",
            "'quiero trabajar con otro',                respuesta-invitar",
            "'que frases entiende el dictado',          respuesta-dictar",
            "'sacar una foto a la pizarra',             respuesta-pizarra",
            "'como genero el backend',                  respuesta-generar-backend",
            "'que es una clave primaria',               respuesta-clave-primaria",
            "'no se por donde empiezo',                 respuesta-como-empiezo",
            "'que multiplicidad pongo',                 respuesta-multiplicidades",
            // La pregunta mas obvia de todas, y la que mas probable es que haga
            // alguien que abre la herramienta por primera vez. Caia en "no la se
            // contestar" justo despues de anunciar que sabe explicar como se usa
            // FORJA: el agente se contradecia solo.
            "'como uso la aplicacion',                  respuesta-como-empiezo",
            "'¿Cómo uso la aplicación?',                respuesta-como-empiezo",
            "'como se usa esto',                        respuesta-como-empiezo",
            "'como funciona',                           respuesta-como-empiezo",
            "'para que sirve',                          respuesta-como-empiezo",
            "'que puedo hacer aca',                     respuesta-como-empiezo",
            "'como uso forja',                          respuesta-como-empiezo",
    })
    void encuentraLaRespuesta(String pregunta, String idEsperado) {
        assertThat(preguntas.responder(pregunta))
                .extracting(Consejo::id)
                .contains(idEsperado);
    }

    @Test
    @DisplayName("los acentos y los signos no cambian la respuesta")
    void losAcentosNoImportan() {
        List<Consejo> con = preguntas.responder("¿Cómo exporto a Enterprise Architect?");
        List<Consejo> sin = preguntas.responder("como exporto a enterprise architect");

        assertThat(con).extracting(Consejo::id).isEqualTo(sin.stream().map(Consejo::id).toList());
    }

    @Test
    @DisplayName("la misma pregunta responde siempre lo mismo")
    void esDeterminista() {
        String pregunta = "en que se diferencian agregacion y composicion";

        List<String> primera = preguntas.responder(pregunta).stream().map(Consejo::id).toList();
        for (int intento = 0; intento < 5; intento++) {
            assertThat(preguntas.responder(pregunta).stream().map(Consejo::id).toList())
                    .isEqualTo(primera);
        }
    }

    @Test
    @DisplayName("una pregunta mas precisa le gana a una que solo comparte una palabra")
    void laMasPrecisaGana() {
        // "rombo lleno" son dos palabras de la entrada de agregacion y
        // composicion; cualquier otra entrada que comparta una sola palabra
        // tiene que quedar detras.
        assertThat(preguntas.responder("que es el rombo lleno").get(0).id())
                .isEqualTo("respuesta-agregacion-vs-composicion");
    }

    @Test
    @DisplayName("cuando no sabe, dice que si sabe en vez de pedir que se reformule")
    void cuandoNoSabe() {
        List<Consejo> respuesta = preguntas.responder("cual es la capital de bolivia");

        assertThat(respuesta).singleElement()
                .extracting(Consejo::id).isEqualTo("respuesta-sin-coincidencia");
        assertThat(respuesta.get(0).porQueImporta())
                .contains("Enterprise Architect")
                .contains("UML");
    }

    @Test
    @DisplayName("una pregunta vacia no rompe nada")
    void preguntaVacia() {
        assertThat(preguntas.responder("   ")).singleElement()
                .extracting(Consejo::id).isEqualTo("respuesta-sin-coincidencia");
        assertThat(preguntas.responder(null)).singleElement()
                .extracting(Consejo::id).isEqualTo("respuesta-sin-coincidencia");
    }

    @Test
    @DisplayName("no devuelve mas de tres respuestas: es ayuda, no un manual")
    void comoMuchoTres() {
        assertThat(preguntas.responder("como crear una clase y dictar y exportar y generar"))
                .hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("toda respuesta trae las tres partes, como cualquier consejo")
    void todasCompletas() {
        for (String pregunta : List.of("como empiezo", "dictar", "herencia", "multiplicidad",
                "generar el backend", "invitar", "bloqueo", "tipos de dato")) {
            assertThat(preguntas.responder(pregunta)).allSatisfy(respuesta -> {
                assertThat(respuesta.queNote()).isNotBlank();
                assertThat(respuesta.porQueImporta()).isNotBlank();
                assertThat(respuesta.comoSeHace()).isNotBlank();
            });
        }
    }
}
