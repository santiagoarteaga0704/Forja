package bo.forja.backend.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sin configuracion, el traductor que se registra es el nulo.
 * <p>
 * Es la propiedad que hace recortable a todo el bloque de IA: si el modelo no
 * esta, si Ollama no corre, o si se decide sacarlo, la aplicacion se comporta
 * exactamente como antes de que existiera. Por eso esta prueba va primero, y
 * por eso ninguna de las 234 que ya habia hizo falta tocarla.
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

    /**
     * La otra mitad del contrato. Existe porque escribiendo esto se rompio al
     * reves: la guarda del if se perdio en una edicion y el bean devolvia
     * siempre el de Ollama. Lo agarro porOmisionEsNulo; esta prueba cubre el
     * lado que aquella no puede ver.
     */
    @Nested
    @SpringBootTest(properties = {"forja.ia.local.habilitada=true",
            // Que se registre el de Ollama no significa hablarle: sin esto el
            // calentador saldria a cargar un modelo de verdad al levantar el contexto.
            "forja.ia.local.precalentar=false"})
    @DisplayName("con la IA habilitada")
    class Habilitada {

        @Autowired
        private Traductor traductor;

        @Test
        @DisplayName("se registra el traductor que habla con Ollama")
        void seRegistraElDeOllama() {
            assertThat(traductor).isInstanceOf(TraductorOllama.class);
            assertThat(traductor.disponible()).isTrue();
        }
    }

    @Test
    @DisplayName("el traductor nulo aguanta cualquier cosa que le entre")
    void elNuloNoSeCaeConNada() {
        // Devolver vacio siempre es una respuesta aceptable, y es la unica que
        // este da. Nada de lo que reciba puede hacerlo fallar.
        assertThat(traductor.aFrasesCanonicas(null, null, null)).isEmpty();
        assertThat(traductor.aFrasesCanonicas("", List.of(), Duration.ZERO)).isEmpty();
    }
}
