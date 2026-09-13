package bo.forja.backend.seguridad;

/** Ya existe una cuenta con ese correo. Se traduce a 409 en la capa REST. */
public class EmailYaRegistrado extends RuntimeException {

    public EmailYaRegistrado(String email) {
        super("Ya existe una cuenta registrada con el correo " + email);
    }
}
