package bo.forja.backend.xmi;

/**
 * El documento recibido no es un XMI que se pueda interpretar.
 * <p>
 * Se distingue de un modelo que viola las reglas de UML: aquello lo detecta
 * el aplicador de comandos y se responde como comando invalido. Esto es un
 * problema del archivo -no es XML, no tiene modelo, viene truncado- y se
 * traduce a 400 en la capa REST.
 */
public class XmiInvalido extends RuntimeException {

    public XmiInvalido(String mensaje) {
        super(mensaje);
    }

    public XmiInvalido(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
