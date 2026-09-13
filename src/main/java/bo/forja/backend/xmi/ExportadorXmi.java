package bo.forja.backend.xmi;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.MetodoUml;
import bo.forja.backend.dominio.ParametroUml;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exporta el diagrama a XMI 2.5.1, el formato con el que Enterprise
 * Architect intercambia modelos.
 * <p>
 * Se escribe el XML a mano en lugar de armar un arbol DOM porque el
 * documento es de estructura fija y conocida: componerlo directamente deja
 * a la vista la forma exacta del resultado, que es lo que hay que poder
 * comparar contra la especificacion cuando una herramienta se niega a
 * importarlo.
 * <p>
 * <b>Sobre lo que UML no puede expresar.</b> El modelo guarda por atributo
 * una longitud, si es identificador y si es unico. Nada de eso tiene lugar
 * en un {@code uml:Property}: son propiedades del almacenamiento, no del
 * modelo conceptual. En vez de forzarlas dentro del estandar o de
 * perderlas, viajan en un bloque {@code xmi:Extension} con extensor
 * {@code FORJA}, el mismo mecanismo que usa Enterprise Architect para sus
 * propios datos. Una herramienta ajena ignora la extension y lee un modelo
 * valido; FORJA la lee y recupera el modelo completo.
 */
@Component
public class ExportadorXmi {

    static final String EXTENSOR = "FORJA";

    /** Tipos con equivalente en la biblioteca de tipos primitivos de UML. */
    private static final Map<String, String> PRIMITIVOS_UML = Map.of(
            "String", "String",
            "Integer", "Integer",
            "int", "Integer",
            "Boolean", "Boolean",
            "boolean", "Boolean",
            "Real", "Real",
            "double", "Real",
            "UnlimitedNatural", "UnlimitedNatural");

    /**
     * Dialecto de XMI en el que se escribe el documento.
     * <p>
     * Se emite XMI 2.1 con el vocabulario de UML 2, y no los identificadores de
     * espacio de nombres de UML 2.5.1 (XMI/20131001 y UML/20161101), porque
     * Enterprise Architect 17 no reconoce estos ultimos: ante ellos su
     * importador no da error, simplemente no trae ningun elemento. Se comprobo
     * contra EA por su interfaz de automatizacion: con estos valores importa el
     * modelo completo y con los de 2.5.1 importa cero. La estructura del
     * documento sigue siendo la de UML 2.5.1, que es la que pide el enunciado;
     * lo unico que cambia es la declaracion de espacios de nombres, que es lo
     * que EA mira para decidir si entiende el archivo.
     */
    private static final String VERSION_FORJA = "1.0";
    private static final String VERSION_XMI = "2.1";
    private static final String NS_XMI = "http://schema.omg.org/spec/XMI/2.1";
    private static final String NS_UML = "http://schema.omg.org/spec/UML/2.0";

    private static final String HREF_PRIMITIVOS =
            "http://schema.omg.org/spec/UML/2.1/uml.xml#";

    public String exportar(Diagrama diagrama, List<ClaseUml> clases, List<RelacionUml> relaciones) {
        StringBuilder xml = new StringBuilder();

        // Los tipos que no son primitivos de UML se declaran como uml:DataType
        // dentro del modelo, para que la referencia del atributo apunte a algo
        // que exista en el documento.
        Map<String, String> tiposDeclarados = declararTipos(clases);

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<xmi:XMI xmi:version=\"" + VERSION_XMI + "\"\n");
        xml.append("         xmlns:xmi=\"" + NS_XMI + "\"\n");
        xml.append("         xmlns:uml=\"" + NS_UML + "\">\n");
        // Enterprise Architect solo lee su seccion de diagramas si el documento
        // declara haber salido de Enterprise Architect: con cualquier otro
        // nombre importa el modelo pero descarta la vista en silencio, y las
        // clases aparecen en el arbol del proyecto con el lienzo vacio. Se
        // comprobo importando el mismo documento con un nombre y con el otro,
        // sin cambiar nada mas. Es una cadena de compatibilidad, del mismo tipo
        // que las que un navegador declara para que un servidor no le niegue
        // contenido; quien genero el archivo queda escrito debajo y en la
        // extension propia que lleva cada clase.
        xml.append("  <xmi:Documentation exporter=\"Enterprise Architect\""
                + " exporterVersion=\"6.5\"/>\n");
        xml.append("  <!-- Generado por FORJA " + VERSION_FORJA + ". El exportador se declara"
                + " como Enterprise Architect por compatibilidad: ver ExportadorXmi. -->\n");
        xml.append("  <uml:Model xmi:type=\"uml:Model\" xmi:id=\"").append(id(diagrama.getId()))
                .append("\" name=\"").append(escapar(diagrama.getNombre())).append("\">\n");

        // Todo cuelga de un paquete porque la vista tiene que pertenecer a uno:
        // un diagrama suelto, sin paquete que lo contenga, no tiene donde
        // aparecer en el arbol del proyecto de la herramienta que lo abra.
        String paquete = idDelPaquete(diagrama.getId());
        xml.append("    <packagedElement xmi:type=\"uml:Package\" xmi:id=\"").append(paquete)
                .append("\" name=\"").append(escapar(diagrama.getNombre())).append("\">\n");

        for (ClaseUml clase : clases) {
            escribirClase(xml, clase, relaciones, tiposDeclarados);
        }
        for (RelacionUml relacion : relaciones) {
            escribirAsociacion(xml, relacion);
            escribirDependencia(xml, relacion);
            escribirRealizacion(xml, relacion);
        }
        for (Map.Entry<String, String> tipo : tiposDeclarados.entrySet()) {
            xml.append("    <packagedElement xmi:type=\"uml:DataType\" xmi:id=\"")
                    .append(tipo.getValue()).append("\" name=\"")
                    .append(escapar(tipo.getKey())).append("\"/>\n");
        }

        xml.append("    </packagedElement>\n");
        xml.append("  </uml:Model>\n");
        escribirVista(xml, diagrama, clases, relaciones, paquete);
        xml.append("</xmi:XMI>\n");
        return xml.toString();
    }

    /**
     * Escribe la vista: el diagrama con la posicion de cada clase.
     * <p>
     * Sin esto, quien abre el documento en Enterprise Architect recibe las
     * clases y sus relaciones, pero ningun dibujo: aparecen en el arbol del
     * proyecto y el lienzo queda vacio, y hay que arrastrarlas a mano una por
     * una. Las coordenadas ya existen en el modelo -son las del lienzo de
     * FORJA- y esto es lo que las hace viajar.
     * <p>
     * La vista no cabe en UML, que describe el modelo y no como se dibuja, asi
     * que viaja en la extension que Enterprise Architect declara para eso. Se
     * emite despues de {@code uml:Model} y no dentro, porque es informacion de
     * la herramienta y no del modelo: otra herramienta la ignora y se queda con
     * el modelo completo, que es justamente lo que se busca.
     */
    private void escribirVista(StringBuilder xml, Diagrama diagrama, List<ClaseUml> clases,
                               List<RelacionUml> relaciones, String paquete) {

        xml.append("  <xmi:Extension extender=\"Enterprise Architect\" extenderID=\"6.5\">\n");
        escribirConectores(xml, relaciones);
        xml.append("    <diagrams>\n");
        xml.append("      <diagram xmi:id=\"").append(idDeLaVista(diagrama.getId())).append("\">\n");
        xml.append("        <model package=\"").append(paquete)
                .append("\" localID=\"1\" owner=\"").append(paquete).append("\"/>\n");
        xml.append("        <properties name=\"").append(escapar(diagrama.getNombre()))
                .append("\" type=\"Logical\"/>\n");
        xml.append("        <project author=\"FORJA\" version=\"1.0\"/>\n");
        xml.append("        <elements>\n");

        int orden = 0;
        for (ClaseUml clase : clases) {
            // La geometria va en el rectangulo que usa la herramienta: el borde
            // derecho y el inferior son absolutos, no un ancho y un alto.
            long izquierda = Math.round(clase.getPosX());
            long arriba = Math.round(clase.getPosY());
            xml.append("          <element geometry=\"Left=").append(izquierda)
                    .append(";Top=").append(arriba)
                    .append(";Right=").append(izquierda + Math.round(clase.getAncho()))
                    .append(";Bottom=").append(arriba + Math.round(clase.getAlto()))
                    .append(";\" subject=\"").append(id(clase.getId()))
                    .append("\" seqno=\"").append(++orden).append("\"/>\n");
        }

        xml.append("        </elements>\n");
        xml.append("      </diagram>\n");
        xml.append("    </diagrams>\n");
        xml.append("  </xmi:Extension>\n");
    }

    /** Una clase marcada como interfaz se exporta como uml:Interface. */
    private static boolean esInterfaz(ClaseUml clase) {
        String estereotipo = clase.getEstereotipo();
        return estereotipo != null
                && ("interface".equalsIgnoreCase(estereotipo.trim())
                || "interfaz".equalsIgnoreCase(estereotipo.trim()));
    }

    /**
     * Repite las relaciones en el vocabulario de conectores de la herramienta.
     * <p>
     * Es duplicacion deliberada y molesta: las relaciones ya estan descritas
     * arriba en UML, como asociaciones con sus extremos. Pero al declarar el
     * documento como salido de Enterprise Architect -sin lo cual EA descarta la
     * vista- EA pasa a leer los vinculos de esta seccion y deja de mirar las
     * asociaciones de UML. Sin esto, importar el archivo trae las clases y el
     * dibujo pero las asociaciones desaparecen: se comprobo, se caian de ocho
     * relaciones a tres. La herencia y la dependencia sobreviven de todos modos
     * porque EA las sigue leyendo del UML, pero se escriben igual para no
     * depender de esa asimetria.
     */
    private void escribirConectores(StringBuilder xml, List<RelacionUml> relaciones) {
        if (relaciones.isEmpty()) {
            return;
        }
        xml.append("    <connectors>\n");
        for (RelacionUml relacion : relaciones) {
            xml.append("      <connector xmi:idref=\"").append(id(relacion.getId())).append("\">\n");
            extremoDelConector(xml, "source", relacion.getOrigen(),
                    relacion.getMultiplicidadOrigen(), agregacionDeEa(relacion.getTipo()));
            extremoDelConector(xml, "target", relacion.getDestino(),
                    relacion.getMultiplicidadDestino(), "none");
            xml.append("        <properties ea_type=\"").append(tipoDeEa(relacion.getTipo()))
                    .append("\" direction=\"Source -&gt; Destination\"/>\n");
            xml.append("      </connector>\n");
        }
        xml.append("    </connectors>\n");
    }

    private void extremoDelConector(StringBuilder xml, String lado, ClaseUml clase,
                                    String multiplicidad, String agregacion) {
        xml.append("        <").append(lado).append(" xmi:idref=\"").append(id(clase.getId()))
                .append("\">\n");
        xml.append("          <model type=\"").append(esInterfaz(clase) ? "Interface" : "Class")
                .append("\" name=\"").append(escapar(clase.getNombre())).append("\"/>\n");
        xml.append("          <role visibility=\"Public\"/>\n");
        xml.append("          <type multiplicity=\"").append(escapar(multiplicidad))
                .append("\" aggregation=\"").append(agregacion)
                .append("\" containment=\"Unspecified\"/>\n");
        xml.append("        </").append(lado).append(">\n");
    }

    /** Nombre que la herramienta le da a cada clase de vinculo. */
    private static String tipoDeEa(TipoRelacion tipo) {
        return switch (tipo) {
            case ASOCIACION -> "Association";
            case AGREGACION, COMPOSICION -> "Aggregation";
            case HERENCIA -> "Generalization";
            case REALIZACION -> "Realisation";
            case DEPENDENCIA -> "Dependency";
        };
    }

    private static String agregacionDeEa(TipoRelacion tipo) {
        String marca = agregacionDe(tipo);
        return marca != null ? marca : "none";
    }

    /** Identificador del paquete que contiene el diagrama. */
    private static String idDelPaquete(UUID diagramaId) {
        return "paquete" + id(diagramaId);
    }

    /** Identificador de la vista, distinto del modelo al que retrata. */
    private static String idDeLaVista(UUID diagramaId) {
        return "vista" + id(diagramaId);
    }

    // ---------- Clases ------------------------------------------------------

    private void escribirClase(StringBuilder xml, ClaseUml clase,
                               List<RelacionUml> relaciones,
                               Map<String, String> tiposDeclarados) {

        boolean esInterfaz = esInterfaz(clase);
        String tipoXmi = esInterfaz ? "uml:Interface" : "uml:Class";

        xml.append("    <packagedElement xmi:type=\"").append(tipoXmi).append("\" xmi:id=\"")
                .append(id(clase.getId())).append("\" name=\"")
                .append(escapar(clase.getNombre())).append("\"");
        if (clase.isEsAbstracta()) {
            xml.append(" isAbstract=\"true\"");
        }
        xml.append(">\n");

        // La generalizacion se declara dentro de la subclase, como manda UML:
        // es la subclase la que conoce de quien deriva.
        relaciones.stream()
                .filter(r -> r.getTipo() == TipoRelacion.HERENCIA)
                .filter(r -> r.getOrigen().getId().equals(clase.getId()))
                .forEach(r -> xml.append("      <generalization xmi:type=\"uml:Generalization\"")
                        .append(" xmi:id=\"").append(id(r.getId()))
                        .append("\" general=\"").append(id(r.getDestino().getId()))
                        .append("\"/>\n"));

        relaciones.stream()
                .filter(r -> r.getTipo() == TipoRelacion.REALIZACION)
                .filter(r -> r.getOrigen().getId().equals(clase.getId()))
                .forEach(r -> xml.append("      <interfaceRealization")
                        .append(" xmi:type=\"uml:InterfaceRealization\" xmi:id=\"")
                        .append(id(r.getId())).append("\" client=\"").append(id(clase.getId()))
                        .append("\" supplier=\"").append(id(r.getDestino().getId()))
                        .append("\" contract=\"").append(id(r.getDestino().getId()))
                        .append("\"/>\n"));

        clase.getAtributos().forEach(a -> escribirAtributo(xml, a, tiposDeclarados));
        clase.getMetodos().forEach(m -> escribirOperacion(xml, m, tiposDeclarados));

        // Los extremos navegables de las asociaciones se declaran como
        // propiedades de la clase, que es la forma en que Enterprise Architect
        // espera encontrarlos.
        relaciones.stream()
                .filter(r -> esEstructural(r.getTipo()))
                .forEach(r -> escribirExtremos(xml, clase, r));

        xml.append("      <xmi:Extension extender=\"").append(EXTENSOR).append("\">\n");
        xml.append("        <geometria x=\"").append(clase.getPosX()).append("\" y=\"")
                .append(clase.getPosY()).append("\" ancho=\"").append(clase.getAncho())
                .append("\" alto=\"").append(clase.getAlto()).append("\"/>\n");
        if (clase.getEstereotipo() != null && !esInterfaz) {
            xml.append("        <estereotipo nombre=\"")
                    .append(escapar(clase.getEstereotipo())).append("\"/>\n");
        }
        xml.append("      </xmi:Extension>\n");

        xml.append("    </packagedElement>\n");
    }

    private void escribirAtributo(StringBuilder xml, AtributoUml atributo,
                                  Map<String, String> tiposDeclarados) {
        xml.append("      <ownedAttribute xmi:type=\"uml:Property\" xmi:id=\"")
                .append(id(atributo.getId())).append("\" name=\"")
                .append(escapar(atributo.getNombre())).append("\" visibility=\"")
                .append(visibilidad(atributo.getVisibilidad())).append("\"");

        // Los tipos que no son primitivos de UML se referencian ademas con el
        // atributo type. Enterprise Architect declaraba el uml:DataType pero
        // dejaba el atributo sin tipo cuando la referencia venia solo en el
        // hijo <type>, que es como haber perdido el tipo al viajar; con la
        // referencia aqui la resuelve, igual que hace con los extremos de
        // asociacion, que siempre llegaron bien.
        String declarado = tiposDeclarados.get(atributo.getTipo());
        if (declarado != null) {
            xml.append(" type=\"").append(declarado).append("\"");
        }
        xml.append(">\n");

        escribirTipo(xml, atributo.getTipo(), tiposDeclarados, "        ");

        // La obligatoriedad si tiene lugar en UML: es el limite inferior de la
        // multiplicidad del atributo.
        xml.append("        <lowerValue xmi:type=\"uml:LiteralInteger\" value=\"")
                .append(atributo.isEsRequerido() ? 1 : 0).append("\"/>\n");
        xml.append("        <upperValue xmi:type=\"uml:LiteralUnlimitedNatural\" value=\"1\"/>\n");

        xml.append("        <xmi:Extension extender=\"").append(EXTENSOR).append("\">\n");
        xml.append("          <almacenamiento esIdentificador=\"")
                .append(atributo.isEsIdentificador()).append("\" esUnico=\"")
                .append(atributo.isEsUnico()).append("\"");
        if (atributo.getLongitud() != null) {
            xml.append(" longitud=\"").append(atributo.getLongitud()).append("\"");
        }
        if (atributo.getValorDefecto() != null) {
            xml.append(" valorDefecto=\"").append(escapar(atributo.getValorDefecto())).append("\"");
        }
        xml.append("/>\n");
        xml.append("        </xmi:Extension>\n");

        xml.append("      </ownedAttribute>\n");
    }

    private void escribirOperacion(StringBuilder xml, MetodoUml metodo,
                                   Map<String, String> tiposDeclarados) {
        xml.append("      <ownedOperation xmi:type=\"uml:Operation\" xmi:id=\"")
                .append(id(metodo.getId())).append("\" name=\"")
                .append(escapar(metodo.getNombre())).append("\" visibility=\"")
                .append(visibilidad(metodo.getVisibilidad())).append("\"");
        if (metodo.isEsAbstracto()) {
            xml.append(" isAbstract=\"true\"");
        }
        if (metodo.isEsEstatico()) {
            xml.append(" isStatic=\"true\"");
        }
        xml.append(">\n");

        for (ParametroUml parametro : metodo.getParametros()) {
            xml.append("        <ownedParameter xmi:type=\"uml:Parameter\" xmi:id=\"")
                    .append(id(parametro.getId())).append("\" name=\"")
                    .append(escapar(parametro.getNombre())).append("\" direction=\"in\">\n");
            escribirTipo(xml, parametro.getTipo(), tiposDeclarados, "          ");
            xml.append("        </ownedParameter>\n");
        }

        // El tipo de retorno es un parametro con direccion "return": en UML una
        // operacion no tiene un campo de retorno aparte.
        if (metodo.getTipoRetorno() != null && !metodo.getTipoRetorno().isBlank()
                && !"void".equalsIgnoreCase(metodo.getTipoRetorno())) {
            xml.append("        <ownedParameter xmi:type=\"uml:Parameter\" xmi:id=\"ret-")
                    .append(id(metodo.getId())).append("\" name=\"return\" direction=\"return\">\n");
            escribirTipo(xml, metodo.getTipoRetorno(), tiposDeclarados, "          ");
            xml.append("        </ownedParameter>\n");
        }

        xml.append("      </ownedOperation>\n");
    }

    // ---------- Asociaciones ------------------------------------------------

    /**
     * Extremos navegables que la clase posee. Se emite el extremo <i>opuesto</i>
     * a la clase: una propiedad de Paciente cuyo tipo es Consulta representa
     * "el Paciente conoce sus Consultas".
     */
    private void escribirExtremos(StringBuilder xml, ClaseUml clase, RelacionUml relacion) {
        boolean esOrigen = relacion.getOrigen().getId().equals(clase.getId());
        boolean esDestino = relacion.getDestino().getId().equals(clase.getId());
        if (!esOrigen && !esDestino) {
            return;
        }

        // El extremo que pertenece al todo es el que lleva la marca de
        // agregacion; en FORJA el todo es siempre el origen de la relacion.
        if (esOrigen) {
            propiedadDeAsociacion(xml, relacion, extremoOrigen(relacion),
                    relacion.getDestino().getId(), relacion.getRolDestino(),
                    relacion.getDestino().getNombre(), relacion.getMultiplicidadDestino(),
                    agregacionDe(relacion.getTipo()));
        }
        if (esDestino) {
            propiedadDeAsociacion(xml, relacion, extremoDestino(relacion),
                    relacion.getOrigen().getId(), relacion.getRolOrigen(),
                    relacion.getOrigen().getNombre(), relacion.getMultiplicidadOrigen(),
                    null);
        }
    }

    /**
     * Traduce el tipo de relacion a la marca de agregacion de UML.
     * <p>
     * Sin esto la composicion y la agregacion llegan a Enterprise Architect
     * como asociaciones simples: se pierde el rombo en el diagrama y, con el,
     * la semantica de pertenencia de la que el generador deduce el borrado en
     * cascada.
     */
    private static String agregacionDe(TipoRelacion tipo) {
        return switch (tipo) {
            case COMPOSICION -> "composite";
            case AGREGACION -> "shared";
            default -> null;
        };
    }

    private void propiedadDeAsociacion(StringBuilder xml, RelacionUml relacion, String idExtremo,
                                       UUID tipoApuntado, String rol, String nombreDeLaClase,
                                       String multiplicidad, String agregacion) {
        String nombre = rol != null && !rol.isBlank() ? rol : nombreDeLaClase;
        Limites limites = Limites.de(multiplicidad);

        xml.append("      <ownedAttribute xmi:type=\"uml:Property\" xmi:id=\"").append(idExtremo)
                .append("\" name=\"").append(escapar(nombre))
                .append("\" type=\"").append(id(tipoApuntado))
                .append("\" association=\"").append(id(relacion.getId())).append("\"");
        if (agregacion != null) {
            xml.append(" aggregation=\"").append(agregacion).append("\"");
        }
        xml.append(">\n");
        xml.append("        <lowerValue xmi:type=\"uml:LiteralInteger\" value=\"")
                .append(limites.inferior()).append("\"/>\n");
        xml.append("        <upperValue xmi:type=\"uml:LiteralUnlimitedNatural\" value=\"")
                .append(limites.superior()).append("\"/>\n");
        xml.append("      </ownedAttribute>\n");
    }

    /**
     * Escribe la dependencia como lo que es en UML: un vinculo dirigido sin
     * extremos ni multiplicidad.
     * <p>
     * Antes viajaba disfrazada de asociacion, y Enterprise Architect la
     * mostraba como una asociacion mas, con una flecha y una cardinalidad que
     * el modelo nunca declaro.
     */
    private void escribirDependencia(StringBuilder xml, RelacionUml relacion) {
        if (relacion.getTipo() != TipoRelacion.DEPENDENCIA) {
            return;
        }
        xml.append("    <packagedElement xmi:type=\"uml:Dependency\" xmi:id=\"")
                .append(id(relacion.getId())).append("\"");
        if (relacion.getEtiqueta() != null && !relacion.getEtiqueta().isBlank()) {
            xml.append(" name=\"").append(escapar(relacion.getEtiqueta())).append("\"");
        }
        xml.append(" client=\"").append(id(relacion.getOrigen().getId()))
                .append("\" supplier=\"").append(id(relacion.getDestino().getId()))
                .append("\"/>\n");
    }

    /**
     * Declara la realizacion tambien como elemento del paquete.
     * <p>
     * Ya viaja dentro de la clase como {@code interfaceRealization}, que es la
     * forma que manda UML, pero Enterprise Architect no la mira: al leer el
     * documento en su modo propio -el que hace falta para que acepte la vista-
     * la realizacion desaparecia y las ocho relaciones llegaban como siete.
     * Declarada ademas aqui, EA la reconoce y el resto de las herramientas
     * encuentran las dos formas, que dicen lo mismo.
     */
    private void escribirRealizacion(StringBuilder xml, RelacionUml relacion) {
        if (relacion.getTipo() != TipoRelacion.REALIZACION) {
            return;
        }
        xml.append("    <packagedElement xmi:type=\"uml:Realization\" xmi:id=\"")
                .append(id(relacion.getId())).append("-realizacion\" client=\"")
                .append(id(relacion.getOrigen().getId())).append("\" supplier=\"")
                .append(id(relacion.getDestino().getId())).append("\"/>\n");
    }

    private void escribirAsociacion(StringBuilder xml, RelacionUml relacion) {
        if (!esEstructural(relacion.getTipo())) {
            return;
        }
        xml.append("    <packagedElement xmi:type=\"uml:Association\" xmi:id=\"")
                .append(id(relacion.getId())).append("\"");
        if (relacion.getEtiqueta() != null && !relacion.getEtiqueta().isBlank()) {
            xml.append(" name=\"").append(escapar(relacion.getEtiqueta())).append("\"");
        }
        xml.append(">\n");
        xml.append("      <memberEnd xmi:idref=\"").append(extremoOrigen(relacion)).append("\"/>\n");
        xml.append("      <memberEnd xmi:idref=\"").append(extremoDestino(relacion)).append("\"/>\n");

        // La agregacion y la composicion son asociaciones con un extremo
        // marcado; el tipo exacto no cabe en uml:Association y se conserva en
        // la extension para no perderlo al volver.
        xml.append("      <xmi:Extension extender=\"").append(EXTENSOR).append("\">\n");
        xml.append("        <clasificacion tipo=\"").append(relacion.getTipo().name())
                .append("\" multiplicidadOrigen=\"")
                .append(escapar(relacion.getMultiplicidadOrigen()))
                .append("\" multiplicidadDestino=\"")
                .append(escapar(relacion.getMultiplicidadDestino()))
                .append("\" origen=\"").append(id(relacion.getOrigen().getId()))
                .append("\" destino=\"").append(id(relacion.getDestino().getId()))
                .append("\"/>\n");
        xml.append("      </xmi:Extension>\n");
        xml.append("    </packagedElement>\n");
    }

    // ---------- Tipos -------------------------------------------------------

    private Map<String, String> declararTipos(List<ClaseUml> clases) {
        Set<String> usados = new LinkedHashSet<>();
        for (ClaseUml clase : clases) {
            clase.getAtributos().forEach(a -> usados.add(a.getTipo()));
            clase.getMetodos().forEach(m -> {
                if (m.getTipoRetorno() != null && !"void".equalsIgnoreCase(m.getTipoRetorno())) {
                    usados.add(m.getTipoRetorno());
                }
                m.getParametros().forEach(p -> usados.add(p.getTipo()));
            });
        }

        Map<String, String> declarados = new LinkedHashMap<>();
        int contador = 0;
        for (String tipo : usados) {
            if (tipo == null || tipo.isBlank() || PRIMITIVOS_UML.containsKey(tipo)) {
                continue;
            }
            declarados.put(tipo, "tipo-" + (++contador));
        }
        return declarados;
    }

    private void escribirTipo(StringBuilder xml, String tipo,
                              Map<String, String> tiposDeclarados, String sangria) {
        if (tipo == null || tipo.isBlank()) {
            return;
        }
        String primitivo = PRIMITIVOS_UML.get(tipo);
        if (primitivo != null) {
            xml.append(sangria).append("<type xmi:type=\"uml:PrimitiveType\" href=\"")
                    .append(HREF_PRIMITIVOS).append(primitivo).append("\"/>\n");
            return;
        }
        String declarado = tiposDeclarados.get(tipo);
        if (declarado != null) {
            // Con el idref a secas Enterprise Architect creaba el DataType pero
            // dejaba el atributo sin tipo, que es como haberlo perdido.
            xml.append(sangria).append("<type xmi:type=\"uml:DataType\" xmi:idref=\"")
                    .append(declarado).append("\"/>\n");
        }
    }

    // ---------- Auxiliares --------------------------------------------------

    /**
     * Relaciones que se materializan como asociacion con dos extremos.
     * <p>
     * La dependencia queda fuera a proposito: no tiene extremos ni
     * multiplicidad, y escribirla como asociacion hacia que Enterprise
     * Architect la mostrara como una asociacion mas.
     */
    private static boolean esEstructural(TipoRelacion tipo) {
        return tipo == TipoRelacion.ASOCIACION || tipo == TipoRelacion.AGREGACION
                || tipo == TipoRelacion.COMPOSICION;
    }

    private static String extremoOrigen(RelacionUml relacion) {
        return "ext-o-" + id(relacion.getId());
    }

    private static String extremoDestino(RelacionUml relacion) {
        return "ext-d-" + id(relacion.getId());
    }

    /**
     * Los identificadores de XMI son NCName y no pueden empezar con un digito,
     * que es algo que un UUID hace la mitad de las veces. El guion bajo delante
     * lo resuelve sin perder el valor original.
     */
    static String id(UUID identificador) {
        return "_" + identificador;
    }

    private static String visibilidad(Visibilidad visibilidad) {
        if (visibilidad == null) {
            return "private";
        }
        return switch (visibilidad) {
            case PUBLICO -> "public";
            case PRIVADO -> "private";
            case PROTEGIDO -> "protected";
            case PAQUETE -> "package";
        };
    }

    static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /** Limites de una multiplicidad, en la forma en que los escribe XMI. */
    private record Limites(int inferior, String superior) {

        static Limites de(String texto) {
            String limpio = texto == null || texto.isBlank() ? "1" : texto.trim();
            int separador = limpio.indexOf("..");
            String inferior = separador >= 0 ? limpio.substring(0, separador).trim() : limpio;
            String superior = separador >= 0 ? limpio.substring(separador + 2).trim() : limpio;

            int minimo;
            try {
                minimo = Integer.parseInt(inferior);
            } catch (NumberFormatException e) {
                minimo = inferior.equals("*") ? 0 : 1;
            }
            // En XMI el infinito de UML se escribe con asterisco.
            return new Limites(minimo, superior.equals("n") ? "*" : superior);
        }
    }
}
