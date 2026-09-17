package bo.forja.backend.voz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * La gramatica contra el corpus compartido.
 * <p>
 * Estos casos no viven aca sino en {@code compartido/corpus-voz.json}, porque
 * los lee tambien la suite de Dart del cliente movil. Es lo unico que impide
 * que la copia del telefono y la del servidor se separen sin que nadie se
 * entere: tocar una regla de un lado pone roja la prueba del otro.
 */
@DisplayName("Dictado por voz contra el corpus compartido")
class ParserVozCorpusTest {

    private final ParserVoz parser = new ParserVoz();

    @TestFactory
    List<DynamicTest> elCorpusEntero() {
        List<DynamicTest> pruebas = new ArrayList<>();
        for (CorpusDeVoz.Caso caso : CorpusDeVoz.cargar()) {
            for (String frase : caso.frases()) {
                pruebas.add(DynamicTest.dynamicTest(
                        caso.id() + ": " + frase,
                        () -> CorpusDeVoz.verificar(caso, frase,
                                parser.interpretar(frase, caso.contextoResuelto()))));
            }
        }
        return pruebas;
    }
}
