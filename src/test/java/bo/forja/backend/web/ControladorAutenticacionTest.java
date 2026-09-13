package bo.forja.backend.web;

import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificacion del alta de cuentas y del inicio de sesion.
 * <p>
 * Ademas del camino feliz se comprueba lo que la autenticacion existe
 * para impedir: entrar con una contrasena equivocada, registrar dos veces
 * el mismo correo y alcanzar un recurso protegido sin presentar token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Autenticacion")
class ControladorAutenticacionTest {

    private static final String PASSWORD = "contrasena-larga-1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UsuarioRepositorio usuarios;

    private String correoCreado;

    @AfterEach
    void limpiar() {
        if (correoCreado != null) {
            usuarios.findByEmail(correoCreado).map(Usuario::getId).ifPresent(usuarios::deleteById);
            correoCreado = null;
        }
    }

    @Test
    @DisplayName("el registro devuelve un token que da acceso a los recursos protegidos")
    void elRegistroEmiteUnTokenUtilizable() throws Exception {
        String correo = correoNuevo();

        MvcResult alta = mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo, "nombre", "Ana Modeladora",
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(correo))
                .andExpect(jsonPath("$.nombre").value("Ana Modeladora"))
                .andReturn();

        String token = leerToken(alta);

        // El token recien emitido debe servir de inmediato: es lo que el
        // cliente guarda para no volver a pedir la contrasena.
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(correo))
                .andExpect(jsonPath("$.usuarioId").isNotEmpty());
    }

    @Test
    @DisplayName("el correo en mayusculas y con espacios se normaliza")
    void elCorreoSeNormaliza() throws Exception {
        String correo = correoNuevo();

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", "  " + correo.toUpperCase() + " ",
                                "nombre", "Ana", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(correo));

        // Y se puede entrar escribiendolo de cualquier forma.
        mvc.perform(post("/api/auth/sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("no se puede registrar dos veces el mismo correo")
    void elCorreoNoSeRepite() throws Exception {
        String correo = correoNuevo();
        String cuerpo = cuerpo(Map.of("email", correo, "nombre", "Ana", "password", PASSWORD));

        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Correo ya registrado"));
    }

    @Test
    @DisplayName("una contrasena equivocada no abre sesion")
    void laContrasenaEquivocadaNoEntra() throws Exception {
        String correo = correoNuevo();
        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo, "nombre", "Ana",
                                "password", PASSWORD))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correo, "password", "otra-contrasena"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un correo que no existe responde igual que una contrasena equivocada")
    void elCorreoInexistenteNoSeDelata() throws Exception {
        mvc.perform(post("/api/auth/sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correoNuevo(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Credenciales invalidas"));
        // Nada quedo creado: el correo aleatorio solo sirvio para la consulta.
        correoCreado = null;
    }

    @Test
    @DisplayName("sin token no se alcanza un recurso protegido")
    void sinTokenNoSeEntra() throws Exception {
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("una contrasena demasiado corta se rechaza detallando el campo")
    void laContrasenaCortaSeRechaza() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(Map.of("email", correoNuevo(), "nombre", "Ana",
                                "password", "corta"))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).contains("password");
        correoCreado = null;
    }

    // ---------- Auxiliares -------------------------------------------------

    private String correoNuevo() {
        correoCreado = "prueba-" + UUID.randomUUID() + "@forja.test";
        return correoCreado;
    }

    private String cuerpo(Map<String, String> campos) {
        return json.writeValueAsString(campos);
    }

    private String leerToken(MvcResult resultado) throws Exception {
        Map<?, ?> respuesta = json.readValue(resultado.getResponse().getContentAsString(), Map.class);
        return (String) respuesta.get("token");
    }
}
