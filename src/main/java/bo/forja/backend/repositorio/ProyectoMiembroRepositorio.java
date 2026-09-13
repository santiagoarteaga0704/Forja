package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProyectoMiembroRepositorio
        extends JpaRepository<ProyectoMiembro, ProyectoMiembroId> {

    Optional<ProyectoMiembro> findByProyectoIdAndUsuarioId(UUID proyectoId, UUID usuarioId);

    List<ProyectoMiembro> findByProyectoId(UUID proyectoId);

    long countByProyectoId(UUID proyectoId);
}
