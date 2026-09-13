package bo.forja.backend.web;

import bo.forja.backend.foto.Lectura;
import bo.forja.backend.foto.ServicioFoto;
import bo.forja.backend.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Construccion del modelo desde la foto de una pizarra.
 * <p>
 * Se reciben dos peticiones y no una porque el flujo tiene dos pasos: primero
 * se lee el texto y se devuelve lo que se entendio, y solo despues -cuando la
 * persona confirmo o corrigio- se aplica. Un unico paso obligaria a aceptar a
 * ciegas lo que el reconocimiento de caracteres haya entendido de una letra
 * manuscrita.
 * <p>
 * Lo que llega es texto, no la imagen: el reconocimiento ocurre en el cliente,
 * donde puede funcionar sin conexion.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/foto")
public class ControladorFoto {

    private final ServicioFoto foto;

    public ControladorFoto(ServicioFoto foto) {
        this.foto = foto;
    }

    /** Primer paso: que se entendio, sin tocar el modelo. */
    @PostMapping("/lectura")
    public Lectura leer(@AuthenticationPrincipal Jwt token,
                        @PathVariable UUID diagramaId,
                        @Valid @RequestBody TextoDePizarra cuerpo) {
        return foto.leer(diagramaId, UsuarioActual.id(token), cuerpo.texto());
    }

    /** Segundo paso: aplicar lo leido al diagrama. */
    @PostMapping
    public ServicioFoto.ResultadoFoto aplicar(@AuthenticationPrincipal Jwt token,
                                              @PathVariable UUID diagramaId,
                                              @Valid @RequestBody TextoDePizarra cuerpo) {
        return foto.aplicar(diagramaId, UsuarioActual.id(token), cuerpo.sesionId(),
                cuerpo.texto(), cuerpo.tokenLectura());
    }

    public record TextoDePizarra(
            @NotBlank @Size(max = 20000) String texto,
            @NotBlank @Size(max = 80) String sesionId,
            String tokenLectura) {
    }
}
