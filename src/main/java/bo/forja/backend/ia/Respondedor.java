package bo.forja.backend.ia;

import java.time.Duration;
import java.util.Optional;

/**
 * Contesta con palabras, cuando la base de reglas del agente no alcanza.
 *
 * <p>Es una capacidad distinta de la de {@link Traductor} y por eso es otra
 * interfaz: aquel convierte una frase libre en instrucciones para la
 * herramienta -su salida la ejecuta una gramatica-, y este redacta una
 * explicacion para una persona. Mezclarlas en un mismo contrato obligaria a que
 * cada implementacion cargue con un metodo que no le corresponde.
 *
 * <p><b>Nunca es la primera via.</b> El agente guia sigue siendo un sistema
 * experto: primero se busca en las respuestas escritas, y solo si ninguna
 * encaja se llega hasta aca. Asi el determinismo se conserva donde importa -las
 * preguntas del catalogo dan siempre la misma respuesta, y eso se prueba- y el
 * modelo aparece unicamente donde hoy el agente decia "esa no la se contestar",
 * que es el unico lugar donde no puede empeorar nada.
 */
public interface Respondedor {

    /**
     * @param pregunta   lo que escribio la persona, tal cual
     * @param contexto   lo que el agente sabe, para que la respuesta salga de
     *                   ahi y no de lo que el modelo crea recordar sobre una
     *                   herramienta que no conoce
     * @param presupuesto cuanto se puede esperar. Al vencer se devuelve vacio
     * @return la respuesta, o vacio si no pudo. <b>Vacio siempre es aceptable</b>:
     *         quien llama vuelve a "esa no la se contestar", que es lo que
     *         habria dicho igual
     */
    Optional<String> responder(String pregunta, String contexto, Duration presupuesto);

    /** Para no prometer en la interfaz algo que no va a contestar. */
    boolean disponible();
}
