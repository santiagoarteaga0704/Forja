package bo.forja.backend.agente;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoRelacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Todo lo que el agente puede mirar, reunido de una vez.
 * <p>
 * Existe para que las reglas no consulten la base de datos. Con catorce reglas
 * evaluandose en cada consulta, cada una haciendo sus propias consultas, el
 * agente costaria mas que el resto de la aplicacion; asi se arma una sola vista
 * del estado y las reglas la recorren en memoria.
 * <p>
 * El otro efecto, mas importante, es que las reglas quedan comprobables sin
 * Postgres: una observacion se construye a mano en una prueba y se verifica que
 * la regla concluye lo que debe. La base de conocimiento se puede revisar
 * entera en milisegundos.
 *
 * @param operacionesPorOrigen cuantos cambios entraron por cada via. Es lo que
 *                             permite notar que alguien nunca probo el dictado,
 *                             y tambien la evidencia que la evaluacion pide
 */
public record Observacion(
        UUID diagramaId,
        String nombreDelDiagrama,
        List<ClaseUml> clases,
        List<RelacionUml> relaciones,
        int cantidadDeMiembros,
        Map<OrigenOperacion, Long> operacionesPorOrigen) {

    public long totalDeOperaciones() {
        return operacionesPorOrigen.values().stream().mapToLong(Long::longValue).sum();
    }

    public long operacionesConOrigen(OrigenOperacion origen) {
        return operacionesPorOrigen.getOrDefault(origen, 0L);
    }

    public long cantidadDeAtributos() {
        return clases.stream().mapToLong(c -> c.getAtributos().size()).sum();
    }

    /** Cierto si la clase se representa como interfaz y no como entidad. */
    public boolean esInterfaz(ClaseUml clase) {
        String estereotipo = clase.getEstereotipo();
        return estereotipo != null
                && (estereotipo.trim().equalsIgnoreCase("interface")
                || estereotipo.trim().equalsIgnoreCase("interfaz"));
    }

    /** Relaciones en las que la clase participa, por cualquiera de sus extremos. */
    public List<RelacionUml> relacionesDe(ClaseUml clase) {
        return relaciones.stream()
                .filter(r -> pertenece(r.getOrigen(), clase) || pertenece(r.getDestino(), clase))
                .toList();
    }

    /** Superclase de la clase, si hereda de alguna. */
    public Optional<ClaseUml> padreDe(ClaseUml clase) {
        return relaciones.stream()
                .filter(r -> r.getTipo() == TipoRelacion.HERENCIA)
                .filter(r -> pertenece(r.getOrigen(), clase))
                .map(RelacionUml::getDestino)
                .findFirst();
    }

    /** Subclases directas de la clase. */
    public List<ClaseUml> hijosDe(ClaseUml clase) {
        return relaciones.stream()
                .filter(r -> r.getTipo() == TipoRelacion.HERENCIA)
                .filter(r -> pertenece(r.getDestino(), clase))
                .map(RelacionUml::getOrigen)
                .toList();
    }

    public List<String> nombresDeAtributos(ClaseUml clase) {
        return clase.getAtributos().stream().map(AtributoUml::getNombre).toList();
    }

    /**
     * Compara por identificador y no por identidad de objeto: las relaciones
     * traen sus extremos cargados aparte y pueden no ser la misma instancia que
     * la de la lista de clases.
     */
    private boolean pertenece(ClaseUml unaClase, ClaseUml otra) {
        return unaClase != null && otra != null && unaClase.getId().equals(otra.getId());
    }
}
