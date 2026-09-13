package bo.forja.backend.voz;

import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Operacion;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.repositorio.BloqueoElementoRepositorio;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import bo.forja.backend.servicio.ServicioBloqueo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de que lo dictado llega al modelo por el camino correcto.
 * <p>
 * El parser ya se prueba aparte y sin base de datos. Lo que se comprueba aqui
 * es lo que la prueba unitaria no puede: que el dictado pasa por el registro de
 * operaciones, que queda anotado con origen {@code VOZ} -la evidencia que la
 * evaluacion pide para demostrar cuanta parte del modelo se construyo
 * hablando- y que respeta el bloqueo de quien este editando.
 */
@SpringBootTest
@DisplayName("Dictado aplicado sobre el diagrama")
class ServicioVozTest {

    @Autowired
    private ServicioVoz voz;
    @Autowired
    private ServicioBloqueo bloqueos;

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
    @Autowired
    private BloqueoElementoRepositorio bloqueosRepo;

    private Usuario ana;
    private Usuario bruno;
    private Diagrama diagrama;

    @BeforeEach
    void prepararEscenario() {
        limpiar();

        ana = nuevoUsuario("ana");
        bruno = nuevoUsuario("bruno");

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Clinica");
        proyecto.setPropietario(ana);
        proyectos.save(proyecto);

        inscribir(proyecto, ana, RolMiembro.PROPIETARIO);
        inscribir(proyecto, bruno, RolMiembro.EDITOR);

        diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre("Dictado");
        diagramas.save(diagrama);
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("lo dictado queda en la bitacora con origen VOZ")
    void elDictadoQuedaTrazado() {
        ServicioVoz.ResultadoDictado resultado = dictar("crea la clase Paciente");

        assertThat(resultado.entendida()).isTrue();
        assertThat(resultado.aplicadas()).isEqualTo(1);
        assertThat(resultado.explicacion()).contains("Paciente");
        assertThat(clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Paciente"))
                .isPresent();

        List<Operacion> registradas = operaciones
                .findByDiagramaIdAndSecuenciaGreaterThanOrderBySecuenciaAsc(diagrama.getId(), 0);
        assertThat(registradas).singleElement()
                .satisfies(o -> assertThat(o.getOrigen()).isEqualTo(OrigenOperacion.VOZ));
    }

    @Test
    @DisplayName("una frase puede producir varias operaciones")
    void unaFraseVariasOperaciones() {
        ServicioVoz.ResultadoDictado resultado = dictar(
                "crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal");

        assertThat(resultado.comandosLeidos()).isEqualTo(3);
        assertThat(resultado.aplicadas()).isEqualTo(3);
        assertThat(resultado.problemas()).isEmpty();

        ClaseUml factura = clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Factura")
                .orElseThrow();
        assertThat(clases.buscarConAtributos(diagrama.getId()))
                .filteredOn(c -> c.getId().equals(factura.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.getAtributos())
                        .extracting(a -> a.getNombre() + ":" + a.getTipo())
                        .containsExactly("numero:String", "total:Decimal"));
    }

    @Test
    @DisplayName("las clases dictadas no se apilan todas en el origen")
    void lasClasesSeDistribuyen() {
        dictar("crea la clase Paciente");
        dictar("crea la clase Consulta");
        dictar("crea la clase Medico");

        List<ClaseUml> creadas = clases.findByDiagramaId(diagrama.getId());
        assertThat(creadas).hasSize(3);
        // Dictando no se dicen coordenadas, pero dos clases en el mismo lugar
        // serian invisibles una detras de la otra.
        assertThat(creadas).extracting(c -> c.getPosX() + "," + c.getPosY())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("una frase que no se entiende no toca el modelo")
    void laFraseSinSentidoNoCambiaNada() {
        ServicioVoz.ResultadoDictado resultado = dictar("che pasame la sal");

        assertThat(resultado.entendida()).isFalse();
        assertThat(resultado.aplicadas()).isZero();
        assertThat(resultado.sugerencias()).isNotEmpty();
        assertThat(operaciones.count()).isZero();
        assertThat(clases.findByDiagramaId(diagrama.getId())).isEmpty();
    }

    @Test
    @DisplayName("el dictado respeta el bloqueo de quien esta editando")
    void elDictadoRespetaElBloqueoAjeno() {
        dictar("crea la clase Paciente");
        ClaseUml paciente = clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Paciente")
                .orElseThrow();

        // Bruno toma la clase, como al empezar a editarla en su lienzo.
        bloqueos.adquirir(diagrama.getId(), TipoElemento.CLASE, paciente.getId(),
                bruno.getId(), "sesion-bruno");

        ServicioVoz.ResultadoDictado resultado = dictar("renombra Paciente a Interno");

        assertThat(resultado.entendida())
                .as("la frase se entendio; lo que fallo fue aplicarla")
                .isTrue();
        assertThat(resultado.aplicadas()).isZero();
        assertThat(resultado.retenidoPor()).isEqualTo(bruno.getNombre());
        assertThat(clases.findById(paciente.getId()).orElseThrow().getNombre())
                .as("el nombre no debe haber cambiado")
                .isEqualTo("Paciente");
    }

    @Test
    @DisplayName("se puede interpretar sin aplicar, para confirmar antes de hacerlo")
    void interpretarSinAplicar() {
        Interpretacion interpretacion = voz.interpretarSinAplicar(
                diagrama.getId(), ana.getId(), "crea la clase Paciente");

        assertThat(interpretacion.entendida()).isTrue();
        assertThat(interpretacion.explicacion()).contains("Paciente");
        // Nada se aplico: es solo una vista previa de lo que se entendio.
        assertThat(operaciones.count()).isZero();
        assertThat(clases.findByDiagramaId(diagrama.getId())).isEmpty();
    }

    // ---------- Auxiliares -------------------------------------------------

    private ServicioVoz.ResultadoDictado dictar(String frase) {
        return voz.dictar(diagrama.getId(), ana.getId(), "sesion-ana", frase);
    }

    private Usuario nuevoUsuario(String alias) {
        Usuario usuario = new Usuario();
        usuario.setEmail(alias + "-" + UUID.randomUUID() + "@forja.test");
        usuario.setNombre("Usuario " + alias);
        usuario.setPasswordHash("no-relevante");
        return usuarios.save(usuario);
    }

    private void inscribir(Proyecto proyecto, Usuario usuario, RolMiembro rol) {
        ProyectoMiembro miembro = new ProyectoMiembro();
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), usuario.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(usuario);
        miembro.setRol(rol);
        miembros.save(miembro);
    }

    private void limpiar() {
        operaciones.deleteAllInBatch();
        bloqueosRepo.deleteAllInBatch();
        relaciones.deleteAllInBatch();
        clases.deleteAllInBatch();
        miembros.deleteAllInBatch();
        diagramas.deleteAllInBatch();
        proyectos.deleteAllInBatch();
        usuarios.deleteAllInBatch();
    }
}
