package bo.forja.backend.servicio;

import bo.forja.backend.operacion.TipoOperacion;

import java.time.Instant;
import java.util.UUID;

/**
 * Desenlace del registro de una operacion.
 * <p>
 * Distingue tres finales que el cliente debe tratar de manera distinta.
 * {@code APLICADA} avanza la version del diagrama y se difunde al resto.
 * {@code DUPLICADA} es la respuesta al reenvio de algo ya registrado: no
 * es un error, es la garantia de idempotencia funcionando, y el cliente
 * debe darla por buena y descartar la copia que tenia encolada.
 * {@code RECHAZADA_POR_BLOQUEO} significa que otro usuario retiene el
 * elemento; el cambio no se aplico y el cliente debe revertir su vista
 * optimista e informar quien lo esta editando.
 */
public record ResultadoOperacion(
        Estado estado,
        UUID operacionId,
        long secuencia,
        long versionDiagrama,
        TipoOperacion tipo,
        Instant creadaEn,
        ResultadoBloqueo bloqueo) {

    public enum Estado {
        APLICADA,
        DUPLICADA,
        RECHAZADA_POR_BLOQUEO
    }

    public boolean fueAceptada() {
        return estado != Estado.RECHAZADA_POR_BLOQUEO;
    }

    /** Se difunde por WebSocket solo lo que efectivamente cambio el modelo. */
    public boolean debeDifundirse() {
        return estado == Estado.APLICADA;
    }

    static ResultadoOperacion rechazoPorBloqueo(ResultadoBloqueo bloqueo) {
        return new ResultadoOperacion(Estado.RECHAZADA_POR_BLOQUEO,
                null, 0L, 0L, null, null, bloqueo);
    }
}
