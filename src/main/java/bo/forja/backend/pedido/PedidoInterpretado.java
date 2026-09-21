package bo.forja.backend.pedido;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.voz.Interpretacion;
import bo.forja.backend.voz.ParserVoz;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un pedido en lenguaje libre en las operaciones que lo cumplen.
 * <p>
 * El modelo propone frases y la gramatica las interpreta, una por una. Es el
 * mismo trato que en el dictado -el motor determinista es el unico que toca el
 * diagrama- con una diferencia: aca se esperan VARIAS frases, y eso es lo que
 * convierte un pedido en un diagrama y no en una clase suelta.
 * <p>
 * Va aparte del servicio de Spring para poder probarse sin base de datos.
 */
public final class PedidoInterpretado {

    /**
     * Es una accion deliberada: se puede esperar, con el progreso a la vista.
     * <p>
     * Eran 30 s hasta que se midio contra Gemma 3 4B de verdad, el 17 de
     * septiembre, en la maquina donde se va a demostrar: una RTX 3050 de portatil
     * con 4 GB de VRAM. El modelo ocupa 3,5 GB, asi que con un navegador abierto
     * NO ENTRA ENTERO y Ollama lo reparte entre GPU y CPU. El mismo pedido, la
     * misma maquina, el mismo prompt:
     * <ul>
     *   <li>con el modelo entero en la GPU: 12 a 25 s
     *   <li>repartido 54% CPU / 46% GPU: 39 a 46 s
     * </ul>
     * El reparto lo decide Ollama al cargar, segun la VRAM que haya libre en ese
     * momento, asi que no es algo que la aplicacion pueda elegir. Por eso el
     * presupuesto se pone en 90 s: no porque se espere tardar eso, sino porque
     * quedarse corto no cuesta una espera mas larga, cuesta la respuesta entera.
     * <p>
     * El prompt no mueve este numero. Se probo acotarlo -menos atributos, sin
     * metodos, techo de tokens- y bajo 6 segundos empeorando lo que propone.
     */
    public static final Duration PRESUPUESTO = Duration.ofSeconds(90);

    private PedidoInterpretado() {
    }

    public static Pedido de(ParserVoz parser, Traductor traductor,
                            String pedido, ContextoDelDiagrama contextoInicial) {

        if (!traductor.disponible()) {
            return Pedido.nada(pedido);
        }

        List<String> propuestas =
                traductor.aFrasesCanonicas(pedido, contextoInicial.nombres(), PRESUPUESTO);

        // El contexto CRECE con lo que las frases anteriores van creando. Sin
        // esto, "Dueno tiene muchas Mascotas" no resuelve ninguno de sus dos
        // extremos -no existen todavia- y la relacion se pierde en silencio:
        // quedarian tres clases sueltas en vez de un diagrama.
        List<ContextoDelDiagrama.ClaseConocida> conocidas =
                new ArrayList<>(contextoInicial.porNombre().values());
        ContextoDelDiagrama contexto = contextoInicial;

        List<Pedido.FrasePropuesta> frases = new ArrayList<>();
        List<ComandoOperacion> comandos = new ArrayList<>();

        for (String propuesta : propuestas) {
            Interpretacion interpretada = parser.interpretar(propuesta, contexto);
            frases.add(new Pedido.FrasePropuesta(propuesta, interpretada.entendida(),
                    interpretada.entendida() ? interpretada.explicacion() : null));

            if (!interpretada.entendida()) {
                continue;
            }

            boolean nacioAlguna = false;
            for (Interpretacion.Paso paso : interpretada.pasos()) {
                comandos.add(paso.comando());
                if (paso.comando() instanceof ComandoOperacion.CrearClase alta) {
                    conocidas.add(new ContextoDelDiagrama.ClaseConocida(
                            alta.claseId(), alta.nombre()));
                    nacioAlguna = true;
                }
            }
            if (nacioAlguna) {
                contexto = ContextoDelDiagrama.de(conocidas);
            }
        }

        return new Pedido(pedido, frases, comandos, 0);
    }
}
