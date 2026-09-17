package bo.forja.backend.voz;

import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que queda de la gramatica fuera del corpus compartido.
 * <p>
 * Los 28 casos que habia aca se mudaron a {@code compartido/corpus-voz.json}
 * para que los lea tambien la suite de Dart del cliente movil, que tiene su
 * propia copia de esta gramatica para poder dictar sin conexion. Quedan dos, y
 * los dos por la misma razon: no son una frase y su resultado, que es lo unico
 * que el corpus sabe expresar.
 *
 * @see ParserVozCorpusTest
 */
@DisplayName("Dictado por voz, lo que no entra en el corpus")
class ParserVozTest {

    private final ParserVoz parser = new ParserVoz();

    private static final UUID ID_PACIENTE = UUID.randomUUID();
    private static final UUID ID_CONSULTA = UUID.randomUUID();

    private final ContextoDelDiagrama contexto = ContextoDelDiagrama.de(List.of(
            new ContextoDelDiagrama.ClaseConocida(ID_PACIENTE, "Paciente"),
            new ContextoDelDiagrama.ClaseConocida(ID_CONSULTA, "Consulta")));

    /**
     * El corpus es un archivo JSON y no puede llevar una frase nula, que es
     * justo la entrada que llega cuando el reconocedor de voz corta sin decir
     * nada.
     */
    @Test
    @DisplayName("una frase nula no revienta")
    void fraseNula() {
        assertThat(parser.interpretar(null, contexto).entendida()).isFalse();
    }

    /**
     * El corpus interpreta cada frase una sola vez, asi que no puede afirmar
     * nada sobre dos interpretaciones seguidas. Y hay algo que afirmar: los
     * identificadores son nuevos en cada una -son de elementos nuevos- pero
     * todo lo demas tiene que salir igual.
     */
    @Test
    @DisplayName("la misma frase produce siempre la misma interpretacion")
    void esDeterminista() {
        String frase = "a Paciente agregale el atributo nombre de tipo texto obligatorio";

        ComandoOperacion.AgregarAtributo primera = unicoAtributo(frase);
        ComandoOperacion.AgregarAtributo segunda = unicoAtributo(frase);

        assertThat(segunda.claseId()).isEqualTo(primera.claseId());
        assertThat(segunda.nombre()).isEqualTo(primera.nombre());
        assertThat(segunda.tipo()).isEqualTo(primera.tipo());
        assertThat(segunda.esRequerido()).isEqualTo(primera.esRequerido());
    }

    private ComandoOperacion.AgregarAtributo unicoAtributo(String frase) {
        Interpretacion resultado = parser.interpretar(frase, contexto);
        assertThat(resultado.entendida())
                .as("no se entendio: \"" + frase + "\"")
                .isTrue();
        assertThat(resultado.pasos()).hasSize(1);
        return (ComandoOperacion.AgregarAtributo) resultado.pasos().get(0).comando();
    }
}
