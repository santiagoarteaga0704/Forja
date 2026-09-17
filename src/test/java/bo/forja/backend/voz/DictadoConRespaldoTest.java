package bo.forja.backend.voz;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.ia.TraductorNulo;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El traductor como RESPALDO, nunca como primera via.
 * <p>
 * Lo que se verifica no es que el modelo acierte sino que la gramatica siga
 * mandando: una frase que ella entiende no llega nunca al traductor, y lo que
 * el traductor propone tiene que pasar por la gramatica igual que cualquier
 * otra frase, porque el motor determinista sigue siendo el unico que toca el
 * diagrama.
 * <p>
 * Corre sin Spring y sin base de datos, y por eso corre en milisegundos.
 */
@DisplayName("El dictado con traductor de respaldo")
class DictadoConRespaldoTest {

    private final ParserVoz parser = new ParserVoz();

    /** Anota si lo llamaron, que es la mitad de lo que hay que probar. */
    private static class TraductorDeMentira implements Traductor {
        final List<String> pedidos = new ArrayList<>();
        final List<Duration> presupuestos = new ArrayList<>();
        List<String> loQuePropone = List.of();

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration p) {
            pedidos.add(pedido);
            presupuestos.add(p);
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

        assertThat(resultado.entendida()).isTrue();
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

    @Test
    @DisplayName("el dictado en vivo tiene presupuesto de tres segundos")
    void presupuestoDeTresSegundos() {
        TraductorDeMentira traductor = new TraductorDeMentira();

        DictadoConRespaldo.interpretar(parser, traductor, "x", ContextoDelDiagrama.vacio());

        // Hablaste y estas esperando: al vencerse vuelven las sugerencias, no
        // un indicador colgado.
        assertThat(traductor.presupuestos).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("sin traductor disponible, todo se comporta como antes")
    void sinTraductorTodoIgual() {
        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, new TraductorNulo(), "hola que tal", ContextoDelDiagrama.vacio());

        assertThat(resultado.entendida()).isFalse();
        assertThat(resultado.sugerencias()).isNotEmpty();
    }

    @Test
    @DisplayName("lo que propone el modelo se resuelve contra las clases que existen")
    void resuelveContraElDiagrama() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("a Paciente agregale el atributo peso de tipo decimal");

        var paciente = java.util.UUID.randomUUID();
        Interpretacion resultado = DictadoConRespaldo.interpretar(parser, traductor,
                "anotale el peso al paciente",
                ContextoDelDiagrama.de(List.of(
                        new ContextoDelDiagrama.ClaseConocida(paciente, "Paciente"))));

        assertThat(resultado.entendida()).isTrue();
        assertThat(resultado.pasos()).hasSize(1);
    }
}
