package bo.forja.backend.ia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cuando se sale a cargar el modelo y cuando no.
 * <p>
 * Sin Spring: lo que hay que verificar es la decision, y la decision son tres
 * lineas que no necesitan un contexto para ejercitarse.
 */
@DisplayName("Calentador del modelo")
class CalentadorDeModeloTest {

    /** Anota si lo llamaron, y deja esperarlo: el precalentado es asincrono. */
    private static final class TraductorEspia implements Traductor {

        private final boolean disponible;
        private final CountDownLatch llamado = new CountDownLatch(1);

        TraductorEspia(boolean disponible) {
            this.disponible = disponible;
        }

        @Override
        public List<String> aFrasesCanonicas(String pedido, List<String> clases, Duration p) {
            return List.of();
        }

        @Override
        public boolean disponible() {
            return disponible;
        }

        @Override
        public void precalentar() {
            llamado.countDown();
        }

        boolean loLlamaron() throws InterruptedException {
            return llamado.await(2, TimeUnit.SECONDS);
        }

        boolean noLoLlamaron() throws InterruptedException {
            return !llamado.await(300, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    @DisplayName("con traductor y con la llave puesta, carga el modelo")
    void calienta() throws InterruptedException {
        TraductorEspia espia = new TraductorEspia(true);

        new CalentadorDeModelo(espia, true).alEstarLista();

        assertThat(espia.loLlamaron()).as("no salio a cargar el modelo").isTrue();
    }

    @Test
    @DisplayName("sin traductor no sale a buscar nada")
    void sinTraductorNoHaceNada() throws InterruptedException {
        TraductorEspia espia = new TraductorEspia(false);

        new CalentadorDeModelo(espia, true).alEstarLista();

        assertThat(espia.noLoLlamaron()).isTrue();
    }

    @Test
    @DisplayName("con la llave apagada no sale, aunque haya traductor")
    void laLlaveApagadaMandaMas() throws InterruptedException {
        // Es la que usan las pruebas: ninguna puede terminar hablando con un
        // modelo de verdad.
        TraductorEspia espia = new TraductorEspia(true);

        new CalentadorDeModelo(espia, false).alEstarLista();

        assertThat(espia.noLoLlamaron()).isTrue();
    }
}
