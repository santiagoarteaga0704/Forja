package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.Operacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.Collection;

public interface OperacionRepositorio extends JpaRepository<Operacion, UUID> {

    /**
     * Delta que necesita un cliente que estuvo sin conexion: todo lo
     * ocurrido despues de la ultima secuencia que alcanzo a conocer.
     */
    List<Operacion> findByDiagramaIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(
            UUID diagramaId, long desdeSecuencia);

    /** Permite resolver el reenvio de una operacion ya registrada. */
    Optional<Operacion> findByDiagramaIdAndTokenCliente(UUID diagramaId, String tokenCliente);

    @Query("SELECT COALESCE(MAX(o.secuencia), 0) FROM Operacion o WHERE o.diagrama.id = :diagramaId")
    long ultimaSecuencia(@Param("diagramaId") UUID diagramaId);

    /** Recuento de operaciones por canal de entrada, para el agente guia. */
    @Query("""
            SELECT new bo.forja.backend.repositorio.ConteoPorOrigen(o.origen, COUNT(o))
            FROM Operacion o
            WHERE o.diagrama.id = :diagramaId
            GROUP BY o.origen
            """)
    List<ConteoPorOrigen> contarPorOrigen(@Param("diagramaId") UUID diagramaId);

    /**
     * Lo mismo, pero sobre todos los proyectos en los que participa una persona.
     * <p>
     * Es lo que permite al agente guia saber que alguien nunca dicto <b>nada</b>,
     * y no solo que no dicto en el diagrama que tiene abierto. Aprender a usar la
     * herramienta se aprende una vez, no una vez por diagrama.
     */
    @Query("""
            SELECT new bo.forja.backend.repositorio.ConteoPorOrigen(o.origen, COUNT(o))
            FROM Operacion o
            WHERE o.diagrama.proyecto.id IN :proyectoIds
            GROUP BY o.origen
            """)
    List<ConteoPorOrigen> contarPorOrigenEnProyectos(
            @Param("proyectoIds") Collection<UUID> proyectoIds);
}
