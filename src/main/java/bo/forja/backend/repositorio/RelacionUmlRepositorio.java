package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.RelacionUml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RelacionUmlRepositorio extends JpaRepository<RelacionUml, UUID> {

    List<RelacionUml> findByDiagramaId(UUID diagramaId);

    List<RelacionUml> findByOrigenIdOrDestinoId(UUID origenId, UUID destinoId);

    /**
     * Relaciones con sus dos extremos ya cargados. Origen y destino son
     * asociaciones simples, no colecciones, asi que traerlas juntas no
     * choca con el limite de un solo fetch por coleccion; y al hacerlo se
     * evita una consulta por extremo al proyectar el diagrama.
     */
    @Query("""
            SELECT r FROM RelacionUml r
            JOIN FETCH r.origen
            JOIN FETCH r.destino
            WHERE r.diagrama.id = :diagramaId
            """)
    List<RelacionUml> buscarConExtremos(@Param("diagramaId") UUID diagramaId);
}
