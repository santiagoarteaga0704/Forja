package bo.forja.backend.ia;

import java.time.Duration;

/**
 * Lee la foto de un diagrama y la devuelve escrita en la notacion de pizarra.
 * <p>
 * Devuelve TEXTO y no comandos, por la misma razon que el traductor del
 * dictado: asi el {@code ParserPizarra} determinista sigue siendo el unico que
 * decide que entra al modelo, y la revision que ya existe en la pantalla sigue
 * teniendo sentido. El modelo transcribe; el parser decide.
 * <p>
 * Es una segunda via y no un reemplazo del reconocimiento del navegador. Ese
 * corre sin conexion y la foto no sale de la maquina; este lee diagramas
 * DIBUJADOS -con sus cajas y sus flechas-, que es lo que el reconocimiento de
 * caracteres no puede hacer: las flechas no son texto. Medido el 21 de
 * septiembre de 2026 sobre un diagrama de Enterprise Architect, el
 * reconocimiento devolvia lineas que cruzaban tres cajas y Gemini devolvio las
 * nueve clases exactas con ocho de sus diez relaciones.
 */
public interface LectorVisual {

    /**
     * @param presupuesto tiempo maximo. Al vencerse se devuelve vacio: del otro
     *                    lado hay alguien esperando
     * @return la transcripcion, o vacio si no pudo. Vacio siempre es una
     *         respuesta aceptable, y la pantalla sigue ofreciendo escribir a
     *         mano
     */
    String aNotacionDePizarra(byte[] imagen, String tipoMime, Duration presupuesto);

    /** Para no ofrecer en la interfaz algo que no va a contestar. */
    boolean disponible();
}
