package bo.forja.backend.web;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.BloqueoElemento;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.MetodoUml;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoDiagrama;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representaciones que la API devuelve.
 * <p>
 * Las entidades no salen nunca tal cual. No es ceremonia: una entidad
 * arrastra asociaciones perezosas que, al serializarse fuera de la
 * transaccion, o fallan o provocan una cascada de consultas inesperada.
 * Convertir a estos registros fija de manera explicita que viaja y que no.
 * <p>
 * La forma esta pensada para el lienzo, que es quien las consume: las
 * relaciones llevan los identificadores de sus extremos y no las clases
 * completas, porque el cliente ya las tiene y repetirlas multiplicaria el
 * tamano de la respuesta sin agregar informacion.
 */
public final class Vistas {

    private Vistas() {
    }

    // ---------- Proyectos y diagramas ------------------------------------

    public record ProyectoVista(
            UUID id,
            String nombre,
            String descripcion,
            UUID propietarioId,
            Instant actualizadoEn) {
    }

    public record DiagramaResumen(
            UUID id,
            String nombre,
            TipoDiagrama tipo,
            long version) {
    }

    /**
     * Estado completo del diagrama en un instante, con los bloqueos vigentes.
     * <p>
     * Incluir los bloqueos en la misma respuesta evita una condicion de
     * carrera al entrar al lienzo: si se pidieran aparte, entre una peticion
     * y la otra podria aparecer o desaparecer un bloqueo y el usuario veria
     * libre un elemento que otro ya esta editando.
     */
    public record DiagramaCompleto(
            UUID id,
            String nombre,
            TipoDiagrama tipo,
            long version,
            List<ClaseVista> clases,
            List<RelacionVista> relaciones,
            List<BloqueoVista> bloqueos) {
    }

    // ---------- Modelo UML -----------------------------------------------

    public record ClaseVista(
            UUID id,
            String nombre,
            String estereotipo,
            boolean esAbstracta,
            double posX,
            double posY,
            double ancho,
            double alto,
            List<AtributoVista> atributos,
            List<MetodoVista> metodos) {
    }

    public record AtributoVista(
            UUID id,
            String nombre,
            String tipo,
            Visibilidad visibilidad,
            boolean esIdentificador,
            boolean esRequerido,
            boolean esUnico,
            Integer longitud,
            int orden) {
    }

    public record MetodoVista(
            UUID id,
            String nombre,
            String tipoRetorno,
            Visibilidad visibilidad,
            boolean esAbstracto,
            boolean esEstatico,
            int orden) {
    }

    public record RelacionVista(
            UUID id,
            UUID origenId,
            UUID destinoId,
            TipoRelacion tipo,
            String multiplicidadOrigen,
            String multiplicidadDestino,
            String rolOrigen,
            String rolDestino,
            String etiqueta) {
    }

    public record BloqueoVista(
            TipoElemento elementoTipo,
            UUID elementoId,
            UUID poseedorId,
            String poseedorNombre,
            Instant expiraEn) {
    }

    // ---------- Conversion ------------------------------------------------

    public static ProyectoVista de(Proyecto proyecto) {
        return new ProyectoVista(proyecto.getId(), proyecto.getNombre(), proyecto.getDescripcion(),
                proyecto.getPropietario().getId(), proyecto.getActualizadoEn());
    }

    public static DiagramaResumen resumen(Diagrama diagrama) {
        return new DiagramaResumen(diagrama.getId(), diagrama.getNombre(),
                diagrama.getTipo(), diagrama.getVersion());
    }

    public static DiagramaCompleto completo(Diagrama diagrama,
                                            List<ClaseUml> clases,
                                            List<RelacionUml> relaciones,
                                            List<BloqueoElemento> bloqueos) {
        return new DiagramaCompleto(
                diagrama.getId(), diagrama.getNombre(), diagrama.getTipo(), diagrama.getVersion(),
                clases.stream().map(Vistas::de).toList(),
                relaciones.stream().map(Vistas::de).toList(),
                bloqueos.stream().map(Vistas::de).toList());
    }

    public static ClaseVista de(ClaseUml clase) {
        return new ClaseVista(clase.getId(), clase.getNombre(), clase.getEstereotipo(),
                clase.isEsAbstracta(), clase.getPosX(), clase.getPosY(),
                clase.getAncho(), clase.getAlto(),
                clase.getAtributos().stream().map(Vistas::de).toList(),
                clase.getMetodos().stream().map(Vistas::de).toList());
    }

    public static AtributoVista de(AtributoUml atributo) {
        return new AtributoVista(atributo.getId(), atributo.getNombre(), atributo.getTipo(),
                atributo.getVisibilidad(), atributo.isEsIdentificador(), atributo.isEsRequerido(),
                atributo.isEsUnico(), atributo.getLongitud(), atributo.getOrden());
    }

    public static MetodoVista de(MetodoUml metodo) {
        return new MetodoVista(metodo.getId(), metodo.getNombre(), metodo.getTipoRetorno(),
                metodo.getVisibilidad(), metodo.isEsAbstracto(), metodo.isEsEstatico(),
                metodo.getOrden());
    }

    public static RelacionVista de(RelacionUml relacion) {
        return new RelacionVista(relacion.getId(),
                relacion.getOrigen().getId(), relacion.getDestino().getId(),
                relacion.getTipo(), relacion.getMultiplicidadOrigen(),
                relacion.getMultiplicidadDestino(), relacion.getRolOrigen(),
                relacion.getRolDestino(), relacion.getEtiqueta());
    }

    public static BloqueoVista de(BloqueoElemento bloqueo) {
        return new BloqueoVista(bloqueo.getElementoTipo(), bloqueo.getElementoId(),
                bloqueo.getUsuario().getId(), bloqueo.getUsuario().getNombre(),
                bloqueo.getExpiraEn());
    }
}
