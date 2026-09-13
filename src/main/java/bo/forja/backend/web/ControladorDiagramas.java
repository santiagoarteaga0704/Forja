package bo.forja.backend.web;

import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.lienzo.EventoLienzo;
import bo.forja.backend.lienzo.RegistroDeSesiones;
import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.TipoOperacion;
import bo.forja.backend.seguridad.UsuarioActual;
import bo.forja.backend.servicio.OperacionRegistrada;
import bo.forja.backend.servicio.ResultadoBloqueo;
import bo.forja.backend.servicio.ResultadoOperacion;
import bo.forja.backend.servicio.ServicioBloqueo;
import bo.forja.backend.servicio.ServicioOperaciones;
import bo.forja.backend.servicio.ServicioProyectos;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lectura del diagrama, registro de cambios y bloqueos de edicion.
 * <p>
 * Los cambios entran por HTTP y no por el WebSocket a proposito: el envio
 * lleva un token de idempotencia y puede reintentarse sin miedo a
 * duplicarse, que es exactamente lo que necesita un cliente que estuvo sin
 * conexion y reproduce su cola. El canal solo difunde lo ya aceptado.
 */
@RestController
@RequestMapping("/api/diagramas")
public class ControladorDiagramas {

    private final ConsultaDeDiagrama consulta;
    private final ServicioOperaciones operaciones;
    private final ServicioBloqueo bloqueos;
    private final ServicioProyectos proyectos;
    private final RegistroDeSesiones canales;
    private final ObjectMapper json;
    private final Validator validador;

    public ControladorDiagramas(ConsultaDeDiagrama consulta,
                                ServicioOperaciones operaciones,
                                ServicioBloqueo bloqueos,
                                ServicioProyectos proyectos,
                                RegistroDeSesiones canales,
                                ObjectMapper json,
                                Validator validador) {
        this.consulta = consulta;
        this.operaciones = operaciones;
        this.bloqueos = bloqueos;
        this.proyectos = proyectos;
        this.canales = canales;
        this.json = json;
        this.validador = validador;
    }

    /** Fotografia completa del diagrama: modelo y bloqueos vigentes. */
    @GetMapping("/{diagramaId}")
    public Vistas.DiagramaCompleto ver(@AuthenticationPrincipal Jwt token,
                                       @PathVariable UUID diagramaId) {
        return consulta.completo(diagramaId, UsuarioActual.id(token));
    }

    /**
     * Registra un cambio y lo difunde a los demas participantes.
     * <p>
     * Solo se difunde lo que efectivamente modifico el modelo. Un reenvio
     * duplicado o un rechazo por bloqueo se responden al autor pero no se
     * anuncian: el resto no vio ningun cambio que deba reflejar.
     */
    @PostMapping("/{diagramaId}/operaciones")
    public ResultadoOperacion registrar(@AuthenticationPrincipal Jwt token,
                                        @PathVariable UUID diagramaId,
                                        @Valid @RequestBody EnvioDeOperacion envio) {

        UUID autorId = UsuarioActual.id(token);
        ComandoOperacion comando = interpretar(envio.tipo(), envio.comando());

        ResultadoOperacion resultado = operaciones.registrar(diagramaId, autorId,
                envio.sesionId(), comando, envio.origen(), envio.tokenCliente());

        if (resultado.debeDifundirse()) {
            OperacionRegistrada aviso = new OperacionRegistrada(resultado.operacionId(),
                    resultado.secuencia(), resultado.tipo(), comando,
                    envio.origen() != null ? envio.origen() : OrigenOperacion.LIENZO,
                    autorId, resultado.creadaEn());
            canales.difundir(diagramaId, EventoLienzo.operacion(aviso), envio.sesionId());
        }
        return resultado;
    }

    /**
     * Cambios posteriores a la version que el cliente conoce. Es la via por
     * la que se pone al dia quien estuvo sin conexion.
     */
    @GetMapping("/{diagramaId}/operaciones")
    public List<OperacionRegistrada> delta(@AuthenticationPrincipal Jwt token,
                                           @PathVariable UUID diagramaId,
                                           @RequestParam(defaultValue = "0") long desde) {
        return operaciones.delta(diagramaId, UsuarioActual.id(token), desde);
    }

    /** Toma el elemento para editarlo, o informa quien lo retiene. */
    @PostMapping("/{diagramaId}/bloqueos")
    public ResultadoBloqueo bloquear(@AuthenticationPrincipal Jwt token,
                                     @PathVariable UUID diagramaId,
                                     @Valid @RequestBody SolicitudBloqueo solicitud) {

        UUID usuarioId = UsuarioActual.id(token);
        // Comprueba la pertenencia al proyecto antes de competir por el
        // elemento: el servicio de bloqueos da por hecha la autorizacion.
        exigirPermisoDeEdicion(diagramaId, usuarioId);

        ResultadoBloqueo resultado = bloqueos.adquirir(diagramaId, solicitud.elementoTipo(),
                solicitud.elementoId(), usuarioId, solicitud.sesionId());

        if (resultado.estado() == ResultadoBloqueo.Estado.CONCEDIDO) {
            canales.difundir(diagramaId, EventoLienzo.bloqueoTomado(
                            solicitud.elementoTipo().name(), solicitud.elementoId(),
                            usuarioId, UsuarioActual.nombre(token)),
                    solicitud.sesionId());
        }
        return resultado;
    }

    /** Suelta el elemento al terminar de editarlo. */
    @DeleteMapping("/{diagramaId}/bloqueos")
    public ResponseEntity<Void> liberar(@AuthenticationPrincipal Jwt token,
                                        @PathVariable UUID diagramaId,
                                        @RequestParam TipoElemento elementoTipo,
                                        @RequestParam UUID elementoId,
                                        @RequestParam String sesionId) {

        UUID usuarioId = UsuarioActual.id(token);
        exigirPermisoDeEdicion(diagramaId, usuarioId);

        boolean liberado = bloqueos.liberar(elementoTipo, elementoId, usuarioId, sesionId);
        if (liberado) {
            canales.difundir(diagramaId,
                    EventoLienzo.bloqueoLiberado(elementoTipo.name(), elementoId), sesionId);
            return ResponseEntity.noContent().build();
        }
        // No era suyo o ya no estaba: en ninguno de los dos casos queda
        // retenido, asi que el cliente puede seguir como si lo hubiera soltado.
        return ResponseEntity.noContent().build();
    }

    // ---------- Interpretacion del comando --------------------------------

    /**
     * Convierte la carga recibida en el comando que corresponde a su tipo.
     * <p>
     * El tipo viaja como campo propio y no incrustado en el JSON del
     * comando, igual que se guarda en la bitacora. Asi el mismo catalogo
     * {@link TipoOperacion} resuelve la lectura de la peticion y la de la
     * bitacora, sin dos mecanismos que puedan divergir.
     */
    private ComandoOperacion interpretar(TipoOperacion tipo, JsonNode carga) {
        ComandoOperacion comando;
        try {
            comando = json.treeToValue(carga, tipo.claseComando());
        } catch (RuntimeException e) {
            throw new ComandoInvalido("La carga no corresponde a un comando de tipo " + tipo);
        }

        Set<ConstraintViolation<ComandoOperacion>> fallas = validador.validate(comando);
        if (!fallas.isEmpty()) {
            String detalle = fallas.stream()
                    .map(f -> f.getPropertyPath() + " " + f.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new ComandoInvalido("El comando " + tipo + " tiene campos invalidos: " + detalle);
        }
        return comando;
    }

    private void exigirPermisoDeEdicion(UUID diagramaId, UUID usuarioId) {
        if (!proyectos.rolEnDiagrama(diagramaId, usuarioId).puedeEditar()) {
            throw new bo.forja.backend.servicio.AccesoDenegado(
                    "El rol asignado no permite modificar el diagrama");
        }
    }

    // ---------- Cuerpos de peticion ---------------------------------------

    public record EnvioDeOperacion(
            @NotNull TipoOperacion tipo,
            @NotNull JsonNode comando,
            OrigenOperacion origen,
            /* Identifica el canal que emite: junto con el usuario determina
             * quien posee el bloqueo del elemento. */
            @NotBlank @Size(max = 80) String sesionId,
            /* Generado por el cliente antes de encolar: hace idempotente el
             * reenvio de una operacion que quedo sin respuesta. */
            @NotBlank @Size(max = 80) String tokenCliente) {
    }

    public record SolicitudBloqueo(
            @NotNull TipoElemento elementoTipo,
            @NotNull UUID elementoId,
            @NotBlank @Size(max = 80) String sesionId) {
    }
}
