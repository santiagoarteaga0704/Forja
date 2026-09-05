package bo.forja.backend.dominio;

/**
 * Canal de entrada por el que se produjo un cambio en el modelo.
 * <p>
 * Registrarlo permite trazar que porcion del diagrama fue construida por
 * dictado de voz, por reconocimiento de una fotografia o por importacion,
 * evidencia que la evaluacion del proyecto exige poder demostrar.
 */
public enum OrigenOperacion {
    LIENZO,
    VOZ,
    FOTO,
    IMPORTACION,
    AGENTE
}
