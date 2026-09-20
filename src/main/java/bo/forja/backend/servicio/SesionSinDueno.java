package bo.forja.backend.servicio;

/**
 * El token es valido pero la persona a la que apunta ya no existe.
 * <p>
 * Es una situacion propia de una sesion SIN ESTADO: el token se firma una vez y
 * vale doce horas, asi que nada lo entera de que la fila del usuario
 * desaparecio. Pasa al rehacer la base con un navegador todavia abierto, que es
 * exactamente lo que se hace antes de una demostracion.
 * <p>
 * Va aparte de {@link RecursoNoEncontrado} porque no es lo mismo y el cliente
 * necesita distinguirlas: un 404 tambien significa "ese diagrama no existe", y
 * ante eso no hay que cerrar la sesion de nadie. Esto en cambio es un problema
 * de la credencial -401-, y la unica salida util es tirar la sesion y volver a
 * entrar. Con un 404 generico el cliente no podia hacer nada y la persona
 * quedaba trabada leyendo un identificador en rojo.
 */
public class SesionSinDueno extends RuntimeException {

    public SesionSinDueno(String mensaje) {
        super(mensaje);
    }
}
