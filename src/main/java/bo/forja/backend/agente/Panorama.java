package bo.forja.backend.agente;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.OrigenOperacion;

import java.util.Map;
import java.util.UUID;

/**
 * Lo que el agente ve de la APLICACION, no de un diagrama.
 * <p>
 * {@link Observacion} mira un diagrama: sus clases, sus relaciones, lo que se
 * hizo dentro de el. Eso alcanza para senalar problemas del modelo, pero no para
 * ensenar la herramienta, que es lo que el enunciado pide: alguien que todavia
 * no creo ningun proyecto no tiene diagrama que observar, y sin embargo es
 * justo quien mas necesita que le expliquen por donde se empieza.
 * <p>
 * Por eso hay dos vistas y no una sola mas grande. Las catorce reglas del
 * modelo siguen recibiendo exactamente lo que recibian -no se tocaron, ni ellas
 * ni sus pruebas- y las reglas de la herramienta reciben esto. Cada familia mira
 * lo suyo.
 * <p>
 * Igual que la otra, se arma de una vez y las reglas no consultan nada: se
 * construye a mano en una prueba y la base entera se revisa en milisegundos.
 *
 * @param enUnDiagrama donde esta la persona ahora mismo. No es adorno: unas
 *                     pocas reglas de esta familia dicen lo mismo que una regla
 *                     del diagrama -que nunca se dicto, por ejemplo- y con el
 *                     lienzo abierto la del diagrama lo dice con mas precision,
 *                     asi que esta se calla para no repetir el consejo
 */
public record Panorama(
        UUID usuarioId,
        int proyectosPropios,
        int proyectosAjenos,
        long diagramas,
        long invitados,
        Map<OrigenOperacion, Long> operacionesPorOrigen,
        Map<Herramienta, Integer> usos,
        boolean enUnDiagrama) {

    public int proyectos() {
        return proyectosPropios + proyectosAjenos;
    }

    public long totalDeOperaciones() {
        return operacionesPorOrigen.values().stream().mapToLong(Long::longValue).sum();
    }

    public long operacionesConOrigen(OrigenOperacion origen) {
        return operacionesPorOrigen.getOrDefault(origen, 0L);
    }

    public int vecesQueUso(Herramienta herramienta) {
        return usos.getOrDefault(herramienta, 0);
    }

    public boolean yaUso(Herramienta herramienta) {
        return vecesQueUso(herramienta) > 0;
    }

    /** Cierto si el modelo entro alguna vez por una via que no sea el lienzo. */
    public boolean proboOtraVia() {
        return operacionesConOrigen(OrigenOperacion.VOZ) > 0
                || operacionesConOrigen(OrigenOperacion.FOTO) > 0;
    }
}
