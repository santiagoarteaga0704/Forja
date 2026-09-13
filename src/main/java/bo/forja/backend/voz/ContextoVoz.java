package bo.forja.backend.voz;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el parser necesita saber del diagrama para entender una frase.
 * <p>
 * Sin contexto, "Paciente tiene muchas Consultas" es indistinguible de
 * "Paciente tiene muchas deudas": la primera relaciona dos clases y la
 * segunda describe un atributo. Lo que las separa es si el segundo nombre
 * corresponde a una clase que existe, y eso solo se sabe mirando el modelo.
 * <p>
 * Los nombres se comparan sin acentos y sin distinguir mayusculas, porque
 * quien dicta no pronuncia mayusculas y el reconocimiento de voz acentua
 * como le parece.
 */
public record ContextoVoz(Map<String, ClaseConocida> porNombre) {

    public record ClaseConocida(UUID id, String nombre) {
    }

    public static ContextoVoz de(List<ClaseConocida> clases) {
        Map<String, ClaseConocida> indice = new LinkedHashMap<>();
        clases.forEach(clase -> indice.put(clave(clase.nombre()), clase));
        return new ContextoVoz(indice);
    }

    public static ContextoVoz vacio() {
        return new ContextoVoz(Map.of());
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

    /** Forma comparable de un nombre: sin acentos, sin espacios y en minuscula. */
    static String clave(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
