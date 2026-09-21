package bo.forja.backend.web;

import bo.forja.backend.foto.Lectura;
import bo.forja.backend.foto.ServicioFoto;
import bo.forja.backend.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
 * Hay un paso mas antes de esos dos: la <b>transcripcion</b>, que es la unica
 * que recibe la imagen. Existe porque el reconocimiento de caracteres lee texto
 * y en un diagrama dibujado las relaciones son flechas; quien las entiende es
 * un modelo de vision, y corre en el servidor porque la clave es de pago y en
 * el navegador quedaria a la vista. Devuelve texto y nada mas: de ahi en
 * adelante el camino es el mismo que el de una pizarra escrita a mano.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/foto")
public class ControladorFoto {

    private final ServicioFoto foto;

    public ControladorFoto(ServicioFoto foto) {
        this.foto = foto;
    }

    /**
     * Paso cero: de la imagen al texto. No toca el modelo.
     * <p>
     * Se responde 503 y no un texto vacio cuando la funcion no esta
     * configurada: son dos situaciones distintas -no esta disponible, o no
     * entendio nada- y la pantalla dice cosas distintas en cada una.
     */
    @PostMapping("/transcripcion")
    public Transcripcion transcribir(@AuthenticationPrincipal Jwt token,
                                     @PathVariable UUID diagramaId,
                                     @RequestParam("imagen") MultipartFile imagen) throws IOException {
        if (!foto.lecturaPorIaDisponible()) {
            throw new LecturaPorIaNoDisponible();
        }
        String tipo = imagen.getContentType() == null ? "image/png" : imagen.getContentType();
        return new Transcripcion(
                foto.transcribir(diagramaId, UsuarioActual.id(token), imagen.getBytes(), tipo));
    }

    /** Para no ofrecer un boton que no va a contestar. */
    @GetMapping("/disponible")
    public Disponibilidad disponible() {
        return new Disponibilidad(foto.lecturaPorIaDisponible());
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

    public record Transcripcion(String texto) {
    }

    public record Disponibilidad(boolean disponible) {
    }

    /** La lectura por IA no esta configurada en este servidor. */
    @org.springframework.web.bind.annotation.ResponseStatus(
            value = org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            reason = "La lectura de la foto por IA no esta configurada en este servidor")
    static class LecturaPorIaNoDisponible extends RuntimeException {
    }

    public record TextoDePizarra(
            @NotBlank @Size(max = 20000) String texto,
            @NotBlank @Size(max = 80) String sesionId,
            String tokenLectura) {
    }
}
