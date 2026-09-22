package bo.forja.backend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * En el despliegue, el cliente web viaja dentro del mismo servidor que la API.
 * <p>
 * Es una sola imagen y un solo origen, y eso elimina de raiz tres problemas que
 * en la nube cuestan tiempo: no hay CORS que configurar, no hay un segundo
 * dominio que registrar, y el canal colaborativo viaja por el mismo puerto.
 * <p>
 * Para que eso funcione, las rutas de la web no pueden exigir un token: quien
 * abre la aplicacion por primera vez todavia no lo tiene, y si {@code /} pidiera
 * autenticacion la pantalla de entrada no se podria ni descargar. Aqui se
 * verifica esa regla sin depender de que los archivos existan -en las pruebas no
 * estan, porque los construye la imagen-: lo que importa es que la seguridad las
 * deje pasar, y una ruta que pasa y no encuentra el archivo responde 404, no 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("La web se sirve desde el mismo servidor que la API")
class WebServidaDesdeElBackendTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("la pagina y sus recursos no exigen token")
    void laWebNoExigeToken() throws Exception {
        for (String ruta : new String[]{"/", "/index.html", "/assets/app.js", "/favicon.ico",
                "/marca/forja-favicon.svg"}) {
            mvc.perform(get(ruta))
                    .andExpect(status().is(not(401)));
        }
    }

    @Test
    @DisplayName("la API sigue exigiendolo")
    void laApiSigueProtegida() throws Exception {
        // La regla anterior no puede abrir la puerta de al lado.
        mvc.perform(get("/api/proyectos")).andExpect(status().isUnauthorized());
    }

    private static org.hamcrest.Matcher<Integer> not(int codigo) {
        return org.hamcrest.Matchers.not(org.hamcrest.Matchers.is(codigo));
    }
}
