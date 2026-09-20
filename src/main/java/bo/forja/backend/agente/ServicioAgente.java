package bo.forja.backend.agente;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.ia.Respondedor;
import bo.forja.backend.ia.RespondedorOllama;
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
import java.time.Duration;
import java.util.List;
import java.util.Locale;
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

    /** Identificador de una respuesta que redacto el modelo y no el catalogo. */
    public static final String RESPUESTA_DEL_MODELO = "respuesta-del-modelo";

    /** Identificador de la respuesta que mira el estado en vez de un catalogo. */
    public static final String PROXIMO_PASO = "respuesta-proximo-paso";

    /**
     * Cuanto se espera al modelo por una pregunta escrita.
     * <p>
     * Menos que el pedido de un diagrama -que tiene 90 s- porque aca del otro
     * lado hay alguien esperando una frase, no un modelo entero. Al vencer se
     * devuelve lo que el agente habria dicho igual.
     */
    private static final Duration PRESUPUESTO_DE_RESPUESTA = Duration.ofSeconds(25);

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
    private final Respondedor respondedor;

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
                          ServicioUso uso,
                          Respondedor respondedor) {
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
        this.respondedor = respondedor;
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

        /*
         * "Y ahora que hago" no se contesta con un texto fijo: se contesta
         * MIRANDO. Antes caia en el catalogo y alguien con el proyecto ya creado
         * recibia "crea un proyecto" -dos veces seguidas, si preguntaba dos
         * veces-, que es la demostracion mas clara posible de que el agente no
         * estaba monitoreando nada. Se responde con el primer paso pendiente del
         * recorrido, que ya se calcula desde la bitacora y el registro de uso.
         */
        if (preguntas.pideElProximoPaso(texto)) {
            return List.of(proximoPaso(mirarLaAplicacion(usuarioId, false)));
        }

        List<Consejo> delCatalogo = preguntas.responder(texto, sobre);
        if (!soloDijoQueNoSabe(delCatalogo) || !respondedor.disponible()) {
            return delCatalogo;
        }

        // Aca, y solo aca, se llega al modelo: el catalogo no engancho, asi que
        // lo unico que se puede perder es un "no la se contestar".
        return respondedor.responder(texto, preguntas.baseComoTexto(), PRESUPUESTO_DE_RESPUESTA)
                .filter(ServicioAgente::dijoAlgo)
                .map(ServicioAgente::comoConsejo)
                .map(List::of)
                .orElse(delCatalogo);
    }

    /**
     * El modelo tiene permitido decir que la respuesta no esta en el contexto,
     * y conviene que lo diga: es como se evita que invente. Pero ese centinela
     * es para el codigo, no para la persona, asi que se trata como un "no
     * contesto" y gana la respuesta escrita.
     */
    private static boolean dijoAlgo(String respuesta) {
        return !respuesta.toUpperCase(Locale.ROOT).contains(RespondedorOllama.NO_SABE);
    }

    /**
     * Lo que sigue, segun lo que la persona de verdad hizo.
     * <p>
     * Los pasos se marcan con evidencia -la bitacora y el registro de uso-, no
     * con pantallas visitadas, asi que esta respuesta no se puede falsear
     * haciendo clic: cambia cuando cambia el modelo.
     */
    private Consejo proximoPaso(Panorama panorama) {
        Recorrido recorrido = Recorrido.de(panorama);

        return recorrido.loQueSigue()
                .map(paso -> Consejo.de(PROXIMO_PASO, Consejo.Categoria.DESCUBRIMIENTO, 95,
                        "Lo que sigue es: " + paso.titulo(),
                        "Llevás " + recorrido.hechos() + " de " + recorrido.total()
                                + " pasos del recorrido. " + paso.comoSeHace(),
                        dondeSeHace(paso)))
                .orElseGet(() -> Consejo.de(PROXIMO_PASO, Consejo.Categoria.DESCUBRIMIENTO, 95,
                        "Ya recorriste los " + recorrido.total() + " pasos",
                        "Modelaste por las tres vías, generaste el backend, intercambiaste con "
                                + "Enterprise Architect y trabajaste con alguien más",
                        "Lo que queda es seguir modelando, o invitar a alguien a un proyecto nuevo"));
    }

    private static String dondeSeHace(Recorrido.Paso paso) {
        return "LIENZO".equals(paso.donde())
                ? "Se hace en el lienzo, con un diagrama abierto"
                : "Se hace en la pantalla de proyectos";
    }

    private boolean soloDijoQueNoSabe(List<Consejo> consejos) {
        return consejos.size() == 1 && Preguntas.SIN_COINCIDENCIA.equals(consejos.getFirst().id());
    }

    /**
     * La respuesta del modelo, envuelta y ETIQUETADA como tal.
     * <p>
     * Se distingue a proposito de las escritas. Una respuesta redactada por un
     * modelo de 4B puede equivocarse, y quien la lee tiene derecho a saber de
     * donde salio: ocultarlo seria vender como conocimiento de la herramienta
     * algo que no lo es. Ademas deja abierta la salida buena -preguntar de nuevo
     * con las palabras del catalogo- que si da una respuesta exacta.
     */
    private static Consejo comoConsejo(String respuesta) {
        return Consejo.de(RESPUESTA_DEL_MODELO, Consejo.Categoria.DESCUBRIMIENTO, 0,
                respuesta,
                "La redactó el modelo local a partir de la documentación de FORJA. No sale de la "
                        + "base de respuestas escritas, así que puede equivocarse",
                "Si necesitás una respuesta exacta, probá con una de las preguntas de acá abajo");
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
