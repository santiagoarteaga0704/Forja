package bo.forja.backend.seguridad;

import java.time.Instant;
import java.util.UUID;

/**
 * Token emitido junto con los datos del usuario que identifica.
 * <p>
 * Se devuelven el nombre y el correo ademas del token para que el cliente
 * no tenga que decodificarlo solo para saber a quien saludar en la
 * pantalla. El identificador lo necesita el lienzo, que marca los
 * elementos bloqueados por uno mismo distinto de los bloqueados por otros.
 */
public record Credencial(
        String token,
        Instant expiraEn,
        UUID usuarioId,
        String nombre,
        String email) {
}
