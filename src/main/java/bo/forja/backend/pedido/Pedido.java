package bo.forja.backend.pedido;

import bo.forja.backend.operacion.ComandoOperacion;

import java.util.List;

/**
 * Lo que el modelo propuso para un pedido, antes de tocar el diagrama.
 * <p>
 * Lleva las frases ademas de los comandos porque se muestran. Un modelo de 4B
 * se equivoca mas que una persona escribiendo, y ver "entendi estas cinco
 * cosas, esta otra no" es lo que separa una funcion demostrable de una loteria.
 * Es el mismo criterio con el que se lee la foto de una pizarra, y por eso
 * tiene la misma forma que {@code Lectura}.
 */
public record Pedido(String pedido,
                     List<FrasePropuesta> frases,
                     List<ComandoOperacion> comandos) {

    /**
     * @param explicacion que se entendio, en palabras, o nulo si no se entendio
     */
    public record FrasePropuesta(String frase, boolean seEntendio, String explicacion) {
    }

    public boolean seEntendioAlgo() {
        return !comandos.isEmpty();
    }

    public static Pedido nada(String pedido) {
        return new Pedido(pedido, List.of(), List.of());
    }
}
