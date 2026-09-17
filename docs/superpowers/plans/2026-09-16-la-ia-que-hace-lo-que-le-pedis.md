# La IA que hace lo que le pedís — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el cliente web entienda frases libres y convierta un pedido en un diagrama, con un modelo que corre en la máquina del usuario.

**Architecture:** Un contrato `Traductor` con tres implementaciones, de las cuales este plan escribe dos: la nula —que es la de por omisión y hace que todo siga funcionando exactamente como hoy— y la de Ollama. El traductor **no emite comandos**: propone frases canónicas que `ParserVoz` vuelve a interpretar, así el motor determinista sigue siendo el único que toca el diagrama y la validación sale gratis.

**Tech Stack:** Java 21 + Spring Boot 4.1.1 · `RestClient` (ya viene con `spring-boot-starter-webmvc`) · `com.sun.net.httpserver` del JDK para el doble de pruebas · React + TypeScript. **Ninguna dependencia nueva.**

**Spec:** `docs/superpowers/specs/2026-09-16-ia-local-en-el-movil-design.md`

Este plan cubre el paso 4 del diseño. El paso 5 —Gemma en el móvil— va aparte y es el único recortable.

## Global Constraints

- **Ninguna dependencia nueva** en `pom.xml` ni en `web/package.json`.
- **El traductor está apagado por omisión.** Sin configuración el bean que se registra es `TraductorNulo`, el backend se comporta exactamente como hoy y **las 234 pruebas actuales siguen verdes sin tocar ninguna**. Eso no es una comodidad: es lo que hace que este bloque sea recortable.
- **Ninguna prueba automatizada invoca un modelo de verdad.** `TraductorOllama` se prueba contra un servidor HTTP del JDK levantado en la prueba. El modelo real se ejercita a mano.
- **El LLM no produce comandos.** Produce frases. Lo que no parsea se descarta sin ruido.
- **`ParserVoz.java` no se toca.**
- **Convención de escritura del repositorio**: comentarios y nombres **sin acentos**; textos de interfaz **con acentos**.
- **Dos presupuestos de tiempo**: 3 s duros para el dictado en vivo, 30 s para el pedido explícito.

## Cambio respecto del diseño

El diseño decía `format: json` en la llamada a Ollama. **Se usa texto plano, una frase por línea.** Un modelo de 4B emitiendo JSON agrega un modo de falla —JSON mal formado, y se pierde la respuesta entera— encima del que ya se maneja sin drama: una línea que no parsea, que se descarta sola. Las líneas se degradan de a una; el JSON se degrada de golpe.

---

### Task 1: El contrato, el traductor nulo y su configuración

Primero la pieza que garantiza que nada cambia. Recién con eso en su lugar tiene sentido enchufar algo que sí hable.

**Files:**
- Create: `src/main/java/bo/forja/backend/ia/Traductor.java`
- Create: `src/main/java/bo/forja/backend/ia/TraductorNulo.java`
- Create: `src/main/java/bo/forja/backend/ia/ConfiguracionIa.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/bo/forja/backend/ia/ConfiguracionIaTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `interface Traductor` con `List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas, Duration presupuesto)` y `boolean disponible()`. Las Tasks 2, 3 y 4 dependen de esta firma exacta.

- [ ] **Step 1: Escribir la prueba que falla**

`src/test/java/bo/forja/backend/ia/ConfiguracionIaTest.java`:

```java
package bo.forja.backend.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sin configuracion, el traductor que se registra es el nulo.
 * <p>
 * Es la propiedad que hace recortable a todo este bloque: si el modelo no esta,
 * si Ollama no corre, o si se decide sacarlo, la aplicacion se comporta
 * exactamente como antes de que existiera.
 */
@SpringBootTest
@DisplayName("Configuracion de la IA")
class ConfiguracionIaTest {

    @Autowired
    private Traductor traductor;

    @Test
    @DisplayName("por omision no hay traductor y no se cae")
    void porOmisionEsNulo() {
        assertThat(traductor.disponible()).isFalse();
        assertThat(traductor.aFrasesCanonicas("lo que sea", List.of("Paciente"),
                Duration.ofSeconds(3))).isEmpty();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw test -Dtest=ConfiguracionIaTest`
Expected: FALLA al compilar, `cannot find symbol: class Traductor`.

- [ ] **Step 3: Escribir el contrato**

`src/main/java/bo/forja/backend/ia/Traductor.java`:

```java
package bo.forja.backend.ia;

import java.time.Duration;
import java.util.List;

/**
 * Traduce lo que una persona pide a las frases que la gramatica sabe
 * interpretar.
 * <p>
 * No emite comandos, y eso es deliberado. Devolviendo frases, el motor
 * determinista sigue siendo el unico que toca el diagrama y la validacion sale
 * gratis: una frase que no parsea se descarta y no pasa nada. Si el traductor
 * emitiera comandos habria que escribir un validador para lo que invente, y un
 * modelo chico inventa identificadores, tipos de operacion y nombres de campo
 * mucho mas facil de lo que dice una frase de otra manera.
 * <p>
 * Devolver una LISTA y no una frase es lo que convierte un pedido en un
 * diagrama: "arma una veterinaria con duenos, mascotas y consultas" son cinco
 * frases, y cada una entra por la gramatica por separado.
 */
public interface Traductor {

    /**
     * @param clasesConocidas las que ya estan en el diagrama, para que el
     *                        modelo las nombre en vez de duplicarlas
     * @param presupuesto     tiempo maximo. Al vencerse se devuelve vacio, no
     *                        se espera: del otro lado hay alguien mirando
     * @return las frases propuestas, o vacio si no pudo. Vacio siempre es una
     *         respuesta aceptable
     */
    List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas,
                                  Duration presupuesto);

    /** Para no ofrecer en la interfaz algo que no va a contestar. */
    boolean disponible();
}
```

`src/main/java/bo/forja/backend/ia/TraductorNulo.java`:

```java
package bo.forja.backend.ia;

import java.time.Duration;
import java.util.List;

/**
 * El traductor de por omision: no traduce nada.
 * <p>
 * Con este registrado la aplicacion es exactamente la de antes de que existiera
 * el bloque de IA. La gramatica sigue siendo la primera y unica via, y una
 * frase que no engancha devuelve sugerencias como siempre.
 */
public class TraductorNulo implements Traductor {

    @Override
    public List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas,
                                         Duration presupuesto) {
        return List.of();
    }

    @Override
    public boolean disponible() {
        return false;
    }
}
```

`src/main/java/bo/forja/backend/ia/ConfiguracionIa.java`:

```java
package bo.forja.backend.ia;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Que traductor se registra. Sin configuracion, el nulo.
 * <p>
 * La condicion es que este habilitado explicitamente, no que Ollama responda:
 * comprobar el modelo al arrancar retrasaria el arranque y ataria la aplicacion
 * a que un servicio externo este vivo antes que ella.
 */
@Configuration
public class ConfiguracionIa {

    @ConfigurationProperties("forja.ia.local")
    public record AjustesDeIa(boolean habilitada, String url, String modelo) {

        public AjustesDeIa {
            url = url == null || url.isBlank() ? "http://localhost:11434" : url;
            modelo = modelo == null || modelo.isBlank() ? "gemma3:4b" : modelo;
        }
    }

    @Bean
    public AjustesDeIa ajustesDeIa() {
        return new AjustesDeIa(false, null, null);
    }

    @Bean
    public Traductor traductor(AjustesDeIa ajustes) {
        return ajustes.habilitada() ? new TraductorOllama(ajustes) : new TraductorNulo();
    }
}
```

**Nota para quien implementa:** el bean `ajustesDeIa` de arriba no es la forma correcta de leer `@ConfigurationProperties` en Spring Boot 4 — está escrito así para que la Task 1 compile sola. La forma correcta es `@EnableConfigurationProperties(AjustesDeIa.class)` sobre la clase de configuración y dejar que Spring construya el record desde `application.yml`. **Usar esa**, y borrar el `@Bean ajustesDeIa`. Si `@EnableConfigurationProperties` sobre un record da problemas en esta versión, mirar cómo lo hace `bo.forja.backend` con `forja.jwt` y copiar ese patrón exacto.

Como `TraductorOllama` todavía no existe, en esta tarea el bean devuelve siempre `new TraductorNulo()` y se deja un `// TODO` explícito; la Task 2 lo completa. Ese es el único TODO que este plan permite, y muere en la tarea siguiente.

- [ ] **Step 4: Agregar la configuración, apagada**

En `src/main/resources/application.yml`, bajo `forja:`, después del bloque `bloqueo:`:

```yaml
  ia:
    local:
      # Apagada por omision a proposito: sin esto la aplicacion es la de
      # siempre, y ese es justamente el punto. Se prende con
      # FORJA_IA_HABILITADA=true y Ollama corriendo en la maquina.
      habilitada: ${FORJA_IA_HABILITADA:false}
      url: ${FORJA_IA_URL:http://localhost:11434}
      modelo: ${FORJA_IA_MODELO:gemma3:4b}
```

**Cuidado:** `gemma3:4b` lleva dos puntos, que en YAML dentro de `${...}` es el separador del valor por omisión. Escribirlo entre comillas: `${FORJA_IA_MODELO:'gemma3:4b'}` o poner el valor por omisión en el record y dejar `${FORJA_IA_MODELO:}`. **Verificar que el backend arranca** después de tocar esto, no solo que las pruebas pasan.

- [ ] **Step 5: Correr y verificar que pasa**

Run: `./mvnw test -Dtest=ConfiguracionIaTest`
Expected: PASA.

- [ ] **Step 6: Correr la suite entera**

Run: `./mvnw test`
Expected: PASA, 235 pruebas (las 234 de antes más ésta). **Ninguna prueba existente se tocó.**

- [ ] **Step 7: Commit**

```bash
git add src/main/java/bo/forja/backend/ia src/main/resources/application.yml src/test/java/bo/forja/backend/ia
git commit -m "Agregar el contrato del traductor, apagado por omision"
```

---

### Task 2: `TraductorOllama` contra un servidor de prueba

**Files:**
- Create: `src/main/java/bo/forja/backend/ia/TraductorOllama.java`
- Modify: `src/main/java/bo/forja/backend/ia/ConfiguracionIa.java` (sacar el TODO)
- Test: `src/test/java/bo/forja/backend/ia/TraductorOllamaTest.java`

**Interfaces:**
- Consumes: `Traductor`, `ConfiguracionIa.AjustesDeIa` (Task 1).
- Produces: `TraductorOllama(AjustesDeIa ajustes)`. Las Tasks 3 y 4 lo usan solo a través del contrato `Traductor`.

Ollama habla HTTP y JSON. `POST /api/generate` con `{"model": ..., "prompt": ..., "stream": false, "options": {"temperature": 0}}` devuelve `{"response": "..."}`.

- [ ] **Step 1: Escribir la prueba que falla**

`src/test/java/bo/forja/backend/ia/TraductorOllamaTest.java`:

```java
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
 * Ninguna prueba invoca un modelo de verdad: uno de 4B tarda segundos, da
 * respuestas distintas en cada corrida y obligaria a tener Ollama instalado
 * para poder construir el proyecto. Lo que hay que verificar aca no es que el
 * modelo acierte -eso se ensaya a mano- sino que el traductor arme bien el
 * pedido, entienda la respuesta y no se cuelgue nunca.
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
        respuesta = """
                {"response":"crea la clase Dueno\\ncrea la clase Mascota\\nDueno tiene muchas Mascotas"}
                """;

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
        respuesta = """
                {"response":"1. crea la clase Dueno\\n\\n- crea la clase Mascota\\n   \\n"}
                """;

        assertThat(traductor().aFrasesCanonicas("x", List.of(), Duration.ofSeconds(5)))
                .containsExactly("crea la clase Dueno", "crea la clase Mascota");
    }

    @Test
    @DisplayName("le pasa al modelo las clases que ya existen")
    void nombraLasClasesConocidas() {
        respuesta = "{\\"response\\":\\"crea la clase Factura\\"}";

        traductor().aFrasesCanonicas("agrega facturas", List.of("Paciente", "Consulta"),
                Duration.ofSeconds(5));

        assertThat(ultimoPedido.get()).contains("Paciente").contains("Consulta");
        assertThat(ultimoPedido.get()).contains("gemma3:4b");
    }

    @Test
    @DisplayName("al vencerse el presupuesto devuelve vacio, no espera")
    void respetaElPresupuesto() {
        respuesta = "{\\"response\\":\\"crea la clase Tarde\\"}";
        demoraMs = 1500;

        long antes = System.currentTimeMillis();
        List<String> frases = traductor().aFrasesCanonicas("x", List.of(), Duration.ofMillis(300));
        long tardo = System.currentTimeMillis() - antes;

        assertThat(frases).isEmpty();
        // Sin reintentos: el presupuesto es el presupuesto, no el presupuesto
        // por la cantidad de intentos.
        assertThat(tardo).isLessThan(1200);
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
    @DisplayName("disponible es cierto cuando esta configurado")
    void disponibleCuandoEstaConfigurado() {
        assertThat(traductor().disponible()).isTrue();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw test -Dtest=TraductorOllamaTest`
Expected: FALLA al compilar, `cannot find symbol: class TraductorOllama`.

- [ ] **Step 3: Escribir el traductor**

`src/main/java/bo/forja/backend/ia/TraductorOllama.java`. Puntos que no se pueden saltear:

1. **`maxRetries` a cero.** El cliente HTTP reintenta por omisión, y con reintentos un presupuesto de 3 s se convierte en 9 s de reloj. Con `RestClient` se consigue no envolviendo en nada que reintente y poniendo el timeout en la fábrica de peticiones.
2. **El timeout se pone por llamada**, porque los dos presupuestos son distintos. `RestClient` toma el timeout de su fábrica, así que se construye un cliente por llamada o se usa `JdkClientHttpRequestFactory` con `setReadTimeout(presupuesto)`.
3. **Nada se propaga.** Todo el cuerpo va en un `try/catch (Exception)` que devuelve `List.of()` y registra en `debug`. Un traductor que lanza rompe el dictado, que es justo lo que no puede pasar.
4. El prompt va abajo, en Step 4.

```java
package bo.forja.backend.ia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TraductorOllama implements Traductor {

    private static final Logger log = LoggerFactory.getLogger(TraductorOllama.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ConfiguracionIa.AjustesDeIa ajustes;

    public TraductorOllama(ConfiguracionIa.AjustesDeIa ajustes) {
        this.ajustes = ajustes;
    }

    @Override
    public List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas,
                                         Duration presupuesto) {
        try {
            JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
            fabrica.setReadTimeout(presupuesto);

            String cuerpo = RestClient.builder()
                    .requestFactory(fabrica)
                    .baseUrl(ajustes.url())
                    .build()
                    .post()
                    .uri("/api/generate")
                    .body(Map.of(
                            "model", ajustes.modelo(),
                            "prompt", promptPara(pedido, clasesConocidas),
                            "stream", false,
                            "options", Map.of("temperature", 0)))
                    .retrieve()
                    .body(String.class);

            return lineasDe(JSON.readValue(cuerpo, Map.class).get("response"));
        } catch (Exception e) {
            // Nunca se propaga: un traductor que revienta rompe el dictado, que
            // es justo lo que este bloque no puede hacer.
            log.debug("El traductor local no contesto: {}", e.toString());
            return List.of();
        }
    }

    /**
     * Una frase por linea, sin lo que el modelo agrega solo. Los modelos chicos
     * numeran las listas y ponen vinetas aunque se les pida que no.
     */
    private List<String> lineasDe(Object respuesta) {
        if (!(respuesta instanceof String texto)) {
            return List.of();
        }
        return Arrays.stream(texto.split("\\R"))
                .map(linea -> linea.replaceAll("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").trim())
                .filter(linea -> !linea.isBlank())
                .toList();
    }

    @Override
    public boolean disponible() {
        return ajustes.habilitada();
    }
}
```

- [ ] **Step 4: Escribir el prompt**

Va como método privado `promptPara(String pedido, List<String> clasesConocidas)` en la misma clase. Las formas que se listan tienen que ser formas que la gramática realmente acepta — están en `compartido/corpus-voz.json`, que es la fuente de verdad. No inventar formas.

```java
    private String promptPara(String pedido, List<String> clasesConocidas) {
        String existentes = clasesConocidas.isEmpty()
                ? "(el diagrama esta vacio)"
                : String.join(", ", clasesConocidas);

        return """
                Convertis un pedido en instrucciones para una herramienta de diagramas de clases UML.

                Respondes SOLO con instrucciones, una por linea. Sin numerar, sin vinetas, sin explicar
                y sin saludar. Si el pedido no se puede expresar con las formas de abajo, no respondes
                nada.

                Formas que entiende la herramienta, y son las unicas que podes usar:
                crea la clase NOMBRE
                crea la clase NOMBRE con los atributos UNO de tipo texto y OTRO de tipo entero
                a NOMBRE agregale el atributo UNO de tipo texto
                a NOMBRE agregale el atributo UNO de tipo texto obligatorio
                a NOMBRE agregale el metodo HACER que devuelve entero
                NOMBRE hereda de OTRO
                NOMBRE tiene muchas OTROS
                NOMBRE se compone de muchas OTROS
                marca NOMBRE como abstracta

                Tipos que existen: texto, entero, decimal, booleano, fecha, fechayhora.
                Los nombres de clase van en singular y con la primera letra en mayuscula.

                Clases que ya estan en el diagrama, usalas en vez de crearlas de nuevo: %s

                Pedido: %s
                """.formatted(existentes, pedido);
    }
```

- [ ] **Step 5: Completar el bean de la Task 1**

En `ConfiguracionIa`, reemplazar el `// TODO` por `return ajustes.habilitada() ? new TraductorOllama(ajustes) : new TraductorNulo();`.

- [ ] **Step 6: Correr y verificar que pasa**

Run: `./mvnw test -Dtest=TraductorOllamaTest`
Expected: PASA, 7 pruebas.

Si `respetaElPresupuesto` falla porque tarda de más, el cliente está reintentando: revisar la fábrica de peticiones.

- [ ] **Step 7: Correr la suite entera**

Run: `./mvnw test`
Expected: PASA, 242 pruebas.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/bo/forja/backend/ia src/test/java/bo/forja/backend/ia
git commit -m "Traducir con Gemma por Ollama, contra un servidor de prueba"
```

---

### Task 3: El respaldo del dictado en la web

**Files:**
- Modify: `src/main/java/bo/forja/backend/voz/ServicioVoz.java`
- Test: `src/test/java/bo/forja/backend/voz/ServicioVozConTraductorTest.java`

**Interfaces:**
- Consumes: `Traductor` (Task 1).
- Produces: nada nuevo hacia afuera. `ServicioVoz.dictar` cambia de comportamiento **solo cuando hay traductor disponible**.

La regla: la gramática va primero **siempre**. Solo si `Interpretacion.entendida()` es falso y el traductor está disponible se pide una traducción, **una sola vuelta**, con presupuesto de 3 s. Cada frase propuesta entra por `parser.interpretar` y se juntan los pasos de las que parsearon.

- [ ] **Step 1: Escribir la prueba que falla**

`src/test/java/bo/forja/backend/voz/ServicioVozConTraductorTest.java`. Usa un traductor de mentira, no Ollama:

```java
package bo.forja.backend.voz;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El traductor como RESPALDO, nunca como primera via.
 * <p>
 * Lo que se verifica no es que el modelo acierte sino que la gramatica siga
 * mandando: una frase que ella entiende no llega nunca al traductor, y lo que
 * el traductor propone tiene que pasar por la gramatica igual que cualquier
 * otra frase.
 */
@DisplayName("El dictado con traductor de respaldo")
class ServicioVozConTraductorTest {

    private final ParserVoz parser = new ParserVoz();

    /** Anota si lo llamaron, que es la mitad de lo que hay que probar. */
    private static class TraductorDeMentira implements Traductor {
        final List<String> pedidos = new java.util.ArrayList<>();
        List<String> loQuePropone = List.of();

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration p) {
            pedidos.add(pedido);
            return loQuePropone;
        }

        @Override
        public boolean disponible() {
            return true;
        }
    }

    @Test
    @DisplayName("una frase que la gramatica entiende no llega al traductor")
    void laGramaticaVaPrimero() {
        TraductorDeMentira traductor = new TraductorDeMentira();

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "crea la clase Factura", ContextoDelDiagrama.vacio());

        assertThat(resultado.entendida()).isTrue();
        assertThat(traductor.pedidos).as("no se le pregunta al modelo si no hace falta").isEmpty();
    }

    @Test
    @DisplayName("una frase libre se traduce y vuelve a pasar por la gramatica")
    void elRespaldoTraduce() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("crea la clase Factura");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "necesito algo para las facturas",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.entendida()).isTrue();
        assertThat(resultado.pasos()).hasSize(1);
        assertThat(traductor.pedidos).containsExactly("necesito algo para las facturas");
    }

    @Test
    @DisplayName("lo que el modelo propone y no parsea se descarta sin ruido")
    void loQueNoParseaSeDescarta() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of(
                "crea la clase Factura",
                "hace lo que quieras con las facturas",
                "crea la clase Renglon");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "facturas con renglones", ContextoDelDiagrama.vacio());

        assertThat(resultado.pasos()).hasSize(2);
    }

    @Test
    @DisplayName("si el traductor no propone nada, vuelven las sugerencias de siempre")
    void sinPropuestaVuelvenLasSugerencias() {
        TraductorDeMentira traductor = new TraductorDeMentira();

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "hola que tal", ContextoDelDiagrama.vacio());

        assertThat(resultado.entendida()).isFalse();
        assertThat(resultado.sugerencias()).isNotEmpty();
    }

    @Test
    @DisplayName("no hay segunda vuelta: lo traducido no se vuelve a traducir")
    void unaSolaVuelta() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("esto tampoco parsea");

        DictadoConRespaldo.interpretar(
                parser, traductor, "cualquier cosa", ContextoDelDiagrama.vacio());

        assertThat(traductor.pedidos).hasSize(1);
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw test -Dtest=ServicioVozConTraductorTest`
Expected: FALLA, `cannot find symbol: class DictadoConRespaldo`.

- [ ] **Step 3: Escribir `DictadoConRespaldo`**

`src/main/java/bo/forja/backend/voz/DictadoConRespaldo.java`. Va como clase aparte y no dentro de `ServicioVoz` para que se pueda probar **sin Spring ni base de datos**, que es lo que hace que estas cinco pruebas corran en milisegundos.

```java
package bo.forja.backend.voz;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ContextoDelDiagrama;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * La gramatica primero; el traductor solo si ella no entendio.
 * <p>
 * Una sola vuelta, a proposito: lo que el traductor propone entra por la
 * gramatica, y si eso tampoco parsea se devuelven las sugerencias de siempre.
 * Volver a preguntarle al modelo con su propia respuesta es como termina un
 * sistema girando sobre si mismo mientras alguien espera.
 */
public final class DictadoConRespaldo {

    /** Hablaste y estas esperando: tres segundos y ni uno mas. */
    public static final Duration PRESUPUESTO_EN_VIVO = Duration.ofSeconds(3);

    private DictadoConRespaldo() {
    }

    public static Interpretacion interpretar(ParserVoz parser, Traductor traductor,
                                             String frase, ContextoDelDiagrama contexto) {
        Interpretacion directa = parser.interpretar(frase, contexto);
        if (directa.entendida() || !traductor.disponible()) {
            return directa;
        }

        List<String> propuestas =
                traductor.aFrasesCanonicas(frase, contexto.nombres(), PRESUPUESTO_EN_VIVO);

        List<Interpretacion.Paso> pasos = new ArrayList<>();
        List<String> explicaciones = new ArrayList<>();
        for (String propuesta : propuestas) {
            Interpretacion interpretada = parser.interpretar(propuesta, contexto);
            if (interpretada.entendida()) {
                pasos.addAll(interpretada.pasos());
                explicaciones.add(interpretada.explicacion());
            }
        }

        if (pasos.isEmpty()) {
            return directa;
        }
        return Interpretacion.entendida(frase, String.join(". ", explicaciones), pasos);
    }
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `./mvnw test -Dtest=ServicioVozConTraductorTest`
Expected: PASA, 5 pruebas.

- [ ] **Step 5: Enchufarlo en `ServicioVoz`**

En `ServicioVoz.java`: agregar `Traductor` al constructor, y en `dictar` reemplazar

```java
Interpretacion interpretacion = parser.interpretar(frase, contexto);
```

por

```java
Interpretacion interpretacion = DictadoConRespaldo.interpretar(parser, traductor, frase, contexto);
```

Hacer lo mismo en `interpretarSinAplicar`. **No tocar nada más** de ese método: la distribución en cuadrícula, los tokens y el registro de operaciones quedan igual.

- [ ] **Step 6: Correr la suite entera**

Run: `./mvnw test`
Expected: PASA, 247 pruebas. Las 6 de `ServicioVozTest` siguen verdes **sin tocarlas**: en las pruebas el bean registrado es `TraductorNulo`, que no está disponible, así que el camino nuevo ni se entra.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/bo/forja/backend/voz src/test/java/bo/forja/backend/voz/ServicioVozConTraductorTest.java
git commit -m "Usar el traductor como respaldo cuando la gramatica no engancha"
```

---

### Task 4: El pedido explícito en el backend

**Files:**
- Create: `src/main/java/bo/forja/backend/pedido/Pedido.java`
- Create: `src/main/java/bo/forja/backend/pedido/ServicioPedido.java`
- Create: `src/main/java/bo/forja/backend/web/ControladorPedido.java`
- Test: `src/test/java/bo/forja/backend/pedido/ServicioPedidoTest.java`

**Interfaces:**
- Consumes: `Traductor` (Task 1), `ParserVoz`, `ServicioOperaciones`, `ServicioProyectos`, `Cuadricula`.
- Produces: `record Pedido(String pedido, List<FrasePropuesta> frases, List<ComandoOperacion> comandos)` con `record FrasePropuesta(String frase, boolean seEntendio, String explicacion)`; `ServicioPedido.leer(...)` y `ServicioPedido.aplicar(...)`. La Task 5 los consume por HTTP.

Es la misma forma que la foto de pizarra, y a propósito: `ControladorFoto` recibe dos peticiones porque el flujo tiene dos pasos, primero lo que se entendió y después aplicar. Acá vale lo mismo y por la misma razón — un modelo de 4B se equivoca más que una persona escribiendo.

Reusar de `ServicioFoto.aplicar` (`:82-130`) tres decisiones ya tomadas: el `tokenLectura` para que reenviar tras un corte no duplique, el token por comando que lleva **el tipo además de la posición**, y `Cuadricula.ubicar` con `siguienteCasilla` contando las clases que ya existen.

- [ ] **Step 1: Escribir la prueba que falla**

`src/test/java/bo/forja/backend/pedido/ServicioPedidoTest.java`, con el mismo traductor de mentira de la Task 3 (copiarlo, no compartirlo entre paquetes de prueba por una clase de cinco líneas):

```java
package bo.forja.backend.pedido;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.voz.ParserVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pedido en lenguaje libre")
class ServicioPedidoTest {

    private final ParserVoz parser = new ParserVoz();

    private static class TraductorDeMentira implements Traductor {
        List<String> loQuePropone = List.of();
        final List<Duration> presupuestos = new ArrayList<>();

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration p) {
            presupuestos.add(p);
            return loQuePropone;
        }

        @Override
        public boolean disponible() {
            return true;
        }
    }

    @Test
    @DisplayName("un pedido se convierte en varias operaciones")
    void unPedidoEsVariasOperaciones() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of(
                "crea la clase Dueno con los atributos nombre de tipo texto",
                "crea la clase Mascota",
                "Dueno tiene muchas Mascotas");

        Pedido resultado = ServicioPedido.interpretar(parser, traductor,
                "arma una veterinaria", ContextoDelDiagrama.vacio());

        assertThat(resultado.frases()).hasSize(3);
        assertThat(resultado.frases()).allMatch(Pedido.FrasePropuesta::seEntendio);
        // Dueno + su atributo + Mascota + la relacion
        assertThat(resultado.comandos()).hasSize(4);
    }

    @Test
    @DisplayName("lo que no se entendio se muestra, no se esconde")
    void loQueNoSeEntendioSeMuestra() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("crea la clase Dueno", "y despues vemos");

        Pedido resultado = ServicioPedido.interpretar(parser, traductor, "x",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.frases()).hasSize(2);
        assertThat(resultado.frases().get(1).seEntendio()).isFalse();
        assertThat(resultado.frases().get(1).frase()).isEqualTo("y despues vemos");
        assertThat(resultado.comandos()).hasSize(1);
    }

    @Test
    @DisplayName("el pedido explicito tiene mas presupuesto que el dictado en vivo")
    void presupuestoLargo() {
        TraductorDeMentira traductor = new TraductorDeMentira();

        ServicioPedido.interpretar(parser, traductor, "x", ContextoDelDiagrama.vacio());

        // Es una accion deliberada: se puede esperar, con progreso a la vista.
        assertThat(traductor.presupuestos).containsExactly(ServicioPedido.PRESUPUESTO);
        assertThat(ServicioPedido.PRESUPUESTO).isGreaterThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("sin traductor disponible el pedido no propone nada y lo dice")
    void sinTraductor() {
        Traductor nulo = new bo.forja.backend.ia.TraductorNulo();

        Pedido resultado = ServicioPedido.interpretar(parser, nulo, "x",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.frases()).isEmpty();
        assertThat(resultado.comandos()).isEmpty();
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `./mvnw test -Dtest=ServicioPedidoTest`
Expected: FALLA, `cannot find symbol: class Pedido`.

- [ ] **Step 3: Escribir `Pedido` y el método puro de `ServicioPedido`**

`Pedido.java`:

```java
package bo.forja.backend.pedido;

import bo.forja.backend.operacion.ComandoOperacion;

import java.util.List;

/**
 * Lo que el modelo propuso para un pedido, antes de tocar el diagrama.
 * <p>
 * Lleva las frases ademas de los comandos porque se muestran: un modelo de 4B
 * se equivoca mas que una persona escribiendo, y ver "entendi estas cinco
 * cosas, esta otra no" es lo que separa una funcion demostrable de una loteria.
 * Es el mismo criterio con el que se lee la foto de una pizarra.
 */
public record Pedido(String pedido, List<FrasePropuesta> frases,
                     List<ComandoOperacion> comandos) {

    public record FrasePropuesta(String frase, boolean seEntendio, String explicacion) {
    }

    public boolean seEntendioAlgo() {
        return !comandos.isEmpty();
    }
}
```

En `ServicioPedido`, el método `interpretar` es **estático y puro** —parser, traductor, texto y contexto— para poder probarlo sin Spring. El servicio de Spring lo envuelve con el acceso al diagrama y el registro de operaciones.

```java
    /** Es una accion deliberada: se puede esperar, con el progreso a la vista. */
    public static final Duration PRESUPUESTO = Duration.ofSeconds(30);

    public static Pedido interpretar(ParserVoz parser, Traductor traductor,
                                     String pedido, ContextoDelDiagrama contexto) {
        if (!traductor.disponible()) {
            return new Pedido(pedido, List.of(), List.of());
        }

        List<String> propuestas = traductor.aFrasesCanonicas(pedido, contexto.nombres(), PRESUPUESTO);

        List<Pedido.FrasePropuesta> frases = new ArrayList<>();
        List<ComandoOperacion> comandos = new ArrayList<>();
        for (String propuesta : propuestas) {
            Interpretacion interpretada = parser.interpretar(propuesta, contexto);
            frases.add(new Pedido.FrasePropuesta(propuesta, interpretada.entendida(),
                    interpretada.entendida() ? interpretada.explicacion() : null));
            interpretada.pasos().forEach(paso -> comandos.add(paso.comando()));
        }
        return new Pedido(pedido, frases, comandos);
    }
```

**Cuidado con el contexto:** las frases se interpretan todas contra el **mismo** contexto inicial, así que "crea la clase Mascota" seguido de "Dueno tiene muchas Mascotas" no resuelve Mascota, que todavía no existe. Eso es un defecto real y la prueba `unPedidoEsVariasOperaciones` lo va a descubrir. La solución es ir **agrandando el contexto** con las clases que las frases anteriores crearon, antes de interpretar la siguiente. Implementarlo así desde el principio: armar una lista mutable de `ClaseConocida`, y después de cada `CrearClase` agregarle la clase nueva y rehacer el contexto con `ContextoDelDiagrama.de(...)`.

- [ ] **Step 4: Correr y verificar que pasa**

Run: `./mvnw test -Dtest=ServicioPedidoTest`
Expected: PASA, 4 pruebas.

- [ ] **Step 5: Escribir el servicio de Spring y el controlador**

`ServicioPedido` gana los métodos `leer` y `aplicar`, copiando la estructura de `ServicioFoto.leer` (`:67-72`) y `ServicioFoto.aplicar` (`:82-130`): `proyectos.diagramaAccesible(...)`, el contexto desde los repositorios, `Cuadricula.ubicar` con `siguienteCasilla`, el token por comando con su tipo, y el `switch` sobre el estado del resultado. El origen de la operación es `OrigenOperacion.VOZ` (no hay uno nuevo: el pedido es lenguaje, igual que el dictado).

El resultado de aplicar es `ServicioPedido.ResultadoPedido`, gemelo de `ServicioFoto.ResultadoFoto`:

```java
    /**
     * @param yaEstaban comandos que el servidor reconocio como reenvio y no
     *                  volvio a aplicar
     * @param retenidoPor nombre de quien tiene tomado un elemento, si algo se
     *                    rechazo por bloqueo
     */
    public record ResultadoPedido(Pedido pedido, int aplicadas, int yaEstaban,
                                  List<String> problemas, String retenidoPor, long version) {
    }
```

`ControladorPedido` es el gemelo de `ControladorFoto`:

```java
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/pedido")
public class ControladorPedido {

    @PostMapping("/lectura")
    public Pedido leer(...) { ... }

    @PostMapping
    public ServicioPedido.ResultadoPedido aplicar(...) { ... }

    public record TextoDelPedido(
            @NotBlank @Size(max = 500) String pedido,
            @NotBlank @Size(max = 80) String sesionId,
            String tokenLectura) {
    }
}
```

- [ ] **Step 6: Correr la suite entera**

Run: `./mvnw test`
Expected: PASA, 251 pruebas.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/bo/forja/backend/pedido src/main/java/bo/forja/backend/web/ControladorPedido.java src/test/java/bo/forja/backend/pedido
git commit -m "Convertir un pedido en varias operaciones, con revision antes de aplicar"
```

---

### Task 5: La pantalla del pedido en la web

**Files:**
- Modify: `web/src/api.ts`
- Modify: `web/src/pantallas/Dictado.tsx`
- Modify: `web/src/estilos.css`

**Interfaces:**
- Consumes: `POST /api/diagramas/{id}/pedido/lectura` y `POST /api/diagramas/{id}/pedido` (Task 4).
- Produces: nada. Es el final del plan.

El campo de texto libre que ya existe en `Dictado.tsx` (`:130-144`) gana un segundo botón: **Pedir**, además del que dicta. El de dictar sigue aplicando directo —la gramática es exacta y no hay nada que revisar—; el de pedir muestra primero.

- [ ] **Step 1: Agregar las dos llamadas a `api.ts`**

Junto a `leerPizarra` y `aplicarPizarra` (`web/src/api.ts:223-232`), con la misma forma:

```ts
  pedirLectura: (diagramaId: string, pedido: string, sesionId: string) =>
    pedir<Pedido>('POST', `/api/diagramas/${diagramaId}/pedido/lectura`, { pedido, sesionId }),

  aplicarPedido: (diagramaId: string, pedido: string, sesionId: string, tokenLectura: string) =>
    pedir<ResultadoPedido>('POST', `/api/diagramas/${diagramaId}/pedido`,
      { pedido, sesionId, tokenLectura }),
```

Los tipos `Pedido` y `ResultadoPedido` van en `web/src/tipos.ts`, copiando la forma de los records de la Task 4.

- [ ] **Step 2: Agregar el botón y el estado a `Dictado.tsx`**

El componente pasa a tener tres estados en vez de uno: `escribiendo`, `proponiendo` (con el progreso a la vista y un botón de cancelar) y `revisando` (mostrando las frases). Es el mismo camino de tres pasos de `Foto.tsx:41` y `:154-163`.

- [ ] **Step 3: Mostrar lo que el modelo propuso**

Una lista donde cada frase se ve con su estado: las que se entendieron con su explicación, las que no con la frase tal cual. `ResumenDeLectura` de `Foto.tsx:225-274` hace exactamente esto para la pizarra — **leerlo y seguir su forma**, incluido que las ignoradas se muestran con su contenido y no como un número.

- [ ] **Step 4: Ocultar el botón si no hay traductor**

Si `disponible()` es falso, el botón de pedir no se muestra. Hace falta un dato del servidor: agregarlo a alguna respuesta que el cliente ya pida al abrir el diagrama, o un `GET /api/ia` de una línea. **No** mostrar un botón que va a contestar "no hay traductor".

- [ ] **Step 5: Verificar**

Run: `cd web && npm run build && npx oxlint`
Expected: compila sin errores de tipos, lint limpio.

- [ ] **Step 6: Probarlo a mano, con Ollama de verdad**

Esto no lo cubre ninguna prueba y es lo único que demuestra la función:

1. `ollama serve` corriendo y `ollama pull gemma3:4b` hecho.
2. Arrancar el backend con `FORJA_IA_HABILITADA=true`.
3. Abrir un diagrama vacío y pedir: *"armá un diagrama de una veterinaria con dueños, mascotas y consultas"*.
4. Verificar que la previsualización muestra las frases, que las que no parsean se ven, y que al aplicar el diagrama queda armado y las clases **no** se pisan.
5. Apagar Ollama y repetir: el botón no tiene que aparecer, y el dictado normal tiene que seguir funcionando igual.

- [ ] **Step 7: Commit**

```bash
git add web/src
git commit -m "Pedir un diagrama en una frase, con revision antes de aplicar"
```

---

## Nota sobre este plan

Las Tasks 1, 2 y 3 llevan el código entero. Las Tasks 4 y 5 no: describen qué escribir señalando el
archivo que hay que copiar, en vez de transcribirlo. Es una concesión deliberada y conviene saberla
antes de empezar — `ServicioPedido.aplicar` es un espejo de sesenta líneas de `ServicioFoto.aplicar`
y la pantalla es React, donde el detalle se decide mirando. Si este plan lo ejecuta alguien que no
escribió el diseño, esas dos tareas necesitan que primero lea `ServicioFoto.java:82-130` y
`Foto.tsx:225-274` enteros.

El corte natural del plan está entre la Task 3 y la Task 4: con las tres primeras, el dictado de la
web ya entiende frases libres y eso es software terminado. Las dos últimas agregan el pedido
explícito.

## Lo que queda para después

- **Plan 4** — el canal Kotlin y Gemma 3 1B en el móvil (paso 5 del diseño), el único recortable.
- **El agente guía del móvil**, que se sacó del alcance el 16 de septiembre y se retoma solo si sobra tiempo después del ensayo.
