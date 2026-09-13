package bo.forja.backend.operacion;

/**
 * Catalogo de comandos: vincula el nombre que se guarda en la columna
 * {@code operacion.tipo} con la clase que sabe interpretar la carga.
 * <p>
 * Se guarda el tipo aparte de la carga, en lugar de incrustar un
 * discriminador dentro del JSON, por dos razones practicas: el historial
 * se puede filtrar por tipo con un indice comun, y la carga conserva
 * exactamente la forma del comando que la produjo, sin campos agregados
 * por el mecanismo de serializacion.
 */
public enum TipoOperacion {

    CLASE_CREAR(ComandoOperacion.CrearClase.class),
    CLASE_RENOMBRAR(ComandoOperacion.RenombrarClase.class),
    CLASE_MOVER(ComandoOperacion.MoverClase.class),
    CLASE_ELIMINAR(ComandoOperacion.EliminarClase.class),
    ATRIBUTO_AGREGAR(ComandoOperacion.AgregarAtributo.class),
    ATRIBUTO_ELIMINAR(ComandoOperacion.EliminarAtributo.class),
    METODO_AGREGAR(ComandoOperacion.AgregarMetodo.class),
    METODO_ELIMINAR(ComandoOperacion.EliminarMetodo.class),
    RELACION_CREAR(ComandoOperacion.CrearRelacion.class),
    RELACION_ELIMINAR(ComandoOperacion.EliminarRelacion.class);

    private final Class<? extends ComandoOperacion> claseComando;

    TipoOperacion(Class<? extends ComandoOperacion> claseComando) {
        this.claseComando = claseComando;
    }

    public Class<? extends ComandoOperacion> claseComando() {
        return claseComando;
    }

    /**
     * Tipo que corresponde a un comando concreto. El {@code switch} sobre
     * la interfaz sellada es exhaustivo: si se agrega un comando y no se
     * lo registra aqui, el codigo no compila.
     */
    public static TipoOperacion de(ComandoOperacion comando) {
        return switch (comando) {
            case ComandoOperacion.CrearClase ignorado -> CLASE_CREAR;
            case ComandoOperacion.RenombrarClase ignorado -> CLASE_RENOMBRAR;
            case ComandoOperacion.MoverClase ignorado -> CLASE_MOVER;
            case ComandoOperacion.EliminarClase ignorado -> CLASE_ELIMINAR;
            case ComandoOperacion.AgregarAtributo ignorado -> ATRIBUTO_AGREGAR;
            case ComandoOperacion.EliminarAtributo ignorado -> ATRIBUTO_ELIMINAR;
            case ComandoOperacion.AgregarMetodo ignorado -> METODO_AGREGAR;
            case ComandoOperacion.EliminarMetodo ignorado -> METODO_ELIMINAR;
            case ComandoOperacion.CrearRelacion ignorado -> RELACION_CREAR;
            case ComandoOperacion.EliminarRelacion ignorado -> RELACION_ELIMINAR;
        };
    }
}
