package bo.forja.backend.dominio;

/** Rol de un usuario dentro de un proyecto colaborativo. */
public enum RolMiembro {

    PROPIETARIO,
    EDITOR,
    LECTOR;

    /** Solo estos roles pueden adquirir bloqueos y emitir operaciones. */
    public boolean puedeEditar() {
        return this == PROPIETARIO || this == EDITOR;
    }
}
