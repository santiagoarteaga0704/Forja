package bo.forja.backend.agente;

import java.util.List;

/**
 * Una regla que ensena a usar la aplicacion.
 * <p>
 * Misma forma que {@link Regla} -condicion y consecuencia, sin estado y sin
 * consultar nada- pero mira el {@link Panorama} en vez de un diagrama. Se
 * separan los dos tipos en lugar de hacer una interfaz que reciba las dos cosas
 * porque asi cada regla declara en su firma que necesita para decidir, y no hay
 * forma de escribir una regla del modelo que se ponga a mirar cuantos proyectos
 * tiene la persona.
 */
public interface ReglaDeLaHerramienta {

    /** Nombre corto, usado para armar el identificador estable de sus consejos. */
    String nombre();

    List<Consejo> evaluar(Panorama panorama);
}
