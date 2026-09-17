package bo.forja.backend.pedido;

import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.Cuadricula;
import bo.forja.backend.operacion.TipoOperacion;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.servicio.ResultadoOperacion;
import bo.forja.backend.servicio.ServicioOperaciones;
import bo.forja.backend.servicio.ServicioProyectos;
import bo.forja.backend.voz.ParserVoz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pedir un diagrama en una frase.
 * <p>
 * Tiene dos pasos y no uno, igual que la lectura de una pizarra y por la misma
 * razon: lo que propone un modelo de 4B se mira antes de aplicarlo. Un solo
 * paso obligaria a aceptar a ciegas lo que el modelo haya entendido.
 * <p>
 * El origen que queda en la bitacora es VOZ y no uno nuevo: un pedido escrito
 * es lenguaje, igual que el dictado, y la via de entrada que interesa contar es
 * esa. Inventar un origen nuevo obligaria a una migracion de base de datos y a
 * revisar el agente, que cuenta operaciones por origen.
 */
@Service
public class ServicioPedido {

    private static final Logger log = LoggerFactory.getLogger(ServicioPedido.class);

    private final ServicioProyectos proyectos;
    private final ServicioOperaciones operaciones;
    private final ClaseUmlRepositorio clases;
    private final ParserVoz parser;
    private final Traductor traductor;

    public ServicioPedido(ServicioProyectos proyectos,
                          ServicioOperaciones operaciones,
                          ClaseUmlRepositorio clases,
                          ParserVoz parser,
                          Traductor traductor) {
        this.proyectos = proyectos;
        this.operaciones = operaciones;
        this.clases = clases;
        this.parser = parser;
        this.traductor = traductor;
    }

    /** Primer paso: que se propuso, sin tocar el modelo. */
    @Transactional(readOnly = true)
    public Pedido leer(UUID diagramaId, UUID usuarioId, String pedido) {
        proyectos.diagramaAccesible(diagramaId, usuarioId);
        return PedidoInterpretado.de(parser, traductor, pedido, contextoDe(diagramaId));
    }

    /**
     * Segundo paso: aplicar lo propuesto.
     * <p>
     * Se vuelve a pedir la traduccion en vez de recibir los comandos del
     * cliente, a proposito: el cliente no decide que entra al modelo. Con
     * {@code temperature} en cero la propuesta es la misma, y si cambiara, lo
     * que entra sigue pasando por la gramatica igual.
     *
     * @param tokenLectura identificador de este envio; si el cliente lo repite
     *                     tras un corte, los comandos se reconocen como ya
     *                     registrados en lugar de duplicar el modelo
     */
    public ResultadoPedido aplicar(UUID diagramaId, UUID usuarioId, String sesionId,
                                   String pedido, String tokenLectura) {

        proyectos.diagramaAccesible(diagramaId, usuarioId);
        ContextoDelDiagrama contexto = contextoDe(diagramaId);
        Pedido propuesto = PedidoInterpretado.de(parser, traductor, pedido, contexto);

        if (!propuesto.seEntendioAlgo()) {
            log.debug("Pedido sin resultado en el diagrama {}: \"{}\"", diagramaId, pedido);
            return new ResultadoPedido(propuesto, 0, 0, List.of(), null);
        }

        String token = tokenLectura == null || tokenLectura.isBlank()
                ? "pedido-" + UUID.randomUUID()
                : tokenLectura.trim();

        int siguienteCasilla = contexto.porNombre().size();
        int aplicadas = 0;
        int yaEstaban = 0;
        List<String> problemas = new ArrayList<>();
        String retenidoPor = null;

        for (int i = 0; i < propuesto.comandos().size(); i++) {
            ComandoOperacion comando = Cuadricula.ubicar(propuesto.comandos().get(i), siguienteCasilla);
            if (comando instanceof ComandoOperacion.CrearClase) {
                siguienteCasilla++;
            }

            try {
                // El token lleva el tipo ademas de la posicion, por lo mismo que
                // en la lectura de pizarra: dos propuestas distintas del mismo
                // pedido tienen largos distintos, y con solo la posicion un
                // comando legitimo se descartaria como si fuera un reenvio.
                String tokenDelComando = token + "-" + i + "-" + TipoOperacion.de(comando).name();

                ResultadoOperacion resultado = operaciones.registrar(diagramaId, usuarioId, sesionId,
                        comando, OrigenOperacion.VOZ, tokenDelComando);

                switch (resultado.estado()) {
                    case APLICADA -> aplicadas++;
                    case DUPLICADA -> yaEstaban++;
                    case RECHAZADA_POR_BLOQUEO -> retenidoPor = resultado.bloqueo() == null
                            ? "otro usuario" : resultado.bloqueo().poseedorNombre();
                }
            } catch (ComandoInvalido e) {
                // Se sigue con el resto: un pedido de seis clases no deberia
                // perderse entero porque una traiga un nombre repetido.
                problemas.add(e.getMessage());
            }
        }

        log.info("Pedido en el diagrama {}: \"{}\" -> {} de {} comandos aplicados, {} ya estaban",
                diagramaId, pedido, aplicadas, propuesto.comandos().size(), yaEstaban);

        return new ResultadoPedido(propuesto, aplicadas, yaEstaban, problemas, retenidoPor);
    }

    @Transactional(readOnly = true)
    ContextoDelDiagrama contextoDe(UUID diagramaId) {
        return ContextoDelDiagrama.de(clases.findByDiagramaId(diagramaId).stream()
                .map(clase -> new ContextoDelDiagrama.ClaseConocida(clase.getId(), clase.getNombre()))
                .toList());
    }

    /** Si hay con que contestar. La interfaz no ofrece lo que no va a andar. */
    public boolean disponible() {
        return traductor.disponible();
    }

    /**
     * Que se propuso y que entro.
     *
     * @param yaEstaban   comandos que el servidor reconocio como reenvio
     * @param retenidoPor nombre de quien tenia tomado un elemento, si algo se
     *                    topo con un bloqueo ajeno
     */
    public record ResultadoPedido(
            Pedido pedido,
            int aplicadas,
            int yaEstaban,
            List<String> problemas,
            String retenidoPor) {
    }
}
