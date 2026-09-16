package bo.forja.backend.dominio;

/**
 * Las acciones de la aplicacion que la bitacora del modelo no puede ver.
 * <p>
 * La bitacora {@code operacion} registra cada cambio del modelo con la via por
 * la que entro -{@link OrigenOperacion}-, asi que el agente guia ya sabe solo si
 * alguien nunca dicto o nunca uso la foto de una pizarra. Estas otras acciones
 * no tocan el modelo: exportar, mirar el codigo generado, descargarlo,
 * preguntarle algo al agente. Sin registrarlas, el agente no podria ensenar
 * justamente las funciones que nadie descubre por su cuenta.
 * <p>
 * Solo se anotan las invisibles. Duplicar aqui lo que la bitacora ya cuenta
 * daria dos fuentes para el mismo hecho, y tarde o temprano una de las dos
 * quedaria vieja.
 */
public enum Herramienta {

    /** Se descargo el XMI del diagrama para abrirlo en otra herramienta. */
    XMI_EXPORTADO,

    /** Se miro el codigo que el generador produce a partir del diagrama. */
    BACKEND_GENERADO,

    /** Se descargo el proyecto Spring Boot completo. */
    PROYECTO_DESCARGADO,

    /** Se le hizo una pregunta al agente guia. */
    AGENTE_CONSULTADO
}
