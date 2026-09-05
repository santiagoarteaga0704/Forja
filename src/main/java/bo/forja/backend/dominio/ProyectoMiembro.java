package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Participacion de un usuario en un proyecto.
 * <p>
 * El rol determina si el usuario puede adquirir bloqueos sobre los
 * elementos del diagrama o si su acceso es de solo lectura.
 */
@Entity
@Table(name = "proyecto_miembro")
@Getter
@Setter
public class ProyectoMiembro {

    @EmbeddedId
    private ProyectoMiembroId id = new ProyectoMiembroId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("proyectoId")
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolMiembro rol = RolMiembro.EDITOR;

    @Column(name = "invitado_en", nullable = false)
    private Instant invitadoEn = Instant.now();
}
