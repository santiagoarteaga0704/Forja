package bo.forja.backend.servicio;

import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.TipoOperacion;
import bo.forja.backend.repositorio.BloqueoElementoRepositorio;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verificacion del registro de operaciones sobre un diagrama compartido.
 * <p>
 * Las pruebas no se detienen en el camino feliz. Lo que hay que constatar
 * es el comportamiento en las tres situaciones que la sincronizacion
 * offline y el trabajo simultaneo producen de verdad: el reenvio de una
 * operacion ya registrada, la edicion de un elemento que otro retiene, y
 * varios cambios reclamando a la vez su lugar en la bitacora.
 * <p>
 * Como en la prueba de exclusion mutua, la clase no es transaccional a
 * proposito: cada llamada debe ejecutar su propia transaccion contra
 * Postgres, que es donde ocurren el arbitraje y la serializacion.
 */
@SpringBootTest
@DisplayName("Registro de operaciones y sincronizacion")
class ServicioOperacionesTest {

    private static final int CAMBIOS_SIMULTANEOS = 8;

    @Autowired
    private ServicioOperaciones servicioOperaciones;

    @Autowired
    private ServicioBloqueo servicioBloqueo;

    @Autowired
    private ServicioModelo servicioModelo;

    @Autowired
    private OperacionRepositorio operaciones;

    @Autowired
    private BloqueoElementoRepositorio bloqueos;

    @Autowired
    private RelacionUmlRepositorio relaciones;

    @Autowired
    private ClaseUmlRepositorio clases;

    @Autowired
    private ProyectoMiembroRepositorio miembros;

    @Autowired
    private DiagramaRepositorio diagramas;

    @Autowired
    private ProyectoRepositorio proyectos;

    @Autowired
    private UsuarioRepositorio usuarios;

    private Proyecto proyecto;
    private Diagrama diagrama;
    private Usuario editor;
    private Usuario otroEditor;
    private Usuario lector;

    @BeforeEach
    void prepararEscenario() {
        limpiar();

        Usuario propietario = nuevoUsuario("propietario");

        proyecto = new Proyecto();
        proyecto.setNombre("Sistema de gestion clinica");
        proyecto.setPropietario(propietario);
        proyectos.save(proyecto);

        diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre("Modelo de dominio");
        diagramas.save(diagrama);

        editor = nuevoMiembro("editor", RolMiembro.EDITOR);
        otroEditor = nuevoMiembro("otro-editor", RolMiembro.EDITOR);
        lector = nuevoMiembro("lector", RolMiembro.LECTOR);
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("una creacion aplica el cambio, avanza la version y queda en la bitacora")
    void laCreacionAvanzaLaVersion() {
        UUID claseId = UUID.randomUUID();

        ResultadoOperacion resultado = registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Paciente", null, false, 40, 60),
                OrigenOperacion.LIENZO, "token-1");

        assertThat(resultado.estado()).isEqualTo(ResultadoOperacion.Estado.APLICADA);
        assertThat(resultado.secuencia()).isEqualTo(1L);
        assertThat(resultado.tipo()).isEqualTo(TipoOperacion.CLASE_CREAR);
        assertThat(servicioOperaciones.versionActual(diagrama.getId())).isEqualTo(1L);
        assertThat(clases.findById(claseId))
                .as("la clase debe existir con el identificador que asigno el cliente")
                .isPresent()
                .get()
                .extracting(ClaseUml::getNombre)
                .isEqualTo("Paciente");
    }

    @Test
    @DisplayName("el reenvio de una operacion ya registrada no la duplica")
    void elReenvioEsIdempotente() {
        UUID claseId = UUID.randomUUID();
        ComandoOperacion comando =
                new ComandoOperacion.CrearClase(claseId, "Consulta", null, false, 0, 0);

        ResultadoOperacion primera =
                registrar(editor, "sesion-movil", comando, OrigenOperacion.LIENZO, "token-repetido");
        // Se reenvia el mismo token: es el caso del cliente que persistio el
        // cambio pero perdio la respuesta antes de recibirla.
        ResultadoOperacion reenvio =
                registrar(editor, "sesion-movil", comando, OrigenOperacion.LIENZO, "token-repetido");

        assertThat(primera.estado()).isEqualTo(ResultadoOperacion.Estado.APLICADA);
        assertThat(reenvio.estado()).isEqualTo(ResultadoOperacion.Estado.DUPLICADA);
        assertThat(reenvio.operacionId())
                .as("el reenvio debe responder la operacion ya registrada")
                .isEqualTo(primera.operacionId());
        assertThat(reenvio.secuencia()).isEqualTo(primera.secuencia());
        assertThat(operaciones.count())
                .as("la bitacora no debe crecer con un reenvio")
                .isEqualTo(1L);
        assertThat(servicioOperaciones.versionActual(diagrama.getId()))
                .as("la version tampoco debe avanzar")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("no se aplica un cambio sobre un elemento que otro usuario retiene")
    void elBloqueoAjenoRechazaElCambio() {
        UUID claseId = UUID.randomUUID();
        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Paciente", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-alta");

        // El primer editor toma el elemento, como al empezar a editarlo.
        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, claseId,
                editor.getId(), "sesion-editor");

        ResultadoOperacion rechazo = registrar(otroEditor, "sesion-otro",
                new ComandoOperacion.RenombrarClase(claseId, "Paciente Internado"),
                OrigenOperacion.LIENZO, "token-intruso");

        assertThat(rechazo.estado()).isEqualTo(ResultadoOperacion.Estado.RECHAZADA_POR_BLOQUEO);
        assertThat(rechazo.fueAceptada()).isFalse();
        assertThat(rechazo.debeDifundirse()).isFalse();
        assertThat(rechazo.bloqueo().poseedorId())
                .as("el rechazo debe decir quien retiene el elemento")
                .isEqualTo(editor.getId());
        assertThat(clases.findById(claseId).orElseThrow().getNombre())
                .as("el modelo no debe haber cambiado")
                .isEqualTo("Paciente");
        assertThat(operaciones.count())
                .as("una operacion rechazada no entra en la bitacora")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("quien retiene el elemento si puede seguir modificandolo")
    void elPoseedorSiPuedeEditar() {
        UUID claseId = UUID.randomUUID();
        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Paciente", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-alta");

        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, claseId,
                editor.getId(), "sesion-editor");

        ResultadoOperacion resultado = registrar(editor, "sesion-editor",
                new ComandoOperacion.RenombrarClase(claseId, "Paciente Internado"),
                OrigenOperacion.LIENZO, "token-renombre");

        assertThat(resultado.estado()).isEqualTo(ResultadoOperacion.Estado.APLICADA);
        assertThat(clases.findById(claseId).orElseThrow().getNombre())
                .isEqualTo("Paciente Internado");
    }

    @Test
    @DisplayName("un miembro con rol de lectura no puede modificar el diagrama")
    void elLectorNoModifica() {
        assertThatThrownBy(() -> registrar(lector, "sesion-lector",
                new ComandoOperacion.CrearClase(UUID.randomUUID(), "Factura", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-lector"))
                .isInstanceOf(AccesoDenegado.class)
                .hasMessageContaining("LECTOR");

        assertThat(operaciones.count()).isZero();
        assertThat(clases.findByDiagramaId(diagrama.getId())).isEmpty();
    }

    @Test
    @DisplayName("quien no es miembro del proyecto no puede modificar el diagrama")
    void elExtranoNoModifica() {
        Usuario extrano = nuevoUsuario("extrano");

        assertThatThrownBy(() -> registrar(extrano, "sesion-extrana",
                new ComandoOperacion.CrearClase(UUID.randomUUID(), "Factura", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-extrano"))
                .isInstanceOf(AccesoDenegado.class);

        assertThat(operaciones.count()).isZero();
    }

    @Test
    @DisplayName("marcar una clase como abstracta y con estereotipo queda guardado")
    void laClaseSeMarca() {
        UUID claseId = UUID.randomUUID();
        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Persona", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-1");

        ResultadoOperacion resultado = registrar(editor, "sesion-editor",
                new ComandoOperacion.MarcarClase(claseId, "interface", true),
                OrigenOperacion.LIENZO, "token-2");

        assertThat(resultado.estado()).isEqualTo(ResultadoOperacion.Estado.APLICADA);
        assertThat(resultado.tipo()).isEqualTo(TipoOperacion.CLASE_MARCAR);

        // El generador decide con estos dos datos si emite una interfaz, una
        // clase abstracta y que estrategia de herencia usa la jerarquia, asi que
        // tienen que haber quedado escritos y no solo aceptados.
        ClaseUml guardada = clases.findById(claseId).orElseThrow();
        assertThat(guardada.getEstereotipo()).isEqualTo("interface");
        assertThat(guardada.isEsAbstracta()).isTrue();
    }

    @Test
    @DisplayName("cada comando aceptado avanza la version del diagrama en uno")
    void laVersionAvanzaConCadaComando() {
        UUID claseId = UUID.randomUUID();

        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Paciente", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-1");
        assertThat(servicioOperaciones.versionActual(diagrama.getId()))
                .as("tras crear la clase").isEqualTo(1L);

        registrar(editor, "sesion-editor",
                new ComandoOperacion.AgregarAtributo(claseId, UUID.randomUUID(), "historiaClinica",
                        "String", Visibilidad.PRIVADO, true, true, true, 40),
                OrigenOperacion.VOZ, "token-2");
        assertThat(servicioOperaciones.versionActual(diagrama.getId()))
                .as("tras agregar el atributo").isEqualTo(2L);

        registrar(editor, "sesion-editor",
                new ComandoOperacion.MoverClase(claseId, 120, 240),
                OrigenOperacion.LIENZO, "token-3");
        assertThat(servicioOperaciones.versionActual(diagrama.getId()))
                .as("tras mover la clase").isEqualTo(3L);

        // Ademas de la version, se comprueba que los efectos de cada comando
        // quedaron escritos: un cambio que solo vive en memoria avanzaria la
        // bitacora sobre un modelo que en realidad no cambio.
        ClaseUml persistida = servicioModelo.clasesCompletas(diagrama.getId()).get(0);
        assertThat(persistida.getAtributos())
                .as("el atributo debe haberse guardado por cascada")
                .hasSize(1);
        assertThat(persistida.getAtributos().get(0).getNombre()).isEqualTo("historiaClinica");
        assertThat(persistida.getPosX()).as("la posicion nueva debe estar guardada").isEqualTo(120);
        assertThat(persistida.getPosY()).isEqualTo(240);
    }

    @Test
    @DisplayName("el delta trae solo lo posterior a la version conocida y reconstruye el comando")
    void elDeltaReconstruyeLosComandos() {
        UUID claseId = UUID.randomUUID();
        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(claseId, "Paciente", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-1");
        registrar(editor, "sesion-editor",
                new ComandoOperacion.AgregarAtributo(claseId, UUID.randomUUID(), "historiaClinica",
                        "String", Visibilidad.PRIVADO, true, true, true, 40),
                OrigenOperacion.VOZ, "token-2");
        registrar(editor, "sesion-editor",
                new ComandoOperacion.MoverClase(claseId, 120, 240),
                OrigenOperacion.LIENZO, "token-3");

        // El cliente conoce hasta la secuencia 1: debe recibir solo las dos
        // posteriores, y en el orden en que fueron aceptadas.
        List<OperacionRegistrada> delta =
                servicioOperaciones.delta(diagrama.getId(), editor.getId(), 1L);

        assertThat(delta).hasSize(2);
        assertThat(delta).extracting(OperacionRegistrada::secuencia).containsExactly(2L, 3L);

        OperacionRegistrada primera = delta.get(0);
        assertThat(primera.tipo()).isEqualTo(TipoOperacion.ATRIBUTO_AGREGAR);
        assertThat(primera.origen())
                .as("el canal de entrada debe conservarse: es la evidencia de cuanto se dicto")
                .isEqualTo(OrigenOperacion.VOZ);
        assertThat(primera.autorId()).isEqualTo(editor.getId());

        // El comando vuelve de la bitacora como el mismo record que se emitio.
        assertThat(primera.comando())
                .isInstanceOf(ComandoOperacion.AgregarAtributo.class);
        ComandoOperacion.AgregarAtributo atributo =
                (ComandoOperacion.AgregarAtributo) primera.comando();
        assertThat(atributo.nombre()).isEqualTo("historiaClinica");
        assertThat(atributo.tipo()).isEqualTo("String");
        assertThat(atributo.visibilidad()).isEqualTo(Visibilidad.PRIVADO);
        assertThat(atributo.esIdentificador()).isTrue();
        assertThat(atributo.longitud()).isEqualTo(40);

        assertThat(delta.get(1).comando()).isInstanceOf(ComandoOperacion.MoverClase.class);
    }

    @Test
    @DisplayName("un lector si puede leer el delta para sincronizarse")
    void elLectorSiSincroniza() {
        registrar(editor, "sesion-editor",
                new ComandoOperacion.CrearClase(UUID.randomUUID(), "Paciente", null, false, 0, 0),
                OrigenOperacion.LIENZO, "token-1");

        List<OperacionRegistrada> delta =
                servicioOperaciones.delta(diagrama.getId(), lector.getId(), 0L);

        assertThat(delta).hasSize(1);
    }

    @Test
    @DisplayName("ante cambios simultaneos las secuencias no se repiten ni dejan huecos")
    void lasSecuenciasSonUnicasBajoConcurrencia() throws Exception {
        // Se crean clases distintas, de modo que no compitan por un bloqueo:
        // lo que se pone a prueba es la asignacion del numero de secuencia.
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(CAMBIOS_SIMULTANEOS);

        List<Future<ResultadoOperacion>> intentos = new ArrayList<>();
        for (int i = 0; i < CAMBIOS_SIMULTANEOS; i++) {
            int indice = i;
            intentos.add(ejecutor.submit(() -> {
                partida.await();
                return registrar(editor, "sesion-" + indice,
                        new ComandoOperacion.CrearClase(UUID.randomUUID(),
                                "Clase" + indice, null, false, indice * 10, 0),
                        OrigenOperacion.LIENZO, "token-" + indice);
            }));
        }

        partida.countDown();
        ejecutor.shutdown();
        assertThat(ejecutor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        List<Long> secuencias = new ArrayList<>();
        for (Future<ResultadoOperacion> intento : intentos) {
            ResultadoOperacion resultado = intento.get();
            assertThat(resultado.estado()).isEqualTo(ResultadoOperacion.Estado.APLICADA);
            secuencias.add(resultado.secuencia());
        }

        assertThat(secuencias)
                .as("cada cambio debe ocupar una posicion propia, sin repetir ni saltear")
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(servicioOperaciones.versionActual(diagrama.getId()))
                .isEqualTo(CAMBIOS_SIMULTANEOS);
        assertThat(clases.findByDiagramaId(diagrama.getId())).hasSize(CAMBIOS_SIMULTANEOS);
    }

    // ---------- Auxiliares -------------------------------------------------

    private ResultadoOperacion registrar(Usuario autor, String sesionId,
                                         ComandoOperacion comando,
                                         OrigenOperacion origen, String token) {
        return servicioOperaciones.registrar(diagrama.getId(), autor.getId(), sesionId,
                comando, origen, token);
    }

    private Usuario nuevoUsuario(String alias) {
        Usuario usuario = new Usuario();
        usuario.setEmail(alias + "-" + UUID.randomUUID() + "@forja.test");
        usuario.setNombre("Usuario " + alias);
        usuario.setPasswordHash("no-relevante-en-esta-prueba");
        return usuarios.save(usuario);
    }

    private Usuario nuevoMiembro(String alias, RolMiembro rol) {
        Usuario usuario = nuevoUsuario(alias);
        ProyectoMiembro miembro = new ProyectoMiembro();
        // La clave se fija explicitamente: con @MapsId sin valor, Spring Data
        // no puede decidir si la fila es nueva y elige la rama equivocada.
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), usuario.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(usuario);
        miembro.setRol(rol);
        miembros.save(miembro);
        return usuario;
    }

    private void limpiar() {
        operaciones.deleteAllInBatch();
        bloqueos.deleteAllInBatch();
        relaciones.deleteAllInBatch();
        clases.deleteAllInBatch();
        miembros.deleteAllInBatch();
        diagramas.deleteAllInBatch();
        proyectos.deleteAllInBatch();
        usuarios.deleteAllInBatch();
    }
}
