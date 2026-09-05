package bo.forja.backend.dominio;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Raiz comun de las entidades del modelo.
 * <p>
 * El identificador es un UUID asignado en el cliente, no una secuencia
 * de la base de datos: la aplicacion movil debe poder crear clases y
 * relaciones sin conexion y sincronizarlas despues sin riesgo de
 * colision. Como consecuencia el identificador nunca es nulo, y Spring
 * Data no puede deducir por si solo si una instancia es nueva; por eso
 * se implementa {@link Persistable} con una marca transitoria que evita
 * el SELECT previo que Hibernate haria en cada alta.
 */
@MappedSuperclass
public abstract class EntidadBase implements Persistable<UUID> {

    @Id
    private UUID id = UUID.randomUUID();

    @Transient
    private boolean nuevo = true;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    /** Una entidad recien cargada o persistida ya no es nueva. */
    @PostPersist
    @PostLoad
    void marcarComoPersistida() {
        this.nuevo = false;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof EntidadBase esa)) {
            return false;
        }
        return id.equals(esa.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
