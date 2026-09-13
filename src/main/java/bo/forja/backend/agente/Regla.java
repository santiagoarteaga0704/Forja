package bo.forja.backend.agente;

import java.util.List;

/**
 * Una regla de la base de conocimiento del agente.
 * <p>
 * Cada regla mira la observacion y devuelve los consejos que se desprenden de
 * ella, o ninguno. No conoce a las demas, no guarda estado y no consulta nada:
 * por eso el conjunto se puede leer como lo que es, una lista de condiciones con
 * su consecuencia, y se puede agregar o quitar una sin tocar el resto.
 * <p>
 * Es la forma clasica de un sistema experto, y es deliberada: el agente guia del
 * enunciado no necesita un modelo de lenguaje para saber que una clase sin
 * atributos va a generar una tabla con una sola columna. Necesita saber la regla.
 */
public interface Regla {

    /** Nombre corto, usado para armar el identificador estable de sus consejos. */
    String nombre();

    List<Consejo> evaluar(Observacion observacion);
}
