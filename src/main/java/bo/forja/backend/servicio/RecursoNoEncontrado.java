package bo.forja.backend.servicio;

/** El recurso solicitado no existe. Se traduce a 404 en la capa REST. */
public class RecursoNoEncontrado extends RuntimeException {

    public RecursoNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
