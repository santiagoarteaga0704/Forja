package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.ClaseUml;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaseUmlRepositorio extends JpaRepository<ClaseUml, UUID> {

    List<ClaseUml> findByDiagramaId(UUID diagramaId);

    Optional<ClaseUml> findByDiagramaIdAndNombreIgnoreCase(UUID diagramaId, String nombre);

    /**
     * Carga las clases con sus atributos y metodos en una sola consulta.
     * Es la entrada del generador de codigo, que necesita el modelo
     * completo y no puede permitirse una consulta por clase.
     */
    @EntityGraph(attributePaths = {"atributos", "metodos"})
    List<ClaseUml> findByDiagramaIdOrderByNombre(UUID diagramaId);
}
