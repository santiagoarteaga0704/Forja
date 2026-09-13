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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorrido completo de la API tal como la usara el lienzo.
 * <p>
 * Reproduce la sesion real de dos personas trabajando sobre el mismo
 * diagrama: una crea el modelo, toma un elemento para editarlo, la otra
 * choca contra ese bloqueo, y despues de liberarlo consigue aplicar su
 * cambio. Es la prueba que demuestra que las piezas -autenticacion,
 * permisos, exclusion mutua, bitacora y sincronizacion- funcionan juntas y
 * no solo por separado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("API del lienzo colaborativo")
class ControladorDiagramasTest {

    private static final String PASSWORD = "contrasena-larga-1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

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

    private String tokenAna;
    private String tokenBruno;
    private UUID anaId;
    private UUID proyectoId;
    private UUID diagramaId;

    private final String sesionAna = "sesion-ana-" + UUID.randomUUID();
    private final String sesionBruno = "sesion-bruno-" + UUID.randomUUID();

    @BeforeEach
    void prepararEscenario() throws Exception {
        limpiar();

        MvcResult altaAna = registrar("ana");
        tokenAna = campo(altaAna, "token");
        anaId = UUID.fromString(campo(altaAna, "usuarioId"));
        tokenBruno = campo(registrar("bruno"), "token");

        MvcResult proyecto = mvc.perform(post("/api/proyectos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Sistema de gestion clinica"))))
                .andExpect(status().isCreated())
                .andReturn();
        proyectoId = UUID.fromString(campo(proyecto, "id"));

        // Ana invita a Bruno: sin esto, Bruno no veria el proyecto.
        mvc.perform(post("/api/proyectos/" + proyectoId + "/miembros")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo("bruno"), "rol", "EDITOR"))))
                .andExpect(status().isCreated());

        MvcResult diagrama = mvc.perform(post("/api/proyectos/" + proyectoId + "/diagramas")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("nombre", "Modelo de dominio", "tipo", "CLASES"))))
                .andExpect(status().isCreated())
                .andReturn();
        diagramaId = UUID.fromString(campo(diagrama, "id"));
    }

    @AfterEach
    void limpiarEscenario() {
        limpiar();
    }

    @Test
    @DisplayName("dos usuarios construyen el modelo y el bloqueo arbitra el conflicto")
    void recorridoCompletoDelLienzo() throws Exception {
        UUID claseId = UUID.randomUUID();

        // 1. Ana crea la clase.
        operacion(tokenAna, sesionAna, "CLASE_CREAR", Map.of(
                "claseId", claseId.toString(), "nombre", "Paciente",
                "esAbstracta", false, "posX", 40, "posY", 60),
                "LIENZO", "token-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.secuencia").value(1));

        // 2. Le dicta un atributo por voz: el origen queda registrado y es la
        //    evidencia de cuanto del modelo se construyo hablando.
        operacion(tokenAna, sesionAna, "ATRIBUTO_AGREGAR", Map.of(
                "claseId", claseId.toString(), "atributoId", UUID.randomUUID().toString(),
                "nombre", "historiaClinica", "tipo", "String",
                "visibilidad", "PRIVADO", "esIdentificador", true,
                "esRequerido", true, "esUnico", true, "longitud", 40),
                "VOZ", "token-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.secuencia").value(2));

        // 3. La fotografia del diagrama refleja lo construido.
        mvc.perform(get("/api/diagramas/" + diagramaId)
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.clases.length()").value(1))
                .andExpect(jsonPath("$.clases[0].nombre").value("Paciente"))
                .andExpect(jsonPath("$.clases[0].atributos.length()").value(1))
                .andExpect(jsonPath("$.clases[0].atributos[0].nombre").value("historiaClinica"))
                .andExpect(jsonPath("$.clases[0].atributos[0].esIdentificador").value(true))
                .andExpect(jsonPath("$.bloqueos.length()").value(0));

        // 4. Ana toma la clase para editarla.
        mvc.perform(post("/api/diagramas/" + diagramaId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("elementoTipo", "CLASE",
                                "elementoId", claseId.toString(), "sesionId", sesionAna))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCEDIDO"));

        // 5. Bruno intenta renombrarla y choca con el bloqueo de Ana.
        operacion(tokenBruno, sesionBruno, "CLASE_RENOMBRAR", Map.of(
                "claseId", claseId.toString(), "nombre", "Paciente Internado"),
                "LIENZO", "token-3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA_POR_BLOQUEO"))
                .andExpect(jsonPath("$.bloqueo.poseedorId").value(anaId.toString()));

        // El modelo no cambio y la bitacora no crecio.
        mvc.perform(get("/api/diagramas/" + diagramaId)
                        .header("Authorization", "Bearer " + tokenBruno))
                .andExpect(jsonPath("$.clases[0].nombre").value("Paciente"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.bloqueos.length()").value(1))
                .andExpect(jsonPath("$.bloqueos[0].poseedorId").value(anaId.toString()));

        // 6. Ana suelta el elemento.
        mvc.perform(delete("/api/diagramas/" + diagramaId + "/bloqueos")
                        .header("Authorization", "Bearer " + tokenAna)
                        .param("elementoTipo", "CLASE")
                        .param("elementoId", claseId.toString())
                        .param("sesionId", sesionAna))
                .andExpect(status().isNoContent());

        // 7. Ahora Bruno si puede aplicar su cambio.
        operacion(tokenBruno, sesionBruno, "CLASE_RENOMBRAR", Map.of(
                "claseId", claseId.toString(), "nombre", "Paciente Internado"),
                "LIENZO", "token-4")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"))
                .andExpect(jsonPath("$.secuencia").value(3));

        // 8. Un cliente que conocia la version 1 recibe solo el delta, con los
        //    comandos reconstruidos y su canal de entrada.
        mvc.perform(get("/api/diagramas/" + diagramaId + "/operaciones")
                        .header("Authorization", "Bearer " + tokenBruno)
                        .param("desde", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].secuencia").value(2))
                .andExpect(jsonPath("$[0].tipo").value("ATRIBUTO_AGREGAR"))
                .andExpect(jsonPath("$[0].origen").value("VOZ"))
                .andExpect(jsonPath("$[0].comando.nombre").value("historiaClinica"))
                .andExpect(jsonPath("$[1].secuencia").value(3))
                .andExpect(jsonPath("$[1].tipo").value("CLASE_RENOMBRAR"));
    }

    @Test
    @DisplayName("el reenvio de una operacion no la duplica")
    void elReenvioNoDuplica() throws Exception {
        UUID claseId = UUID.randomUUID();
        Map<String, Object> comando = Map.of("claseId", claseId.toString(), "nombre", "Consulta",
                "esAbstracta", false, "posX", 0, "posY", 0);

        operacion(tokenAna, sesionAna, "CLASE_CREAR", comando, "LIENZO", "token-unico")
                .andExpect(jsonPath("$.estado").value("APLICADA"));
        operacion(tokenAna, sesionAna, "CLASE_CREAR", comando, "LIENZO", "token-unico")
                .andExpect(jsonPath("$.estado").value("DUPLICADA"))
                .andExpect(jsonPath("$.secuencia").value(1));

        mvc.perform(get("/api/diagramas/" + diagramaId)
                        .header("Authorization", "Bearer " + tokenAna))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.clases.length()").value(1));
    }

    @Test
    @DisplayName("un comando que viola el modelo se rechaza con 422")
    void elComandoInvalidoSeRechaza() throws Exception {
        UUID claseId = UUID.randomUUID();
        operacion(tokenAna, sesionAna, "CLASE_CREAR", Map.of(
                "claseId", claseId.toString(), "nombre", "Paciente",
                "esAbstracta", false, "posX", 0, "posY", 0), "LIENZO", "token-1")
                .andExpect(status().isOk());

        // Dos clases con el mismo nombre en un diagrama no son UML valido.
        operacion(tokenAna, sesionAna, "CLASE_CREAR", Map.of(
                "claseId", UUID.randomUUID().toString(), "nombre", "Paciente",
                "esAbstracta", false, "posX", 200, "posY", 0), "LIENZO", "token-2")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Comando invalido"));
    }

    @Test
    @DisplayName("quien no es miembro del proyecto no ve el diagrama")
    void elExtranoNoVeElDiagrama() throws Exception {
        String tokenCarla = campo(registrar("carla"), "token");

        mvc.perform(get("/api/diagramas/" + diagramaId)
                        .header("Authorization", "Bearer " + tokenCarla))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin token no se alcanza el diagrama")
    void sinTokenNoSeVe() throws Exception {
        mvc.perform(get("/api/diagramas/" + diagramaId))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Auxiliares -------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions operacion(
            String token, String sesionId, String tipo,
            Map<String, Object> comando, String origen, String tokenCliente) throws Exception {

        Map<String, Object> envio = new LinkedHashMap<>();
        envio.put("tipo", tipo);
        envio.put("comando", comando);
        envio.put("origen", origen);
        envio.put("sesionId", sesionId);
        envio.put("tokenCliente", tokenCliente);

        return mvc.perform(post("/api/diagramas/" + diagramaId + "/operaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(envio)));
    }

    private MvcResult registrar(String alias) throws Exception {
        return mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo(alias), "nombre", "Usuario " + alias,
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    /**
     * Correo estable por alias dentro de la prueba: el escenario necesita
     * invitar a Bruno por su correo despues de haberlo registrado.
     */
    private String correo(String alias) {
        return alias + "@forja.test";
    }

    private String cuerpo(Map<String, ?> campos) {
        return json.writeValueAsString(campos);
    }

    private String campo(MvcResult resultado, String nombre) throws Exception {
        Map<?, ?> respuesta = json.readValue(resultado.getResponse().getContentAsString(), Map.class);
        return String.valueOf(respuesta.get(nombre));
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
