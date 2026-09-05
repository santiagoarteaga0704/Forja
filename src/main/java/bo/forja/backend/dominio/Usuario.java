package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Persona que modela y colabora dentro de FORJA. */
@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario extends EntidadBase {

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();
}
