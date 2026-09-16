package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cuantas veces un usuario uso una funcion que no deja rastro en el modelo.
 *
 * @see Herramienta
 */
@Entity
@Table(name = "uso_herramienta")
@Getter
@Setter
public class UsoHerramienta {

    @EmbeddedId
    private UsoHerramientaId id = new UsoHerramientaId();

    @Column(nullable = false)
    private int veces = 1;

    @Column(name = "primera_vez", nullable = false)
    private Instant primeraVez = Instant.now();

    @Column(name = "ultima_vez", nullable = false)
    private Instant ultimaVez = Instant.now();
}
