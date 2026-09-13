package bo.forja.backend.generador;

/**
 * Multiplicidad de un extremo de asociacion, en notacion UML.
 * <p>
 * Solo importa una cosa para generar el codigo: si el extremo admite mas de
 * un objeto o uno solo. De la combinacion de los dos extremos sale la
 * cardinalidad de la asociacion, y de ella el tipo de campo y la anotacion
 * JPA que le corresponde.
 * <p>
 * Se interpretan las formas usuales -{@code 1}, {@code 0..1}, {@code 1..*},
 * {@code *}, {@code 0..*}, {@code 2..5}- y ante cualquier cosa que no se
 * entienda se asume {@code 1}. La alternativa seria rechazar el diagrama
 * por un extremo mal escrito, y perder la generacion completa por eso es
 * peor que asumir el caso mas frecuente.
 */
public record Multiplicidad(String texto, boolean muchos, boolean obligatorio) {

    public static Multiplicidad de(String texto) {
        String limpio = texto == null ? "1" : texto.trim();
        if (limpio.isEmpty()) {
            limpio = "1";
        }

        // El limite superior es lo que decide si hay coleccion: esta despues
        // de ".." cuando el rango lo trae, y es el valor entero cuando no.
        int separador = limpio.indexOf("..");
        String superior = separador >= 0 ? limpio.substring(separador + 2).trim() : limpio;
        String inferior = separador >= 0 ? limpio.substring(0, separador).trim() : limpio;

        boolean muchos = superior.equals("*") || superior.equals("n") || mayorQueUno(superior);
        boolean obligatorio = !inferior.equals("0") && !inferior.equals("*");

        return new Multiplicidad(limpio, muchos, obligatorio);
    }

    private static boolean mayorQueUno(String valor) {
        try {
            return Integer.parseInt(valor) > 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
