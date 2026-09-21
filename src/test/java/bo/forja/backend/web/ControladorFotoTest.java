package bo.forja.backend.web;

import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.OperacionRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La foto de un diagrama dibujado, leida por el servicio de vision.
 * <p>
 * Contra un servicio de mentira y no contra Gemini: una prueba que llama al
 * servicio de verdad cuesta dinero, necesita una clave para construir el
 * proyecto y contesta distinto en cada corrida. Lo que se verifica aca es que
 * la imagen llegue, que la transcripcion vuelva, y sobre todo <b>quien puede
 * pedirla</b>: del otro lado hay una clave de pago, asi que la puerta importa
 * tanto como la funcion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Lectura de la foto por el servicio de vision")
class ControladorFotoTest {

    private static final String PASSWORD = "contrasena-larga-1";
    private static final byte[] IMAGEN = "no-es-una-imagen-de-verdad".getBytes(StandardCharsets.UTF_8);

    private static HttpServer servicioDeMentira;
    private static volatile String respuesta = "";

    @BeforeAll
    static void levantarServicio() throws IOException {
        servicioDeMentira = HttpServer.create(new InetSocketAddress(0), 0);
        servicioDeMentira.createContext("/", intercambio -> {
            intercambio.getRequestBody().readAllBytes();
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servicioDeMentira.start();
    }

    @AfterAll
    static void bajarServicio() {
        servicioDeMentira.stop(0);
    }

    @DynamicPropertySource
    static void apuntarAlServicioDeMentira(DynamicPropertyRegistry registro) {
        registro.add("forja.ia.vision.habilitada", () -> true);
        registro.add("forja.ia.vision.clave", () -> "la-clave");
        registro.add("forja.ia.vision.url",
                () -> "http://localhost:" + servicioDeMentira.getAddress().getPort());
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private OperacionRepositorio operaciones;
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

    private String tokenAna;
    private String tokenAjena;
    private UUID diagramaId;

    @BeforeEach
    void prepararEscenario() throws Exception {
        limpiar();
        tokenAna = campo(registrar("ana"), "token");
        tokenAjena = campo(registrar("ajena"), "token");

        UUID proyectoId = UUID.fromString(campo(mvc.perform(post("/api/proyectos")
                .header("Authorization", "Bearer " + tokenAna)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("nombre", "Sistema academico"))))
                .andExpect(status().isCreated())
                .andReturn(), "id"));

        diagramaId = UUID.fromString(campo(mvc.perform(post("/api/proyectos/" + proyectoId + "/diagramas")
                .header("Authorization", "Bearer " + tokenAna)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Map.of("nombre", "Modelo de dominio", "tipo", "CLASES"))))
                .andExpect(status().isCreated())
                .andReturn(), "id"));
    }

    @AfterEach
    void limpiar() {
        operaciones.deleteAll();
        relaciones.deleteAll();
        clases.deleteAll();
        diagramas.deleteAll();
        miembros.deleteAll();
        proyectos.deleteAll();
        usuarios.deleteAll();
    }

    @Test
    @DisplayName("devuelve la transcripcion de la imagen, sin tocar el diagrama")
    void transcribeLaImagen() throws Exception {
        contesta("Paciente\\n+ nombre: String");

        mvc.perform(multipart("/api/diagramas/" + diagramaId + "/foto/transcripcion")
                        .file(new MockMultipartFile("imagen", "pizarra.png", "image/png", IMAGEN))
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Paciente\n+ nombre: String"));

        // La transcripcion no aplica nada: eso lo decide la persona despues.
        assert clases.count() == 0;
    }

    @Test
    @DisplayName("sin token no se puede pedir")
    void sinToken() throws Exception {
        contesta("Paciente");

        mvc.perform(multipart("/api/diagramas/" + diagramaId + "/foto/transcripcion")
                        .file(new MockMultipartFile("imagen", "pizarra.png", "image/png", IMAGEN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("quien no tiene el diagrama no puede gastar la clave")
    void ajenoAlProyecto() throws Exception {
        // Del otro lado hay un servicio de pago: sin esta puerta, cualquier
        // cuenta registrada podria mandar imagenes a costa del proyecto.
        contesta("Paciente");

        mvc.perform(multipart("/api/diagramas/" + diagramaId + "/foto/transcripcion")
                        .file(new MockMultipartFile("imagen", "pizarra.png", "image/png", IMAGEN))
                        .header("Authorization", "Bearer " + tokenAjena))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("dice si la lectura por IA esta disponible")
    void informaSiEstaDisponible() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/diagramas/" + diagramaId + "/foto/disponible")
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true));
    }

    private void contesta(String texto) {
        respuesta = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + texto + "\"}]}}]}";
    }

    private MvcResult registrar(String nombre) throws Exception {
        return mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of(
                                "nombre", nombre,
                                "email", nombre + "-" + UUID.randomUUID() + "@forja.test",
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String cuerpo(Map<String, Object> datos) {
        return json.writeValueAsString(datos);
    }

    @SuppressWarnings("unchecked")
    private String campo(MvcResult resultado, String nombre) throws Exception {
        return String.valueOf(json.readValue(
                resultado.getResponse().getContentAsString(), Map.class).get(nombre));
    }
}
