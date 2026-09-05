package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.Diagrama;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramaRepositorio extends JpaRepository<Diagrama, UUID> {

    List<Diagrama> findByProyectoIdOrderByNombre(UUID proyectoId);

    /**
     * Carga el diagrama tomando un bloqueo pesimista de escritura sobre su
     * fila. Se usa al registrar una operacion: serializa la asignacion del
     * numero de secuencia, de modo que dos cambios simultaneos no puedan
     * obtener la misma posicion en la bitacora.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Diagrama d WHERE d.id = :id")
    Optional<Diagrama> buscarParaActualizar(@Param("id") UUID id);
}
