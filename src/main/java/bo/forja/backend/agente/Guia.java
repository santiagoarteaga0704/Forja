package bo.forja.backend.agente;

import java.util.List;

/**
 * Lo que el agente guia tiene para decir en este momento.
 * <p>
 * Va todo junto en una sola respuesta porque el cliente lo muestra junto: los
 * consejos son lo que conviene hacer ahora, y el recorrido es donde esta parada
 * la persona dentro de la herramienta completa. Separarlo en dos llamadas
 * obligaria al panel a dibujarse en dos tiempos.
 */
public record Guia(List<Consejo> consejos, Recorrido recorrido) {
}
