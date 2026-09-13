package bo.forja.backend.lienzo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canales abiertos sobre cada diagrama.
 * <p>
 * Este registro es <b>local a la instancia</b> y eso es una limitacion
 * conocida, no un descuido: si el backend se desplegara replicado, dos
 * usuarios conectados a instancias distintas no se verian. La exclusion
 * mutua, en cambio, seguiria siendo correcta, porque la arbitra la base de
 * datos y no la memoria del proceso. Es decir que el peor efecto de
 * escalar sin resolver esto seria un lienzo que tarda en refrescarse, no
 * dos personas editando el mismo elemento a la vez. La solucion, si
 * hiciera falta, es publicar los avisos por un canal comun -LISTEN/NOTIFY
 * de Postgres alcanza- sin tocar el resto del diseno.
 * <p>
 * El despliegue previsto es una sola instancia en EC2, asi que la
 * limitacion no se manifiesta.
 */
@Component
public class RegistroDeSesiones {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeSesiones.class);

    /** Un conjunto concurrente por diagrama: se escribe al conectar y desconectar. */
    private final Map<UUID, Set<WebSocketSession>> canales = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public RegistroDeSesiones(ObjectMapper json) {
        this.json = json;
    }

    public void registrar(UUID diagramaId, WebSocketSession sesion) {
        canales.computeIfAbsent(diagramaId, id -> ConcurrentHashMap.newKeySet()).add(sesion);
        log.debug("Canal abierto sobre el diagrama {}: {} conectados", diagramaId, conectados(diagramaId));
    }

    public void quitar(UUID diagramaId, WebSocketSession sesion) {
        Set<WebSocketSession> abiertos = canales.get(diagramaId);
        if (abiertos == null) {
            return;
        }
        abiertos.remove(sesion);
        // Se retira la entrada vacia para que el mapa no crezca con el tiempo
        // acumulando diagramas que ya nadie mira.
        if (abiertos.isEmpty()) {
            canales.remove(diagramaId, abiertos);
        }
    }

    public int conectados(UUID diagramaId) {
        Set<WebSocketSession> abiertos = canales.get(diagramaId);
        return abiertos == null ? 0 : abiertos.size();
    }

    /**
     * Empuja el aviso a todos los canales del diagrama menos al que lo
     * origino: quien hizo el cambio ya conoce el resultado por la respuesta
     * HTTP, y reenviarselo lo obligaria a distinguir su propio eco.
     */
    public void difundir(UUID diagramaId, EventoLienzo evento, String sesionOrigen) {
        Set<WebSocketSession> abiertos = canales.get(diagramaId);
        if (abiertos == null || abiertos.isEmpty()) {
            return;
        }

        String cuerpo = json.writeValueAsString(evento);
        for (WebSocketSession sesion : abiertos) {
            if (sesionOrigen != null && sesionOrigen.equals(Atributos.sesionId(sesion))) {
                continue;
            }
            enviar(sesion, cuerpo);
        }
    }

    /**
     * Un canal que ya no acepta mensajes no debe interrumpir la difusion al
     * resto: se anota y se sigue. El cierre lo resolvera el propio manejador
     * cuando el contenedor lo notifique.
     */
    private void enviar(WebSocketSession sesion, String cuerpo) {
        try {
            if (sesion.isOpen()) {
                sesion.sendMessage(new TextMessage(cuerpo));
            }
        } catch (IOException e) {
            log.debug("No se pudo escribir en el canal {}: {}", sesion.getId(), e.getMessage());
        }
    }
}
