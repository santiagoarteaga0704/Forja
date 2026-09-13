package bo.forja.backend.lienzo;

import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

/**
 * Datos que el apreton de manos deja adheridos al canal.
 * <p>
 * Se resuelven una sola vez, al abrir la conexion, y quedan disponibles
 * durante toda su vida. La alternativa -confiar en lo que el cliente
 * escriba en cada mensaje- permitiria que un canal ya autenticado actuara
 * en nombre de otro usuario.
 */
public final class Atributos {

    static final String USUARIO_ID = "forja.usuarioId";
    static final String USUARIO_NOMBRE = "forja.usuarioNombre";
    static final String DIAGRAMA_ID = "forja.diagramaId";
    static final String SESION_ID = "forja.sesionId";

    private Atributos() {
    }

    static void poner(Map<String, Object> destino, UUID usuarioId, String usuarioNombre,
                      UUID diagramaId, String sesionId) {
        destino.put(USUARIO_ID, usuarioId);
        destino.put(USUARIO_NOMBRE, usuarioNombre);
        destino.put(DIAGRAMA_ID, diagramaId);
        destino.put(SESION_ID, sesionId);
    }

    public static UUID usuarioId(WebSocketSession sesion) {
        return (UUID) sesion.getAttributes().get(USUARIO_ID);
    }

    public static String usuarioNombre(WebSocketSession sesion) {
        return (String) sesion.getAttributes().get(USUARIO_NOMBRE);
    }

    public static UUID diagramaId(WebSocketSession sesion) {
        return (UUID) sesion.getAttributes().get(DIAGRAMA_ID);
    }

    public static String sesionId(WebSocketSession sesion) {
        return (String) sesion.getAttributes().get(SESION_ID);
    }
}
