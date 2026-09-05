package bo.forja.backend.dominio;

/**
 * Visibilidad de un miembro de clase segun UML 2.5.1 (OMG).
 * <p>
 * Cada valor conoce su simbolo en la notacion grafica y su modificador
 * equivalente en Java, de modo que tanto el renderizado del lienzo como
 * el generador de codigo consultan una unica fuente de verdad.
 */
public enum Visibilidad {

    PUBLICO("+", "public"),
    PRIVADO("-", "private"),
    PROTEGIDO("#", "protected"),
    PAQUETE("~", "");

    private final String simbolo;
    private final String modificadorJava;

    Visibilidad(String simbolo, String modificadorJava) {
        this.simbolo = simbolo;
        this.modificadorJava = modificadorJava;
    }

    public String getSimbolo() {
        return simbolo;
    }

    public String getModificadorJava() {
        return modificadorJava;
    }
}
