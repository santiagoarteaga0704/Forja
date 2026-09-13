package bo.forja.backend.lienzo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Publica el canal colaborativo.
 * <p>
 * Se usa WebSocket a secas en lugar de STOMP porque los dos clientes son
 * de tecnologias distintas -un navegador y una aplicacion Flutter- y
 * ambos hablan WebSocket de forma nativa. STOMP aportaria un enrutamiento
 * por destinos que aqui no hace falta: el destino es siempre el diagrama
 * que se indico al abrir la conexion, y a cambio obligaria al cliente
 * movil a incorporar una biblioteca del protocolo.
 */
@Configuration
@EnableWebSocket
public class ConfiguracionWebSocket implements WebSocketConfigurer {

    private final ManejadorLienzo manejador;
    private final ApretonDeManos apreton;
    private final String[] origenesPermitidos;

    public ConfiguracionWebSocket(ManejadorLienzo manejador,
                                  ApretonDeManos apreton,
                                  @Value("${forja.web.origenes:http://localhost:5173}")
                                  String[] origenesPermitidos) {
        this.manejador = manejador;
        this.apreton = apreton;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registro) {
        registro.addHandler(manejador, "/ws/diagramas")
                .addInterceptors(apreton)
                // El cliente movil no envia cabecera Origin; el navegador si, y
                // se limita a los origenes declarados para que una pagina ajena
                // no pueda abrir el canal con un token robado.
                .setAllowedOrigins(origenesPermitidos);
    }
}
