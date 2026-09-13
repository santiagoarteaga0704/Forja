package bo.forja.backend.foto;

import bo.forja.backend.operacion.ComandoOperacion;

import java.util.List;

/**
 * Lo que se reconocio en el texto de una pizarra.
 * <p>
 * Se devuelve antes de aplicar nada, y por eso lleva un resumen legible ademas
 * de los comandos. El reconocimiento de texto sobre letra manuscrita nunca es
 * exacto: mostrar "reconoci la clase Paciente con 3 atributos" y dejar corregir
 * es lo que separa una funcion demostrable de una loteria. Las lineas que no se
 * entendieron viajan con su numero para poder encontrarlas en el texto.
 */
public record Lectura(
        int lineasLeidas,
        List<ComandoOperacion> comandos,
        List<ClaseLeida> clases,
        List<RelacionLeida> relaciones,
        List<String> ignoradas) {

    /**
     * @param yaExistia cierto si la clase ya estaba en el diagrama. En ese caso
     *                  no se vuelve a crear ni se le tocan los miembros: la foto
     *                  agrega lo que falta, no reescribe lo que ya esta
     */
    public record ClaseLeida(
            String nombre,
            String estereotipo,
            boolean esAbstracta,
            int atributos,
            int operaciones,
            boolean yaExistia) {
    }

    public record RelacionLeida(String origen, String tipo, String destino, String multiplicidades) {
    }

    public boolean seEntendioAlgo() {
        return !comandos.isEmpty();
    }

    public static Lectura vacia(int lineasLeidas, List<String> ignoradas) {
        return new Lectura(lineasLeidas, List.of(), List.of(), List.of(), ignoradas);
    }
}
