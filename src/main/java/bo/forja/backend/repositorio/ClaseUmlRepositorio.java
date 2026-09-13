package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.ClaseUml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaseUmlRepositorio extends JpaRepository<ClaseUml, UUID> {

    List<ClaseUml> findByDiagramaId(UUID diagramaId);

    Optional<ClaseUml> findByDiagramaIdAndNombreIgnoreCase(UUID diagramaId, String nombre);

    /**
     * Clases del diagrama con sus atributos ya cargados.
     * <p>
     * Los atributos y los metodos NO pueden traerse en la misma consulta:
     * ambos son colecciones ordenadas y JPA no permite combinar dos de
     * ellas en un mismo fetch, porque el producto cartesiano haria
     * ambiguo a que fila corresponde cada elemento. Por eso el modelo
     * completo se arma en dos pasos; ver {@code ServicioModelo}.
     */
    @Query("""
            SELECT DISTINCT c FROM ClaseUml c
            LEFT JOIN FETCH c.atributos
            WHERE c.diagrama.id = :diagramaId
            ORDER BY c.nombre
            """)
    List<ClaseUml> buscarConAtributos(@Param("diagramaId") UUID diagramaId);

    /** Complemento del anterior: carga los metodos de las mismas clases. */
    @Query("""
            SELECT DISTINCT c FROM ClaseUml c
            LEFT JOIN FETCH c.metodos
            WHERE c.diagrama.id = :diagramaId
            ORDER BY c.nombre
            """)
    List<ClaseUml> buscarConMetodos(@Param("diagramaId") UUID diagramaId);
}
