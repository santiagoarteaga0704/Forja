package bo.forja.backend.agente;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.repositorio.ConteoPorOrigen;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.servicio.ServicioModelo;
import bo.forja.backend.servicio.ServicioProyectos;
import bo.forja.backend.servicio.ServicioUso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * El agente guia.
 * <p>
 * Observa el diagrama y la bitacora y devuelve lo que conviene saber en ese
 * momento: como usar lo que la persona todavia no probo, y que del modelo va a
 * afectar al codigo que se genere. No cambia nada; solo mira y explica.
 * <p>
 * <b>Es un agente simbolico, no un modelo de lenguaje</b>, y esa es una decision
 * y no una limitacion. Para saber que una clase marcada como interfaz no puede
 * tener atributos no hace falta inferir nada: hace falta saber la regla. Un
 * modelo de lenguaje lo diria a veces, y a veces diria otra cosa. Quedan asi los
 * dos paradigmas de inteligencia artificial implementados donde cada uno sirve:
 * el de reglas para razonar sobre el modelo, y el generativo -en el cliente
 * movil- para entender el lenguaje natural.
 * <p>
 * Se devuelven pocos consejos a proposito. Un agente que muestra veinte avisos a
 * la vez es una lista de pendientes que nadie lee; se ordenan por prioridad y se
 * entregan los primeros, de modo que lo que puede romper el codigo generado
 * aparezca antes que una convencion de nombres.
 */
@Service
public class ServicioAgente {

    private static final Logger log = LoggerFactory.getLogger(ServicioAgente.class);

    /** Cuantos consejos se entregan como maximo en una consulta. */
    private static final int CUANTOS_A_LA_VEZ = 6;

    /**
     * Cuantos consejos puede aportar una misma regla.
     * <p>
     * Hace falta porque varias reglas emiten un consejo por cada clase. Con
     * cinco clases sin atributos, esa sola regla llenaba los seis lugares y
     * tapaba todo lo demas -incluida la inferencia de la superclase que falta,
     * que es el consejo mas valioso que el agente sabe dar-. Limitando cada
     * regla, los seis que se entregan son variados: se ve un problema de cada
     * tipo en lugar de cinco veces el mismo.
     */
    private static final int POR_REGLA = 2;

    private final ServicioProyectos proyectos;
    private final ServicioModelo modelo;
    private final RelacionUmlRepositorio relaciones;
    private final OperacionRepositorio operaciones;
    private final ProyectoMiembroRepositorio miembros;
    private final BaseDeConocimiento base;
    private final BaseDeLaHerramienta baseDeLaHerramienta;
    private final Preguntas preguntas;
    private final ProyectoRepositorio proyectoRepositorio;
    private final DiagramaRepositorio diagramas;
    private final ServicioUso uso;

    public ServicioAgente(ServicioProyectos proyectos,
                          ServicioModelo modelo,
                          RelacionUmlRepositorio relaciones,
                          OperacionRepositorio operaciones,
                          ProyectoMiembroRepositorio miembros,
                          BaseDeConocimiento base,
                          BaseDeLaHerramienta baseDeLaHerramienta,
                          Preguntas preguntas,
                          ProyectoRepositorio proyectoRepositorio,
                          DiagramaRepositorio diagramas,
                          ServicioUso uso) {
        this.proyectos = proyectos;
        this.modelo = modelo;
        this.relaciones = relaciones;
        this.operaciones = operaciones;
        this.miembros = miembros;
        this.base = base;
        this.baseDeLaHerramienta = baseDeLaHerramienta;
        this.preguntas = preguntas;
        this.proyectoRepositorio = proyectoRepositorio;
        this.diagramas = diagramas;
        this.uso = uso;
    }

    /**
     * Todo lo que el agente tiene para decir donde sea que este la persona.
     * <p>
     * Es la entrada que usa el panel, que ahora vive en toda la aplicacion y no
     * solo en el lienzo. Si hay un diagrama abierto se evaluan las dos familias
     * de reglas -la que ensena la herramienta y la que revisa el modelo- y se
     * ordenan juntas por prioridad, de modo que un problema del modelo que va a
     * romper el codigo generado aparezca antes que una funcion sin descubrir.
     * Sin diagrama solo puede hablar de la herramienta, que es exactamente lo
     * que necesita quien todavia no creo ninguno.
     *
     * @param diagramaId el diagrama abierto, o nulo si no hay ninguno
     */
    @Transactional(readOnly = true)
    public Guia guia(UUID usuarioId, UUID diagramaId, Set<String> descartados) {
        Panorama panorama = mirarLaAplicacion(usuarioId, diagramaId != null);

        Stream<Consejo> deLaHerramienta = baseDeLaHerramienta.reglas().stream()
                .flatMap(regla -> regla.evaluar(panorama).stream());

        Stream<Consejo> delModelo = diagramaId == null
                ? Stream.empty()
                : base.reglas().stream()
                        .flatMap(regla -> regla.evaluar(observar(diagramaId, usuarioId)).stream()
                                .limit(POR_REGLA));

        List<Consejo> todos = Stream.concat(deLaHerramienta, delModelo)
                .filter(consejo -> !descartados.contains(consejo.id()))
                .sorted(Comparator.comparingInt(Consejo::prioridad).reversed())
                .limit(CUANTOS_A_LA_VEZ)
                .toList();

        log.debug("Agente para {} (diagrama {}): {} consejos, {} pasos del recorrido hechos",
                usuarioId, diagramaId, todos.size(), Recorrido.de(panorama).hechos());

        return new Guia(todos, Recorrido.de(panorama));
    }

    /**
     * Responde una pregunta escrita.
     * <p>
     * Anota la consulta como uso de la herramienta: es lo que permite a la regla
     * "nunca-pregunto" dejar de insistir en cuanto alguien descubrio que al
     * agente se le puede preguntar.
     */
    public List<Consejo> responder(UUID usuarioId, String texto, String sobre) {
        uso.anotar(usuarioId, Herramienta.AGENTE_CONSULTADO);
        return preguntas.responder(texto, sobre);
    }

    /** Las preguntas que si tienen respuesta, para ofrecerlas antes de fallar. */
    public List<Preguntas.Tema> temas() {
        return preguntas.temas();
    }

    /** El estado de la aplicacion entera para esta persona, en una sola pasada. */
    @Transactional(readOnly = true)
    public Panorama mirarLaAplicacion(UUID usuarioId, boolean enUnDiagrama) {
        List<Proyecto> suyos = proyectoRepositorio.buscarPorParticipante(usuarioId);
        List<UUID> ids = suyos.stream().map(Proyecto::getId).toList();

        int propios = (int) suyos.stream()
                .filter(p -> p.getPropietario().getId().equals(usuarioId))
                .count();

        // Sin proyectos no hay nada que contar, y un IN con la lista vacia no es
        // valido en SQL: se corta antes en lugar de armar consultas imposibles.
        Map<OrigenOperacion, Long> porOrigen = new EnumMap<>(OrigenOperacion.class);
        long cuantosDiagramas = 0;
        if (!ids.isEmpty()) {
            for (ConteoPorOrigen conteo : operaciones.contarPorOrigenEnProyectos(ids)) {
                porOrigen.put(conteo.origen(), conteo.cantidad());
            }
            cuantosDiagramas = diagramas.contarEnProyectos(ids);
        }

        return new Panorama(
                usuarioId,
                propios,
                suyos.size() - propios,
                cuantosDiagramas,
                miembros.contarInvitadosPor(usuarioId),
                porOrigen,
                uso.deUsuario(usuarioId),
                enUnDiagrama);
    }

    /**
     * Consejos para el diagrama, ya ordenados y recortados.
     *
     * @param descartados identificadores que el cliente ya mostro y la persona
     *                    cerro; se filtran aqui para que no vuelvan
     */
    @Transactional(readOnly = true)
    public List<Consejo> consejos(UUID diagramaId, UUID usuarioId, Set<String> descartados) {
        Observacion observacion = observar(diagramaId, usuarioId);

        List<Consejo> todos = base.reglas().stream()
                .flatMap(regla -> regla.evaluar(observacion).stream()
                        .filter(consejo -> !descartados.contains(consejo.id()))
                        // El recorte se aplica dentro de cada regla y no al final:
                        // asi lo que se descarta es la quinta clase sin atributos,
                        // no el unico consejo de otra regla.
                        .limit(POR_REGLA))
                .sorted(Comparator.comparingInt(Consejo::prioridad).reversed())
                .toList();

        log.debug("Agente en el diagrama {}: {} consejos, se entregan {}",
                diagramaId, todos.size(), Math.min(todos.size(), CUANTOS_A_LA_VEZ));

        return todos.stream().limit(CUANTOS_A_LA_VEZ).toList();
    }

    /** Todo lo que las reglas van a mirar, reunido en una sola pasada. */
    @Transactional(readOnly = true)
    public Observacion observar(UUID diagramaId, UUID usuarioId) {
        Diagrama diagrama = proyectos.diagramaAccesible(diagramaId, usuarioId);

        Map<OrigenOperacion, Long> porOrigen = new EnumMap<>(OrigenOperacion.class);
        for (ConteoPorOrigen conteo : operaciones.contarPorOrigen(diagramaId)) {
            porOrigen.put(conteo.origen(), conteo.cantidad());
        }

        return new Observacion(
                diagramaId,
                diagrama.getNombre(),
                modelo.clasesCompletas(diagramaId),
                relaciones.buscarConExtremos(diagramaId),
                (int) miembros.countByProyectoId(diagrama.getProyecto().getId()),
                porOrigen);
    }
}
