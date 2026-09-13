package bo.forja.backend.seguridad;

/**
 * El correo no existe, la contrasena no coincide o la cuenta esta dada de
 * baja. El mensaje es deliberadamente el mismo en los tres casos: decir
 * cual de ellos fallo le permitiria a un atacante averiguar que correos
 * estan registrados. Se traduce a 401 en la capa REST.
 */
public class CredencialesInvalidas extends RuntimeException {

    public CredencialesInvalidas() {
        super("Las credenciales no son validas");
    }
}
