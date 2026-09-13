package bo.forja.backend.servicio;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Operacion;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.operacion.AplicadorComando;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.Elemento;
import bo.forja.backend.operacion.TipoOperacion;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registro y reproduccion de los cambios sobre un diagrama compartido.
 * <p>
 * Es el nucleo del trabajo colaborativo: toda modificacion entra por
 * aqui, sin importar si la origino el lienzo, un dictado de voz, una
 * fotografia o una importacion XMI. El servicio se ocupa de cuatro cosas
 * que deben ocurrir juntas o no ocurrir: comprobar el permiso, respetar
 * la exclusion mutua, aplicar el cambio y anotarlo en la bitacora con un
 * numero de secuencia.
 * <p>
 * El orden de los pasos esta elegido, no es casual:
 * <ol>
 *   <li>Se toma un bloqueo pesimista sobre la fila del diagrama. Es lo que
 *       serializa la asignacion del numero de secuencia; sin el, dos
 *       cambios simultaneos podrian reclamar la misma posicion en la
 *       bitacora y la restriccion de unicidad abortaria uno de los dos.</li>
 *   <li>Se resuelve la idempotencia antes de tocar el modelo. Un reenvio
 *       no debe volver a aplicar nada, y averiguarlo primero evita el
 *       trabajo inutil.</li>
 *   <li>Recien entonces se compite por el bloqueo del elemento y se
 *       aplica el comando.</li>
 * </ol>
 * Todo sucede dentro de una sola transaccion: si el comando resulta
 * invalido, ni el bloqueo ni la operacion quedan registrados.
 */
@Service
public class ServicioOperaciones {

    private static final Logger log = LoggerFactory.getLogger(ServicioOperaciones.class);

    private final DiagramaRepositorio diagramas;
    private final OperacionRepositorio operaciones;
    private final ProyectoMiembroRepositorio miembros;
    private final UsuarioRepositorio usuarios;
    private final ServicioBloqueo bloqueos;
    private final AplicadorComando aplicador;
    private final ObjectMapper json;

    public ServicioOperaciones(DiagramaRepositorio diagramas,
                               OperacionRepositorio operaciones,
                               ProyectoMiembroRepositorio miembros,
                               UsuarioRepositorio usuarios,
                               ServicioBloqueo bloqueos,
                               AplicadorComando aplicador,
                               ObjectMapper json) {
        this.diagramas = diagramas;
        this.operaciones = operaciones;
        this.miembros = miembros;
        this.usuarios = usuarios;
        this.bloqueos = bloqueos;
        this.aplicador = aplicador;
        this.json = json;
    }

    /**
     * Registra un cambio sobre el diagrama.
     *
     * @param sesionId     canal desde el que se emite; identifica al
     *                     poseedor del bloqueo junto con el usuario
     * @param tokenCliente identificador que genera el cliente antes de
     *                     encolar la operacion; hace idempotente el reenvio
     */
    @Transactional
    public ResultadoOperacion registrar(UUID diagramaId,
                                        UUID usuarioId,
                                        String sesionId,
                                        ComandoOperacion comando,
                                        OrigenOperacion origen,
                                        String tokenCliente) {

        Diagrama diagrama = diagramas.buscarParaActualizar(diagramaId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el diagrama " + diagramaId));

        exigirPermisoDeEdicion(diagrama, usuarioId);

        Optional<Operacion> yaRegistrada =
                operaciones.findByDiagramaIdAndTokenCliente(diagramaId, tokenCliente);
        if (yaRegistrada.isPresent()) {
            Operacion previa = yaRegistrada.get();
            log.debug("Reenvio de la operacion {} (token {}): se responde la ya registrada",
                    previa.getId(), tokenCliente);
            return new ResultadoOperacion(ResultadoOperacion.Estado.DUPLICADA,
                    previa.getId(), previa.getSecuencia(), diagrama.getVersion(),
                    TipoOperacion.valueOf(previa.getTipo()), previa.getCreadaEn(), null);
        }

        // Exclusion mutua. Se pide el bloqueo en lugar de solo verificarlo:
        // el cliente interactivo ya lo retiene y solo lo renueva, mientras
        // que el cliente que reproduce una cola offline lo toma ahora.
        Optional<Elemento> afectado = comando.elementoAfectado();
        Elemento tomadoAlPaso = null;

        if (afectado.isPresent()) {
            Elemento elemento = afectado.get();
            ResultadoBloqueo bloqueo = bloqueos.adquirir(
                    diagramaId, elemento.tipo(), elemento.id(), usuarioId, sesionId);
            if (!bloqueo.puedeEditar()) {
                log.debug("Operacion rechazada: {} {} lo retiene {}",
                        elemento.tipo(), elemento.id(), bloqueo.poseedorNombre());
                return ResultadoOperacion.rechazoPorBloqueo(bloqueo);
            }
            // Distinguir CONCEDIDO de RENOVADO es lo que separa las dos
            // situaciones: si el bloqueo ya era suyo, la persona sigue
            // editando y hay que dejarselo; si se acaba de tomar para poder
            // aplicar este cambio, nadie lo esta editando y retenerlo dejaria
            // el elemento ocupado hasta que venciera, sin motivo.
            if (bloqueo.estado() == ResultadoBloqueo.Estado.CONCEDIDO) {
                tomadoAlPaso = elemento;
            }
        }

        aplicador.aplicar(diagrama, comando);

        long secuencia = diagrama.getVersion() + 1;
        TipoOperacion tipo = TipoOperacion.de(comando);

        Operacion registro = new Operacion();
        registro.setDiagrama(diagrama);
        registro.setSecuencia(secuencia);
        registro.setUsuario(usuarios.getReferenceById(usuarioId));
        registro.setTipo(tipo.name());
        registro.setCarga(serializar(comando));
        registro.setTokenCliente(tokenCliente);
        registro.setOrigen(origen != null ? origen : OrigenOperacion.LIENZO);
        operaciones.save(registro);

        // El contador lo escribe la base de datos, no la deteccion de
        // cambios: ver el comentario de fijarVersion en el repositorio.
        diagramas.fijarVersion(diagramaId, secuencia, registro.getCreadaEn());

        if (tomadoAlPaso != null) {
            bloqueos.liberar(tomadoAlPaso.tipo(), tomadoAlPaso.id(), usuarioId, sesionId);
        }

        return new ResultadoOperacion(ResultadoOperacion.Estado.APLICADA,
                registro.getId(), secuencia, secuencia, tipo, registro.getCreadaEn(), null);
    }

    /**
     * Delta que necesita un cliente para ponerse al dia: los comandos
     * posteriores a la version que ya conoce, en el orden en que fueron
     * aceptados. Reproducirlos en ese orden reconstruye el mismo modelo,
     * que es lo que permite sincronizar sin descargar el diagrama entero.
     */
    @Transactional(readOnly = true)
    public List<OperacionRegistrada> delta(UUID diagramaId, UUID usuarioId, long desdeSecuencia) {
        Diagrama diagrama = diagramas.findById(diagramaId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el diagrama " + diagramaId));

        // Para leer alcanza con ser miembro: un lector tambien sincroniza.
        exigirMembresia(diagrama, usuarioId);

        return operaciones
                .findByDiagramaIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(diagramaId, desdeSecuencia)
                .stream()
                .map(this::reconstruir)
                .toList();
    }

    /** Version actual del diagrama; el cliente la usa para pedir su delta. */
    @Transactional(readOnly = true)
    public long versionActual(UUID diagramaId) {
        return diagramas.findById(diagramaId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el diagrama " + diagramaId))
                .getVersion();
    }

    // ---------- Autorizacion ---------------------------------------------

    private ProyectoMiembro exigirMembresia(Diagrama diagrama, UUID usuarioId) {
        return miembros
                .findByProyectoIdAndUsuarioId(diagrama.getProyecto().getId(), usuarioId)
                .orElseThrow(() -> new AccesoDenegado(
                        "El usuario no es miembro del proyecto que contiene el diagrama"));
    }

    private void exigirPermisoDeEdicion(Diagrama diagrama, UUID usuarioId) {
        ProyectoMiembro miembro = exigirMembresia(diagrama, usuarioId);
        if (!miembro.getRol().puedeEditar()) {
            throw new AccesoDenegado(
                    "El rol " + miembro.getRol() + " no permite modificar el diagrama");
        }
    }

    // ---------- Serializacion de la carga --------------------------------

    private String serializar(ComandoOperacion comando) {
        try {
            return json.writeValueAsString(comando);
        } catch (JacksonException e) {
            // Los comandos son records de tipos simples: si esto falla, el
            // defecto esta en el codigo y no en el dato recibido.
            throw new IllegalStateException("No se pudo serializar el comando "
                    + comando.getClass().getSimpleName(), e);
        }
    }

    private OperacionRegistrada reconstruir(Operacion registro) {
        TipoOperacion tipo = TipoOperacion.valueOf(registro.getTipo());
        try {
            ComandoOperacion comando = json.readValue(registro.getCarga(), tipo.claseComando());
            return new OperacionRegistrada(registro.getId(), registro.getSecuencia(), tipo,
                    comando, registro.getOrigen(), registro.getUsuario().getId(),
                    registro.getCreadaEn());
        } catch (JacksonException e) {
            throw new IllegalStateException("La bitacora tiene una carga ilegible en la operacion "
                    + registro.getId(), e);
        }
    }
}
