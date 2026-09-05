package bo.forja.backend.servicio;

import bo.forja.backend.dominio.TipoElemento;

import java.time.Instant;
import java.util.UUID;

/**
 * Desenlace de una solicitud de bloqueo.
 * <p>
 * Cuando la solicitud es rechazada se informa quien retiene el elemento:
 * el cliente no solo necesita saber que no puede editar, sino poder
 * mostrar en el lienzo el nombre de quien lo esta editando.
 */
public record ResultadoBloqueo(
        Estado estado,
        TipoElemento elementoTipo,
        UUID elementoId,
        UUID poseedorId,
        String poseedorNombre,
        Instant expiraEn) {

    public enum Estado {
        /** El solicitante tomo el bloqueo. */
        CONCEDIDO,
        /** El solicitante ya lo tenia y se extendio su vigencia. */
        RENOVADO,
        /** Otro usuario retiene el elemento. */
        RECHAZADO
    }

    public boolean puedeEditar() {
        return estado != Estado.RECHAZADO;
    }
}
