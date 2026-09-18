package bo.forja.backend.ia;

import java.time.Duration;
import java.util.List;

/**
 * Traduce lo que una persona pide a las frases que la gramatica sabe
 * interpretar.
 * <p>
 * No emite comandos, y eso es deliberado. Devolviendo frases, el motor
 * determinista sigue siendo el unico que toca el diagrama y la validacion sale
 * gratis: una frase que no parsea se descarta y no pasa nada. Si el traductor
 * emitiera comandos habria que escribir un validador para lo que invente, y un
 * modelo chico inventa identificadores, tipos de operacion y nombres de campo
 * mucho mas facil de lo que dice una frase de otra manera.
 * <p>
 * Devolver una LISTA y no una frase es lo que convierte un pedido en un
 * diagrama: "arma una veterinaria con duenos, mascotas y consultas" son cinco
 * frases, y cada una entra por la gramatica por separado.
 */
public interface Traductor {

    /**
     * @param clasesConocidas las que ya estan en el diagrama, para que el
     *                        modelo las nombre en vez de duplicarlas
     * @param presupuesto     tiempo maximo. Al vencerse se devuelve vacio y no
     *                        se espera mas: del otro lado hay alguien mirando
     * @return las frases propuestas, o vacio si no pudo. Vacio siempre es una
     *         respuesta aceptable
     */
    List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas,
                                  Duration presupuesto);

    /** Para no ofrecer en la interfaz algo que no va a contestar. */
    boolean disponible();

    /**
     * Deja el modelo listo para contestar rapido.
     * <p>
     * Cargar uno de 4B a memoria la primera vez cuesta mas de un minuto, mas que
     * cualquiera de los dos presupuestos: sin esto la primera traduccion se
     * pierde entera, siempre, y quien mira la aplicacion ve que la funcion no
     * anda. Es una comodidad y nunca un requisito, asi que no devuelve nada ni
     * lanza: si falla, se paga el arranque en frio y ya.
     */
    default void precalentar() {
    }
}
