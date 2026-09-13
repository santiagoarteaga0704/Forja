package bo.forja.backend.operacion;

import java.util.Locale;
import java.util.Map;

/**
 * Tipos tal como los escribe o los dice una persona, llevados a un nombre
 * unico.
 * <p>
 * Lo comparten los canales que interpretan texto -el dictado y la lectura de
 * una fotografia- y esa union no es comodidad: si cada uno normalizara por su
 * cuenta, el mismo modelo terminaria con "texto" en un atributo y "String" en
 * otro, el generador emitiria dos tipos distintos para lo mismo y la
 * exportacion XMI declararia dos tipos de datos donde hay uno.
 * <p>
 * Un tipo que no esta en la tabla se respeta tal cual: puede ser otra clase del
 * modelo, o un enumerado que la persona va a crear despues. Inventar una
 * equivalencia seria peor que no tener ninguna.
 */
public final class TiposDeclarados {

    private static final Map<String, String> EQUIVALENCIAS = Map.ofEntries(
            // Texto
            Map.entry("texto", "String"), Map.entry("cadena", "String"),
            Map.entry("string", "String"), Map.entry("caracteres", "String"),
            Map.entry("char", "String"), Map.entry("varchar", "String"),
            Map.entry("text", "String"), Map.entry("str", "String"),
            // Enteros
            Map.entry("entero", "Integer"), Map.entry("numero", "Integer"),
            Map.entry("int", "Integer"), Map.entry("integer", "Integer"),
            Map.entry("number", "Integer"),
            Map.entry("largo", "Long"), Map.entry("long", "Long"),
            Map.entry("bigint", "Long"),
            // Decimales
            Map.entry("decimal", "Decimal"), Map.entry("importe", "Decimal"),
            Map.entry("precio", "Decimal"), Map.entry("monto", "Decimal"),
            Map.entry("real", "Decimal"), Map.entry("double", "Decimal"),
            Map.entry("float", "Decimal"), Map.entry("money", "Decimal"),
            // Booleanos
            Map.entry("booleano", "Boolean"), Map.entry("logico", "Boolean"),
            Map.entry("bool", "Boolean"), Map.entry("boolean", "Boolean"),
            // Fechas
            Map.entry("fecha", "Date"), Map.entry("date", "Date"),
            Map.entry("fechayhora", "DateTime"), Map.entry("fechahora", "DateTime"),
            Map.entry("datetime", "DateTime"), Map.entry("marcadetiempo", "DateTime"),
            Map.entry("timestamp", "DateTime"),
            Map.entry("hora", "Time"), Map.entry("time", "Time"),
            // Identificadores
            Map.entry("uuid", "UUID"), Map.entry("guid", "UUID"));

    private TiposDeclarados() {
    }

    public static String normalizar(String escrito) {
        if (escrito == null || escrito.isBlank()) {
            return "String";
        }
        String limpio = escrito.trim();
        String clave = limpio.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");

        String conocido = EQUIVALENCIAS.get(clave);
        if (conocido != null) {
            return conocido;
        }
        return Character.toUpperCase(limpio.charAt(0)) + limpio.substring(1);
    }
}
