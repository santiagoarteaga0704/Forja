package bo.forja.backend.agente;

import bo.forja.backend.ia.Respondedor;
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
 * El agente es hibrido: reglas primero, modelo despues.
 * <p>
 * Lo que se fija aqui es el ORDEN, que es todo el argumento de diseno. Una
 * pregunta del catalogo no puede llegar nunca al modelo -si llegara, se perderia
 * el determinismo que hace que la misma pregunta de siempre la misma respuesta,
 * y eso es justamente lo que se puede demostrar y un modelo de lenguaje no-. El
 * modelo aparece solo donde el agente hoy decia "esa no la se contestar", que es
 * el unico lugar donde no puede empeorar nada.
 */
@SpringBootTest
@DisplayName("El agente guia, hibrido")
class AgenteHibridoTest {

    /** Anota si lo llamaron, que es la mitad de lo que hay que probar. */
    static class RespondedorDeMentira implements Respondedor {
        final List<String> preguntas = new ArrayList<>();
        final List<String> contextos = new ArrayList<>();
        String loQueContesta = "Se arrastran las clases sobre la hoja y el equipo lo ve al instante.";

        @Override
        public Optional<String> responder(String pregunta, String contexto, Duration presupuesto) {
            preguntas.add(pregunta);
            contextos.add(contexto);
            return loQueContesta == null ? Optional.empty() : Optional.of(loQueContesta);
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

    private static final UUID ALGUIEN = UUID.randomUUID();

    private RespondedorDeMentira deMentira() {
        return (RespondedorDeMentira) respondedor;
    }

    @BeforeEach
    void limpiarElContador() {
        deMentira().preguntas.clear();
        deMentira().contextos.clear();
        deMentira().loQueContesta =
                "Se arrastran las clases sobre la hoja y el equipo lo ve al instante.";
    }

    @Test
    @DisplayName("una pregunta del catálogo NO llega al modelo")
    void elCatalogoManda() {
        List<Consejo> respuesta = agente.responder(ALGUIEN, "como exporto a enterprise architect", null);

        assertThat(respuesta).extracting(Consejo::id).contains("respuesta-exportar-xmi");
        assertThat(deMentira().preguntas)
                .as("las reglas alcanzaron: el modelo no tiene nada que hacer acá")
                .isEmpty();
    }

    @Test
    @DisplayName("lo que las reglas no cubren lo contesta el modelo")
    void elModeloRecoge() {
        // Verificada contra las 128 claves del catalogo: ninguna aparece en esta frase.
        String rara = "hay atajos de teclado";

        List<Consejo> respuesta = agente.responder(ALGUIEN, rara, null);

        assertThat(deMentira().preguntas)
                .as("el catálogo no engancha, así que acá sí se consulta al modelo")
                .containsExactly(rara);
        assertThat(respuesta).extracting(Consejo::id)
                .doesNotContain(Preguntas.SIN_COINCIDENCIA);
        assertThat(respuesta).singleElement()
                .extracting(Consejo::queNote)
                .asString()
                .contains("arrastran las clases");
    }

    @Test
    @DisplayName("al modelo se le pasa la base de conocimiento escrita")
    void elModeloRecibeElContexto() {
        agente.responder(ALGUIEN, "se puede cambiar el color de las cajas", null);

        assertThat(deMentira().contextos).singleElement().asString()
                .as("sin el contexto el modelo inventaría: no conoce FORJA")
                .contains("Enterprise Architect")
                .contains("¿Por dónde empiezo?");
    }

    /**
     * El modelo tiene permitido decir que no sabe, y lo usa: preguntado por
     * atajos de teclado o por el color de las cajas contesta el centinela. Eso
     * NO se le muestra a nadie: es un "no contesto", y quien pregunto merece la
     * respuesta escrita, que ademas ofrece los temas que si estan.
     */
    @Test
    @DisplayName("si el modelo dice que no está en la documentación, no se muestra crudo")
    void elCentinelaNoSeMuestra() {
        deMentira().loQueContesta = "NO ESTA EN LA DOCUMENTACION";

        List<Consejo> respuesta = agente.responder(ALGUIEN, "hay atajos de teclado", null);

        assertThat(respuesta).extracting(Consejo::id).containsExactly(Preguntas.SIN_COINCIDENCIA);
    }

    /**
     * El camino que importa para la demostracion: si el modelo no contesta -no
     * esta, vencio el presupuesto, devolvio vacio- el agente dice lo que decia
     * antes. Nunca se queda mudo.
     */
    @Test
    @DisplayName("si el modelo no contesta, vuelve a la respuesta de siempre")
    void sinModeloSigueComoAntes() {
        deMentira().loQueContesta = null;

        List<Consejo> respuesta = agente.responder(ALGUIEN, "funciona en un telefono viejo", null);

        assertThat(respuesta).extracting(Consejo::id).containsExactly(Preguntas.SIN_COINCIDENCIA);
    }
}
