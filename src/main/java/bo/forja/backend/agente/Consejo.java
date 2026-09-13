package bo.forja.backend.agente;

import java.util.UUID;

/**
 * Una observacion del agente guia.
 * <p>
 * Cada consejo dice tres cosas y ninguna sobra: <b>que</b> noto, <b>por que</b>
 * importa y, cuando corresponde, <b>como</b> se resuelve. La parte del porque es
 * la que convierte al agente en algo que ensena en lugar de un validador
 * quejoso: "Paciente no tiene atributos" no le sirve a nadie; "el generador le
 * va a crear solo una clave, sin ningun dato" explica la consecuencia y con eso
 * la persona decide.
 *
 * @param id          identificador estable del consejo para este diagrama: el
 *                    cliente lo usa para recordar los que ya descarto, de modo
 *                    que no vuelvan a aparecer en cada consulta
 * @param elementoId  clase o relacion a la que se refiere, para que el lienzo
 *                    la pueda senalar; nulo si habla del diagrama entero
 * @param comoSeHace  instruccion concreta, dictable: se aprovecha que el parser
 *                    de voz entiende estas mismas frases
 */
public record Consejo(
        String id,
        Categoria categoria,
        int prioridad,
        String queNote,
        String porQueImporta,
        String comoSeHace,
        UUID elementoId) {

    public enum Categoria {
        /** Ensena a usar la herramienta: lo que la persona todavia no descubrio. */
        DESCUBRIMIENTO,
        /** Senala algo incompleto del modelo que va a afectar lo que se genere. */
        MODELO,
        /** Sugiere una mejora de diseno que el modelo ya permite ver. */
        DISENO
    }

    static Consejo de(String id, Categoria categoria, int prioridad,
                     String queNote, String porQueImporta, String comoSeHace) {
        return new Consejo(id, categoria, prioridad, queNote, porQueImporta, comoSeHace, null);
    }

    static Consejo sobre(String id, Categoria categoria, int prioridad, UUID elementoId,
                         String queNote, String porQueImporta, String comoSeHace) {
        return new Consejo(id, categoria, prioridad, queNote, porQueImporta, comoSeHace, elementoId);
    }
}
