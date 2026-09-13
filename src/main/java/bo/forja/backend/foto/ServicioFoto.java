package bo.forja.backend.foto;

import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.Cuadricula;
import bo.forja.backend.operacion.TipoOperacion;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.servicio.ResultadoOperacion;
import bo.forja.backend.servicio.ServicioOperaciones;
import bo.forja.backend.servicio.ServicioProyectos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Construccion del modelo a partir de la foto de una pizarra.
 * <p>
 * El servicio recibe el <b>texto</b> reconocido, no la imagen. El
 * reconocimiento de caracteres ocurre en el cliente: en el navegador con las
 * herramientas del navegador, y en el telefono con el reconocedor del sistema,
 * que funciona sin conexion. Subir la imagen al servidor obligaria a montar un
 * reconocedor propio con sus dependencias nativas, y a tener conexion para algo
 * que el enunciado pide que funcione sin ella.
 * <p>
 * Lo que no se delega es la interpretacion. El texto se convierte en comandos
 * aqui, en un solo lugar, y se aplica por el registro de operaciones: queda en
 * la bitacora con origen {@code FOTO} -evidencia de cuanto del modelo salio de
 * una pizarra- y respeta el bloqueo de quien este editando.
 * <p>
 * La lectura se puede pedir <b>sin aplicar</b>, y en la practica es lo que
 * conviene hacer siempre: el reconocimiento sobre letra manuscrita nunca es
 * exacto, y revisar el texto antes de tocar el modelo es lo que separa una
 * funcion demostrable de una que depende de la suerte.
 */
@Service
public class ServicioFoto {

    private static final Logger log = LoggerFactory.getLogger(ServicioFoto.class);

    private final ServicioProyectos proyectos;
    private final ServicioOperaciones operaciones;
    private final ClaseUmlRepositorio clases;
    private final RelacionUmlRepositorio relaciones;
    private final ParserPizarra parser;

    public ServicioFoto(ServicioProyectos proyectos,
                        ServicioOperaciones operaciones,
                        ClaseUmlRepositorio clases,
                        RelacionUmlRepositorio relaciones,
                        ParserPizarra parser) {
        this.proyectos = proyectos;
        this.operaciones = operaciones;
        this.clases = clases;
        this.relaciones = relaciones;
        this.parser = parser;
    }

    /** Interpreta el texto sin tocar el modelo, para poder revisarlo antes. */
    @Transactional(readOnly = true)
    public Lectura leer(UUID diagramaId, UUID usuarioId, String texto) {
        proyectos.diagramaAccesible(diagramaId, usuarioId);
        return parser.interpretar(texto, contextoDe(diagramaId));
    }

    /**
     * Interpreta y aplica.
     *
     * @param tokenLectura identificador de este envio; si el cliente lo repite
     *                     tras un corte, los comandos se reconocen como ya
     *                     registrados en lugar de duplicar el modelo
     */
    public ResultadoFoto aplicar(UUID diagramaId, UUID usuarioId, String sesionId,
                                 String texto, String tokenLectura) {

        proyectos.diagramaAccesible(diagramaId, usuarioId);
        ContextoDelDiagrama contexto = contextoDe(diagramaId);
        Lectura lectura = parser.interpretar(texto, contexto);

        if (!lectura.seEntendioAlgo()) {
            log.debug("Lectura de pizarra sin resultado en el diagrama {}", diagramaId);
            return new ResultadoFoto(lectura, 0, 0, List.of(), null);
        }

        String token = tokenLectura == null || tokenLectura.isBlank()
                ? "foto-" + UUID.randomUUID()
                : tokenLectura.trim();

        int siguienteCasilla = contexto.porNombre().size();
        int aplicadas = 0;
        int yaEstaban = 0;
        List<String> problemas = new ArrayList<>();
        String retenidoPor = null;

        for (int i = 0; i < lectura.comandos().size(); i++) {
            ComandoOperacion comando = Cuadricula.ubicar(lectura.comandos().get(i), siguienteCasilla);
            if (comando instanceof ComandoOperacion.CrearClase) {
                siguienteCasilla++;
            }

            try {
                // El token lleva el tipo del comando ademas de su posicion. Con
                // solo la posicion, dos lecturas distintas de la misma pizarra
                // -que producen listas de largo distinto- reusaban el mismo token
                // para comandos distintos, y uno legitimo se descartaba en
                // silencio como si fuera un reenvio.
                String tokenDelComando = token + "-" + i + "-"
                        + TipoOperacion.de(comando).name();

                ResultadoOperacion resultado = operaciones.registrar(diagramaId, usuarioId, sesionId,
                        comando, OrigenOperacion.FOTO, tokenDelComando);

                switch (resultado.estado()) {
                    case APLICADA -> aplicadas++;
                    case DUPLICADA -> yaEstaban++;
                    case RECHAZADA_POR_BLOQUEO -> retenidoPor = resultado.bloqueo() == null
                            ? "otro usuario" : resultado.bloqueo().poseedorNombre();
                }
            } catch (ComandoInvalido e) {
                // Se sigue con el resto: una pizarra de diez clases no deberia
                // perderse entera porque una traiga un nombre repetido.
                problemas.add(e.getMessage());
            }
        }

        log.info("Pizarra leida en el diagrama {}: {} de {} comandos aplicados, {} ya estaban, "
                + "{} con problemas", diagramaId, aplicadas, lectura.comandos().size(), yaEstaban,
                problemas.size());

        return new ResultadoFoto(lectura, aplicadas, yaEstaban, problemas, retenidoPor);
    }

    @Transactional(readOnly = true)
    ContextoDelDiagrama contextoDe(UUID diagramaId) {
        List<ContextoDelDiagrama.ClaseConocida> conocidas = clases.findByDiagramaId(diagramaId).stream()
                .map(clase -> new ContextoDelDiagrama.ClaseConocida(clase.getId(), clase.getNombre()))
                .toList();

        // Las relaciones ya trazadas van en el contexto para que leer dos veces
        // la misma pizarra no las repita.
        Set<String> trazadas = relaciones.buscarConExtremos(diagramaId).stream()
                .map(r -> ContextoDelDiagrama.claveDeRelacion(
                        r.getOrigen().getId(), r.getDestino().getId(), r.getTipo().name()))
                .collect(Collectors.toSet());

        return ContextoDelDiagrama.de(conocidas, trazadas);
    }

    /**
     * Que se leyo y que entro.
     *
     * @param retenidoPor nombre de quien tenia tomado un elemento, si la lectura
     *                    se topo con un bloqueo ajeno
     */
    public record ResultadoFoto(
            Lectura lectura,
            int aplicadas,
            /** Comandos que el servidor reconocio como reenvio de algo ya hecho. */
            int yaEstaban,
            List<String> problemas,
            String retenidoPor) {
    }
}
