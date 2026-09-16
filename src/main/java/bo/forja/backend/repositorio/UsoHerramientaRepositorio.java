package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.UsoHerramienta;
import bo.forja.backend.dominio.UsoHerramientaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UsoHerramientaRepositorio extends JpaRepository<UsoHerramienta, UsoHerramientaId> {

    /**
     * Anota un uso mas, creando la fila si es la primera vez.
     * <p>
     * Va como sentencia nativa con {@code ON CONFLICT} y no como leer-modificar-
     * guardar por la misma razon que los bloqueos: dos pestanas del mismo usuario
     * exportando a la vez chocarian contra la clave primaria, y el arbitraje lo
     * resuelve Postgres en una sola sentencia sin marcar la transaccion para
     * reversion.
     */
    @Modifying
    @Query(value = """
            INSERT INTO uso_herramienta (usuario_id, herramienta, veces, primera_vez, ultima_vez)
            VALUES (:usuarioId, :herramienta, 1, now(), now())
            ON CONFLICT (usuario_id, herramienta)
            DO UPDATE SET veces = uso_herramienta.veces + 1, ultima_vez = now()
            """, nativeQuery = true)
    void anotar(@Param("usuarioId") UUID usuarioId, @Param("herramienta") String herramienta);

    @Query("select u from UsoHerramienta u where u.id.usuarioId = :usuarioId")
    List<UsoHerramienta> deUsuario(@Param("usuarioId") UUID usuarioId);
}
