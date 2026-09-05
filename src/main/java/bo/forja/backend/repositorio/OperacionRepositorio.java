package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.Operacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
