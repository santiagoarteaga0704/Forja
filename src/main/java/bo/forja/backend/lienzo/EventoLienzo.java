package bo.forja.backend.lienzo;

import bo.forja.backend.servicio.OperacionRegistrada;

import java.util.Map;
import java.util.UUID;

/**
 * Aviso que el servidor empuja a los demas participantes de un diagrama.
 * <p>
 * Los comandos no viajan por este canal: se envian por HTTP, que es
 * reintentable e idempotente, y el WebSocket solo se usa para notificar lo
 * que ya fue aceptado. La separacion es deliberada. Un cliente que perdio
 * la conexion necesita reenviar su cola de cambios con garantia de no
 * duplicarlos, y eso lo da el token de idempotencia sobre HTTP, no un
 * socket que se corta a mitad de un mensaje.
 */
public record EventoLienzo(String evento, Object contenido) {

    public static EventoLienzo operacion(OperacionRegistrada operacion) {
        return new EventoLienzo("OPERACION", operacion);
    }

    public static EventoLienzo bloqueoTomado(String elementoTipo, UUID elementoId,
                                             UUID poseedorId, String poseedorNombre) {
        return new EventoLienzo("BLOQUEO_TOMADO", Map.of(
                "elementoTipo", elementoTipo,
                "elementoId", elementoId,
                "poseedorId", poseedorId,
                "poseedorNombre", poseedorNombre));
    }

    public static EventoLienzo bloqueoLiberado(String elementoTipo, UUID elementoId) {
        return new EventoLienzo("BLOQUEO_LIBERADO", Map.of(
                "elementoTipo", elementoTipo,
                "elementoId", elementoId));
    }

    /**
     * Al cerrarse un canal se avisa cuantos bloqueos solto, para que el resto
     * vuelva a pedir el estado en lugar de quedarse con elementos que ya
     * estan libres pero siguen dibujados como ocupados.
     */
    public static EventoLienzo sesionCerrada(String sesionId, int bloqueosLiberados) {
        return new EventoLienzo("SESION_CERRADA", Map.of(
                "sesionId", sesionId,
                "bloqueosLiberados", bloqueosLiberados));
    }
}
