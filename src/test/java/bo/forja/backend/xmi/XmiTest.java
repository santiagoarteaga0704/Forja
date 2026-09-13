package bo.forja.backend.xmi;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.MetodoUml;
import bo.forja.backend.dominio.Operacion;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.ParametroUml;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import bo.forja.backend.servicio.ServicioModelo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificacion del intercambio XMI 2.5.1.
 * <p>
 * La prueba central es un <b>viaje de ida y vuelta</b>: se exporta un modelo,
 * se importa el documento en un diagrama vacio y se comprueba que el
 * resultado describe lo mismo que el original. Es lo unico que verifica un
 * intercambio de verdad, porque un exportador puede producir un XMI
 * impecable y un importador puede leer sin errores un documento del que
 * pierde la mitad de la informacion; solo comparando el modelo reconstruido
 * contra el de partida aparecen esas perdidas.
 * <p>
 * La compatibilidad con Enterprise Architect se cubre por separado, leyendo
 * un documento escrito con las <i>otras</i> formas que XMI permite para lo
 * mismo. Lo que ninguna prueba automatica puede reemplazar es abrir el
 * archivo exportado en Enterprise Architect, y eso queda como verificacion
 * manual antes de la presentacion.
 */
@SpringBootTest
@DisplayName("Intercambio XMI con Enterprise Architect")
class XmiTest {

    @Autowired
    private ServicioXmi xmi;
    @Autowired
    private ServicioModelo modelo;

    @Autowired
    private UsuarioRepositorio usuarios;
    @Autowired
    private ProyectoRepositorio proyectos;
    @Autowired
    private ProyectoMiembroRepositorio miembros;
    @Autowired
    private DiagramaRepositorio diagramas;
    @Autowired
    private ClaseUmlRepositorio clases;
    @Autowired
    private RelacionUmlRepositorio relaciones;
    @Autowired
    private OperacionRepositorio operaciones;

    private Usuario autor;
    private Proyecto proyecto;
    private Diagrama original;
    private Diagrama vacio;

    @BeforeEach
    void prepararEscenario() {
        limpiar();

        autor = new Usuario();
        autor.setEmail("xmi-" + UUID.randomUUID() + "@forja.test");
        autor.setNombre("Autora del modelo");
        autor.setPasswordHash("no-relevante");
        usuarios.save(autor);

        proyecto = new Proyecto();
        proyecto.setNombre("Clinica");
        proyecto.setPropietario(autor);
        proyectos.save(proyecto);

        ProyectoMiembro miembro = new ProyectoMiembro();
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), autor.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(autor);
        miembro.setRol(RolMiembro.PROPIETARIO);
        miembros.save(miembro);

        original = nuevoDiagrama("Modelo original");
        vacio = nuevoDiagrama("Destino de la importacion");

        construirModelo();
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("el documento exportado tiene la estructura de XMI 2.5.1")
    void elDocumentoEsXmi251() {
        String documento = xmi.exportar(original.getId(), autor.getId());

        // Los espacios de nombres son los de XMI 2.1, no los de 2.5.1, y es
        // deliberado: se comprobo contra Enterprise Architect 17 por su
        // interfaz de automatizacion que con los de 2.5.1 el importador de EA
        // no da error pero no trae ni un elemento, mientras que con estos trae
        // el modelo entero. La estructura del documento -que es lo que la
        // prueba verifica debajo- sigue siendo la de UML 2.5.1.
        assertThat(documento)
                .contains("xmi:version=\"2.1\"")
                .contains("xmlns:uml=\"http://schema.omg.org/spec/UML/2.0\"")
                .contains("xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\"")
                .contains("<uml:Model xmi:type=\"uml:Model\"")
                .contains("<packagedElement xmi:type=\"uml:Class\"")
                .contains("<packagedElement xmi:type=\"uml:Interface\"")
                .contains("<packagedElement xmi:type=\"uml:Association\"")
                .contains("<ownedAttribute xmi:type=\"uml:Property\"")
                .contains("<ownedOperation xmi:type=\"uml:Operation\"")
                .contains("<generalization xmi:type=\"uml:Generalization\"")
                .contains("<interfaceRealization")
                .contains("isAbstract=\"true\"");

        // Los tipos estandar apuntan a la biblioteca de tipos primitivos de UML.
        assertThat(documento)
                .contains("href=\"http://schema.omg.org/spec/UML/2.1/uml.xml#String\"");

        // La composicion y la agregacion se marcan en el extremo que las lleva:
        // sin esto llegan a Enterprise Architect como asociaciones sin rombo.
        assertThat(documento).contains("aggregation=\"composite\"");

        // La dependencia viaja como lo que es, no disfrazada de asociacion.
        assertThat(documento).contains("<packagedElement xmi:type=\"uml:Dependency\"");

        // Lo que UML no puede expresar viaja en una extension declarada.
        assertThat(documento)
                .contains("<xmi:Extension extender=\"FORJA\">")
                .contains("esIdentificador=");
    }

    @Test
    @DisplayName("exportar e importar reconstruye el mismo modelo")
    void elViajeDeIdaYVueltaConservaElModelo() {
        String documento = xmi.exportar(original.getId(), autor.getId());

        ServicioXmi.ResumenImportacion resumen = xmi.importar(
                vacio.getId(), autor.getId(), "sesion-importacion", documento, "token-importacion");

        assertThat(resumen.problemas())
                .as("ningun comando deberia ser rechazado por el modelo")
                .isEmpty();
        assertThat(resumen.rechazadasPorBloqueo()).isZero();
        assertThat(resumen.aplicadas()).isEqualTo(resumen.comandosLeidos());

        assertThat(descripcion(vacio.getId()))
                .as("el modelo importado debe describir lo mismo que el original")
                .isEqualTo(descripcion(original.getId()));
    }

    @Test
    @DisplayName("el documento lleva la vista, con la posicion de cada clase")
    void elDocumentoLlevaLaVista() {
        String documento = xmi.exportar(original.getId(), autor.getId());

        // Sin la vista, quien abre el documento en Enterprise Architect recibe
        // las clases pero ningun dibujo, y tiene que acomodarlas a mano.
        assertThat(documento)
                .contains("<xmi:Extension extender=\"Enterprise Architect\"")
                .contains("<diagrams>")
                .contains("type=\"Logical\"")
                .contains("geometry=\"Left=")
                .contains(";Top=")
                .contains(";Bottom=");

        // Enterprise Architect solo lee esa seccion si el documento dice venir
        // de Enterprise Architect; con otro nombre descarta la vista en
        // silencio. Queda fijado aqui para que nadie lo "corrija" sin saberlo.
        assertThat(documento).contains("<xmi:Documentation exporter=\"Enterprise Architect\"");
        assertThat(documento)
                .as("el documento debe seguir diciendo quien lo genero")
                .contains("Generado por FORJA");

        // En ese modo Enterprise Architect lee los vinculos de <connectors> y
        // deja de mirar las asociaciones de UML: sin esta seccion se pierden.
        assertThat(documento)
                .contains("<connectors>")
                .contains("ea_type=\"Aggregation\"")
                .contains("<packagedElement xmi:type=\"uml:Realization\"");
    }

    @Test
    @DisplayName("lo importado queda en la bitacora con origen IMPORTACION")
    void laImportacionQuedaTrazada() {
        String documento = xmi.exportar(original.getId(), autor.getId());
        xmi.importar(vacio.getId(), autor.getId(), "sesion-importacion", documento, "tk");

        List<Operacion> registradas = operaciones
                .findByDiagramaIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(vacio.getId(), 0);

        assertThat(registradas).isNotEmpty();
        assertThat(registradas)
                .as("es la evidencia de que el modelo entro por importacion y no a mano")
                .allMatch(o -> o.getOrigen() == OrigenOperacion.IMPORTACION);
    }

    @Test
    @DisplayName("reenviar la misma importacion no duplica el modelo")
    void laImportacionEsIdempotente() {
        String documento = xmi.exportar(original.getId(), autor.getId());

        ServicioXmi.ResumenImportacion primera = xmi.importar(
                vacio.getId(), autor.getId(), "sesion-importacion", documento, "token-repetido");
        ServicioXmi.ResumenImportacion reenvio = xmi.importar(
                vacio.getId(), autor.getId(), "sesion-importacion", documento, "token-repetido");

        assertThat(primera.aplicadas()).isPositive();
        assertThat(reenvio.aplicadas())
                .as("el reenvio no debe aplicar nada nuevo")
                .isZero();
        assertThat(reenvio.duplicadas()).isEqualTo(primera.aplicadas());
        assertThat(descripcion(vacio.getId())).isEqualTo(descripcion(original.getId()));
    }

    @Test
    @DisplayName("se lee un XMI escrito con las otras formas que el estandar permite")
    void seLeeUnXmiDeOtraHerramienta() {
        // Documento al estilo de Enterprise Architect: los extremos de la
        // asociacion son propios de la asociacion en lugar de propiedades de
        // las clases, la composicion se marca con aggregation, el tipo va como
        // atributo y no hay ninguna extension de FORJA de la que apoyarse.
        String ajeno = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="20131001"
                         xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" xmi:id="M1" name="Ventas">
                    <packagedElement xmi:type="uml:Class" xmi:id="C1" name="Pedido">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="A1" name="fecha" visibility="private">
                        <type xmi:type="uml:PrimitiveType" href="http://www.omg.org/spec/UML/20161101/PrimitiveTypes.xmi#String"/>
                        <lowerValue xmi:type="uml:LiteralInteger" value="1"/>
                      </ownedAttribute>
                      <ownedOperation xmi:type="uml:Operation" xmi:id="O1" name="total" visibility="public">
                        <ownedParameter xmi:type="uml:Parameter" xmi:id="P1" name="return" direction="return" type="D1"/>
                      </ownedOperation>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Class" xmi:id="C2" name="LineaDePedido">
                      <ownedAttribute xmi:type="uml:Property" xmi:id="A2" name="cantidad" type="D2">
                        <lowerValue xmi:type="uml:LiteralInteger" value="0"/>
                      </ownedAttribute>
                    </packagedElement>
                    <packagedElement xmi:type="uml:Association" xmi:id="R1">
                      <memberEnd xmi:idref="E1"/>
                      <memberEnd xmi:idref="E2"/>
                      <ownedEnd xmi:type="uml:Property" xmi:id="E1" name="lineas" type="C2" association="R1">
                        <lowerValue xmi:type="uml:LiteralInteger" value="1"/>
                        <upperValue xmi:type="uml:LiteralUnlimitedNatural" value="*"/>
                      </ownedEnd>
                      <ownedEnd xmi:type="uml:Property" xmi:id="E2" name="pedido" type="C1"
                                association="R1" aggregation="composite">
                        <lowerValue xmi:type="uml:LiteralInteger" value="1"/>
                        <upperValue xmi:type="uml:LiteralUnlimitedNatural" value="1"/>
                      </ownedEnd>
                    </packagedElement>
                    <packagedElement xmi:type="uml:DataType" xmi:id="D1" name="Decimal"/>
                    <packagedElement xmi:type="uml:DataType" xmi:id="D2" name="Integer"/>
                  </uml:Model>
                </xmi:XMI>
                """;

        ServicioXmi.ResumenImportacion resumen = xmi.importar(
                vacio.getId(), autor.getId(), "sesion-ajena", ajeno, "tk-ajeno");

        assertThat(resumen.problemas()).isEmpty();

        List<ClaseUml> importadas = modelo.clasesCompletas(vacio.getId());
        assertThat(importadas).extracting(ClaseUml::getNombre)
                .containsExactlyInAnyOrder("Pedido", "LineaDePedido");

        ClaseUml pedido = importadas.stream()
                .filter(c -> c.getNombre().equals("Pedido")).findFirst().orElseThrow();
        assertThat(pedido.getAtributos()).singleElement()
                .satisfies(a -> {
                    assertThat(a.getNombre()).isEqualTo("fecha");
                    assertThat(a.getTipo()).isEqualTo("String");
                    assertThat(a.isEsRequerido()).isTrue();
                });
        assertThat(pedido.getMetodos()).singleElement()
                .satisfies(m -> {
                    assertThat(m.getNombre()).isEqualTo("total");
                    // El tipo de retorno vino como referencia a un uml:DataType.
                    assertThat(m.getTipoRetorno()).isEqualTo("Decimal");
                });

        ClaseUml linea = importadas.stream()
                .filter(c -> c.getNombre().equals("LineaDePedido")).findFirst().orElseThrow();
        assertThat(linea.getAtributos()).singleElement()
                .satisfies(a -> {
                    assertThat(a.getTipo()).isEqualTo("Integer");
                    assertThat(a.isEsRequerido())
                            .as("el limite inferior era 0, asi que es opcional")
                            .isFalse();
                });

        assertThat(relaciones.buscarConExtremos(vacio.getId())).singleElement()
                .satisfies(r -> {
                    assertThat(r.getTipo())
                            .as("el extremo marcado con aggregation composite la vuelve composicion")
                            .isEqualTo(TipoRelacion.COMPOSICION);
                    assertThat(r.getOrigen().getNombre()).isEqualTo("Pedido");
                    assertThat(r.getDestino().getNombre()).isEqualTo("LineaDePedido");
                    assertThat(r.getMultiplicidadDestino()).isEqualTo("1..*");
                });
    }

    @Test
    @DisplayName("un documento que no es XML se rechaza con un mensaje claro")
    void elDocumentoMalFormadoSeRechaza() {
        assertThatThrownBy(() -> xmi.importar(vacio.getId(), autor.getId(), "s",
                "esto no es xml", "tk"))
                .isInstanceOf(XmiInvalido.class)
                .hasMessageContaining("XML");
    }

    @Test
    @DisplayName("un XMI sin clases se rechaza en lugar de importar nada")
    void elDocumentoSinClasesSeRechaza() {
        String sinClases = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="20131001"
                         xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" xmi:id="M1" name="Vacio"/>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> xmi.importar(vacio.getId(), autor.getId(), "s", sinClases, "tk"))
                .isInstanceOf(XmiInvalido.class)
                .hasMessageContaining("ninguna clase");
    }

    @Test
    @DisplayName("un documento con entidades externas se rechaza sin resolverlas")
    void seRechazaElIntentoDeLeerArchivosDelServidor() {
        // XXE: el documento le pide al analizador que abra un archivo del
        // servidor y lo incruste en el modelo. Debe fallar por la declaracion
        // de tipo, antes de llegar a resolver nada.
        String conEntidadExterna = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xmi:XMI [ <!ENTITY secreto SYSTEM "file:///etc/passwd"> ]>
                <xmi:XMI xmi:version="20131001"
                         xmlns:xmi="http://www.omg.org/spec/XMI/20131001"
                         xmlns:uml="http://www.omg.org/spec/UML/20161101">
                  <uml:Model xmi:type="uml:Model" xmi:id="M1" name="&secreto;"/>
                </xmi:XMI>
                """;

        assertThatThrownBy(() -> xmi.importar(vacio.getId(), autor.getId(), "s",
                conEntidadExterna, "tk"))
                .isInstanceOf(XmiInvalido.class);
    }

    // ---------- Descripcion canonica del modelo -----------------------------

    /**
     * Representacion textual y ordenada de un diagrama.
     * <p>
     * Comparar dos de estas cadenas es comparar los modelos sin depender de
     * los identificadores, que necesariamente cambian al importar, ni del
     * orden en que la base de datos devuelva las filas.
     */
    private String descripcion(UUID diagramaId) {
        String clasesDescritas = modelo.clasesCompletas(diagramaId).stream()
                .sorted(Comparator.comparing(ClaseUml::getNombre))
                .map(this::describirClase)
                .collect(Collectors.joining("\n"));

        String relacionesDescritas = relaciones.buscarConExtremos(diagramaId).stream()
                .map(r -> r.getTipo() + " " + r.getOrigen().getNombre() + " -> "
                        + r.getDestino().getNombre()
                        + " [" + r.getMultiplicidadOrigen() + ", " + r.getMultiplicidadDestino() + "]")
                .sorted()
                .collect(Collectors.joining("\n"));

        return clasesDescritas + "\n--- relaciones ---\n" + relacionesDescritas;
    }

    private String describirClase(ClaseUml clase) {
        String atributos = clase.getAtributos().stream()
                .sorted(Comparator.comparingInt(AtributoUml::getOrden))
                .map(a -> "    " + a.getNombre() + ": " + a.getTipo()
                        + " vis=" + a.getVisibilidad()
                        + " req=" + a.isEsRequerido()
                        + " uni=" + a.isEsUnico()
                        + " id=" + a.isEsIdentificador()
                        + " len=" + a.getLongitud())
                .collect(Collectors.joining("\n"));

        String metodos = clase.getMetodos().stream()
                .sorted(Comparator.comparingInt(MetodoUml::getOrden))
                .map(m -> "    " + m.getNombre() + "("
                        + m.getParametros().stream()
                        .sorted(Comparator.comparingInt(ParametroUml::getOrden))
                        .map(p -> p.getNombre() + ": " + p.getTipo())
                        .collect(Collectors.joining(", "))
                        + "): " + m.getTipoRetorno()
                        + " vis=" + m.getVisibilidad()
                        + " abs=" + m.isEsAbstracto()
                        + " est=" + m.isEsEstatico())
                .collect(Collectors.joining("\n"));

        return "clase " + clase.getNombre()
                + " abstracta=" + clase.isEsAbstracta()
                + " estereotipo=" + clase.getEstereotipo()
                + " pos=" + clase.getPosX() + "," + clase.getPosY()
                + "\n" + atributos + "\n" + metodos;
    }

    // ---------- Construccion del modelo de prueba ---------------------------

    private void construirModelo() {
        ClaseUml persona = clase("Persona", null, true, 10, 20);
        atributo(persona, "nombre", "String", 120, true, false, false, Visibilidad.PROTEGIDO);
        atributo(persona, "fechaNacimiento", "Date", null, false, false, false, Visibilidad.PRIVADO);
        metodo(persona, "edad", "Integer", true, false, Visibilidad.PUBLICO);
        clases.save(persona);

        ClaseUml paciente = clase("Paciente", null, false, 300, 20);
        atributo(paciente, "historiaClinica", "String", 40, true, true, true, Visibilidad.PRIVADO);
        metodoConParametros(paciente, "registrarConsulta", "Boolean",
                new String[][]{{"motivo", "String"}, {"importe", "Decimal"}});
        clases.save(paciente);

        ClaseUml auditable = clase("Auditable", "interface", false, 600, 20);
        metodoConParametros(auditable, "registrarCambio", "void",
                new String[][]{{"detalle", "String"}});
        clases.save(auditable);

        ClaseUml consulta = clase("Consulta", null, false, 300, 240);
        atributo(consulta, "fechaHora", "DateTime", null, true, false, false, Visibilidad.PRIVADO);
        atributo(consulta, "importe", "Decimal", null, false, false, false, Visibilidad.PRIVADO);
        clases.save(consulta);

        ClaseUml especialidad = clase("Especialidad", "catalogo", false, 600, 240);
        atributo(especialidad, "nombre", "String", 80, true, true, false, Visibilidad.PUBLICO);
        clases.save(especialidad);

        relacion(paciente, persona, TipoRelacion.HERENCIA, "1", "1");
        relacion(paciente, auditable, TipoRelacion.REALIZACION, "1", "1");
        relacion(paciente, consulta, TipoRelacion.COMPOSICION, "1", "0..*");
        relacion(consulta, especialidad, TipoRelacion.AGREGACION, "0..*", "1");
        relacion(paciente, especialidad, TipoRelacion.ASOCIACION, "1..*", "1..*");
        relacion(consulta, persona, TipoRelacion.DEPENDENCIA, "1", "1");
    }

    private Diagrama nuevoDiagrama(String nombre) {
        Diagrama diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre(nombre);
        return diagramas.save(diagrama);
    }

    private ClaseUml clase(String nombre, String estereotipo, boolean abstracta,
                           double x, double y) {
        ClaseUml clase = new ClaseUml();
        clase.setDiagrama(original);
        clase.setNombre(nombre);
        clase.setEstereotipo(estereotipo);
        clase.setEsAbstracta(abstracta);
        clase.setPosX(x);
        clase.setPosY(y);
        return clase;
    }

    private void atributo(ClaseUml clase, String nombre, String tipo, Integer longitud,
                          boolean requerido, boolean unico, boolean identificador,
                          Visibilidad visibilidad) {
        AtributoUml atributo = new AtributoUml();
        atributo.setClase(clase);
        atributo.setNombre(nombre);
        atributo.setTipo(tipo);
        atributo.setLongitud(longitud);
        atributo.setEsRequerido(requerido);
        atributo.setEsUnico(unico);
        atributo.setEsIdentificador(identificador);
        atributo.setVisibilidad(visibilidad);
        atributo.setOrden(clase.getAtributos().size());
        clase.getAtributos().add(atributo);
    }

    private void metodo(ClaseUml clase, String nombre, String retorno,
                        boolean abstracto, boolean estatico, Visibilidad visibilidad) {
        metodoConParametros(clase, nombre, retorno, new String[0][]);
        MetodoUml ultimo = clase.getMetodos().get(clase.getMetodos().size() - 1);
        ultimo.setEsAbstracto(abstracto);
        ultimo.setEsEstatico(estatico);
        ultimo.setVisibilidad(visibilidad);
    }

    private void metodoConParametros(ClaseUml clase, String nombre, String retorno,
                                     String[][] parametros) {
        MetodoUml metodo = new MetodoUml();
        metodo.setClase(clase);
        metodo.setNombre(nombre);
        metodo.setTipoRetorno(retorno);
        metodo.setVisibilidad(Visibilidad.PUBLICO);
        metodo.setOrden(clase.getMetodos().size());

        int orden = 0;
        for (String[] declarado : parametros) {
            ParametroUml parametro = new ParametroUml();
            parametro.setMetodo(metodo);
            parametro.setNombre(declarado[0]);
            parametro.setTipo(declarado[1]);
            parametro.setOrden(orden++);
            metodo.getParametros().add(parametro);
        }
        clase.getMetodos().add(metodo);
    }

    private void relacion(ClaseUml origen, ClaseUml destino, TipoRelacion tipo,
                          String multOrigen, String multDestino) {
        RelacionUml relacion = new RelacionUml();
        relacion.setDiagrama(original);
        relacion.setOrigen(origen);
        relacion.setDestino(destino);
        relacion.setTipo(tipo);
        relacion.setMultiplicidadOrigen(multOrigen);
        relacion.setMultiplicidadDestino(multDestino);
        relaciones.save(relacion);
    }

    private void limpiar() {
        operaciones.deleteAllInBatch();
        relaciones.deleteAllInBatch();
        clases.deleteAllInBatch();
        miembros.deleteAllInBatch();
        diagramas.deleteAllInBatch();
        proyectos.deleteAllInBatch();
        usuarios.deleteAllInBatch();
    }
}
