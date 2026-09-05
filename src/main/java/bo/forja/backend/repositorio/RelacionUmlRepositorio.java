package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.RelacionUml;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RelacionUmlRepositorio extends JpaRepository<RelacionUml, UUID> {

    List<RelacionUml> findByDiagramaId(UUID diagramaId);

    List<RelacionUml> findByOrigenIdOrDestinoId(UUID origenId, UUID destinoId);
}
