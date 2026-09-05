package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProyectoRepositorio extends JpaRepository<Proyecto, UUID> {

    /** Proyectos en los que el usuario participa, sea o no su propietario. */
    @Query("""
            SELECT DISTINCT p FROM Proyecto p
            LEFT JOIN p.miembros m
            WHERE p.propietario.id = :usuarioId OR m.usuario.id = :usuarioId
            ORDER BY p.actualizadoEn DESC
            """)
    List<Proyecto> buscarPorParticipante(@Param("usuarioId") UUID usuarioId);
}
