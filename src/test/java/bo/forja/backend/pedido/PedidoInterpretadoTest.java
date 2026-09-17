package bo.forja.backend.pedido;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.ia.TraductorNulo;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.voz.ParserVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un pedido en lenguaje libre, convertido en varias operaciones.
 * <p>
 * Es la parte que se puede probar sin Spring: dado un traductor que propone
 * ciertas frases, que sale. Lo que el modelo de verdad proponga se ensaya a
 * mano; lo que hay que fijar aca es que las frases se encadenen bien y que lo
 * que no se entendio se muestre en vez de desaparecer.
 */
@DisplayName("Pedido en lenguaje libre")
class PedidoInterpretadoTest {

    private final ParserVoz parser = new ParserVoz();

    private static class TraductorDeMentira implements Traductor {
        List<String> loQuePropone = List.of();
        final List<Duration> presupuestos = new ArrayList<>();
        final List<List<String>> clasesQueLePasaron = new ArrayList<>();

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration p) {
            presupuestos.add(p);
            clasesQueLePasaron.add(clases);
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

        Pedido resultado = PedidoInterpretado.de(parser, traductor,
                "arma una veterinaria", ContextoDelDiagrama.vacio());

        assertThat(resultado.frases()).hasSize(3);
        assertThat(resultado.frases()).allMatch(Pedido.FrasePropuesta::seEntendio);
        // Dueno, su atributo, Mascota, y la relacion entre las dos.
        assertThat(resultado.comandos()).hasSize(4);
    }

    @Test
    @DisplayName("una frase puede referirse a una clase que creo la frase anterior")
    void elContextoCreceConLoQueSeVaCreando() {
        // Sin esto, "Dueno tiene muchas Mascotas" no resuelve ninguno de los dos
        // extremos -no existen todavia- y la relacion se pierde en silencio. Es
        // la diferencia entre armar un diagrama y armar clases sueltas.
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of(
                "crea la clase Dueno",
                "crea la clase Mascota",
                "Dueno tiene muchas Mascotas");

        Pedido resultado = PedidoInterpretado.de(parser, traductor, "x",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.comandos()).hasSize(3);
        ComandoOperacion ultimo = resultado.comandos().get(2);
        assertThat(ultimo).isInstanceOf(ComandoOperacion.CrearRelacion.class);

        // Y apunta a las clases que las frases anteriores crearon, no a un id
        // inventado.
        ComandoOperacion.CrearClase dueno = (ComandoOperacion.CrearClase) resultado.comandos().get(0);
        ComandoOperacion.CrearClase mascota = (ComandoOperacion.CrearClase) resultado.comandos().get(1);
        ComandoOperacion.CrearRelacion relacion = (ComandoOperacion.CrearRelacion) ultimo;
        assertThat(relacion.origenId()).isEqualTo(dueno.claseId());
        assertThat(relacion.destinoId()).isEqualTo(mascota.claseId());
    }

    @Test
    @DisplayName("lo que no se entendio se muestra, no se esconde")
    void loQueNoSeEntendioSeMuestra() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("crea la clase Dueno", "y despues vemos");

        Pedido resultado = PedidoInterpretado.de(parser, traductor, "x",
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

        PedidoInterpretado.de(parser, traductor, "x", ContextoDelDiagrama.vacio());

        // Es una accion deliberada: se puede esperar, con progreso a la vista.
        assertThat(traductor.presupuestos).containsExactly(PedidoInterpretado.PRESUPUESTO);
        assertThat(PedidoInterpretado.PRESUPUESTO).isGreaterThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("al modelo se le pasan las clases que ya estan en el diagrama")
    void leDiceQueClasesExisten() {
        TraductorDeMentira traductor = new TraductorDeMentira();

        PedidoInterpretado.de(parser, traductor, "x", ContextoDelDiagrama.de(List.of(
                new ContextoDelDiagrama.ClaseConocida(UUID.randomUUID(), "Paciente"))));

        assertThat(traductor.clasesQueLePasaron).containsExactly(List.of("Paciente"));
    }

    @Test
    @DisplayName("sin traductor disponible no se propone nada")
    void sinTraductor() {
        Pedido resultado = PedidoInterpretado.de(parser, new TraductorNulo(), "x",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.frases()).isEmpty();
        assertThat(resultado.comandos()).isEmpty();
        assertThat(resultado.seEntendioAlgo()).isFalse();
    }
}
