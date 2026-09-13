package bo.forja.backend.generador;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Conversion de nombres del modelo a identificadores de Java y SQL.
 * <p>
 * El modelo lo escriben personas: una clase puede llamarse "Historia
 * Clinica", "historia_clinica" o "HistoriaClínica", y las tres deben
 * producir el mismo codigo. Aqui se normaliza una sola vez para que el
 * generador no tenga que desconfiar de cada nombre que recibe.
 * <p>
 * Se quitan los acentos porque un identificador con tilde es valido en
 * Java pero rompe en cuanto el nombre viaja a una columna de base de
 * datos, a una ruta HTTP o a un archivo, y el codigo generado atraviesa
 * los tres.
 */
public final class Nombres {

    private Nombres() {
    }

    /** {@code "historia clinica"} se convierte en {@code "HistoriaClinica"}. */
    public static String clase(String nombre) {
        StringBuilder salida = new StringBuilder();
        for (String palabra : palabras(nombre)) {
            salida.append(Character.toUpperCase(palabra.charAt(0)))
                    .append(palabra.substring(1));
        }
        return salida.isEmpty() ? "SinNombre" : salida.toString();
    }

    /** {@code "Historia Clinica"} se convierte en {@code "historiaClinica"}. */
    public static String campo(String nombre) {
        String enClase = clase(nombre);
        return Character.toLowerCase(enClase.charAt(0)) + enClase.substring(1);
    }

    /** {@code "HistoriaClinica"} se convierte en {@code "historia_clinica"}. */
    public static String columna(String nombre) {
        return String.join("_", palabras(nombre)).toLowerCase(Locale.ROOT);
    }

    /**
     * Nombre de tabla: la forma de columna en plural. Se pluraliza para que
     * el esquema generado lea como el de un sistema escrito a mano, donde
     * una tabla guarda muchas filas.
     */
    public static String tabla(String nombre) {
        return plural(columna(nombre));
    }

    /**
     * Identificador con guiones, sin pluralizar: sirve para nombres de
     * artefacto Maven y de carpeta, donde el plural estorba.
     */
    public static String slug(String nombre) {
        String[] partes = palabras(nombre);
        return partes.length == 0 ? "" : String.join("-", partes).toLowerCase(Locale.ROOT);
    }

    /** Segmento de ruta HTTP: {@code "HistoriaClinica"} da {@code "historia-clinicas"}. */
    public static String ruta(String nombre) {
        return plural(String.join("-", palabras(nombre)).toLowerCase(Locale.ROOT));
    }

    /**
     * Pluralizacion deliberadamente simple, con las reglas del castellano que
     * cubren la inmensa mayoria de los nombres de entidad. No pretende ser
     * completa: las excepciones se resolverian con un diccionario, y un
     * diccionario incompleto engana mas que una regla previsible.
     */
    public static String plural(String palabra) {
        if (palabra.isEmpty() || palabra.endsWith("s")) {
            return palabra;
        }
        char ultima = palabra.charAt(palabra.length() - 1);
        if ("aeiou".indexOf(ultima) >= 0) {
            return palabra + "s";
        }
        if (ultima == 'z') {
            return palabra.substring(0, palabra.length() - 1) + "ces";
        }
        return palabra + "es";
    }

    /** Descompone en palabras: separa por espacios, guiones y cambios de caja. */
    private static String[] palabras(String nombre) {
        String limpio = sinAcentos(nombre)
                .replaceAll("[^A-Za-z0-9]+", " ")
                // Corta entre minuscula y mayuscula para respetar el camello
                // que ya traiga el nombre original.
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .trim();
        return limpio.isEmpty() ? new String[0] : limpio.split("\\s+");
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
