package bo.forja.backend.voz;

import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.TipoOperacion;

import java.util.List;

/**
 * Lo que el parser entendio de una frase.
 * <p>
 * Cuando no entiende no devuelve un error sino <b>sugerencias</b>. La
 * diferencia importa para algo que se usa hablando: "no te entendi" deja a
 * la persona probando al azar, mientras que "proba: agrega el atributo X de
 * tipo texto a Paciente" le ensena la forma que si funciona. Es el mismo
 * criterio con el que esta pensado el agente guia.
 */
public record Interpretacion(
        String frase,
        boolean entendida,
        /** Los comandos a aplicar, en orden. Una frase puede producir varios. */
        List<Paso> pasos,
        /** Que se hizo, en palabras, para devolverselo a quien dicto. */
        String explicacion,
        List<String> sugerencias) {

    public record Paso(TipoOperacion tipo, ComandoOperacion comando) {
    }

    public static Interpretacion entendida(String frase, String explicacion, List<Paso> pasos) {
        return new Interpretacion(frase, true, pasos, explicacion, List.of());
    }

    public static Interpretacion noEntendida(String frase, List<String> sugerencias) {
        return new Interpretacion(frase, false, List.of(),
                "No reconoci esa instruccion", sugerencias);
    }
}
