package bo.forja.backend.web;

import bo.forja.backend.seguridad.UsuarioActual;
import bo.forja.backend.voz.Interpretacion;
import bo.forja.backend.voz.ServicioVoz;
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
 * Dictado por voz.
 * <p>
 * El reconocimiento de voz ocurre en el cliente -en el navegador, con la API
 * del propio navegador; en el movil, con el reconocedor de Android- y lo que
 * llega aqui es texto. La division no es arbitraria: pasar audio al servidor
 * obligaria a subirlo y a montar un reconocedor propio, cuando los dos
 * clientes ya tienen uno que funciona sin conexion.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/voz")
public class ControladorVoz {

    private final ServicioVoz voz;

    public ControladorVoz(ServicioVoz voz) {
        this.voz = voz;
    }

    /** Interpreta la frase y aplica lo que entendio. */
    @PostMapping
    public ServicioVoz.ResultadoDictado dictar(@AuthenticationPrincipal Jwt token,
                                               @PathVariable UUID diagramaId,
                                               @Valid @RequestBody Dictado dictado) {
        return voz.dictar(diagramaId, UsuarioActual.id(token), dictado.sesionId(), dictado.frase());
    }

    /**
     * Interpreta sin aplicar. Permite mostrar "entendi: agregar nombre a
     * Paciente" y esperar la confirmacion, que es lo que conviene cuando el
     * reconocimiento de voz no es confiable.
     */
    @PostMapping("/interpretacion")
    public Interpretacion interpretar(@AuthenticationPrincipal Jwt token,
                                      @PathVariable UUID diagramaId,
                                      @Valid @RequestBody Dictado dictado) {
        return voz.interpretarSinAplicar(diagramaId, UsuarioActual.id(token), dictado.frase());
    }

    public record Dictado(
            @NotBlank @Size(max = 400) String frase,
            @NotBlank @Size(max = 80) String sesionId) {
    }
}
