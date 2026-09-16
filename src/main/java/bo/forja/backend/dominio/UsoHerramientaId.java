package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Clave compuesta del registro de uso: quien uso que. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UsoHerramientaId implements Serializable {

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Herramienta herramienta;
}
