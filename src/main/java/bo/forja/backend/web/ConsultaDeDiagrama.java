package bo.forja.backend.web;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.repositorio.BloqueoElementoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.servicio.ServicioModelo;
import bo.forja.backend.servicio.ServicioProyectos;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Arma la fotografia completa de un diagrama para el cliente.
 * <p>
 * Vive del lado de la web y no entre los servicios de dominio porque su
 * producto es una representacion de salida, no una regla de negocio. Lo
 * que si aporta es la <b>frontera transaccional</b>: la conversion recorre
 * asociaciones que solo pueden leerse con la sesion abierta, y hacerlo en
 * el controlador terminaria en un fallo de inicializacion perezosa.
 * <p>
 * Las tres consultas ocurren dentro de la misma transaccion, de modo que
 * el modelo y los bloqueos que se devuelven corresponden al mismo instante.
 */
@Component
public class ConsultaDeDiagrama {

    private final ServicioProyectos proyectos;
    private final ServicioModelo modelo;
    private final RelacionUmlRepositorio relaciones;
    private final BloqueoElementoRepositorio bloqueos;

    public ConsultaDeDiagrama(ServicioProyectos proyectos,
                              ServicioModelo modelo,
                              RelacionUmlRepositorio relaciones,
                              BloqueoElementoRepositorio bloqueos) {
        this.proyectos = proyectos;
        this.modelo = modelo;
        this.relaciones = relaciones;
        this.bloqueos = bloqueos;
    }

    @Transactional(readOnly = true)
    public Vistas.DiagramaCompleto completo(UUID diagramaId, UUID usuarioId) {
        Diagrama diagrama = proyectos.diagramaAccesible(diagramaId, usuarioId);
        return Vistas.completo(diagrama,
                modelo.clasesCompletas(diagramaId),
                relaciones.buscarConExtremos(diagramaId),
                bloqueos.buscarConPoseedor(diagramaId));
    }
}
