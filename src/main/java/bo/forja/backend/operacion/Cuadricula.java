package bo.forja.backend.operacion;

/**
 * Ubica en el lienzo las clases que llegan sin coordenadas.
 * <p>
 * Ni dictando ni fotografiando una pizarra se obtienen posiciones: al dictar no
 * se dicen coordenadas, y del reconocimiento de texto solo sale texto. Pero la
 * clase tiene que aparecer en algun lugar visible, y dejarlas todas en el origen
 * las apila una encima de otra, invisibles salvo la ultima.
 * <p>
 * Es una sola regla compartida a proposito: si cada canal distribuyera a su
 * manera, un modelo armado en parte dictando y en parte con una foto tendria dos
 * disposiciones distintas superpuestas.
 */
public final class Cuadricula {

    /*
     * El paso deja sitio para el conector, no solo para la caja.
     *
     * Una clase mide 220 de ancho, asi que un paso de 260 dejaba 40 de hueco:
     * entraba la linea y nada mas. El rombo de una composicion ocupa 14 y cada
     * multiplicidad se dibuja a 26 de su extremo, de modo que con 40 de hueco
     * los dos rotulos se montaban uno sobre otro y el rombo quedaba pisando la
     * caja vecina. Se vio recien al dictar un modelo entero y mirarlo: con
     * frases sueltas nunca hay dos clases contiguas.
     */
    private static final double PASO_X = 380;
    private static final double PASO_Y = 260;
    private static final int POR_FILA = 4;

    private Cuadricula() {
    }

    /**
     * Devuelve el comando con coordenadas si es un alta de clase; cualquier otro
     * comando pasa sin cambios.
     *
     * @param casilla posicion a ocupar, contando las clases que ya existen
     */
    public static ComandoOperacion ubicar(ComandoOperacion comando, int casilla) {
        if (!(comando instanceof ComandoOperacion.CrearClase alta)) {
            return comando;
        }
        return new ComandoOperacion.CrearClase(alta.claseId(), alta.nombre(), alta.estereotipo(),
                alta.esAbstracta(),
                (casilla % POR_FILA) * PASO_X,
                (double) (casilla / POR_FILA) * PASO_Y);
    }
}
