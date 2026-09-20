package bo.forja.backend.pedido;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.ia.Traductor;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Que se aplique lo que se reviso, y no otra cosa.
 * <p>
 * Esta prueba nace de una corrida real del 20 de septiembre de 2026 contra
 * Gemma 3 4B: la propuesta que se mostro traia cuatro clases -una de ellas con
 * una composicion sin sentido- y al aplicar entraron tres distintas. El modelo
 * responde distinto en cada corrida aun con {@code temperature} en cero, asi
 * que consultarlo de nuevo al aplicar convertia el paso de revision en un
 * adorno: la persona aprobaba una propuesta y el diagrama recibia otra.
 * <p>
 * El traductor de mentira propone algo DISTINTO en la segunda llamada, que es
 * exactamente lo que hace el modelo de verdad. Si {@code aplicar} volviera a
 * consultarlo, en el diagrama quedaria Veterinario en vez de Mascota.
 */
@SpringBootTest
@DisplayName("Lo que se aplica es lo que se reviso")
class ServicioPedidoRevisionTest {

    /**
     * Anota cuantas veces lo llamaron y cambia de opinion, como el modelo real.
     */
    static class TraductorQueCambiaDeOpinion implements Traductor {
        final List<String> llamadas = new ArrayList<>();

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration presupuesto) {
            llamadas.add(pedido);
            return llamadas.size() == 1
                    ? List.of("crea la clase Mascota")
                    : List.of("crea la clase Veterinario");
        }

        @Override
        public boolean disponible() {
            return true;
        }
    }

    @TestConfiguration
    static class ConTraductorDeMentira {
        @Bean
        @Primary
        Traductor traductorDeMentira() {
            return new TraductorQueCambiaDeOpinion();
        }
    }

    @Autowired
    private ServicioPedido pedidos;
    @Autowired
    private Traductor traductor;

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
    private Diagrama diagrama;

    @BeforeEach
    void prepararEscenario() {
        limpiar();
        // El traductor es un bean, y el contexto se reusa entre metodos: sin
        // esto el contador arrastra las llamadas de la prueba anterior.
        ((TraductorQueCambiaDeOpinion) traductor).llamadas.clear();
        ana = nuevoUsuario("ana");

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Veterinaria");
        proyecto.setPropietario(ana);
        proyectos.save(proyecto);
        inscribir(proyecto, ana, RolMiembro.PROPIETARIO);

        diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre("Pedido");
        diagramas.save(diagrama);
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("aplicar usa la propuesta revisada, sin volver a consultar al modelo")
    void seAplicaLoRevisado() {
        TraductorQueCambiaDeOpinion deMentira = (TraductorQueCambiaDeOpinion) traductor;
        String token = "lectura-" + UUID.randomUUID();

        Pedido propuesto = pedidos.leer(diagrama.getId(), ana.getId(), "arma una veterinaria", token);
        assertThat(propuesto.comandos()).hasSize(1);
        assertThat(deMentira.llamadas).hasSize(1);

        ServicioPedido.ResultadoPedido resultado = pedidos.aplicar(
                diagrama.getId(), ana.getId(), "sesion-1", "arma una veterinaria", token);

        assertThat(resultado.aplicadas()).isEqualTo(1);
        assertThat(deMentira.llamadas)
                .as("aplicar no debe volver a consultar al modelo")
                .hasSize(1);
        assertThat(clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Mascota"))
                .as("tiene que entrar lo revisado")
                .isPresent();
        assertThat(clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Veterinario"))
                .as("no puede entrar una propuesta que nadie vio")
                .isEmpty();
    }

    /**
     * Sin lectura previa no hay nada que reusar, y la funcion tiene que seguir
     * andando igual que antes: se consulta al modelo y se aplica. Es el camino
     * del cliente movil, que puede pedir y aplicar en un solo paso.
     */
    @Test
    @DisplayName("sin propuesta guardada se consulta al modelo, como antes")
    void sinLecturaPreviaSeConsulta() {
        TraductorQueCambiaDeOpinion deMentira = (TraductorQueCambiaDeOpinion) traductor;

        ServicioPedido.ResultadoPedido resultado = pedidos.aplicar(
                diagrama.getId(), ana.getId(), "sesion-1", "arma una veterinaria",
                "token-sin-lectura");

        assertThat(deMentira.llamadas).hasSize(1);
        assertThat(resultado.aplicadas()).isEqualTo(1);
        assertThat(clases.findByDiagramaIdAndNombreIgnoreCase(diagrama.getId(), "Mascota"))
                .isPresent();
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
