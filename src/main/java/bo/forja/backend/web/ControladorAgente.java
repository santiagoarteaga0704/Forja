package bo.forja.backend.web;

import bo.forja.backend.agente.Consejo;
import bo.forja.backend.agente.ServicioAgente;
import bo.forja.backend.seguridad.UsuarioActual;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consejos del agente guia sobre el diagrama.
 * <p>
 * Los consejos descartados los recuerda el cliente y los manda en cada consulta,
 * en lugar de guardarse en el servidor. Es deliberado: cerrar un aviso es una
 * preferencia de quien lo esta mirando en ese momento, no un hecho del modelo, y
 * no tiene por que ocupar una tabla ni afectar a los demas miembros del proyecto.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/agente")
public class ControladorAgente {

    private final ServicioAgente agente;

    public ControladorAgente(ServicioAgente agente) {
        this.agente = agente;
    }

    @GetMapping
    public List<Consejo> consejos(@AuthenticationPrincipal Jwt token,
                                 @PathVariable UUID diagramaId,
                                 @RequestParam(required = false) List<String> descartados) {
        return agente.consejos(diagramaId, UsuarioActual.id(token),
                descartados == null ? Set.of() : Set.copyOf(descartados));
    }
}
