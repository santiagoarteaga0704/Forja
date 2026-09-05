package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.BloqueoElemento;
import bo.forja.backend.dominio.TipoElemento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BloqueoElementoRepositorio extends JpaRepository<BloqueoElemento, UUID> {

    /**
     * Intenta tomar el bloqueo de un elemento en una unica sentencia
     * atomica.
     * <p>
     * La clausula {@code ON CONFLICT DO NOTHING} se apoya en la
     * restriccion de unicidad {@code uq_bloqueo_elemento}: si otra
     * transaccion ya posee el elemento, la insercion no ocurre y el
     * metodo devuelve 0 en lugar de lanzar una excepcion. Esto evita dos
     * problemas de la alternativa basada en capturar la violacion de
     * integridad: la transaccion en curso no queda marcada para
     * reversion, y no hace falta abrir una transaccion anidada para
     * recuperarse del choque.
     *
     * @return 1 si el bloqueo fue concedido, 0 si el elemento ya estaba tomado
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO bloqueo_elemento
                (id, diagrama_id, elemento_tipo, elemento_id,
                 usuario_id, sesion_id, adquirido_en, expira_en)
            VALUES
                (:id, :diagramaId, :elementoTipo, :elementoId,
                 :usuarioId, :sesionId, :momento, :expiraEn)
            ON CONFLICT (elemento_tipo, elemento_id) DO NOTHING
            """, nativeQuery = true)
    int intentarAdquirir(@Param("id") UUID id,
                         @Param("diagramaId") UUID diagramaId,
                         @Param("elementoTipo") String elementoTipo,
                         @Param("elementoId") UUID elementoId,
                         @Param("usuarioId") UUID usuarioId,
                         @Param("sesionId") String sesionId,
                         @Param("momento") Instant momento,
                         @Param("expiraEn") Instant expiraEn);

    /**
     * Extiende la vigencia de un bloqueo que el solicitante ya posee.
     * Exigir coincidencia de usuario y de sesion impide que otro cliente
     * prolongue un bloqueo ajeno.
     *
     * @return 1 si la renovacion se aplico, 0 si el solicitante no era el poseedor
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE BloqueoElemento b
               SET b.expiraEn = :expiraEn
             WHERE b.elementoTipo = :elementoTipo
               AND b.elementoId = :elementoId
               AND b.usuario.id = :usuarioId
               AND b.sesionId = :sesionId
            """)
    int renovar(@Param("elementoTipo") TipoElemento elementoTipo,
                @Param("elementoId") UUID elementoId,
                @Param("usuarioId") UUID usuarioId,
                @Param("sesionId") String sesionId,
                @Param("expiraEn") Instant expiraEn);

    /**
     * Descarta el bloqueo de un elemento si ya vencio. Se ejecuta justo
     * antes de intentar adquirirlo, para que un cliente caido no deje el
     * elemento retenido de forma indefinida.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM BloqueoElemento b
             WHERE b.elementoTipo = :elementoTipo
               AND b.elementoId = :elementoId
               AND b.expiraEn < :momento
            """)
    int purgarSiVencio(@Param("elementoTipo") TipoElemento elementoTipo,
                       @Param("elementoId") UUID elementoId,
                       @Param("momento") Instant momento);

    /** Barrido periodico de bloqueos vencidos en todo el sistema. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM BloqueoElemento b WHERE b.expiraEn < :momento")
    int purgarVencidos(@Param("momento") Instant momento);

    /** Libera un bloqueo, verificando que quien lo pide sea su poseedor. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM BloqueoElemento b
             WHERE b.elementoTipo = :elementoTipo
               AND b.elementoId = :elementoId
               AND b.usuario.id = :usuarioId
               AND b.sesionId = :sesionId
            """)
    int liberar(@Param("elementoTipo") TipoElemento elementoTipo,
                @Param("elementoId") UUID elementoId,
                @Param("usuarioId") UUID usuarioId,
                @Param("sesionId") String sesionId);

    /**
     * Libera todo lo que retenia una sesion. Se invoca al cerrarse el
     * canal WebSocket, para no esperar al vencimiento.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM BloqueoElemento b WHERE b.sesionId = :sesionId")
    int liberarPorSesion(@Param("sesionId") String sesionId);

    Optional<BloqueoElemento> findByElementoTipoAndElementoId(
            TipoElemento elementoTipo, UUID elementoId);

    List<BloqueoElemento> findByDiagramaId(UUID diagramaId);
}
