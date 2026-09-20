package bo.forja.backend.agente;

import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.ia.Respondedor;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuando le preguntan "y ahora que hago", el agente MIRA.
 * <p>
 * Nace de una queja justa, vista el 20 de septiembre: alguien con un proyecto ya
 * creado le pregunto "pero ahorita que hago, ya tengo el proyecto" y el agente
 * le contesto, palabra por palabra, "crea un proyecto". Dos veces seguidas.
 * <p>
 * La causa era que las preguntas eran un catalogo de palabras clave y NO
 * recibian el estado: contestaban igual el primer dia que el ultimo. Los
 * consejos si miraban -{@code Panorama} y {@code Observacion}-, pero esa mitad
 * no. Para un agente cuyo requisito es MONITOREAR la aplicacion y ensenar a
 * usarla, contestar sin mirar es el peor defecto posible.
 * <p>
 * Ahora esa pregunta se responde con el primer paso pendiente del
 * {@link Recorrido}, que ya estaba calculado a partir de evidencia real -la
 * bitacora y el registro de uso- y no de pantallas visitadas.
 */
@SpringBootTest
@DisplayName("El agente mira el estado para decir qué sigue")
class AgenteMiraElEstadoTest {

    /** Guarda el contexto que se le paso, que es lo que hay que verificar. */
    static class RespondedorDeMentira implements Respondedor {
        final List<String> contextos = new ArrayList<>();

        @Override
        public Optional<String> responder(String pregunta, String contexto, Duration presupuesto) {
            contextos.add(contexto);
            return Optional.of("Una respuesta cualquiera.");
        }

        @Override
        public boolean disponible() {
            return true;
        }
    }

    @TestConfiguration
    static class ConRespondedorDeMentira {
        @Bean
        @Primary
        Respondedor respondedorDeMentira() {
            return new RespondedorDeMentira();
        }
    }

    @Autowired
    private ServicioAgente agente;
    @Autowired
    private Respondedor respondedor;

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
    private BloqueoElementoRepositorio bloqueos;

    private Usuario santiago;

    @BeforeEach
    void prepararEscenario() {
        limpiar();
        santiago = new Usuario();
        santiago.setEmail("santiago-" + UUID.randomUUID() + "@forja.test");
        santiago.setNombre("Santiago");
        santiago.setPasswordHash("no-relevante");
        usuarios.save(santiago);
        ((RespondedorDeMentira) respondedor).contextos.clear();
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("sin nada creado, lo que sigue es crear un proyecto")
    void sinProyectosMandaACrearlo() {
        List<Consejo> respuesta = agente.responder(santiago.getId(), "ahora que hago", null);

        assertThat(textoDe(respuesta)).containsIgnoringCase("proyecto");
    }

    /**
     * El caso exacto de la queja. Con el proyecto ya creado, repetir "crea un
     * proyecto" es la prueba de que no se esta mirando nada.
     */
    @Test
    @DisplayName("con el proyecto ya creado, NO vuelve a decir que cree un proyecto")
    void conProyectoNoRepiteElPrimerPaso() {
        crearProyecto("prueba");

        List<Consejo> respuesta = agente.responder(santiago.getId(),
                "pero ahorita que hago ya tengo el proyecto", null);

        assertThat(textoDe(respuesta))
                .as("ya tiene proyecto: el paso siguiente es el diagrama")
                .containsIgnoringCase("diagrama");
        assertThat(textoDe(respuesta))
                .as("no puede mandarlo a crear lo que ya creó")
                .doesNotContainIgnoringCase("creá un proyecto")
                .doesNotContainIgnoringCase("crea un proyecto");
    }

    @Test
    @DisplayName("la respuesta cambia cuando cambia el estado")
    void laRespuestaSigueAlEstado() {
        String sinNada = textoDe(agente.responder(santiago.getId(), "y ahora que sigue", null));

        crearProyecto("prueba");
        String conProyecto = textoDe(agente.responder(santiago.getId(), "y ahora que sigue", null));

        assertThat(conProyecto)
                .as("el agente monitorea: la misma pregunta da otra respuesta si el estado cambió")
                .isNotEqualTo(sinNada);
    }

    /**
     * El caso que lo motivo: con dos proyectos y ningun diagrama, se pregunto
     * "como lo creo" y el modelo contesto "con el boton Clase de la barra".
     * Ese boton vive en el lienzo, que la persona no tenia abierto, asi que la
     * mando a buscar algo que no estaba en su pantalla. El modelo no tenia como
     * saberlo: se le pasaba el catalogo y nada mas.
     */
    @Test
    @DisplayName("al modelo se le pasa dónde está la persona y qué le falta")
    void elModeloRecibeElEstado() {
        crearProyecto("hola");

        agente.responder(santiago.getId(), "y donde esta ese boton exactamente", null);

        String contexto = ((RespondedorDeMentira) respondedor).contextos.getLast();
        assertThat(contexto)
                .as("tiene que saber qué hay y qué falta")
                .contains("Crear un diagrama")
                .containsIgnoringCase("proyectos");
        assertThat(contexto)
                .as("y que los botones del lienzo no existen sin un diagrama abierto")
                .containsIgnoringCase("lienzo");
    }

    /**
     * El paso tiene que decir QUE APRETAR, no solo en que pantalla. Decir "se
     * hace en la pantalla de proyectos" obliga a preguntar de nuevo, y esa
     * segunda pregunta caia en el modelo, que contestaba cualquier cosa.
     */
    @Test
    @DisplayName("lo que sigue dice qué apretar, con el nombre del control")
    void elPasoDiceQueApretar() {
        crearProyecto("hola");

        String texto = textoDe(agente.responder(santiago.getId(), "ahora que sigue", null));

        assertThat(texto)
                .as("el control que hay que usar, por su nombre")
                .containsIgnoringCase("Crear y abrir");
    }

    /**
     * El caso exacto: "¿cómo lo creo?" dicho justo despues de "lo que sigue es
     * crear un diagrama". Es un seguimiento de ese paso y lo tienen que
     * contestar las reglas, que saben cual es. Antes caia en el modelo y
     * contestaba "con el boton Clase de la barra", que vive en otra pantalla.
     */
    @Test
    @DisplayName("«cómo lo creo» después de un paso lo contestan las reglas, no el modelo")
    void elSeguimientoLoContestanLasReglas() {
        crearProyecto("hola");

        List<Consejo> respuesta = agente.responder(
                santiago.getId(), "como lo creo", ServicioAgente.PROXIMO_PASO);

        assertThat(respuesta).extracting(Consejo::id)
                .as("no puede haber ido al modelo")
                .containsExactly(ServicioAgente.PROXIMO_PASO);
        assertThat(textoDe(respuesta))
                .containsIgnoringCase("diagrama")
                .doesNotContainIgnoringCase("botón Clase");
    }

    private String textoDe(List<Consejo> consejos) {
        return consejos.stream()
                .map(c -> c.queNote() + " " + c.porQueImporta() + " " + c.comoSeHace())
                .reduce("", (a, b) -> a + " " + b);
    }

    private void crearProyecto(String nombre) {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setPropietario(santiago);
        proyectos.save(proyecto);

        ProyectoMiembro miembro = new ProyectoMiembro();
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), santiago.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(santiago);
        miembro.setRol(RolMiembro.PROPIETARIO);
        miembros.save(miembro);
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
