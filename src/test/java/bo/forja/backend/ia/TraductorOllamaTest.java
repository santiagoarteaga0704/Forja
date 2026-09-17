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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El traductor contra un servidor de mentira.
 * <p>
 * Ninguna prueba invoca un modelo de verdad: uno de 4B tarda segundos, contesta
 * distinto en cada corrida y obligaria a tener Ollama instalado para poder
 * construir el proyecto. Lo que hay que verificar aca no es que el modelo
 * acierte -eso se ensaya a mano- sino que el traductor arme bien el pedido,
 * entienda la respuesta y NO SE CUELGUE NUNCA, que es lo unico que puede
 * arruinar el dictado.
 */
@DisplayName("Traductor por Ollama")
class TraductorOllamaTest {

    private HttpServer servidor;
    private final AtomicReference<String> ultimoPedido = new AtomicReference<>();
    private volatile String respuesta = "";
    private volatile int demoraMs = 0;
    private volatile int codigo = 200;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(0), 0);
        servidor.createContext("/api/generate", intercambio -> {
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

    private Traductor traductor() {
        return new TraductorOllama(new ConfiguracionIa.AjustesDeIa(
                true, "http://localhost:" + servidor.getAddress().getPort(), "gemma3:4b"));
    }

    @Test
    @DisplayName("parte la respuesta en una frase por linea")
    void partePorLineas() {
        respuesta = "{\"response\":\"crea la clase Dueno\\ncrea la clase Mascota\\n"
                + "Dueno tiene muchas Mascotas\"}";

        List<String> frases = traductor().aFrasesCanonicas(
                "arma una veterinaria", List.of(), Duration.ofSeconds(5));

        assertThat(frases).containsExactly(
                "crea la clase Dueno",
                "crea la clase Mascota",
                "Dueno tiene muchas Mascotas");
    }

    @Test
    @DisplayName("descarta las lineas vacias y la numeracion que el modelo agrega solo")
    void limpiaLaSalida() {
        // Los modelos chicos numeran y ponen vinetas aunque se les pida que no.
        respuesta = "{\"response\":\"1. crea la clase Dueno\\n\\n- crea la clase Mascota\\n   \\n\"}";

        assertThat(traductor().aFrasesCanonicas("x", List.of(), Duration.ofSeconds(5)))
                .containsExactly("crea la clase Dueno", "crea la clase Mascota");
    }

    @Test
    @DisplayName("le pasa al modelo las clases que ya existen y el nombre del modelo")
    void nombraLasClasesConocidas() {
        respuesta = "{\"response\":\"crea la clase Factura\"}";

        traductor().aFrasesCanonicas("agrega facturas", List.of("Paciente", "Consulta"),
                Duration.ofSeconds(5));

        assertThat(ultimoPedido.get()).contains("Paciente").contains("Consulta");
        assertThat(ultimoPedido.get()).contains("gemma3:4b");
    }

    @Test
    @DisplayName("al vencerse el presupuesto devuelve vacio, no espera")
    void respetaElPresupuesto() {
        respuesta = "{\"response\":\"crea la clase Tarde\"}";
        demoraMs = 1500;

        long antes = System.currentTimeMillis();
        List<String> frases = traductor().aFrasesCanonicas("x", List.of(), Duration.ofMillis(300));
        long tardo = System.currentTimeMillis() - antes;

        assertThat(frases).isEmpty();
        // Sin reintentos: el presupuesto es el presupuesto, no el presupuesto
        // multiplicado por la cantidad de intentos.
        assertThat(tardo).as("tardo " + tardo + " ms").isLessThan(1200);
    }

    @Test
    @DisplayName("un error del servidor devuelve vacio y no propaga")
    void unErrorNoSePropaga() {
        codigo = 500;
        respuesta = "{}";

        assertThat(traductor().aFrasesCanonicas("x", List.of(), Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    @DisplayName("una respuesta que no es JSON devuelve vacio")
    void respuestaIlegible() {
        respuesta = "esto no es json";

        assertThat(traductor().aFrasesCanonicas("x", List.of(), Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    @DisplayName("si Ollama no esta, devuelve vacio en vez de reventar")
    void sinServidor() {
        Traductor sinNadie = new TraductorOllama(
                new ConfiguracionIa.AjustesDeIa(true, "http://localhost:1", "gemma3:4b"));

        assertThat(sinNadie.aFrasesCanonicas("x", List.of(), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    @DisplayName("disponible es cierto cuando esta configurado")
    void disponibleCuandoEstaConfigurado() {
        assertThat(traductor().disponible()).isTrue();
    }
}
