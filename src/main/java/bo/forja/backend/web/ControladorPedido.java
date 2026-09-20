package bo.forja.backend.web;

import bo.forja.backend.pedido.Pedido;
import bo.forja.backend.pedido.ServicioPedido;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pedir un diagrama en una frase.
 * <p>
 * Dos peticiones y no una, igual que la lectura de una pizarra: primero se
 * devuelve lo que el modelo propuso y solo despues -cuando la persona lo vio- se
 * aplica. Lo que propone un modelo de 4B no se acepta a ciegas.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/pedido")
public class ControladorPedido {

    private final ServicioPedido pedidos;

    public ControladorPedido(ServicioPedido pedidos) {
        this.pedidos = pedidos;
    }

    /**
     * Si hay un modelo con que contestar.
     * <p>
     * El cliente lo consulta para no ofrecer un boton que va a responder que no
     * hay traductor. Sin esto habria que instalar Ollama solo para que la
     * interfaz deje de mentir.
     */
    @GetMapping("/disponible")
    public Disponibilidad disponible(@AuthenticationPrincipal Jwt token,
                                     @PathVariable UUID diagramaId) {
        return new Disponibilidad(pedidos.disponible());
    }

    /** Primer paso: que se propuso, sin tocar el modelo. */
    @PostMapping("/lectura")
    public Pedido leer(@AuthenticationPrincipal Jwt token,
                       @PathVariable UUID diagramaId,
                       @Valid @RequestBody TextoDelPedido cuerpo) {
        return pedidos.leer(diagramaId, UsuarioActual.id(token), cuerpo.pedido(),
                cuerpo.tokenLectura());
    }

    /** Segundo paso: aplicar lo propuesto al diagrama. */
    @PostMapping
    public ServicioPedido.ResultadoPedido aplicar(@AuthenticationPrincipal Jwt token,
                                                  @PathVariable UUID diagramaId,
                                                  @Valid @RequestBody TextoDelPedido cuerpo) {
        return pedidos.aplicar(diagramaId, UsuarioActual.id(token), cuerpo.sesionId(),
                cuerpo.pedido(), cuerpo.tokenLectura());
    }

    public record TextoDelPedido(
            @NotBlank @Size(max = 500) String pedido,
            @NotBlank @Size(max = 80) String sesionId,
            String tokenLectura) {
    }

    public record Disponibilidad(boolean hayModelo) {
    }
}
