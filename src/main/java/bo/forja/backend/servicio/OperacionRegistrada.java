package bo.forja.backend.servicio;

import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.TipoOperacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Operacion de la bitacora ya reconstruida como comando.
 * <p>
 * Es lo que consume el cliente que vuelve de estar sin conexion: en lugar
 * de descargar el diagrama completo, recibe la lista ordenada de comandos
 * posteriores a la version que conocia y los reproduce contra su copia
 * local. El comando viaja reconstruido y no como texto JSON crudo, de
 * modo que la misma estructura que el cliente emite es la que recibe.
 */
public record OperacionRegistrada(
        UUID operacionId,
        long secuencia,
        TipoOperacion tipo,
        ComandoOperacion comando,
        OrigenOperacion origen,
        UUID autorId,
        Instant creadaEn) {
}
