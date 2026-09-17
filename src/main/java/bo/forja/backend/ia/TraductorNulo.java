package bo.forja.backend.ia;

import java.time.Duration;
import java.util.List;

/**
 * El traductor de por omision: no traduce nada.
 * <p>
 * Con este registrado la aplicacion es exactamente la de antes de que existiera
 * el bloque de IA. La gramatica sigue siendo la primera y unica via, y una
 * frase que no engancha devuelve sugerencias como siempre.
 * <p>
 * No es un marcador de posicion ni una clase de prueba: es la configuracion con
 * la que el proyecto se entrega si el bloque de IA se recorta, y la que corre
 * en todas las pruebas que no son del traductor.
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
