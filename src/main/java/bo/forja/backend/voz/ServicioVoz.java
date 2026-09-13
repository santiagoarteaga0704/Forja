package bo.forja.backend.voz;

import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.Cuadricula;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.servicio.ResultadoOperacion;
import bo.forja.backend.servicio.ServicioOperaciones;
import bo.forja.backend.servicio.ServicioProyectos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dictado de cambios sobre el diagrama.
 * <p>
 * El parser decide que quiso decir la persona; este servicio lo aplica por la
 * misma puerta que todo lo demas. La consecuencia es la que interesa para la
 * evaluacion: lo dictado queda en la bitacora con origen {@code VOZ}, de modo
 * que se puede demostrar con datos cuanta parte del modelo se construyo
 * hablando, y respeta los bloqueos de quien este editando en ese momento.
 * <p>
 * La posicion de una clase nueva no la decide el parser: se la asigna
 * {@link Cuadricula}, que es la misma regla que usa la lectura de una pizarra.
 */
@Service
public class ServicioVoz {

    private static final Logger log = LoggerFactory.getLogger(ServicioVoz.class);

    private final ServicioProyectos proyectos;
    private final ServicioOperaciones operaciones;
    private final ClaseUmlRepositorio clases;
    private final ParserVoz parser;

    public ServicioVoz(ServicioProyectos proyectos,
                       ServicioOperaciones operaciones,
                       ClaseUmlRepositorio clases,
                       ParserVoz parser) {
        this.proyectos = proyectos;
        this.operaciones = operaciones;
        this.clases = clases;
        this.parser = parser;
    }

    public ResultadoDictado dictar(UUID diagramaId, UUID usuarioId, String sesionId, String frase) {
        proyectos.diagramaAccesible(diagramaId, usuarioId);

        ContextoDelDiagrama contexto = contextoDe(diagramaId);
        Interpretacion interpretacion = parser.interpretar(frase, contexto);

        if (!interpretacion.entendida()) {
            log.debug("Dictado no reconocido en el diagrama {}: \"{}\"", diagramaId, frase);
            return ResultadoDictado.noEntendido(interpretacion);
        }

        String token = "voz-" + UUID.randomUUID();
        int siguienteCasilla = contexto.porNombre().size();
        int aplicadas = 0;
        List<String> problemas = new ArrayList<>();
        ResultadoOperacion ultimoRechazo = null;

        for (int i = 0; i < interpretacion.pasos().size(); i++) {
            Interpretacion.Paso paso = interpretacion.pasos().get(i);
            ComandoOperacion comando = Cuadricula.ubicar(paso.comando(), siguienteCasilla);
            if (comando instanceof ComandoOperacion.CrearClase) {
                siguienteCasilla++;
            }

            try {
                ResultadoOperacion resultado = operaciones.registrar(diagramaId, usuarioId, sesionId,
                        comando, OrigenOperacion.VOZ, token + "-" + i);

                if (resultado.estado() == ResultadoOperacion.Estado.RECHAZADA_POR_BLOQUEO) {
                    ultimoRechazo = resultado;
                } else {
                    aplicadas++;
                }
            } catch (ComandoInvalido e) {
                problemas.add(e.getMessage());
            }
        }

        long version = operaciones.versionActual(diagramaId);
        log.info("Dictado en el diagrama {}: \"{}\" -> {} de {} comandos aplicados",
                diagramaId, frase, aplicadas, interpretacion.pasos().size());

        return new ResultadoDictado(true, interpretacion.explicacion(), List.of(),
                interpretacion.pasos().size(), aplicadas, problemas,
                ultimoRechazo == null ? null : nombreDelPoseedor(ultimoRechazo), version);
    }

    /** Interpreta sin aplicar: sirve para mostrar que se entendio antes de hacerlo. */
    @Transactional(readOnly = true)
    public Interpretacion interpretarSinAplicar(UUID diagramaId, UUID usuarioId, String frase) {
        proyectos.diagramaAccesible(diagramaId, usuarioId);
        return parser.interpretar(frase, contextoDe(diagramaId));
    }

    @Transactional(readOnly = true)
    ContextoDelDiagrama contextoDe(UUID diagramaId) {
        List<ContextoDelDiagrama.ClaseConocida> conocidas = clases.findByDiagramaId(diagramaId).stream()
                .map(clase -> new ContextoDelDiagrama.ClaseConocida(clase.getId(), clase.getNombre()))
                .toList();
        return ContextoDelDiagrama.de(conocidas);
    }

    private String nombreDelPoseedor(ResultadoOperacion rechazo) {
        return rechazo.bloqueo() == null ? "otro usuario" : rechazo.bloqueo().poseedorNombre();
    }

    /**
     * Que se entendio y que se hizo.
     *
     * @param retenidoPor nombre de quien tenia tomado el elemento, si el dictado
     *                    se topo con un bloqueo ajeno
     */
    public record ResultadoDictado(
            boolean entendida,
            String explicacion,
            List<String> sugerencias,
            int comandosLeidos,
            int aplicadas,
            List<String> problemas,
            String retenidoPor,
            long versionDelDiagrama) {

        static ResultadoDictado noEntendido(Interpretacion interpretacion) {
            return new ResultadoDictado(false, interpretacion.explicacion(),
                    interpretacion.sugerencias(), 0, 0, List.of(), null, 0);
        }
    }
}
