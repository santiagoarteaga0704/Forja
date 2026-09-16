package bo.forja.backend.agente;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.OrigenOperacion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Las reglas que ensenan a usar FORJA.
 * <p>
 * El enunciado pide un agente guia que <b>monitoree la aplicacion y ensene a
 * usarla</b>, y eso es mas que revisar un diagrama: alguien que recien entra no
 * tiene diagrama, y es quien mas necesita que le digan por donde se empieza.
 * Esta familia mira el {@link Panorama} -cuantos proyectos hay, si alguna vez se
 * dicto algo, si se llego a generar el backend, si se invito a alguien- y va
 * abriendo la herramienta de a una funcion por vez.
 * <p>
 * El orden de prioridad sigue el camino natural de aprendizaje: primero tener
 * donde trabajar, despues modelar, despues descubrir que hay otras formas de
 * entrar el modelo, y solo entonces las salidas -generar, exportar- y el trabajo
 * con otra gente. Mostrar "exporta a Enterprise Architect" a quien todavia no
 * creo un proyecto no ensena nada.
 * <p>
 * <b>Ninguna regla dice solo que hacer: dice para que sirve.</b> "Proba el
 * dictado" se ignora; "el dictado es una de las tres formas de construir el
 * modelo que pide el enunciado, y queda anotado en la bitacora con su origen"
 * explica por que vale la pena.
 */
@Component
public class BaseDeLaHerramienta {

    /**
     * A partir de cuantos cambios se considera que ya hay un modelo.
     * <p>
     * Con dos o tres operaciones la persona todavia esta probando; sugerirle
     * generar el backend ahi la manda a mirar un proyecto de una clase vacia y
     * la funcion queda desacreditada. Cuatro es donde ya hay algo que mostrar.
     */
    private static final int MODELO_CON_CUERPO = 4;

    public List<ReglaDeLaHerramienta> reglas() {
        return List.of(
                // ---------- Tener donde trabajar ----------
                regla("sin-proyectos", this::sinProyectos),
                regla("sin-diagramas", this::sinDiagramas),
                regla("hoja-en-blanco", this::hojaEnBlanco),

                // ---------- Las otras dos vias de entrada ----------
                regla("nunca-dicto", this::nuncaDicto),
                regla("nunca-uso-la-pizarra", this::nuncaUsoLaPizarra),

                // ---------- Las salidas ----------
                regla("nunca-genero", this::nuncaGenero),
                regla("nunca-intercambio", this::nuncaIntercambio),

                // ---------- Trabajar con otra gente ----------
                regla("nunca-invito", this::nuncaInvito),

                // ---------- El propio agente ----------
                regla("nunca-pregunto", this::nuncaPregunto),
                regla("ya-recorrio-todo", this::yaRecorrioTodo));
    }

    // ---------- Tener donde trabajar ----------------------------------------

    private List<Consejo> sinProyectos(Panorama p) {
        if (p.proyectos() > 0) {
            return List.of();
        }
        return List.of(Consejo.de("sin-proyectos", Consejo.Categoria.DESCUBRIMIENTO, 100,
                "Todavía no tenés ningún proyecto",
                "Un proyecto agrupa los diagramas de un mismo sistema y la gente que puede "
                        + "editarlos: es lo primero, porque los diagramas viven adentro de uno",
                "Escribí un nombre arriba y dale a Crear proyecto"));
    }

    private List<Consejo> sinDiagramas(Panorama p) {
        if (p.proyectos() == 0 || p.diagramas() > 0) {
            return List.of();
        }
        return List.of(Consejo.de("sin-diagramas", Consejo.Categoria.DESCUBRIMIENTO, 95,
                "El proyecto está creado pero todavía no tiene diagramas",
                "El diagrama es la hoja donde se modela, y de él salen las dos cosas que "
                        + "FORJA produce: el backend Spring Boot y el archivo para Enterprise Architect",
                "Elegí el proyecto y usá Crear y abrir: el lienzo se abre solo"));
    }

    private List<Consejo> hojaEnBlanco(Panorama p) {
        // Con el lienzo abierto, la regla "diagrama-vacio" de la otra familia
        // dice esto mismo mirando el diagrama concreto.
        if (p.enUnDiagrama() || p.diagramas() == 0 || p.totalDeOperaciones() > 0) {
            return List.of();
        }
        return List.of(Consejo.de("hoja-en-blanco", Consejo.Categoria.DESCUBRIMIENTO, 90,
                "Tenés un diagrama abierto pero todavía no modelaste nada",
                "Hasta que no haya una clase no hay nada que generar, que exportar ni que "
                        + "revisar: todo lo demás de FORJA parte del modelo",
                "Abrí el diagrama y usá el botón Clase, o dictá: crear la clase Paciente"));
    }

    // ---------- Las otras dos vias de entrada -------------------------------

    private List<Consejo> nuncaDicto(Panorama p) {
        if (p.enUnDiagrama() || p.totalDeOperaciones() == 0
                || p.operacionesConOrigen(OrigenOperacion.VOZ) > 0) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-dicto", Consejo.Categoria.DESCUBRIMIENTO, 70,
                "Nunca construiste el modelo dictando",
                "Dictar es una de las tres formas de armar el diagrama, y cada cambio queda "
                        + "anotado en la bitácora con la vía por la que entró",
                "En el lienzo, botón Dictar, y probá: un Paciente tiene muchas Consultas"));
    }

    private List<Consejo> nuncaUsoLaPizarra(Panorama p) {
        if (p.enUnDiagrama() || p.totalDeOperaciones() == 0
                || p.operacionesConOrigen(OrigenOperacion.FOTO) > 0) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-uso-la-pizarra", Consejo.Categoria.DESCUBRIMIENTO, 62,
                "Nunca leíste el diagrama desde la foto de una pizarra",
                "Es la tercera vía de entrada. El reconocimiento ocurre en tu navegador y la "
                        + "foto no se sube: al servidor le llega el texto, así que funciona sin conexión",
                "En el lienzo, botón Pizarra. Si no tenés una foto a mano, podés escribir el "
                        + "texto y probar igual"));
    }

    // ---------- Las salidas --------------------------------------------------

    private List<Consejo> nuncaGenero(Panorama p) {
        if (p.totalDeOperaciones() < MODELO_CON_CUERPO || p.yaUso(Herramienta.BACKEND_GENERADO)) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-genero", Consejo.Categoria.DESCUBRIMIENTO, 85,
                "Tu modelo ya alcanza para generar el backend y todavía no lo miraste",
                "De cada clase salen cuatro capas -entidad JPA, repositorio, servicio y "
                        + "controlador REST- y el proyecto arranca sin configurar nada",
                "En el lienzo, botón Generar backend: podés leer el código en pantalla antes "
                        + "de descargarlo"));
    }

    private List<Consejo> nuncaIntercambio(Panorama p) {
        if (p.totalDeOperaciones() < MODELO_CON_CUERPO
                || p.yaUso(Herramienta.XMI_EXPORTADO)
                || p.operacionesConOrigen(OrigenOperacion.IMPORTACION) > 0) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-intercambio", Consejo.Categoria.DESCUBRIMIENTO, 66,
                "Nunca llevaste el diagrama a Enterprise Architect",
                "FORJA exporta e importa XMI 2.5.1, así que el modelo entra y sale de otras "
                        + "herramientas sin volver a dibujarlo",
                "En el lienzo, menú XMI, Exportar. El archivo se abre directamente en EA"));
    }

    // ---------- Trabajar con otra gente --------------------------------------

    private List<Consejo> nuncaInvito(Panorama p) {
        // Dentro del lienzo lo dice "proyecto-de-uno", que ademas sabe cuanta
        // gente hay en ese proyecto en particular.
        if (p.enUnDiagrama() || p.proyectosPropios() == 0 || p.invitados() > 0) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-invito", Consejo.Categoria.DESCUBRIMIENTO, 45,
                "Estás trabajando solo en todos tus proyectos",
                "El lienzo es colaborativo de verdad: dos personas modelan a la vez, cada una "
                        + "ve lo que la otra está editando y nadie puede tocar un elemento tomado",
                "En la pantalla de proyectos, Invitar a colaborar, con el correo de otra cuenta"));
    }

    // ---------- El propio agente ---------------------------------------------

    private List<Consejo> nuncaPregunto(Panorama p) {
        if (p.yaUso(Herramienta.AGENTE_CONSULTADO)) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-pregunto", Consejo.Categoria.DESCUBRIMIENTO, 30,
                "Me podés preguntar, no sólo leerme",
                "Sé explicar qué hace cada parte de FORJA y las dudas de UML que más aparecen "
                        + "al modelar, como en qué se diferencian una agregación y una composición",
                "Escribí tu pregunta en el campo de acá abajo"));
    }

    private List<Consejo> yaRecorrioTodo(Panorama p) {
        boolean todo = p.proyectos() > 0
                && p.diagramas() > 0
                && p.proboOtraVia()
                && p.yaUso(Herramienta.BACKEND_GENERADO)
                && p.yaUso(Herramienta.XMI_EXPORTADO)
                && p.invitados() > 0;
        if (!todo) {
            return List.of();
        }
        return List.of(Consejo.de("ya-recorrio-todo", Consejo.Categoria.DESCUBRIMIENTO, 15,
                "Ya probaste todo lo que FORJA sabe hacer",
                "Dibujaste, dictaste o fotografiaste, generaste el backend, intercambiaste con "
                        + "Enterprise Architect y trabajaste con otra persona",
                "De acá en adelante te voy a hablar sólo del modelo: lo que puede afectar al "
                        + "código que se genere"));
    }

    // ---------- Armado -------------------------------------------------------

    private ReglaDeLaHerramienta regla(String nombre, Function<Panorama, List<Consejo>> condicion) {
        return new ReglaDeLaHerramienta() {
            @Override
            public String nombre() {
                return nombre;
            }

            @Override
            public List<Consejo> evaluar(Panorama panorama) {
                return condicion.apply(panorama);
            }
        };
    }
}
