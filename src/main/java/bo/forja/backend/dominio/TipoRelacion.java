package bo.forja.backend.dominio;

/**
 * Tipos de relacion entre clasificadores soportados por FORJA,
 * segun UML 2.5.1 (OMG).
 * <p>
 * La distincion entre relaciones estructurales y de generalizacion es la
 * que gobierna el generador de codigo: las estructurales se traducen en
 * asociaciones JPA, mientras que HERENCIA y REALIZACION determinan la
 * jerarquia de tipos del backend generado.
 */
public enum TipoRelacion {

    ASOCIACION,
    AGREGACION,
    COMPOSICION,
    HERENCIA,
    DEPENDENCIA,
    REALIZACION;

    /** Indica si la relacion se materializa como atributo de asociacion. */
    public boolean esEstructural() {
        return this == ASOCIACION || this == AGREGACION || this == COMPOSICION;
    }

    /** Indica si la relacion define una jerarquia de tipos. */
    public boolean esJerarquica() {
        return this == HERENCIA || this == REALIZACION;
    }
}
