package bo.forja.backend.xmi;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Interpreta un documento XMI y lo convierte en comandos.
 * <p>
 * Que el resultado sean comandos y no entidades es la decision que sostiene
 * toda la importacion. Un modelo que llega de Enterprise Architect pasa por
 * exactamente las mismas validaciones que un trazo en el lienzo o una frase
 * dictada, porque recorre el mismo camino: no hay una segunda puerta de
 * entrada al modelo con sus propias reglas, que es donde estos importadores
 * suelen acumular incoherencias.
 * <p>
 * <b>Tolerancia deliberada.</b> XMI admite varias formas de escribir lo
 * mismo y las herramientas las usan todas. El tipo de un atributo puede
 * venir como {@code href} a la biblioteca de tipos primitivos, como
 * {@code xmi:idref} a un tipo declarado en el documento o como atributo
 * {@code type} del propio elemento; los extremos de una asociacion pueden
 * ser propiedades de las clases o extremos propios de la asociacion. Se
 * aceptan todas las variantes, porque rechazar un archivo valido por estar
 * escrito de la otra manera valida haria inutil la importacion.
 * <p>
 * Los identificadores se reasignan: el documento trae los del modelo de
 * origen y reutilizarlos chocaria con las clases que ya existen. Se
 * conserva la correspondencia interna para que las relaciones sigan
 * apuntando a donde deben.
 */
@Component
public class ImportadorXmi {

    private static final Logger log = LoggerFactory.getLogger(ImportadorXmi.class);

    private static final String NS_XMI = "http://www.omg.org/spec/XMI/20131001";
    /** Separacion entre clases al distribuirlas cuando el XMI no trae posiciones. */
    private static final double PASO_X = 260;
    private static final double PASO_Y = 190;
    private static final int POR_FILA = 5;

    public List<ComandoOperacion> interpretar(String xmi) {
        Document documento = leer(xmi);

        // La vista de EA vive aparte de las clases y se referencia por id, asi
        // que se censa una vez y se consulta al crear cada clase.
        Map<String, Geometria> vistaEa = vistaDeEnterpriseArchitect(documento);

        Map<String, String> nombresDeTipos = new HashMap<>();
        Map<String, UUID> equivalencias = new LinkedHashMap<>();
        List<Element> clasificadores = new ArrayList<>();
        List<Element> asociaciones = new ArrayList<>();
        List<Element> dependencias = new ArrayList<>();

        // Primera pasada: censar que hay y con que identificador, para poder
        // resolver despues las referencias entre elementos.
        for (Element elemento : elementos(documento, "packagedElement", "ownedMember")) {
            String tipo = tipoXmi(elemento);
            String id = atributoXmi(elemento, "id");
            if (id == null) {
                continue;
            }
            switch (tipo) {
                case "uml:Class", "uml:Interface" -> {
                    clasificadores.add(elemento);
                    equivalencias.put(id, UUID.randomUUID());
                    nombresDeTipos.put(id, elemento.getAttribute("name"));
                }
                case "uml:Association" -> asociaciones.add(elemento);
                case "uml:Dependency", "uml:Usage" -> dependencias.add(elemento);
                case "uml:DataType", "uml:PrimitiveType", "uml:Enumeration" ->
                        nombresDeTipos.put(id, elemento.getAttribute("name"));
                default -> {
                    // Paquetes, comentarios y cuanto mas traiga el documento: no
                    // aportan al diagrama de clases y se ignoran sin ruido.
                }
            }
        }

        if (clasificadores.isEmpty()) {
            throw new XmiInvalido("El documento no contiene ninguna clase que importar");
        }

        List<ComandoOperacion> altas = new ArrayList<>();
        List<ComandoOperacion> contenidos = new ArrayList<>();
        List<ComandoOperacion> relaciones = new ArrayList<>();
        List<ExtremoDeAsociacion> extremos = new ArrayList<>();

        int indice = 0;
        for (Element clasificador : clasificadores) {
            String idXmi = atributoXmi(clasificador, "id");
            UUID claseId = equivalencias.get(idXmi);

            altas.add(crearClase(clasificador, claseId, indice++, vistaEa));
            contenidos.addAll(atributosDe(clasificador, claseId, nombresDeTipos, extremos, idXmi));
            contenidos.addAll(operacionesDe(clasificador, claseId, nombresDeTipos));
            relaciones.addAll(herenciasDe(clasificador, claseId, equivalencias));
        }

        for (Element asociacion : asociaciones) {
            interpretarAsociacion(asociacion, equivalencias, extremos)
                    .ifPresent(relaciones::add);
        }

        // La dependencia no tiene extremos que reconstruir: se lee de sus dos
        // referencias y basta. Vienen como atributos en el dialecto de UML 2.5.1
        // y como hijos <client>/<supplier> en el de otras herramientas.
        for (Element dependencia : dependencias) {
            UUID cliente = equivalencias.get(referenciaDeExtremo(dependencia, "client"));
            UUID proveedor = equivalencias.get(referenciaDeExtremo(dependencia, "supplier"));
            if (cliente != null && proveedor != null && !cliente.equals(proveedor)) {
                relaciones.add(relacion(cliente, proveedor, TipoRelacion.DEPENDENCIA, "1", "1"));
            }
        }

        log.info("XMI interpretado: {} clases, {} elementos internos, {} relaciones",
                altas.size(), contenidos.size(), relaciones.size());

        // El orden es obligatorio: una relacion no puede aplicarse antes de que
        // existan sus dos extremos, ni un atributo antes que su clase.
        List<ComandoOperacion> todos = new ArrayList<>(altas);
        todos.addAll(contenidos);
        todos.addAll(relaciones);
        return todos;
    }

    // ---------- Clases ------------------------------------------------------

    private ComandoOperacion crearClase(Element clasificador, UUID claseId, int indice,
                                        Map<String, Geometria> vistaEa) {
        String nombre = clasificador.getAttribute("name");
        if (nombre.isBlank()) {
            nombre = "ClaseSinNombre" + (indice + 1);
        }
        boolean abstracta = "true".equalsIgnoreCase(clasificador.getAttribute("isAbstract"));

        // Una uml:Interface se representa en el modelo como una clase con
        // estereotipo, que es la forma en que el lienzo la dibuja.
        String estereotipo = "uml:Interface".equals(tipoXmi(clasificador))
                ? "interface"
                : estereotipoDeLaExtension(clasificador);

        Geometria geometria = geometriaDe(clasificador, indice, vistaEa);
        return new ComandoOperacion.CrearClase(claseId, nombre, estereotipo, abstracta,
                geometria.x(), geometria.y());
    }

    private String estereotipoDeLaExtension(Element clasificador) {
        return hijos(clasificador, "estereotipo").stream()
                .map(e -> e.getAttribute("nombre"))
                .filter(n -> !n.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Posicion de la clase en el lienzo. Si el documento la trae -porque lo
     * exporto FORJA- se respeta; si viene de otra herramienta se distribuyen
     * en una cuadricula, que es preferible a apilarlas todas en el origen.
     */
    private Geometria geometriaDe(Element clasificador, int indice,
                                  Map<String, Geometria> vistaEa) {
        for (Element geometria : hijos(clasificador, "geometria")) {
            try {
                return new Geometria(
                        Double.parseDouble(geometria.getAttribute("x")),
                        Double.parseDouble(geometria.getAttribute("y")));
            } catch (NumberFormatException e) {
                break;
            }
        }
        // Un documento de Enterprise Architect no trae la etiqueta de arriba:
        // su geometria vive en el bloque de la vista, referenciada por id.
        String id = atributoXmi(clasificador, "id");
        if (id != null) {
            Geometria deLaVista = vistaEa.get(id);
            if (deLaVista != null) {
                return deLaVista;
            }
        }

        return new Geometria((indice % POR_FILA) * PASO_X, (indice / POR_FILA) * PASO_Y);
    }

    // ---------- Atributos ---------------------------------------------------

    private List<ComandoOperacion> atributosDe(Element clasificador, UUID claseId,
                                               Map<String, String> nombresDeTipos,
                                               List<ExtremoDeAsociacion> extremos,
                                               String idClaseXmi) {
        List<ComandoOperacion> comandos = new ArrayList<>();

        for (Element propiedad : hijosDirectos(clasificador, "ownedAttribute")) {
            String asociacion = propiedad.getAttribute("association");
            if (!asociacion.isBlank()) {
                // No es un atributo sino un extremo navegable: se guarda para
                // cuando se interprete la asociacion a la que pertenece.
                extremos.add(new ExtremoDeAsociacion(
                        atributoXmi(propiedad, "id"), asociacion, idClaseXmi,
                        referenciaDeTipo(propiedad), multiplicidad(propiedad),
                        propiedad.getAttribute("name"),
                        propiedad.getAttribute("aggregation")));
                continue;
            }

            String nombre = propiedad.getAttribute("name");
            if (nombre.isBlank()) {
                continue;
            }
            Almacenamiento extra = almacenamientoDe(propiedad);

            comandos.add(new ComandoOperacion.AgregarAtributo(
                    claseId, UUID.randomUUID(), nombre,
                    tipoDe(propiedad, nombresDeTipos),
                    visibilidad(propiedad.getAttribute("visibility")),
                    extra.esIdentificador(), esObligatorio(propiedad), extra.esUnico(),
                    extra.longitud()));
        }
        return comandos;
    }

    /** El limite inferior de la multiplicidad es lo que marca la obligatoriedad. */
    private boolean esObligatorio(Element propiedad) {
        for (Element limite : hijosDirectos(propiedad, "lowerValue")) {
            String valor = limite.getAttribute("value");
            return !valor.isBlank() && !"0".equals(valor.trim());
        }
        // Sin limite inferior explicito, UML asume 1.
        return true;
    }

    private Almacenamiento almacenamientoDe(Element propiedad) {
        for (Element extension : hijos(propiedad, "almacenamiento")) {
            Integer longitud = null;
            try {
                String texto = extension.getAttribute("longitud");
                longitud = texto.isBlank() ? null : Integer.valueOf(texto.trim());
            } catch (NumberFormatException e) {
                longitud = null;
            }
            return new Almacenamiento(
                    "true".equalsIgnoreCase(extension.getAttribute("esIdentificador")),
                    "true".equalsIgnoreCase(extension.getAttribute("esUnico")),
                    longitud);
        }
        return new Almacenamiento(false, false, null);
    }

    // ---------- Operaciones -------------------------------------------------

    private List<ComandoOperacion> operacionesDe(Element clasificador, UUID claseId,
                                                 Map<String, String> nombresDeTipos) {
        List<ComandoOperacion> comandos = new ArrayList<>();

        for (Element operacion : hijosDirectos(clasificador, "ownedOperation")) {
            String nombre = operacion.getAttribute("name");
            if (nombre.isBlank()) {
                continue;
            }

            String retorno = "void";
            List<ComandoOperacion.Parametro> parametros = new ArrayList<>();

            for (Element parametro : hijosDirectos(operacion, "ownedParameter")) {
                String direccion = parametro.getAttribute("direction");
                if ("return".equalsIgnoreCase(direccion)) {
                    // En UML el tipo de retorno es un parametro con esa direccion.
                    retorno = tipoDe(parametro, nombresDeTipos);
                    continue;
                }
                String nombreParametro = parametro.getAttribute("name");
                if (nombreParametro.isBlank()) {
                    continue;
                }
                parametros.add(new ComandoOperacion.Parametro(UUID.randomUUID(),
                        nombreParametro, tipoDe(parametro, nombresDeTipos)));
            }

            comandos.add(new ComandoOperacion.AgregarMetodo(claseId, UUID.randomUUID(), nombre,
                    retorno, visibilidad(operacion.getAttribute("visibility")),
                    "true".equalsIgnoreCase(operacion.getAttribute("isAbstract")),
                    "true".equalsIgnoreCase(operacion.getAttribute("isStatic")),
                    parametros));
        }
        return comandos;
    }

    // ---------- Herencia y realizacion --------------------------------------

    private List<ComandoOperacion> herenciasDe(Element clasificador, UUID claseId,
                                               Map<String, UUID> equivalencias) {
        List<ComandoOperacion> comandos = new ArrayList<>();

        for (Element generalizacion : hijosDirectos(clasificador, "generalization")) {
            String general = primeraReferencia(generalizacion, "general");
            UUID padre = equivalencias.get(general);
            if (padre != null) {
                comandos.add(relacion(claseId, padre, TipoRelacion.HERENCIA, "1", "1"));
            }
        }

        for (Element realizacion : hijosDirectos(clasificador, "interfaceRealization")) {
            String contrato = Optional.of(realizacion.getAttribute("contract"))
                    .filter(v -> !v.isBlank())
                    .orElseGet(() -> realizacion.getAttribute("supplier"));
            UUID interfaz = equivalencias.get(contrato);
            if (interfaz != null) {
                comandos.add(relacion(claseId, interfaz, TipoRelacion.REALIZACION, "1", "1"));
            }
        }
        return comandos;
    }

    // ---------- Asociaciones ------------------------------------------------

    private Optional<ComandoOperacion> interpretarAsociacion(
            Element asociacion, Map<String, UUID> equivalencias,
            List<ExtremoDeAsociacion> extremos) {

        String idAsociacion = atributoXmi(asociacion, "id");

        // Camino preferente: el documento lo exporto FORJA y trae el tipo exacto
        // y las multiplicidades tal como estaban.
        for (Element clasificacion : hijos(asociacion, "clasificacion")) {
            UUID origen = equivalencias.get(clasificacion.getAttribute("origen"));
            UUID destino = equivalencias.get(clasificacion.getAttribute("destino"));
            if (origen == null || destino == null) {
                continue;
            }
            TipoRelacion tipo;
            try {
                tipo = TipoRelacion.valueOf(clasificacion.getAttribute("tipo"));
            } catch (IllegalArgumentException e) {
                tipo = TipoRelacion.ASOCIACION;
            }
            return Optional.of(relacion(origen, destino, tipo,
                    valorOPorDefecto(clasificacion.getAttribute("multiplicidadOrigen"), "1"),
                    valorOPorDefecto(clasificacion.getAttribute("multiplicidadDestino"), "1")));
        }

        // Camino general: reconstruir la relacion desde sus dos extremos, vengan
        // como propiedades de las clases o como extremos propios.
        List<ExtremoDeAsociacion> propios = new ArrayList<>(extremos.stream()
                .filter(e -> idAsociacion.equals(e.asociacion()))
                .toList());
        for (Element propio : hijosDirectos(asociacion, "ownedEnd")) {
            propios.add(new ExtremoDeAsociacion(atributoXmi(propio, "id"), idAsociacion, null,
                    referenciaDeTipo(propio), multiplicidad(propio),
                    propio.getAttribute("name"), propio.getAttribute("aggregation")));
        }

        List<String> orden = referenciasDeMiembros(asociacion);
        propios.sort((a, b) -> Integer.compare(posicion(orden, a), posicion(orden, b)));

        if (propios.size() < 2) {
            log.debug("Asociacion {} omitida: no se pudieron resolver sus dos extremos",
                    idAsociacion);
            return Optional.empty();
        }

        // El primer extremo es el que esta del lado del destino, que es como lo
        // escribe el exportador y como queda al listar memberEnd en orden.
        ExtremoDeAsociacion ladoDestino = propios.get(0);
        ExtremoDeAsociacion ladoOrigen = propios.get(1);

        UUID destino = equivalencias.get(ladoDestino.tipo());
        UUID origen = equivalencias.get(ladoOrigen.tipo());
        if (origen == null || destino == null) {
            return Optional.empty();
        }

        return Optional.of(relacion(origen, destino,
                tipoPorAgregacion(ladoDestino, ladoOrigen),
                ladoOrigen.multiplicidad(), ladoDestino.multiplicidad()));
    }

    /**
     * La agregacion y la composicion se reconocen por la marca del extremo.
     * Enterprise Architect la escribe asi cuando el documento no viene de
     * FORJA y no trae la clasificacion explicita.
     */
    private TipoRelacion tipoPorAgregacion(ExtremoDeAsociacion uno, ExtremoDeAsociacion otro) {
        for (ExtremoDeAsociacion extremo : List.of(uno, otro)) {
            String agregacion = extremo.agregacion();
            if ("composite".equalsIgnoreCase(agregacion)) {
                return TipoRelacion.COMPOSICION;
            }
            if ("shared".equalsIgnoreCase(agregacion)) {
                return TipoRelacion.AGREGACION;
            }
        }
        return TipoRelacion.ASOCIACION;
    }

    private List<String> referenciasDeMiembros(Element asociacion) {
        List<String> referencias = new ArrayList<>();
        for (Element miembro : hijosDirectos(asociacion, "memberEnd")) {
            String referencia = atributoXmi(miembro, "idref");
            if (referencia == null || referencia.isBlank()) {
                referencia = miembro.getAttribute("xmi:idref");
            }
            if (!referencia.isBlank()) {
                referencias.add(referencia);
            }
        }
        return referencias;
    }

    private int posicion(List<String> orden, ExtremoDeAsociacion extremo) {
        int indice = orden.indexOf(extremo.id());
        // Un extremo que no aparece en memberEnd va al final, sin alterar el
        // orden relativo de los que si aparecen.
        return indice < 0 ? Integer.MAX_VALUE : indice;
    }

    private ComandoOperacion relacion(UUID origen, UUID destino, TipoRelacion tipo,
                                      String multOrigen, String multDestino) {
        return new ComandoOperacion.CrearRelacion(UUID.randomUUID(), origen, destino, tipo,
                multOrigen, multDestino, null, null, null);
    }

    // ---------- Resolucion de tipos -----------------------------------------

    /**
     * Tipo declarado para un atributo o parametro, aceptando las tres formas en
     * que XMI permite escribirlo.
     */
    private String tipoDe(Element propiedad, Map<String, String> nombresDeTipos) {
        for (Element tipo : hijosDirectos(propiedad, "type")) {
            String href = tipo.getAttribute("href");
            if (!href.isBlank()) {
                int fragmento = href.lastIndexOf('#');
                return fragmento < 0 ? href : href.substring(fragmento + 1);
            }
            String referencia = atributoXmi(tipo, "idref");
            if (referencia != null && !referencia.isBlank()) {
                String nombre = nombresDeTipos.get(referencia);
                if (nombre != null && !nombre.isBlank()) {
                    return nombre;
                }
            }
        }
        String directo = propiedad.getAttribute("type");
        if (!directo.isBlank()) {
            String nombre = nombresDeTipos.get(directo);
            if (nombre != null && !nombre.isBlank()) {
                return nombre;
            }
        }
        // Sin tipo declarado se asume texto: es lo que menos restringe el
        // esquema que se genere despues.
        return "String";
    }

    /** Identificador de la clase a la que apunta un extremo de asociacion. */
    private String referenciaDeTipo(Element propiedad) {
        String directo = propiedad.getAttribute("type");
        if (!directo.isBlank()) {
            return directo;
        }
        for (Element tipo : hijosDirectos(propiedad, "type")) {
            String referencia = atributoXmi(tipo, "idref");
            if (referencia != null && !referencia.isBlank()) {
                return referencia;
            }
        }
        return null;
    }

    /**
     * Lee un extremo de dependencia, aceptando las dos formas que XMI permite:
     * como atributo del propio elemento o como hijo con {@code xmi:idref}.
     */
    private String referenciaDeExtremo(Element elemento, String nombre) {
        String directo = elemento.getAttribute(nombre);
        if (directo != null && !directo.isBlank()) {
            return directo.trim();
        }
        for (Element hijo : hijosDirectos(elemento, nombre)) {
            String referencia = atributoXmi(hijo, "idref");
            if (referencia != null && !referencia.isBlank()) {
                return referencia.trim();
            }
        }
        return null;
    }

    private String multiplicidad(Element propiedad) {
        String inferior = normalizarLimite(valorDeLimite(propiedad, "lowerValue", "1"));
        String superior = normalizarLimite(valorDeLimite(propiedad, "upperValue", "1"));
        if (inferior.equals(superior)) {
            return superior;
        }
        return inferior + ".." + superior;
    }

    /**
     * Deja el limite de una multiplicidad en la notacion que FORJA sabe manejar.
     * <p>
     * UML admite multiplicidades simbolicas -el modelo de ejemplo que trae
     * Enterprise Architect usa {@code [BookCount]}-, pero el generador de codigo
     * solo sabe traducir numeros y {@code *}, y la columna que las guarda es
     * corta. Sin esta normalizacion el valor viajaba sin mirarse hasta
     * PostgreSQL y la importacion de un archivo legitimo de EA se caia con un
     * error 500 por desbordar la columna. Ante algo que no se entiende se
     * asume {@code 1}, que es el valor por defecto de UML.
     */
    private String normalizarLimite(String valor) {
        if (valor == null) {
            return "1";
        }
        String limpio = valor.trim();
        if (limpio.equals("*") || limpio.equals("-1")) {
            return "*";
        }
        return limpio.matches("\\d{1,4}") ? limpio : "1";
    }

    private String valorDeLimite(Element propiedad, String limite, String pordefecto) {
        for (Element valor : hijosDirectos(propiedad, limite)) {
            String texto = valor.getAttribute("value");
            if (!texto.isBlank()) {
                return texto.trim();
            }
            // Un LiteralUnlimitedNatural sin valor significa infinito.
            String tipo = tipoXmi(valor);
            if (tipo.contains("UnlimitedNatural")) {
                return "*";
            }
        }
        return pordefecto;
    }

    private Visibilidad visibilidad(String texto) {
        if (texto == null || texto.isBlank()) {
            return Visibilidad.PRIVADO;
        }
        return switch (texto.trim().toLowerCase()) {
            case "public" -> Visibilidad.PUBLICO;
            case "protected" -> Visibilidad.PROTEGIDO;
            case "package" -> Visibilidad.PAQUETE;
            default -> Visibilidad.PRIVADO;
        };
    }

    // ---------- Lectura del documento ---------------------------------------

    /**
     * Lee el XML desactivando las entidades externas.
     * <p>
     * El documento llega subido por un usuario, y un XML puede pedirle al
     * analizador que resuelva referencias a archivos del servidor o a
     * direcciones de red. Desactivarlo cierra esa via -la vulnerabilidad
     * conocida como XXE- y no quita nada: un XMI legitimo no necesita
     * entidades externas.
     */
    private Document leer(String xmi) {
        if (xmi == null || xmi.isBlank()) {
            throw new XmiInvalido("El documento esta vacio");
        }
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setNamespaceAware(true);

            Document documento = fabrica.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xmi.getBytes(StandardCharsets.UTF_8)));
            documento.getDocumentElement().normalize();
            return documento;

        } catch (SAXException e) {
            throw new XmiInvalido("El documento no es XML bien formado: " + e.getMessage(), e);
        } catch (ParserConfigurationException | IOException e) {
            throw new XmiInvalido("No se pudo leer el documento", e);
        }
    }

    /** Todos los elementos con ese nombre local, sin importar el prefijo. */
    /**
     * Elementos del documento con alguno de esos nombres locales.
     * <p>
     * Se aceptan varios porque XMI permite mas de una forma de decir lo mismo:
     * un documento de UML 2.5.1 mete los clasificadores en {@code
     * packagedElement}, mientras que Enterprise Architect, cuando exporta en su
     * dialecto de XMI 2.1, los mete en {@code ownedMember}. Aceptar solo el
     * primero hacia que un archivo legitimo de EA se rechazara diciendo que no
     * contenia ninguna clase.
     */
    private List<Element> elementos(Document documento, String... nombresLocales) {
        List<Element> encontrados = new ArrayList<>();
        NodeList todos = documento.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node nodo = todos.item(i);
            if (!(nodo instanceof Element elemento)) {
                continue;
            }
            String local = local(elemento);
            for (String nombre : nombresLocales) {
                if (nombre.equals(local)) {
                    encontrados.add(elemento);
                    break;
                }
            }
        }
        return encontrados;
    }

    /** Hijos inmediatos con ese nombre local. */
    private List<Element> hijosDirectos(Element padre, String nombreLocal) {
        List<Element> encontrados = new ArrayList<>();
        NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            Node nodo = hijos.item(i);
            if (nodo instanceof Element elemento && nombreLocal.equals(local(elemento))) {
                encontrados.add(elemento);
            }
        }
        return encontrados;
    }

    /** Descendientes con ese nombre: para lo que viaja dentro de una extension. */
    private List<Element> hijos(Element padre, String nombreLocal) {
        List<Element> encontrados = new ArrayList<>();
        NodeList todos = padre.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Node nodo = todos.item(i);
            if (nodo instanceof Element elemento && nombreLocal.equals(local(elemento))) {
                encontrados.add(elemento);
            }
        }
        return encontrados;
    }

    private static String local(Element elemento) {
        return elemento.getLocalName() != null ? elemento.getLocalName() : elemento.getTagName();
    }

    /**
     * Atributo del espacio de nombres de XMI. Se prueba primero por espacio de
     * nombres y luego por el prefijo literal, porque no todos los documentos
     * declaran el espacio como corresponde.
     */
    private static String atributoXmi(Element elemento, String nombreLocal) {
        String porEspacio = elemento.getAttributeNS(NS_XMI, nombreLocal);
        if (porEspacio != null && !porEspacio.isBlank()) {
            return porEspacio;
        }
        String porPrefijo = elemento.getAttribute("xmi:" + nombreLocal);
        return porPrefijo.isBlank() ? null : porPrefijo;
    }

    private static String tipoXmi(Element elemento) {
        String tipo = atributoXmi(elemento, "type");
        return tipo == null ? "" : tipo;
    }

    private static String primeraReferencia(Element elemento, String atributo) {
        String directo = elemento.getAttribute(atributo);
        if (!directo.isBlank()) {
            return directo;
        }
        // Forma alternativa: la referencia como elemento hijo.
        for (Element hijo : List.copyOf(hijosDeNombre(elemento, atributo))) {
            String referencia = atributoXmi(hijo, "idref");
            if (referencia != null && !referencia.isBlank()) {
                return referencia;
            }
        }
        return "";
    }

    private static List<Element> hijosDeNombre(Element padre, String nombreLocal) {
        List<Element> encontrados = new ArrayList<>();
        NodeList hijos = padre.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof Element elemento && nombreLocal.equals(local(elemento))) {
                encontrados.add(elemento);
            }
        }
        return encontrados;
    }

    private static String valorOPorDefecto(String valor, String pordefecto) {
        return valor == null || valor.isBlank() ? pordefecto : valor;
    }

    // ---------- Estructuras internas ----------------------------------------

    /**
     * La posicion de cada clase segun la vista de Enterprise Architect.
     * <p>
     * EA no escribe la geometria dentro de la clase: la escribe aparte, en
     * {@code xmi:Extension/diagrams/diagram/elements}, como
     * {@code <element geometry="Left=137;Top=411;Right=337;Bottom=531;"
     * subject="EAID_..."/>}, donde {@code subject} referencia el identificador
     * del clasificador.
     * <p>
     * Sin esto, un modelo hecho en EA entraba con las clases acomodadas en una
     * fila y habia que ordenarlo a mano -justo lo que el intercambio viene a
     * evitar-. Se comprobo exportando desde FORJA, quitandole su propia
     * extension y volviendo a importar: las clases perdian su lugar.
     */
    private Map<String, Geometria> vistaDeEnterpriseArchitect(Document documento) {
        Map<String, Geometria> porSujeto = new HashMap<>();

        for (Element elemento : elementos(documento, "element")) {
            String sujeto = elemento.getAttribute("subject");
            String geometria = elemento.getAttribute("geometry");
            if (sujeto.isBlank() || geometria.isBlank()) {
                continue;
            }
            posicionDe(geometria).ifPresent(punto -> porSujeto.put(sujeto, punto));
        }
        return porSujeto;
    }

    /**
     * Lee "Left=137;Top=411;Right=337;Bottom=531;".
     * <p>
     * Solo interesan Left y Top: el ancho y el alto de la caja los decide el
     * lienzo a partir de cuantos atributos y operaciones tiene la clase, asi
     * que respetar los de EA daria cajas que no coinciden con su contenido.
     */
    private Optional<Geometria> posicionDe(String geometria) {
        Double izquierda = null;
        Double arriba = null;

        for (String parte : geometria.split(";")) {
            String[] clave = parte.split("=", 2);
            if (clave.length != 2) {
                continue;
            }
            try {
                double valor = Double.parseDouble(clave[1].trim());
                if ("Left".equalsIgnoreCase(clave[0].trim())) {
                    izquierda = valor;
                } else if ("Top".equalsIgnoreCase(clave[0].trim())) {
                    arriba = valor;
                }
            } catch (NumberFormatException e) {
                // Una coordenada ilegible no invalida el documento: se cae al
                // acomodo por omision, que es peor pero sigue siendo utilizable.
                return Optional.empty();
            }
        }
        return izquierda == null || arriba == null
                ? Optional.empty()
                : Optional.of(new Geometria(izquierda, arriba));
    }

    private record Geometria(double x, double y) {
    }

    private record Almacenamiento(boolean esIdentificador, boolean esUnico, Integer longitud) {
    }

    private record ExtremoDeAsociacion(
            String id,
            String asociacion,
            String claseQueLoPosee,
            String tipo,
            String multiplicidad,
            String nombre,
            String agregacion) {
    }
}
