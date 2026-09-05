package bo.forja.backend.servicio;

import bo.forja.backend.dominio.BloqueoElemento;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.repositorio.BloqueoElementoRepositorio;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion del mecanismo de exclusion mutua del lienzo colaborativo.
 * <p>
 * La prueba central no comprueba el camino feliz sino la carrera: varios
 * usuarios solicitando el mismo elemento en el mismo instante. Es la
 * situacion que el control de concurrencia existe para resolver y la
 * unica forma de constatar que la restriccion de unicidad realmente
 * arbitra el conflicto.
 * <p>
 * La clase no se anota como transaccional a proposito: cada hilo debe
 * ejecutar su propia transaccion contra la base de datos real, que es
 * donde ocurre el arbitraje.
 */
@SpringBootTest
@DisplayName("Exclusion mutua sobre elementos del diagrama")
class ServicioBloqueoTest {

    private static final int USUARIOS_EN_COMPETENCIA = 8;

    @Autowired
    private ServicioBloqueo servicioBloqueo;

    @Autowired
    private BloqueoElementoRepositorio bloqueos;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private ProyectoRepositorio proyectos;

    @Autowired
    private DiagramaRepositorio diagramas;

    @Autowired
    private ClaseUmlRepositorio clases;

    private Diagrama diagrama;
    private ClaseUml clase;
    private List<Usuario> competidores;

    @BeforeEach
    void prepararEscenario() {
        limpiar();

        Usuario propietario = nuevoUsuario("propietario");
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Sistema de gestion clinica");
        proyecto.setPropietario(propietario);
        proyectos.save(proyecto);

        diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre("Modelo de dominio");
        diagramas.save(diagrama);

        clase = new ClaseUml();
        clase.setDiagrama(diagrama);
        clase.setNombre("Paciente");
        clases.save(clase);

        competidores = new ArrayList<>();
        for (int i = 0; i < USUARIOS_EN_COMPETENCIA; i++) {
            competidores.add(nuevoUsuario("competidor" + i));
        }
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("ante una solicitud simultanea, exactamente un usuario obtiene el bloqueo")
    void unSoloUsuarioGanaLaCarrera() throws Exception {
        // Todos los hilos quedan detenidos en la misma barrera y se sueltan
        // a la vez, para que la solicitud sea genuinamente simultanea.
        CountDownLatch partida = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(USUARIOS_EN_COMPETENCIA);

        List<Future<ResultadoBloqueo>> intentos = new ArrayList<>();
        for (Usuario competidor : competidores) {
            intentos.add(ejecutor.submit(() -> {
                partida.await();
                return servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE,
                        clase.getId(), competidor.getId(), "sesion-" + competidor.getId());
            }));
        }

        partida.countDown();
        ejecutor.shutdown();
        assertThat(ejecutor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<ResultadoBloqueo> resultados = new ArrayList<>();
        for (Future<ResultadoBloqueo> intento : intentos) {
            resultados.add(intento.get());
        }

        long concedidos = resultados.stream()
                .filter(r -> r.estado() == ResultadoBloqueo.Estado.CONCEDIDO)
                .count();
        long rechazados = resultados.stream()
                .filter(r -> r.estado() == ResultadoBloqueo.Estado.RECHAZADO)
                .count();

        assertThat(concedidos)
                .as("un unico usuario debe obtener el bloqueo")
                .isEqualTo(1);
        assertThat(rechazados)
                .as("el resto debe ser rechazado")
                .isEqualTo(USUARIOS_EN_COMPETENCIA - 1);
        assertThat(bloqueos.findByDiagramaId(diagrama.getId()))
                .as("solo puede existir una fila de bloqueo para el elemento")
                .hasSize(1);
    }

    @Test
    @DisplayName("el rechazo informa que usuario retiene el elemento")
    void elRechazoIdentificaAlPoseedor() {
        Usuario poseedor = competidores.get(0);
        Usuario intruso = competidores.get(1);

        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, clase.getId(),
                poseedor.getId(), "sesion-poseedor");

        ResultadoBloqueo rechazo = servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE,
                clase.getId(), intruso.getId(), "sesion-intruso");

        assertThat(rechazo.estado()).isEqualTo(ResultadoBloqueo.Estado.RECHAZADO);
        assertThat(rechazo.puedeEditar()).isFalse();
        assertThat(rechazo.poseedorId()).isEqualTo(poseedor.getId());
        assertThat(rechazo.poseedorNombre()).isEqualTo(poseedor.getNombre());
    }

    @Test
    @DisplayName("quien ya posee el bloqueo lo renueva en lugar de competir consigo mismo")
    void elPoseedorRenuevaSuBloqueo() {
        Usuario poseedor = competidores.get(0);

        ResultadoBloqueo primera = servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE,
                clase.getId(), poseedor.getId(), "sesion-unica");
        ResultadoBloqueo segunda = servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE,
                clase.getId(), poseedor.getId(), "sesion-unica");

        assertThat(primera.estado()).isEqualTo(ResultadoBloqueo.Estado.CONCEDIDO);
        assertThat(segunda.estado()).isEqualTo(ResultadoBloqueo.Estado.RENOVADO);
        assertThat(segunda.expiraEn())
                .as("la renovacion debe extender la vigencia")
                .isAfterOrEqualTo(primera.expiraEn());
        assertThat(bloqueos.findByDiagramaId(diagrama.getId())).hasSize(1);
    }

    @Test
    @DisplayName("un bloqueo vencido no retiene el elemento")
    void elBloqueoVencidoSeDescarta() {
        Usuario ausente = competidores.get(0);
        Usuario siguiente = competidores.get(1);

        // Se simula el cliente que perdio la conexion sin liberar:
        // el bloqueo quedo en la tabla, pero su vigencia ya paso.
        BloqueoElemento abandonado = new BloqueoElemento();
        abandonado.setDiagrama(diagrama);
        abandonado.setElementoTipo(TipoElemento.CLASE);
        abandonado.setElementoId(clase.getId());
        abandonado.setUsuario(ausente);
        abandonado.setSesionId("sesion-caida");
        abandonado.setAdquiridoEn(Instant.now().minus(10, ChronoUnit.MINUTES));
        abandonado.setExpiraEn(Instant.now().minus(5, ChronoUnit.MINUTES));
        bloqueos.save(abandonado);

        ResultadoBloqueo resultado = servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE,
                clase.getId(), siguiente.getId(), "sesion-nueva");

        assertThat(resultado.estado()).isEqualTo(ResultadoBloqueo.Estado.CONCEDIDO);
        assertThat(bloqueos.findByDiagramaId(diagrama.getId())).hasSize(1);
    }

    @Test
    @DisplayName("un usuario no puede liberar el bloqueo de otro")
    void nadieLiberaBloqueoAjeno() {
        Usuario poseedor = competidores.get(0);
        Usuario intruso = competidores.get(1);

        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, clase.getId(),
                poseedor.getId(), "sesion-poseedor");

        boolean liberadoPorIntruso = servicioBloqueo.liberar(TipoElemento.CLASE, clase.getId(),
                intruso.getId(), "sesion-intruso");
        boolean liberadoPorPoseedor = servicioBloqueo.liberar(TipoElemento.CLASE, clase.getId(),
                poseedor.getId(), "sesion-poseedor");

        assertThat(liberadoPorIntruso).isFalse();
        assertThat(liberadoPorPoseedor).isTrue();
        assertThat(bloqueos.findByDiagramaId(diagrama.getId())).isEmpty();
    }

    @Test
    @DisplayName("al cerrarse la sesion se liberan todos sus bloqueos")
    void elCierreDeSesionLiberaTodo() {
        Usuario usuario = competidores.get(0);
        ClaseUml otra = new ClaseUml();
        otra.setDiagrama(diagrama);
        otra.setNombre("Consulta");
        clases.save(otra);

        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, clase.getId(),
                usuario.getId(), "sesion-movil");
        servicioBloqueo.adquirir(diagrama.getId(), TipoElemento.CLASE, otra.getId(),
                usuario.getId(), "sesion-movil");

        assertThat(bloqueos.findByDiagramaId(diagrama.getId())).hasSize(2);

        int liberados = servicioBloqueo.liberarSesion("sesion-movil");

        assertThat(liberados).isEqualTo(2);
        assertThat(bloqueos.findByDiagramaId(diagrama.getId())).isEmpty();
    }

    private Usuario nuevoUsuario(String alias) {
        Usuario usuario = new Usuario();
        usuario.setEmail(alias + "-" + UUID.randomUUID() + "@forja.test");
        usuario.setNombre("Usuario " + alias);
        usuario.setPasswordHash("no-relevante-en-esta-prueba");
        return usuarios.save(usuario);
    }

    private void limpiar() {
        bloqueos.deleteAllInBatch();
        clases.deleteAllInBatch();
        diagramas.deleteAllInBatch();
        proyectos.deleteAllInBatch();
        usuarios.deleteAllInBatch();
    }
}
