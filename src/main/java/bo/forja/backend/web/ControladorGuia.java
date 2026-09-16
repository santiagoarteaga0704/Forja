package bo.forja.backend.web;

import bo.forja.backend.agente.Consejo;
import bo.forja.backend.agente.Guia;
import bo.forja.backend.agente.Preguntas;
import bo.forja.backend.agente.ServicioAgente;
import bo.forja.backend.seguridad.UsuarioActual;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * El agente guia, disponible en toda la aplicacion.
 * <p>
 * El endpoint anterior colgaba de un diagrama
 * ({@code /api/diagramas/{id}/agente}) y por eso el agente no existia fuera del
 * lienzo. El enunciado pide uno que monitoree la aplicacion y ensene a usarla, y
 * quien mas lo necesita es justamente quien todavia no creo ningun diagrama, asi
 * que el diagrama pasa a ser un parametro opcional: con el, el agente ademas
 * revisa el modelo; sin el, habla de la herramienta.
 * <p>
 * Aquel endpoint se conserva porque sigue siendo la consulta correcta cuando lo
 * unico que interesa es el diagrama, y porque ya tiene sus pruebas.
 */
@RestController
@RequestMapping("/api/guia")
public class ControladorGuia {

    private final ServicioAgente agente;

    public ControladorGuia(ServicioAgente agente) {
        this.agente = agente;
    }

    /**
     * @param diagramaId  el diagrama abierto, si hay alguno
     * @param descartados avisos que la persona ya cerro. Los recuerda el cliente
     *                    y no el servidor: cerrar un aviso es una preferencia de
     *                    quien mira, no un hecho que deba ocupar una tabla
     */
    @GetMapping
    public Guia guia(@AuthenticationPrincipal Jwt token,
                     @RequestParam(required = false) UUID diagramaId,
                     @RequestParam(required = false) List<String> descartados) {
        return agente.guia(UsuarioActual.id(token), diagramaId,
                descartados == null ? Set.of() : Set.copyOf(descartados));
    }

    /**
     * Una pregunta escrita, respondida desde la base de conocimiento.
     * <p>
     * <b>No valida la entrada y es a proposito.</b> Rechazar un texto vacio o
     * larguisimo devolveria un 400, y un error es justo lo que no puede pasar
     * mientras alguien prueba la herramienta delante de un aula: el agente
     * quedaria mudo en el peor momento. Cualquier texto entra, se recorta si
     * hace falta, y siempre sale una respuesta.
     */
    @PostMapping("/pregunta")
    public List<Consejo> preguntar(@AuthenticationPrincipal Jwt token,
                                   @RequestBody(required = false) Pregunta pregunta) {
        String texto = pregunta == null ? null : pregunta.texto();
        String sobre = pregunta == null ? null : pregunta.sobre();
        return agente.responder(UsuarioActual.id(token), texto, sobre);
    }

    /** Las preguntas que el agente sabe responder, para ofrecerlas. */
    @GetMapping("/temas")
    public List<Preguntas.Tema> temas() {
        return agente.temas();
    }

    /**
     * @param sobre identificador de la ultima respuesta, si la hubo. Deja que
     *              una repregunta corta -"¿y eso?", "no entendi"- vuelva sobre
     *              ese tema en lugar de caer en "no la se contestar"
     */
    public record Pregunta(String texto, String sobre) {
    }
}
