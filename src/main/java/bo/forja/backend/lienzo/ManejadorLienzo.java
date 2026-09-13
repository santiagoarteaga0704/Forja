package bo.forja.backend.lienzo;

import bo.forja.backend.servicio.ServicioBloqueo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * Canal por el que cada participante recibe los cambios del diagrama.
 * <p>
 * La responsabilidad que justifica su existencia esta en el cierre. Los
 * bloqueos tienen vencimiento, de modo que un cliente que desaparece no
 * deja elementos retenidos para siempre; pero esperar ese vencimiento
 * significaria que, al cerrar alguien su pestana, el resto no puede tocar
 * lo que esa persona estaba editando hasta que pase el plazo. Al enterarse
 * del cierre, el canal libera de inmediato todo lo que la sesion retenia y
 * avisa a los demas.
 * <p>
 * Los mensajes entrantes se limitan a mantener vivo el canal: los cambios
 * del modelo no entran por aqui sino por HTTP, donde el reenvio es
 * idempotente.
 */
@Component
public class ManejadorLienzo extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorLienzo.class);

    private final RegistroDeSesiones registro;
    private final ServicioBloqueo bloqueos;

    public ManejadorLienzo(RegistroDeSesiones registro, ServicioBloqueo bloqueos) {
        this.registro = registro;
        this.bloqueos = bloqueos;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) {
        UUID diagramaId = Atributos.diagramaId(sesion);
        registro.registrar(diagramaId, sesion);
        log.debug("{} se conecto al diagrama {}", Atributos.usuarioNombre(sesion), diagramaId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) {
        UUID diagramaId = Atributos.diagramaId(sesion);
        String sesionId = Atributos.sesionId(sesion);
        registro.quitar(diagramaId, sesion);

        int liberados = bloqueos.liberarSesion(sesionId);
        if (liberados > 0) {
            // Solo se avisa si habia algo retenido: un cierre sin bloqueos no
            // obliga a nadie a refrescar su vista.
            registro.difundir(diagramaId, EventoLienzo.sesionCerrada(sesionId, liberados), sesionId);
        }
        log.debug("{} se desconecto del diagrama {} ({} bloqueos liberados)",
                Atributos.usuarioNombre(sesion), diagramaId, liberados);
    }

    /**
     * Responde al latido del cliente. Sirve para que los intermediarios no
     * cierren por inactividad un canal que solo esta esperando cambios.
     */
    @Override
    protected void handleTextMessage(WebSocketSession sesion, TextMessage mensaje) throws Exception {
        if ("ping".equals(mensaje.getPayload())) {
            sesion.sendMessage(new TextMessage("pong"));
        }
    }
}
