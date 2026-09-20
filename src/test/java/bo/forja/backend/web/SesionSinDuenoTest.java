package bo.forja.backend.web;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un token valido cuya persona ya no existe.
 * <p>
 * No es un caso de laboratorio: paso de verdad el 20 de septiembre de 2026.
 * La sesion no tiene estado y el token dura doce horas, asi que sobrevive a que
 * la fila del usuario desaparezca -y desaparece cada vez que se corre la suite,
 * que vacia las tablas-. El sintoma era pesimo: se apretaba "Crear proyecto" y
 * salia "No existe el usuario 6278cccb-..." en rojo, con un 404 que el cliente
 * no podia distinguir de "ese diagrama no existe", asi que no podia reaccionar
 * y la persona quedaba trabada sin saber que hacer.
 * <p>
 * Es <b>401 y no 404</b>: el problema no es el recurso que se pidio, es la
 * credencial, que apunta a alguien que ya no esta. Con ese estado el cliente
 * tira la sesion y manda al login, que es la unica salida util.
 * <p>
 * Importa para la defensa: rehacer la base antes de demostrar, con el navegador
 * todavia abierto, es exactamente esta situacion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Sesión cuya cuenta ya no existe")
class SesionSinDuenoTest {

    private static final String PASSWORD = "contrasena-larga-1";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

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

    private String token;

    @BeforeEach
    void prepararEscenario() throws Exception {
        limpiar();

        MvcResult alta = mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", "fantasma@forja.test",
                                "nombre", "Fantasma",
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> credencial = json.readValue(alta.getResponse().getContentAsString(), Map.class);
        token = String.valueOf(credencial.get("token"));

        // Se va la persona; el token, que no tiene estado, sigue siendo valido.
        usuarios.deleteAllInBatch();
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("crear un proyecto responde 401, no 404")
    void crearProyectoDa401() throws Exception {
        mvc.perform(post("/api/proyectos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nombre", "Prueba"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("sesión")));
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
