package bo.forja.backend.voz;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.ia.TraductorNulo;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.TipoOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        traductor.loQuePropone = List.of("a Factura agregale el atributo total de tipo decimal");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "la factura necesita el total",
                conClases("Factura"));

        assertThat(resultado.entendida()).isTrue();
        assertThat(resultado.pasos()).hasSize(1);
        assertThat(traductor.pedidos).containsExactly("la factura necesita el total");
    }

    /**
     * El respaldo existe para salvar una frase mal reconocida, no para decidir
     * que clases tiene el modelo. Se comprobo el 21 de septiembre de 2026
     * dictando: el reconocedor escucho "Ha pedido" donde se dijo "a Pedido", la
     * gramatica no entendio, el modelo propuso crear una clase Pedido -que ya
     * existia- y el diagrama quedo con una repetida. Sin revision de por medio,
     * porque el dictado aplica al instante.
     * <p>
     * Crear una clase es ademas lo unico que siempre se puede decir bien: la
     * gramatica acepta seis formas de "crea la clase X". Si hace falta el modelo
     * para adivinar que se queria crear una clase, la frase estaba demasiado
     * rota como para confiar en ella.
     */
    @Test
    @DisplayName("el respaldo NO puede crear clases: eso lo decide quien modela")
    void elRespaldoNoCreaClases() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of("crea la clase Factura");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "necesito algo para las facturas",
                ContextoDelDiagrama.vacio());

        assertThat(resultado.entendida()).isFalse();
        assertThat(resultado.sugerencias()).isNotEmpty();
    }

    @Test
    @DisplayName("de lo propuesto entra lo que toca una clase que ya existe, y se cae la creacion")
    void laCreacionSeCaeYElRestoEntra() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of(
                "crea la clase Pedido",
                "a Pedido agregale el atributo id de tipo entero");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "ha pedido anadir el atributo id", conClases("Pedido"));

        assertThat(resultado.entendida()).isTrue();
        assertThat(resultado.pasos())
                .as("la clase repetida no entra; el atributo si")
                .hasSize(1);
        assertThat(resultado.pasos().get(0).tipo()).isEqualTo(TipoOperacion.ATRIBUTO_AGREGAR);
    }

    @Test
    @DisplayName("lo que el modelo propone y no parsea se descarta sin ruido")
    void loQueNoParseaSeDescarta() {
        TraductorDeMentira traductor = new TraductorDeMentira();
        traductor.loQuePropone = List.of(
                "a Factura agregale el atributo total de tipo decimal",
                "hace lo que quieras con las facturas",
                "a Renglon agregale el atributo cantidad de tipo entero");

        Interpretacion resultado = DictadoConRespaldo.interpretar(
                parser, traductor, "facturas con renglones", conClases("Factura", "Renglon"));

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

    /** Un diagrama que ya tiene estas clases. */
    private static ContextoDelDiagrama conClases(String... nombres) {
        List<ContextoDelDiagrama.ClaseConocida> conocidas = new ArrayList<>();
        for (String nombre : nombres) {
            conocidas.add(new ContextoDelDiagrama.ClaseConocida(UUID.randomUUID(), nombre));
        }
        return ContextoDelDiagrama.de(conocidas);
    }
}
