package bo.forja.backend.servicio;

/**
 * El usuario existe y esta autenticado, pero no tiene permiso sobre el
 * proyecto: o no es miembro, o su rol es de solo lectura. Se traduce a
 * 403 en la capa REST.
 */
public class AccesoDenegado extends RuntimeException {

    public AccesoDenegado(String mensaje) {
        super(mensaje);
    }
}
