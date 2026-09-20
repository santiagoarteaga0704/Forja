package bo.forja.backend.pedido;

import bo.forja.backend.operacion.ComandoOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el modelo propuso, guardado hasta que la persona decide.
 * <p>
 * Existe por una razon medida, no teorica: el 20 de septiembre se comprobo que
 * el mismo pedido da propuestas distintas en cada corrida aun con
 * {@code temperature} en cero, asi que volver a consultar al modelo al aplicar
 * hacia que se aplicara algo que nadie habia revisado. El paso de revision solo
 * significa algo si lo que entra es exactamente lo que se mostro.
 * <p>
 * El cliente no manda los comandos -eso seguiria dejandole decidir que entra-,
 * manda el token de su lectura y el servidor busca lo que el mismo propuso.
 * <p>
 * Corre sin Spring y sin base de datos, y por eso corre en milisegundos.
 */
@DisplayName("Las propuestas que esperan revision")
class PropuestasEnRevisionTest {

    private static final UUID ALGUIEN = UUID.randomUUID();
    private static final UUID OTRO = UUID.randomUUID();
    private static final UUID DIAGRAMA = UUID.randomUUID();

    /** Un reloj que se mueve cuando la prueba lo dice, no cuando pasa el tiempo. */
    private static class RelojDeMentira extends Clock {
        private Instant ahora = Instant.parse("2026-09-20T15:00:00Z");

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }
    }

    private static Pedido propuestaDe(String nombreDeClase) {
        ComandoOperacion.CrearClase crear = new ComandoOperacion.CrearClase(
                UUID.randomUUID(), nombreDeClase, null, false, 0, 0);
        return new Pedido("da igual el texto",
                List.of(new Pedido.FrasePropuesta("crea la clase " + nombreDeClase, true, "Cree " + nombreDeClase)),
                List.of(crear));
    }

    @Test
    @DisplayName("lo propuesto se recupera con el token de esa lectura")
    void seRecuperaConSuToken() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());
        Pedido propuesto = propuestaDe("Mascota");

        propuestas.guardar(ALGUIEN, DIAGRAMA, "token-1", propuesto);

        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-1")).contains(propuesto);
    }

    @Test
    @DisplayName("un token que nadie guardo no devuelve nada")
    void tokenDesconocidoNoDevuelveNada() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());

        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-que-no-existe")).isEmpty();
    }

    @Test
    @DisplayName("la propuesta de una persona no la recupera otra")
    void nadieLeeLaPropuestaDeOtro() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());
        propuestas.guardar(ALGUIEN, DIAGRAMA, "token-1", propuestaDe("Mascota"));

        assertThat(propuestas.recuperar(OTRO, DIAGRAMA, "token-1")).isEmpty();
    }

    @Test
    @DisplayName("una propuesta vencida se olvida en vez de aplicarse tarde")
    void loViejoSeOlvida() {
        RelojDeMentira reloj = new RelojDeMentira();
        PropuestasEnRevision propuestas = new PropuestasEnRevision(reloj);
        propuestas.guardar(ALGUIEN, DIAGRAMA, "token-1", propuestaDe("Mascota"));

        reloj.avanzar(PropuestasEnRevision.VIGENCIA.plusSeconds(1));

        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-1")).isEmpty();
    }

    @Test
    @DisplayName("no se guarda sin limite: lo mas viejo cede el lugar")
    void noCreceSinLimite() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());

        for (int i = 0; i <= PropuestasEnRevision.TECHO; i++) {
            propuestas.guardar(ALGUIEN, DIAGRAMA, "token-" + i, propuestaDe("Clase" + i));
        }

        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-0")).isEmpty();
        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-" + PropuestasEnRevision.TECHO))
                .isPresent();
    }

    /**
     * A proposito NO se consume al recuperarla. El {@code tokenLectura} existe
     * justamente para que el cliente pueda repetir el envio tras un corte; si
     * la propuesta desapareciera en el primer intento, el reintento volveria a
     * consultar al modelo y aplicaria algo distinto de lo que se reviso, que es
     * el defecto que esta clase viene a cerrar.
     */
    @Test
    @DisplayName("sigue estando para el reenvio tras un corte")
    void sobreviveAlReenvio() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());
        Pedido propuesto = propuestaDe("Mascota");
        propuestas.guardar(ALGUIEN, DIAGRAMA, "token-1", propuesto);

        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-1")).contains(propuesto);
        assertThat(propuestas.recuperar(ALGUIEN, DIAGRAMA, "token-1")).contains(propuesto);
    }

    @Test
    @DisplayName("la propuesta de un diagrama no sirve para otro")
    void noCruzaDiagramas() {
        PropuestasEnRevision propuestas = new PropuestasEnRevision(Clock.systemUTC());
        propuestas.guardar(ALGUIEN, DIAGRAMA, "token-1", propuestaDe("Mascota"));

        assertThat(propuestas.recuperar(ALGUIEN, UUID.randomUUID(), "token-1")).isEmpty();
    }
}
