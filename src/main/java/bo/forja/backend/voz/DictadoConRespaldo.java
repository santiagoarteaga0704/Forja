package bo.forja.backend.voz;

import bo.forja.backend.ia.Traductor;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.TipoOperacion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * La gramatica primero; el traductor solo si ella no entendio.
 * <p>
 * Es una clase aparte y no un metodo de {@link ServicioVoz} para poder probarla
 * sin Spring y sin base de datos. Lo que hay que verificar aca es una decision
 * -cuando se le pregunta al modelo y que se hace con lo que conteste- y eso no
 * necesita ni un repositorio ni una transaccion.
 * <p>
 * Una sola vuelta, a proposito: lo que el traductor propone entra por la
 * gramatica, y si eso tampoco parsea se devuelven las sugerencias de siempre.
 * Volver a preguntarle al modelo con su propia respuesta es como termina un
 * sistema girando sobre si mismo mientras alguien espera.
 */
public final class DictadoConRespaldo {

    /** Hablaste y estas esperando: tres segundos y ni uno mas. */
    public static final Duration PRESUPUESTO_EN_VIVO = Duration.ofSeconds(3);

    private DictadoConRespaldo() {
    }

    public static Interpretacion interpretar(ParserVoz parser, Traductor traductor,
                                             String frase, ContextoDelDiagrama contexto) {
        Interpretacion directa = parser.interpretar(frase, contexto);
        if (directa.entendida() || !traductor.disponible()) {
            return directa;
        }

        List<String> propuestas =
                traductor.aFrasesCanonicas(frase, contexto.nombres(), PRESUPUESTO_EN_VIVO);

        List<Interpretacion.Paso> pasos = new ArrayList<>();
        List<String> explicaciones = new ArrayList<>();
        for (String propuesta : propuestas) {
            Interpretacion interpretada = parser.interpretar(propuesta, contexto);
            // Lo que el modelo propuso y la gramatica no entiende se descarta
            // sin ruido: no es un error del que dicto y no hay nada que pueda
            // hacer con esa informacion.
            if (interpretada.entendida() && !creariaUnaClase(interpretada)) {
                pasos.addAll(interpretada.pasos());
                explicaciones.add(interpretada.explicacion());
            }
        }

        if (pasos.isEmpty()) {
            return directa;
        }
        return Interpretacion.entendida(frase, String.join(". ", explicaciones), pasos);
    }

    /**
     * Si la propuesta del modelo agrega una clase al diagrama.
     * <p>
     * El respaldo existe para salvar una frase mal reconocida, no para decidir
     * que clases tiene el modelo. Se vio dictando el 21 de septiembre de 2026:
     * el reconocedor escucho "Ha pedido" donde se dijo "a Pedido", la gramatica
     * no entendio, el modelo propuso crear una clase Pedido -que ya estaba- y
     * el diagrama quedo con una repetida. Sin nadie que lo revisara, porque a
     * diferencia del pedido en lenguaje libre, el dictado aplica al instante.
     * <p>
     * Se descarta la propuesta ENTERA y no solo el comando de alta: lo que
     * venga colgado de una clase que no va a existir tampoco tiene donde
     * apoyarse.
     * <p>
     * No es una limitacion que cueste nada: crear una clase es lo unico que
     * siempre se puede decir bien, porque la gramatica acepta seis formas de
     * "crea la clase X". Si hace falta el modelo para adivinar que se queria
     * crear una clase, la frase estaba demasiado rota como para confiar en
     * ella, y es mejor devolver las sugerencias.
     */
    private static boolean creariaUnaClase(Interpretacion interpretada) {
        return interpretada.pasos().stream()
                .anyMatch(paso -> paso.tipo() == TipoOperacion.CLASE_CREAR);
    }
}
