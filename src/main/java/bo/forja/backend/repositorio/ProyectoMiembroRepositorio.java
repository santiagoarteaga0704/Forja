package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

public interface ProyectoMiembroRepositorio
        extends JpaRepository<ProyectoMiembro, ProyectoMiembroId> {

    Optional<ProyectoMiembro> findByProyectoIdAndUsuarioId(UUID proyectoId, UUID usuarioId);

    List<ProyectoMiembro> findByProyectoId(UUID proyectoId);

    long countByProyectoId(UUID proyectoId);

    /**
     * A cuanta gente invito esta persona a sus propios proyectos.
     * <p>
     * Se excluye a si misma: el propietario figura como miembro de su proyecto, y
     * sin descontarlo el agente creeria que todo el mundo ya colabora con alguien.
     */
    @Query("""
            SELECT COUNT(m) FROM ProyectoMiembro m
            WHERE m.proyecto.propietario.id = :usuarioId AND m.usuario.id <> :usuarioId
            """)
    long contarInvitadosPor(@Param("usuarioId") UUID usuarioId);
}
