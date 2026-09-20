package bo.forja.backend.ia;

import java.time.Duration;
import java.util.Optional;

/**
 * El respondedor de por omision: no contesta nada.
 * <p>
 * Con este registrado el agente guia es exactamente el sistema experto de
 * siempre: las respuestas escritas y, si ninguna encaja, "esa no la se
 * contestar" con los temas que si sabe. Es la configuracion con la que el
 * proyecto se entrega si el bloque de IA se recorta, y la que corre en todas
 * las pruebas que no son del modelo.
 */
public class RespondedorNulo implements Respondedor {

    @Override
    public Optional<String> responder(String pregunta, String contexto, Duration presupuesto) {
        return Optional.empty();
    }

    @Override
    public boolean disponible() {
        return false;
    }
}
