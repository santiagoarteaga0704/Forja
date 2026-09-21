package bo.forja.backend.ia;

import java.time.Duration;

/** Cuando la lectura por IA esta apagada. No falla: no esta. */
public class LectorVisualNulo implements LectorVisual {

    @Override
    public String aNotacionDePizarra(byte[] imagen, String tipoMime, Duration presupuesto) {
        return "";
    }

    @Override
    public boolean disponible() {
        return false;
    }
}
