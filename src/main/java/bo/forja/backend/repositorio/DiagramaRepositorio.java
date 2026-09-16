package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.Diagrama;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.Collection;

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

    /**
     * Fija la version del diagrama con una sentencia explicita en lugar de
     * confiar en la deteccion de cambios de Hibernate.
     * <p>
     * La distincion no es cosmetica. La misma fila llega al contexto de
     * persistencia por dos caminos -la consulta con bloqueo pesimista y la
     * navegacion {@code clase.getDiagrama()}-, y modificar la instancia
     * equivocada deja el cambio sin emitir: la operacion se registraria con
     * un numero de secuencia que la version del diagrama no acompana, y la
     * siguiente operacion reclamaria esa misma posicion. Al escribirla como
     * sentencia, el contador queda donde debe estar, en la base de datos.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Diagrama d SET d.version = :version, d.actualizadoEn = :momento WHERE d.id = :id")
    int fijarVersion(@Param("id") UUID id,
                     @Param("version") long version,
                     @Param("momento") Instant momento);

    /** Cuantos diagramas hay en un conjunto de proyectos. Lo usa el agente guia. */
    @Query("SELECT COUNT(d) FROM Diagrama d WHERE d.proyecto.id IN :proyectoIds")
    long contarEnProyectos(@Param("proyectoIds") Collection<UUID> proyectoIds);
}
