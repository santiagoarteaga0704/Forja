package bo.forja.backend.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Extrae del token la identidad del solicitante.
 * <p>
 * Los controladores nunca reciben el identificador del usuario como
 * parametro de la peticion: lo toman del token verificado. Es la
 * diferencia entre autenticar y confiar en lo que el cliente afirma ser.
 */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static UUID id(Jwt token) {
        return UUID.fromString(token.getSubject());
    }

    public static String nombre(Jwt token) {
        return token.getClaimAsString("nombre");
    }
}
