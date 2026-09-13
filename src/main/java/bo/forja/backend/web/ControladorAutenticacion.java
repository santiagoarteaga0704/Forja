package bo.forja.backend.web;

import bo.forja.backend.seguridad.Credencial;
import bo.forja.backend.seguridad.ServicioAutenticacion;
import bo.forja.backend.seguridad.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Alta de cuentas e inicio de sesion. */
@RestController
@RequestMapping("/api/auth")
public class ControladorAutenticacion {

    private final ServicioAutenticacion autenticacion;

    public ControladorAutenticacion(ServicioAutenticacion autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/registro")
    public ResponseEntity<Credencial> registrar(@Valid @RequestBody SolicitudRegistro solicitud) {
        Credencial credencial = autenticacion.registrar(
                solicitud.email(), solicitud.nombre(), solicitud.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(credencial);
    }

    @PostMapping("/sesion")
    public Credencial iniciarSesion(@Valid @RequestBody SolicitudSesion solicitud) {
        return autenticacion.iniciarSesion(solicitud.email(), solicitud.password());
    }

    /** Permite al cliente confirmar que su token guardado sigue vigente. */
    @GetMapping("/yo")
    public IdentidadActual yo(@AuthenticationPrincipal Jwt token) {
        return new IdentidadActual(UsuarioActual.id(token), UsuarioActual.nombre(token),
                token.getClaimAsString("email"));
    }

    public record SolicitudRegistro(
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(max = 120) String nombre,
            // Ocho caracteres es el minimo por debajo del cual una contrasena
            // deja de resistir una prueba exhaustiva en tiempo razonable.
            @NotBlank @Size(min = 8, max = 100) String password) {

        /**
         * Se recortan los espacios al construir el registro, no en el
         * servicio. El orden importa: la validacion corre sobre el objeto ya
         * construido, y un correo con un espacio al final -lo que deja el
         * teclado de un telefono al autocompletar- seria rechazado antes de
         * que nadie tuviera oportunidad de limpiarlo.
         */
        public SolicitudRegistro {
            email = recortar(email);
            nombre = recortar(nombre);
        }
    }

    public record SolicitudSesion(
            @NotBlank @Email String email,
            @NotBlank String password) {

        public SolicitudSesion {
            email = recortar(email);
        }
    }

    private static String recortar(String texto) {
        return texto == null ? null : texto.trim();
    }

    public record IdentidadActual(UUID usuarioId, String nombre, String email) {
    }
}
