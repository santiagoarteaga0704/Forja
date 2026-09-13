package bo.forja.backend.operacion;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Nombres de clase que ya existen en el diagrama, para resolver las
 * referencias que llegan escritas por una persona.
 * <p>
 * Lo comparten los dos canales que interpretan texto. Al dictar, sin contexto
 * "Paciente tiene muchas Consultas" es indistinguible de "Paciente tiene
 * muchas deudas": la primera relaciona dos clases y la segunda describe un
 * atributo, y lo que las separa es si el segundo nombre corresponde a una
 * clase que existe. Al leer la foto de una pizarra, el mismo contexto es lo
 * que evita duplicar una clase que ya estaba dibujada.
 * <p>
 * Los nombres se comparan sin acentos y sin distinguir mayusculas, porque
 * quien dicta no pronuncia mayusculas, el reconocimiento de voz acentua como
 * le parece y el de una fotografia confunde la caja de las letras.
 */
public record ContextoDelDiagrama(
        Map<String, ClaseConocida> porNombre,
        /* Relaciones que ya estan trazadas, como "origen|destino|tipo". Permite
         * que leer dos veces la misma pizarra no vuelva a trazar lo mismo. */
        Set<String> relacionesExistentes) {

    public record ClaseConocida(UUID id, String nombre) {
    }

    public static ContextoDelDiagrama de(List<ClaseConocida> clases) {
        return de(clases, Set.of());
    }

    public static ContextoDelDiagrama de(List<ClaseConocida> clases, Set<String> relaciones) {
        Map<String, ClaseConocida> indice = new LinkedHashMap<>();
        clases.forEach(clase -> indice.put(clave(clase.nombre()), clase));
        return new ContextoDelDiagrama(indice, relaciones);
    }

    public static ContextoDelDiagrama vacio() {
        return new ContextoDelDiagrama(Map.of(), Set.of());
    }

    /** Clave con la que se identifica una relacion entre dos clases. */
    public static String claveDeRelacion(UUID origen, UUID destino, String tipo) {
        return origen + "|" + destino + "|" + tipo;
    }

    public boolean yaExisteRelacion(UUID origen, UUID destino, String tipo) {
        return relacionesExistentes.contains(claveDeRelacion(origen, destino, tipo))
                // Una asociacion dibujada al reves es la misma relacion.
                || relacionesExistentes.contains(claveDeRelacion(destino, origen, tipo));
    }

    /**
     * Busca la clase que nombra el texto.
     * <p>
     * Primero por coincidencia exacta normalizada. Si no la hay, se admite una
     * coincidencia parcial <b>solo cuando es unica</b>: dictando es facil que
     * "historia" quiera decir "HistoriaClinica", pero si hubiera dos clases que
     * empiezan igual, elegir una seria adivinar.
     */
    public Optional<ClaseConocida> resolver(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        String clave = clave(texto);

        ClaseConocida exacta = porNombre.get(clave);
        if (exacta != null) {
            return Optional.of(exacta);
        }

        List<ClaseConocida> parciales = porNombre.entrySet().stream()
                .filter(entrada -> entrada.getKey().startsWith(clave) || clave.startsWith(entrada.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return parciales.size() == 1 ? Optional.of(parciales.get(0)) : Optional.empty();
    }

    public List<String> nombres() {
        return porNombre.values().stream().map(ClaseConocida::nombre).toList();
    }

    /**
     * Forma comparable de un nombre: sin acentos, sin espacios y en minuscula.
     * Publica porque los parsers la usan para no duplicar una clase que ya
     * existe escrita de otra manera.
     */
    public static String clave(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
