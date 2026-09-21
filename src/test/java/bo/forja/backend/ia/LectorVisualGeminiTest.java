package bo.forja.backend.ia;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El lector visual contra un servicio de mentira.
 * <p>
 * Ninguna prueba llama a Gemini de verdad: costaria dinero, necesitaria una
 * clave para construir el proyecto y contestaria distinto en cada corrida. Que
 * el modelo acierte se ensaya a mano y se anota lo medido; lo que se verifica
 * aca es que el lector arme bien el pedido, entienda la respuesta, NO SE CUELGUE
 * NUNCA y no filtre la clave.
 */
@DisplayName("Lectura de una foto por Gemini")
class LectorVisualGeminiTest {

    private static final byte[] IMAGEN = "no-es-una-imagen-de-verdad".getBytes(StandardCharsets.UTF_8);

    private HttpServer servidor;
    private final AtomicReference<String> ultimoPedido = new AtomicReference<>();
    private final AtomicReference<String> ultimaRuta = new AtomicReference<>();
    private final AtomicReference<String> claveEnCabecera = new AtomicReference<>();
    private volatile String respuesta = "";
    private volatile int demoraMs = 0;
    private volatile int codigo = 200;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(0), 0);
        servidor.createContext("/", intercambio -> {
            ultimaRuta.set(intercambio.getRequestURI().toString());
            claveEnCabecera.set(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            try (InputStream entrada = intercambio.getRequestBody()) {
                ultimoPedido.set(new String(entrada.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(codigo, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void bajarServidor() {
        servidor.stop(0);
    }

    private LectorVisualGemini lector() {
        return new LectorVisualGemini(new ConfiguracionIa.AjustesDeVision(
                true, "http://localhost:" + servidor.getAddress().getPort(),
                "gemini-3.1-flash-lite", "la-clave"));
    }

    private void contesta(String texto) {
        respuesta = """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
                """.formatted(texto.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
    }

    @Test
    @DisplayName("manda la imagen adentro del pedido, en base 64")
    void mandaLaImagen() {
        contesta("Paciente");

        lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30));

        assertThat(ultimoPedido.get())
                .contains(Base64.getEncoder().encodeToString(IMAGEN))
                .contains("image/png");
    }

    @Test
    @DisplayName("la clave viaja en la cabecera y no en la direccion")
    void laClaveNoViajaEnLaDireccion() {
        // En la direccion terminaria en los registros del servidor, en el
        // historial del navegador y en cualquier intermediario. Es la misma
        // clave para todo el proyecto.
        contesta("Paciente");

        lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30));

        assertThat(claveEnCabecera.get()).isEqualTo("la-clave");
        assertThat(ultimaRuta.get()).doesNotContain("la-clave");
    }

    @Test
    @DisplayName("devuelve la transcripcion tal como la escribio el modelo")
    void devuelveLaTranscripcion() {
        contesta("Paciente\n+ nombre: String\n\nPaciente --|> Persona");

        String leido = lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30));

        assertThat(leido).isEqualTo("Paciente\n+ nombre: String\n\nPaciente --|> Persona");
    }

    @Test
    @DisplayName("le saca las comillas de codigo con que el modelo envuelve la respuesta")
    void sacaLasComillasDeCodigo() {
        // Se le pide que no use markdown y lo usa igual. Sin sacarlas, la
        // primera y la ultima linea entrarian al parser como basura.
        contesta("```\nPaciente\n+ nombre: String\n```");

        String leido = lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30));

        assertThat(leido).isEqualTo("Paciente\n+ nombre: String");
    }

    @Test
    @DisplayName("un error del servicio devuelve vacio y no rompe")
    void elServicioFalla() {
        // Medido el 21 de septiembre de 2026: en una misma sesion el servicio
        // devolvio 404 por un modelo retirado y 503 por alta demanda. Que la
        // pantalla siga viva no es una hipotesis.
        codigo = 503;
        respuesta = "{\"error\":{\"message\":\"high demand\"}}";

        assertThatCode(() -> assertThat(
                lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30))).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("al vencerse el presupuesto devuelve vacio y no se cuelga")
    void noSeCuelga() {
        demoraMs = 2000;
        contesta("Paciente");

        long arranque = System.currentTimeMillis();
        String leido = lector().aNotacionDePizarra(IMAGEN, "image/png", Duration.ofMillis(300));

        assertThat(leido).isEmpty();
        assertThat(System.currentTimeMillis() - arranque).isLessThan(1500);
    }

    @Test
    @DisplayName("sin clave no esta disponible y no llama a nadie")
    void sinClaveNoEstaDisponible() {
        LectorVisualGemini sinClave = new LectorVisualGemini(new ConfiguracionIa.AjustesDeVision(
                true, "http://localhost:" + servidor.getAddress().getPort(),
                "gemini-3.1-flash-lite", "  "));

        assertThat(sinClave.disponible()).isFalse();
        assertThat(sinClave.aNotacionDePizarra(IMAGEN, "image/png", Duration.ofSeconds(30))).isEmpty();
        assertThat(ultimoPedido.get()).isNull();
    }
}
