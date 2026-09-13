package bo.forja.backend.lienzo;

import bo.forja.backend.servicio.AccesoDenegado;
import bo.forja.backend.servicio.RecursoNoEncontrado;
import bo.forja.backend.servicio.ServicioProyectos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Autentica y autoriza la apertura del canal colaborativo.
 * <p>
 * El token viaja en la cadena de consulta y no en una cabecera porque el
 * WebSocket del navegador no permite enviar cabeceras al abrir la
 * conexion. Es una limitacion del estandar, no una eleccion: la
 * contrapartida es que el token queda escrito en los registros del
 * servidor, y por eso su vigencia es corta y el canal se sirve solo sobre
 * TLS en el despliegue.
 * <p>
 * Se rechaza aqui, antes de aceptar la conexion, todo lo que no deba
 * pasar: token invalido, diagrama inexistente o usuario que no pertenece
 * al proyecto. Un canal abierto es un canal que recibe todos los cambios
 * del diagrama, asi que no puede quedar sujeto a comprobaciones
 * posteriores.
 */
@Component
public class ApretonDeManos implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApretonDeManos.class);

    private final JwtDecoder tokens;
    private final ServicioProyectos proyectos;

    public ApretonDeManos(JwtDecoder tokens, ServicioProyectos proyectos) {
        this.tokens = tokens;
        this.proyectos = proyectos;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                                   WebSocketHandler manejador, Map<String, Object> atributos) {

        Map<String, String> parametros = parametros(peticion);
        String token = parametros.get("token");
        String diagrama = parametros.get("diagrama");
        String sesion = parametros.get("sesion");

        if (token == null || diagrama == null || sesion == null) {
            log.debug("Apreton de manos rechazado: faltan token, diagrama o sesion");
            return false;
        }

        try {
            Jwt verificado = tokens.decode(token);
            UUID usuarioId = UUID.fromString(verificado.getSubject());
            UUID diagramaId = UUID.fromString(diagrama);

            // Comprueba de una vez que el diagrama existe y que el usuario
            // pertenece a su proyecto.
            proyectos.diagramaAccesible(diagramaId, usuarioId);

            Atributos.poner(atributos, usuarioId,
                    verificado.getClaimAsString("nombre"), diagramaId, sesion);
            return true;

        } catch (JwtException e) {
            log.debug("Apreton de manos rechazado: token invalido");
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("Apreton de manos rechazado: identificador mal formado");
            return false;
        } catch (AccesoDenegado | RecursoNoEncontrado e) {
            log.debug("Apreton de manos rechazado: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest peticion, ServerHttpResponse respuesta,
                               WebSocketHandler manejador, Exception excepcion) {
        // Nada que hacer: el registro del canal ocurre al establecerse.
    }

    private static Map<String, String> parametros(ServerHttpRequest peticion) {
        return UriComponentsBuilder.fromUri(peticion.getURI())
                .build()
                .getQueryParams()
                .toSingleValueMap();
    }
}
