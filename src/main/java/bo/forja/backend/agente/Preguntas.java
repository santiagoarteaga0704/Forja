package bo.forja.backend.agente;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lo que el agente guia sabe responder cuando se le pregunta.
 * <p>
 * Es la otra mitad de "interactuar con el agente": hasta aca el agente hablaba
 * cuando queria: ahora tambien contesta. Y contesta de la misma forma que
 * aconseja -que es, por que importa, como se hace- para que quien lee no tenga
 * que cambiar de registro segun de donde vino el texto.
 * <p>
 * <b>La busqueda es por palabras clave sobre un catalogo escrito a mano, no un
 * modelo de lenguaje</b>, y es la misma decision que el resto del agente: a
 * "en que se diferencian una agregacion y una composicion" hay una respuesta
 * correcta y conviene que sea siempre la misma, revisable y sin inventar nada.
 * Un modelo generativo la diria bien casi siempre, y el "casi" es justo lo que
 * no se puede defender en una herramienta que ensena.
 * <p>
 * El catalogo cubre dos cosas distintas a proposito: <b>como se usa FORJA</b> y
 * <b>UML</b>. Las dudas que frenan a alguien modelando son de los dos tipos, y
 * mandarlo a buscar la mitad afuera seria dejar el trabajo por la mitad.
 */
@Component
public class Preguntas {

    /** Cuantas respuestas se devuelven como maximo. */
    private static final int CUANTAS = 3;

    /**
     * Una entrada del catalogo.
     *
     * @param claves las formas en que alguien puede preguntar por esto. Se
     *               escriben sin acentos porque el texto de la pregunta se
     *               normaliza antes de comparar: asi "que es una composicion" y
     *               "¿Qué es una Composición?" buscan lo mismo.
     *               <p>
     *               Varias son RAICES y no palabras enteras -"invit", "export",
     *               "gener"- porque la comparacion es por subcadena y nadie
     *               pregunta en infinitivo: se escribe "como invito a alguien",
     *               no "como invitar a alguien". Con la palabra entera, esa
     *               pregunta no encontraba su respuesta. Se descubrio probandolo
     */
    private record Entrada(String id, int prioridad, List<String> claves,
                           String queEs, String porQue, String como) {
    }

    private static final List<Entrada> CATALOGO = List.of(

            // ---------- Usar FORJA ----------
            new Entrada("como-empiezo", 90,
                    List.of("como empiezo", "por donde empiezo", "primeros pasos", "como se usa",
                            "no se que hacer", "ayuda"),
                    "El camino es: proyecto, diagrama, modelo, y de ahí salen el backend y el XMI",
                    "Todo en FORJA cuelga de un diagrama, y un diagrama vive dentro de un proyecto. "
                            + "Con el modelo hecho, las dos salidas son automáticas",
                    "Creá un proyecto, creá un diagrama adentro y el lienzo se abre solo"),

            new Entrada("crear-proyecto", 70,
                    List.of("crear un proyecto", "crear proyecto", "nuevo proyecto", "que es un proyecto",
                            "proyecto nuevo"),
                    "Un proyecto agrupa los diagramas de un mismo sistema y a quienes pueden editarlos",
                    "Es el contenedor: los diagramas no existen sueltos, y los permisos se dan "
                            + "por proyecto y no por diagrama",
                    "En la pantalla de proyectos, escribí el nombre y dale a Crear proyecto"),

            new Entrada("invitar", 70,
                    List.of("invit", "colabor", "trabajar con otro", "varias personas",
                            "compartir", "sumar a alguien", "equipo"),
                    "Invitás por correo a una cuenta que ya exista, y entra como editor del proyecto",
                    "Desde ahí los dos pueden modelar el mismo diagrama a la vez: cada uno ve lo "
                            + "que el otro está editando y nadie puede tocar un elemento tomado",
                    "En la pantalla de proyectos, elegí el proyecto y usá Invitar a colaborar"),

            new Entrada("crear-clase", 75,
                    List.of("crear una clase", "agregar clase", "nueva clase", "como hago una clase",
                            "poner una clase"),
                    "Con el botón Clase de la barra, o dictándolo",
                    "Las clases son lo único que el generador convierte en tablas y en código: "
                            + "sin clases no hay backend que generar",
                    "Botón Clase, o dictá: crear la clase Paciente"),

            new Entrada("dictar", 80,
                    List.of("dict", "voz", "hablar", "microfono", "que frases entiende",
                            "que puedo decir"),
                    "Hablás o escribís una frase en castellano y FORJA la convierte en cambios del modelo",
                    "Es una de las tres formas de construir el diagrama. Entiende crear y borrar "
                            + "clases, atributos con sus marcas, operaciones con su firma y los seis "
                            + "tipos de relación. Si no entiende algo te propone cómo decirlo",
                    "Botón Dictar. Probá: un Paciente tiene muchas Consultas, o "
                            + "Paciente tiene un nombre de tipo texto"),

            new Entrada("pizarra", 78,
                    List.of("pizarra", "foto", "imagen", "camara", "pizarron", "ocr", "escanear",
                            "sacar una foto"),
                    "Le sacás una foto a una pizarra y FORJA lee el diagrama que hay dibujado",
                    "El reconocimiento ocurre en tu navegador y la foto no se sube: al servidor le "
                            + "llega el texto. Por eso funciona sin conexión. En el medio hay un paso "
                            + "de revisión, porque sobre letra manuscrita el reconocimiento nunca es exacto",
                    "Botón Pizarra. Elegís la imagen, corregís el texto y recién ahí se aplica"),

            new Entrada("generar-backend", 85,
                    List.of("gener", "backend", "spring boot", "codigo", "java",
                            "descargar el proyecto"),
                    "De cada clase salen cuatro capas: entidad JPA, repositorio, servicio y controlador REST",
                    "El proyecto que baja arranca sin configurar nada, con su pom, su clase de "
                            + "arranque y su application.yml",
                    "Botón Generar backend: podés leer el código en pantalla antes de descargarlo"),

            new Entrada("exportar-xmi", 85,
                    List.of("export", "enterprise architect", "xmi", "ea", "llevar a otra herramienta",
                            "abrir en enterprise"),
                    "FORJA exporta XMI 2.5.1, que Enterprise Architect abre directamente",
                    "Viaja el modelo entero y también la vista, así que EA dibuja el diagrama con "
                            + "las clases donde vos las dejaste, no apiladas en una esquina",
                    "Menú XMI, Exportar. El archivo se descarga y se importa en EA"),

            new Entrada("importar-xmi", 60,
                    List.of("import", "traer un modelo", "abrir un xmi", "cargar un xmi",
                            "modelo de otra herramienta"),
                    "Podés traer un modelo hecho en Enterprise Architect o en otra herramienta",
                    "Lo importado recorre exactamente las mismas validaciones que lo que dibujás a "
                            + "mano, y queda en la bitácora con origen IMPORTACION",
                    "Menú XMI, Importar, y elegís el archivo .xmi o .xml"),

            new Entrada("bloqueos", 55,
                    List.of("bloqueo", "esta editando", "no me deja", "tomado", "otro usuario",
                            "por que no puedo mover"),
                    "Cuando alguien está editando un elemento, queda tomado y nadie más puede tocarlo",
                    "Es lo que evita que dos personas pisen el mismo cambio. El bloqueo se pide al "
                            + "empezar a arrastrar, no al seleccionar, y vence solo si la persona se va",
                    "Si ves el nombre de alguien sobre una clase, esperá a que la suelte"),

            new Entrada("mover-lienzo", 40,
                    List.of("mover", "arrastrar", "zoom", "acercar", "desplazar", "navegar el lienzo",
                            "como muevo"),
                    "Arrastrás una clase para moverla, y el fondo para desplazar la hoja",
                    "Al soltar se manda una sola operación con la posición final: el recorrido "
                            + "intermedio no se registra porque no aporta nada",
                    "Rueda del mouse para acercar y alejar; arrastrar el fondo para desplazarte"),

            // ---------- UML ----------
            new Entrada("agregacion-vs-composicion", 88,
                    List.of("agregacion", "composicion", "rombo", "diferencia entre agregacion",
                            "rombo lleno", "rombo hueco"),
                    "La composición borra las partes con el todo; la agregación no",
                    "Si borrás un Pedido, sus Líneas de pedido no tienen sentido solas: eso es "
                            + "composición, rombo lleno. Si borrás un Equipo, sus Jugadores siguen "
                            + "existiendo: eso es agregación, rombo hueco. En el código generado, la "
                            + "composición arrastra el borrado y la agregación no",
                    "Menú Relación: el rombo lleno es composición, el hueco es agregación"),

            new Entrada("herencia-vs-realizacion", 80,
                    List.of("herencia", "realizacion", "interfaz", "triangulo", "extends", "implements",
                            "diferencia entre herencia"),
                    "La herencia es «es un»; la realización es «cumple con»",
                    "La herencia genera extends y comparte los atributos del padre. La realización "
                            + "genera implements: la interfaz sólo declara operaciones, y la clase que "
                            + "la realiza recibe el cuerpo. Una interfaz con atributos los pierde al generar",
                    "Menú Relación: triángulo con línea llena es herencia, con línea punteada es realización"),

            new Entrada("multiplicidades", 70,
                    List.of("multiplicidad", "cardinalidad", "uno a muchos", "0..*", "muchos a muchos",
                            "que pongo en multiplicidad"),
                    "Dicen cuántos elementos de un lado se relacionan con cuántos del otro",
                    "Se escriben 1, 0..1, 0..* o *. Son las que deciden el código: el lado «muchos» "
                            + "se lleva la clave ajena, y un muchos a muchos produce una tabla de unión",
                    "Al trazar la relación te las pide las dos juntas, y te muestra cómo queda la frase"),

            new Entrada("clave-primaria", 72,
                    List.of("clave", "primary key", "pk", "identificador", "id", "clave primaria",
                            "marcar la clave"),
                    "Marcás un atributo como identificador y ese pasa a ser la clave de la tabla",
                    "Si no marcás ninguno, el generador agrega un id numérico automático. Funciona, "
                            + "pero si tu modelo ya tiene un código o una matrícula propia conviene decirlo",
                    "Seleccioná la clase y marcá la casilla de clave en el atributo, en el panel derecho"),

            new Entrada("abstracta", 50,
                    List.of("abstracta", "abstract", "clase abstracta", "estereotipo"),
                    "Una clase abstracta no se instancia: existe para que otras hereden de ella",
                    "En el diagrama se dibuja con el nombre en cursiva. Al generar, la jerarquía usa "
                            + "JOINED, así que cada subclase conserva sus obligatoriedades",
                    "Seleccioná la clase y marcá abstracta en el panel, o dictá: "
                            + "la clase Persona es abstracta"),

            new Entrada("tipos", 45,
                    List.of("tipo", "tipos de dato", "que tipos hay", "texto", "entero", "fecha",
                            "decimal", "string"),
                    "Podés escribir los tipos en castellano y FORJA los normaliza",
                    "texto, entero, decimal, fecha, booleano y sus equivalentes en inglés terminan "
                            + "todos en el mismo tipo Java. Si cada vía normalizara por su cuenta, el "
                            + "mismo modelo tendría «texto» y «String» mezclados",
                    "Al dictar: Paciente tiene un nombre de tipo texto"));

    /**
     * Las respuestas que mejor encajan con la pregunta.
     * <p>
     * Se puntua por cuantas de sus claves aparecen en el texto, y la prioridad
     * de la entrada solo desempata. Asi "como exporto a enterprise architect"
     * gana con dos coincidencias sobre una entrada que solo comparte "como".
     */
    public List<Consejo> responder(String texto) {
        String normalizado = normalizar(texto);
        if (normalizado.isBlank()) {
            return List.of(noEntendi());
        }

        List<Consejo> encontradas = CATALOGO.stream()
                .map(entrada -> new Puntuada(entrada, puntaje(entrada, normalizado)))
                .filter(p -> p.puntos() > 0)
                .sorted(Comparator.comparingInt(Puntuada::puntos).reversed()
                        .thenComparing(p -> -p.entrada().prioridad()))
                .limit(CUANTAS)
                .map(p -> aConsejo(p.entrada()))
                .toList();

        return encontradas.isEmpty() ? List.of(noEntendi()) : encontradas;
    }

    private record Puntuada(Entrada entrada, int puntos) {
    }

    private int puntaje(Entrada entrada, String textoNormalizado) {
        int puntos = 0;
        for (String clave : entrada.claves()) {
            if (textoNormalizado.contains(clave)) {
                // Una clave de varias palabras vale mas que una suelta: quien
                // escribe "rombo lleno" esta siendo mas preciso que quien
                // escribe "rombo", y la respuesta tiene que reflejarlo.
                puntos += 1 + (int) clave.chars().filter(c -> c == ' ').count();
            }
        }
        return puntos;
    }

    private Consejo aConsejo(Entrada entrada) {
        return Consejo.de("respuesta-" + entrada.id(), Consejo.Categoria.DESCUBRIMIENTO,
                entrada.prioridad(), entrada.queEs(), entrada.porQue(), entrada.como());
    }

    /**
     * Cuando no hay coincidencia se dice que si se sabe, en vez de pedir que
     * reformule. Un agente que responde "no entendi" y nada mas deja a la
     * persona exactamente donde estaba.
     */
    private Consejo noEntendi() {
        return Consejo.de("respuesta-sin-coincidencia", Consejo.Categoria.DESCUBRIMIENTO, 0,
                "Esa no la sé contestar",
                "Sé explicar cómo se usa FORJA -proyectos, invitaciones, dictado, foto de pizarra, "
                        + "generación del backend, intercambio con Enterprise Architect- y las dudas de "
                        + "UML que más aparecen al modelar",
                "Probá con: ¿en qué se diferencian agregación y composición? o ¿cómo exporto a "
                        + "Enterprise Architect?");
    }

    /**
     * Deja el texto comparable: sin mayusculas, sin acentos y sin signos.
     * <p>
     * Sin quitar los acentos, "composicion" no encontraria "composición", que es
     * como lo va a escribir cualquiera que tenga el teclado en castellano; y sin
     * quitar los signos, "¿composición?" tampoco.
     */
    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 .*]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
