package bo.forja.backend.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deja el modelo cargado apenas la aplicacion esta lista.
 * <p>
 * Cargar Gemma 3 4B a memoria cuesta mas de un minuto la primera vez, contra 12
 * segundos cuando ya esta: mas que cualquiera de los dos presupuestos, asi que
 * sin esto la primera traduccion se pierde SIEMPRE y la funcion parece rota
 * justo cuando alguien la mira por primera vez.
 * <p>
 * Corre despues de que la aplicacion esta arriba y en un hilo aparte, para
 * respetar lo que dice {@link ConfiguracionIa}: el arranque no se retrasa ni
 * queda atado a que Ollama este vivo. Si no esta, no pasa nada y se paga el
 * arranque en frio en el primer pedido, como antes.
 */
@Component
public class CalentadorDeModelo {

    private final Traductor traductor;
    private final boolean precalentar;

    /**
     * @param precalentar lo apagan las pruebas. Sin esta llave, una prueba que
     *                    levanta el contexto con la IA habilitada saldria a
     *                    buscar un modelo de verdad, que es justamente lo que
     *                    ninguna prueba de este proyecto hace
     */
    public CalentadorDeModelo(Traductor traductor,
                              @Value("${forja.ia.local.precalentar:true}") boolean precalentar) {
        this.traductor = traductor;
        this.precalentar = precalentar;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alEstarLista() {
        if (!precalentar || !traductor.disponible()) {
            return;
        }
        Thread.ofVirtual().name("precalentar-modelo").start(traductor::precalentar);
    }
}
