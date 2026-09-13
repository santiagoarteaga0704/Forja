package bo.forja.backend.generador;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Traduccion de los tipos escritos en el diagrama a tipos de Java.
 * <p>
 * En un diagrama de clases el tipo es texto libre: quien modela escribe
 * "String", "string", "texto", "int" o "entero" segun su costumbre. El
 * generador no puede exigir una notacion canonica sin volver el diagrama
 * incomodo de dibujar, asi que acepta las formas habituales en ingles y en
 * castellano y las lleva a un unico tipo de Java.
 * <p>
 * Un tipo desconocido no se inventa ni se convierte en {@code Object}: se
 * respeta tal cual, en su forma de nombre de clase. Es lo que permite que
 * un atributo cuyo tipo es otra clase del modelo, o un enumerado que el
 * usuario agregara despues, siga teniendo sentido en el codigo generado.
 */
public final class TipoJava {

    /**
     * Tipos que la base de datos almacena en una columna de texto y a los
     * que, por lo tanto, les corresponde una longitud.
     */
    private static final Set<String> TEXTUALES = Set.of("String");

    private static final Map<String, String> EQUIVALENCIAS = Map.ofEntries(
            // Texto
            Map.entry("string", "String"),
            Map.entry("str", "String"),
            Map.entry("texto", "String"),
            Map.entry("cadena", "String"),
            Map.entry("char", "String"),
            Map.entry("varchar", "String"),
            Map.entry("text", "String"),
            // Enteros
            Map.entry("int", "Integer"),
            Map.entry("integer", "Integer"),
            Map.entry("entero", "Integer"),
            Map.entry("number", "Integer"),
            Map.entry("numero", "Integer"),
            Map.entry("long", "Long"),
            Map.entry("bigint", "Long"),
            Map.entry("short", "Short"),
            // Decimales: se elige BigDecimal y no double para lo monetario,
            // porque el punto flotante binario no representa exactamente los
            // decimales y en un importe eso se nota.
            Map.entry("decimal", "java.math.BigDecimal"),
            Map.entry("bigdecimal", "java.math.BigDecimal"),
            Map.entry("money", "java.math.BigDecimal"),
            Map.entry("importe", "java.math.BigDecimal"),
            Map.entry("precio", "java.math.BigDecimal"),
            Map.entry("double", "Double"),
            Map.entry("float", "Float"),
            Map.entry("real", "Double"),
            // Booleanos
            Map.entry("boolean", "Boolean"),
            Map.entry("bool", "Boolean"),
            Map.entry("booleano", "Boolean"),
            Map.entry("logico", "Boolean"),
            // Fechas
            Map.entry("date", "java.time.LocalDate"),
            Map.entry("fecha", "java.time.LocalDate"),
            Map.entry("localdate", "java.time.LocalDate"),
            Map.entry("datetime", "java.time.LocalDateTime"),
            Map.entry("fechahora", "java.time.LocalDateTime"),
            Map.entry("localdatetime", "java.time.LocalDateTime"),
            Map.entry("timestamp", "java.time.Instant"),
            Map.entry("instant", "java.time.Instant"),
            Map.entry("time", "java.time.LocalTime"),
            Map.entry("hora", "java.time.LocalTime"),
            // Identificadores
            Map.entry("uuid", "java.util.UUID"),
            Map.entry("guid", "java.util.UUID"));

    private TipoJava() {
    }

    /** Tipo de Java que corresponde al tipo escrito en el diagrama. */
    public static String de(String tipoDelDiagrama) {
        if (tipoDelDiagrama == null || tipoDelDiagrama.isBlank()) {
            return "String";
        }
        String clave = tipoDelDiagrama.trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(EQUIVALENCIAS.get(clave))
                .orElseGet(() -> Nombres.clase(tipoDelDiagrama));
    }

    /** Nombre simple para declarar el campo, sin el paquete. */
    public static String simple(String tipoJava) {
        int punto = tipoJava.lastIndexOf('.');
        return punto < 0 ? tipoJava : tipoJava.substring(punto + 1);
    }

    /**
     * Importacion que hace falta para usar el tipo, si la hace falta. Los
     * tipos de {@code java.lang} y los del propio paquete generado no la
     * necesitan.
     */
    public static Optional<String> importacion(String tipoJava) {
        return tipoJava.contains(".") ? Optional.of(tipoJava) : Optional.empty();
    }

    public static boolean esTextual(String tipoJava) {
        return TEXTUALES.contains(tipoJava);
    }

    /** Tipos validos para una clave primaria generada automaticamente. */
    public static boolean sirveDeIdentificador(String tipoJava) {
        return switch (tipoJava) {
            case "Long", "Integer", "java.util.UUID", "String" -> true;
            default -> false;
        };
    }
}
