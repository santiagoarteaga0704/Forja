package bo.forja.backend.operacion;

/**
 * El comando no puede aplicarse sobre el estado actual del diagrama.
 * <p>
 * Cubre las violaciones de la semantica de UML y de la integridad del
 * modelo: una clase que ya no existe, un nombre repetido dentro del mismo
 * diagrama, una relacion entre clases de diagramas distintos. Se
 * distingue del rechazo por bloqueo, que no es un error del comando sino
 * una consecuencia de la concurrencia.
 */
public class ComandoInvalido extends RuntimeException {

    public ComandoInvalido(String mensaje) {
        super(mensaje);
    }
}
