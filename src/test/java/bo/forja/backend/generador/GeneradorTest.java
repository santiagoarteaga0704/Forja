package bo.forja.backend.generador;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.MetodoUml;
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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verificacion del generador de proyectos Spring Boot.
 * <p>
 * La prueba central no compara cadenas de texto: <b>compila el codigo
 * generado</b> con el compilador del JDK. Es la unica comprobacion que
 * significa algo para un generador, porque un archivo puede contener todas
 * las anotaciones esperadas y no compilar por una importacion que falta,
 * una clase que implementa una interfaz sin definir sus metodos o un tipo
 * mal deducido. Comparar texto solo verifica que el generador hace lo que
 * el autor de la prueba creia; compilar verifica que hace algo valido.
 * <p>
 * El diagrama de prueba no es trivial a proposito: incluye herencia con
 * clase abstracta, una interfaz realizada, composicion, asociacion muchos a
 * muchos, uno a uno y operaciones declaradas. Son los casos donde las
 * reglas de traduccion se pueden equivocar.
 */
@SpringBootTest
@DisplayName("Generacion de proyectos Spring Boot")
class GeneradorTest {

    @Autowired
    private ServicioGeneracion generacion;

    @Autowired
    private PlanificadorGeneracion planificador;

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

    private Usuario autor;
    private Diagrama diagrama;

    @BeforeEach
    void construirDiagrama() {
        limpiar();

        autor = new Usuario();
        autor.setEmail("generador-" + UUID.randomUUID() + "@forja.test");
        autor.setNombre("Autora del modelo");
        autor.setPasswordHash("no-relevante");
        usuarios.save(autor);

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Clinica");
        proyecto.setPropietario(autor);
        proyectos.save(proyecto);

        ProyectoMiembro miembro = new ProyectoMiembro();
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), autor.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(autor);
        miembro.setRol(RolMiembro.PROPIETARIO);
        miembros.save(miembro);

        diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre("Gestion Clinica");
        diagramas.save(diagrama);

        // --- Jerarquia: Persona abstracta, Paciente y Medico la extienden ---
        ClaseUml persona = clase("Persona", null, true);
        atributo(persona, "nombre completo", "String", 120, true, false, false);
        atributo(persona, "fechaNacimiento", "fecha", null, false, false, false);
        clases.save(persona);

        ClaseUml paciente = clase("Paciente", null, false);
        atributo(paciente, "historia clinica", "String", 40, true, true, false);
        metodo(paciente, "calcularEdad", "entero");
        clases.save(paciente);

        ClaseUml medico = clase("Medico", null, false);
        atributo(medico, "matricula", "String", 20, true, true, false);
        clases.save(medico);

        // --- Interfaz realizada por Medico ---------------------------------
        ClaseUml auditable = clase("Auditable", "interface", false);
        metodoConParametro(auditable, "registrarCambio", "void", "motivo", "texto");
        clases.save(auditable);

        // --- Composicion: una consulta no existe sin su paciente -----------
        ClaseUml consulta = clase("Consulta", null, false);
        atributo(consulta, "fecha y hora", "datetime", null, true, false, false);
        atributo(consulta, "motivo", "texto", 300, false, false, false);
        atributo(consulta, "importe", "decimal", null, false, false, false);
        clases.save(consulta);

        // --- Muchos a muchos ------------------------------------------------
        ClaseUml especialidad = clase("Especialidad", null, false);
        atributo(especialidad, "nombre", "String", 80, true, true, false);
        clases.save(especialidad);

        // --- Uno a uno --------------------------------------------------------
        ClaseUml direccion = clase("Direccion", null, false);
        atributo(direccion, "calle", "String", 150, true, false, false);
        clases.save(direccion);

        relacion(paciente, persona, TipoRelacion.HERENCIA, "1", "1", null, null);
        relacion(medico, persona, TipoRelacion.HERENCIA, "1", "1", null, null);
        relacion(medico, auditable, TipoRelacion.REALIZACION, "1", "1", null, null);
        relacion(paciente, consulta, TipoRelacion.COMPOSICION, "1", "0..*", null, null);
        relacion(medico, consulta, TipoRelacion.ASOCIACION, "1", "0..*", null, null);
        relacion(medico, especialidad, TipoRelacion.ASOCIACION, "1..*", "1..*", null, null);
        relacion(paciente, direccion, TipoRelacion.ASOCIACION, "1", "0..1", null, null);
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("el codigo generado compila con el compilador de Java")
    void elCodigoGeneradoCompila() throws Exception {
        JavaCompiler compilador = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compilador != null,
                "hace falta un JDK con compilador; con un JRE la prueba no aplica");

        Map<String, String> archivos = generar();

        Path raiz = Files.createTempDirectory("forja-generado-");
        try {
            List<Path> fuentes = volcar(archivos, raiz);
            assertThat(fuentes).as("debe haber archivos Java para compilar").isNotEmpty();

            DiagnosticCollector<JavaFileObject> diagnosticos = new DiagnosticCollector<>();
            Path salida = Files.createDirectory(raiz.resolve("clases-compiladas"));

            try (StandardJavaFileManager archivador =
                         compilador.getStandardFileManager(diagnosticos, null, null)) {

                archivador.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(salida));
                archivador.setLocationFromPaths(StandardLocation.CLASS_PATH, rutaDeClases());

                boolean compilo = compilador.getTask(null, archivador, diagnosticos,
                                List.of("-proc:none"), null,
                                archivador.getJavaFileObjectsFromPaths(fuentes))
                        .call();

                String errores = diagnosticos.getDiagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(d -> "  " + d.getSource().getName() + ":" + d.getLineNumber()
                                + " " + d.getMessage(null))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

                assertThat(compilo)
                        .as("el proyecto generado debe compilar. Errores:\n" + errores)
                        .isTrue();
            }
        } finally {
            borrar(raiz);
        }
    }

    @Test
    @DisplayName("se generan las cuatro capas por cada entidad concreta")
    void seGeneranLasCuatroCapas() {
        Map<String, String> archivos = generar();

        // Persona es abstracta y Auditable es una interfaz: ninguna recibe
        // repositorio ni API, porque no hay filas que consultar.
        for (String entidad : List.of("Paciente", "Medico", "Consulta", "Especialidad", "Direccion")) {
            assertThat(archivos).containsKey(ruta("dominio/" + entidad + ".java"));
            assertThat(archivos).containsKey(ruta("repositorio/" + entidad + "Repositorio.java"));
            assertThat(archivos).containsKey(ruta("servicio/" + entidad + "Servicio.java"));
            assertThat(archivos).containsKey(ruta("web/" + entidad + "Controlador.java"));
        }
        assertThat(archivos).containsKey(ruta("dominio/Persona.java"));
        assertThat(archivos).doesNotContainKey(ruta("repositorio/PersonaRepositorio.java"));
        assertThat(archivos).doesNotContainKey(ruta("repositorio/AuditableRepositorio.java"));

        assertThat(archivos).containsKeys("pom.xml", "README.md",
                "src/main/resources/application.yml");
    }

    @Test
    @DisplayName("la herencia produce extends, estrategia JOINED y una sola clave")
    void laHerenciaSeTraduceBien() {
        Map<String, String> archivos = generar();

        String persona = archivos.get(ruta("dominio/Persona.java"));
        assertThat(persona)
                .contains("@Inheritance(strategy = InheritanceType.JOINED)")
                .contains("public abstract class Persona")
                .contains("@Id");

        String paciente = archivos.get(ruta("dominio/Paciente.java"));
        assertThat(paciente).contains("public class Paciente extends Persona");
        assertThat(paciente)
                .as("la subclase no puede declarar su propia clave: la hereda")
                .doesNotContain("@Id");

        // El repositorio de la subclase necesita el tipo de la clave heredada.
        assertThat(archivos.get(ruta("repositorio/PacienteRepositorio.java")))
                .contains("JpaRepository<Paciente, Long>");
    }

    @Test
    @DisplayName("la composicion arrastra el borrado de las partes")
    void laComposicionCascada() {
        String paciente = generar().get(ruta("dominio/Paciente.java"));

        assertThat(paciente)
                .contains("@OneToMany(mappedBy = \"paciente\"")
                .contains("cascade = CascadeType.ALL, orphanRemoval = true")
                .contains("private List<Consulta> consultas = new ArrayList<>();");

        String consulta = generar().get(ruta("dominio/Consulta.java"));
        assertThat(consulta)
                .as("la clave ajena vive en el lado muchos")
                .contains("@ManyToOne(fetch = FetchType.LAZY, optional = false)")
                .contains("@JoinColumn(name = \"paciente_id\", nullable = false)");
    }

    @Test
    @DisplayName("muchos a muchos genera la tabla de union en un solo lado")
    void muchosAMuchos() {
        Map<String, String> archivos = generar();

        assertThat(archivos.get(ruta("dominio/Medico.java")))
                .contains("@ManyToMany")
                .contains("@JoinTable(name = \"medicos_especialidades\"");

        assertThat(archivos.get(ruta("dominio/Especialidad.java")))
                .as("el lado inverso solo refleja la relacion, no la vuelve a declarar")
                .contains("@ManyToMany(mappedBy = \"especialidades\")")
                .doesNotContain("@JoinTable");
    }

    @Test
    @DisplayName("la interfaz realizada obliga a implementar sus operaciones")
    void laInterfazSeImplementa() {
        Map<String, String> archivos = generar();

        assertThat(archivos.get(ruta("dominio/Auditable.java")))
                .contains("public interface Auditable")
                .contains("void registrarCambio(String motivo);");

        assertThat(archivos.get(ruta("dominio/Medico.java")))
                .contains("implements Auditable")
                .as("sin el cuerpo del metodo heredado de la interfaz no compilaria")
                .contains("public void registrarCambio(String motivo)");
    }

    @Test
    @DisplayName("los tipos del diagrama se traducen a tipos de Java")
    void losTiposSeTraducen() {
        String consulta = generar().get(ruta("dominio/Consulta.java"));

        assertThat(consulta)
                .contains("private LocalDateTime fechaYHora;")
                .as("un importe se guarda en BigDecimal y no en punto flotante")
                .contains("private BigDecimal importe;")
                .contains("import java.math.BigDecimal;")
                .contains("import java.time.LocalDateTime;");
    }

    @Test
    @DisplayName("el plan resuelve la cardinalidad de cada asociacion")
    void elPlanResuelveCardinalidades() {
        Plan.Proyecto plan = planificador.planificar("com.clinica.demo", "clinica", "Clinica",
                cargarClases(), relaciones.buscarConExtremos(diagrama.getId()));

        Plan.Clase medico = plan.porNombre("Medico");
        assertThat(medico.asociaciones())
                .extracting(Plan.Asociacion::cardinalidad)
                .contains(Plan.Cardinalidad.UNO_A_MUCHOS, Plan.Cardinalidad.MUCHOS_A_MUCHOS);

        Plan.Clase direccion = plan.porNombre("Direccion");
        assertThat(direccion.asociaciones())
                .singleElement()
                .extracting(Plan.Asociacion::cardinalidad)
                .isEqualTo(Plan.Cardinalidad.UNO_A_UNO);
    }

    @Test
    @DisplayName("el proyecto se empaqueta en un zip con todo dentro")
    void seEmpaquetaEnZip() {
        Map<String, String> archivos = generar();
        byte[] zip = generacion.comprimir(archivos, "clinica");

        assertThat(zip).isNotEmpty();
        // La firma PK marca el comienzo de un archivo zip.
        assertThat(zip[0]).isEqualTo((byte) 'P');
        assertThat(zip[1]).isEqualTo((byte) 'K');
        assertThat(zip.length)
                .as("debe contener el proyecto entero, no solo un descriptor")
                .isGreaterThan(2000);
    }

    // ---------- Auxiliares -------------------------------------------------

    private static final String PAQUETE = "com.clinica.demo";

    private Map<String, String> generar() {
        return generacion.archivos(diagrama.getId(), autor.getId(), PAQUETE);
    }

    private String ruta(String relativa) {
        return "src/main/java/" + PAQUETE.replace('.', '/') + "/" + relativa;
    }

    /**
     * Las dos consultas del modelo tienen que ocurrir en una misma
     * transaccion para que la segunda complete las instancias de la primera;
     * de lo contrario los metodos quedan sin inicializar. Es justamente el
     * trabajo de ServicioModelo, asi que se usa ese y no los repositorios.
     */
    private List<ClaseUml> cargarClases() {
        return modelo.clasesCompletas(diagrama.getId());
    }

    /** Rutas de las bibliotecas contra las que debe compilar lo generado. */
    private List<Path> rutaDeClases() {
        return Stream.of(
                        jakarta.persistence.Entity.class,
                        org.springframework.stereotype.Service.class,
                        org.springframework.transaction.annotation.Transactional.class,
                        org.springframework.data.jpa.repository.JpaRepository.class,
                        org.springframework.data.repository.Repository.class,
                        org.springframework.web.bind.annotation.RestController.class,
                        org.springframework.http.ResponseEntity.class,
                        org.springframework.boot.SpringApplication.class,
                        org.springframework.boot.autoconfigure.SpringBootApplication.class)
                .map(clase -> {
                    try {
                        return Path.of(clase.getProtectionDomain().getCodeSource()
                                .getLocation().toURI());
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "No se pudo ubicar el jar de " + clase.getName(), e);
                    }
                })
                .distinct()
                .toList();
    }

    private List<Path> volcar(Map<String, String> archivos, Path raiz) throws IOException {
        List<Path> fuentes = new ArrayList<>();
        for (Map.Entry<String, String> archivo : archivos.entrySet()) {
            Path destino = raiz.resolve(archivo.getKey());
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, archivo.getValue());
            if (archivo.getKey().endsWith(".java")) {
                fuentes.add(destino);
            }
        }
        return fuentes;
    }

    private void borrar(Path raiz) throws IOException {
        try (Stream<Path> contenido = Files.walk(raiz)) {
            contenido.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // Un temporal que no se pudo borrar no invalida la prueba.
                }
            });
        }
    }

    // ---------- Construccion del diagrama de prueba ------------------------

    private ClaseUml clase(String nombre, String estereotipo, boolean abstracta) {
        ClaseUml clase = new ClaseUml();
        clase.setDiagrama(diagrama);
        clase.setNombre(nombre);
        clase.setEstereotipo(estereotipo);
        clase.setEsAbstracta(abstracta);
        return clase;
    }

    private void atributo(ClaseUml clase, String nombre, String tipo, Integer longitud,
                          boolean requerido, boolean unico, boolean identificador) {
        AtributoUml atributo = new AtributoUml();
        atributo.setClase(clase);
        atributo.setNombre(nombre);
        atributo.setTipo(tipo);
        atributo.setLongitud(longitud);
        atributo.setEsRequerido(requerido);
        atributo.setEsUnico(unico);
        atributo.setEsIdentificador(identificador);
        atributo.setVisibilidad(Visibilidad.PRIVADO);
        atributo.setOrden(clase.getAtributos().size());
        clase.getAtributos().add(atributo);
    }

    private void metodo(ClaseUml clase, String nombre, String tipoRetorno) {
        MetodoUml metodo = new MetodoUml();
        metodo.setClase(clase);
        metodo.setNombre(nombre);
        metodo.setTipoRetorno(tipoRetorno);
        metodo.setVisibilidad(Visibilidad.PUBLICO);
        metodo.setOrden(clase.getMetodos().size());
        clase.getMetodos().add(metodo);
    }

    private void metodoConParametro(ClaseUml clase, String nombre, String tipoRetorno,
                                    String nombreParametro, String tipoParametro) {
        MetodoUml metodo = new MetodoUml();
        metodo.setClase(clase);
        metodo.setNombre(nombre);
        metodo.setTipoRetorno(tipoRetorno);
        metodo.setVisibilidad(Visibilidad.PUBLICO);
        metodo.setOrden(clase.getMetodos().size());

        ParametroUml parametro = new ParametroUml();
        parametro.setMetodo(metodo);
        parametro.setNombre(nombreParametro);
        parametro.setTipo(tipoParametro);
        parametro.setOrden(0);
        metodo.getParametros().add(parametro);

        clase.getMetodos().add(metodo);
    }

    private void relacion(ClaseUml origen, ClaseUml destino, TipoRelacion tipo,
                          String multOrigen, String multDestino,
                          String rolOrigen, String rolDestino) {
        RelacionUml relacion = new RelacionUml();
        relacion.setDiagrama(diagrama);
        relacion.setOrigen(origen);
        relacion.setDestino(destino);
        relacion.setTipo(tipo);
        relacion.setMultiplicidadOrigen(multOrigen);
        relacion.setMultiplicidadDestino(multDestino);
        relacion.setRolOrigen(rolOrigen);
        relacion.setRolDestino(rolDestino);
        relaciones.save(relacion);
    }

    private void limpiar() {
        relaciones.deleteAllInBatch();
        clases.deleteAllInBatch();
        miembros.deleteAllInBatch();
        diagramas.deleteAllInBatch();
        proyectos.deleteAllInBatch();
        usuarios.deleteAllInBatch();
    }
}
