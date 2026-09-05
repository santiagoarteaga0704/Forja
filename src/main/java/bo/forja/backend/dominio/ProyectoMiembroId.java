package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Clave compuesta de la membresia de un usuario en un proyecto. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProyectoMiembroId implements Serializable {

    @Column(name = "proyecto_id", nullable = false)
    private UUID proyectoId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;
}
