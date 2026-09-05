package bo.forja.backend.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Propiedad de una clase.
 * <p>
 * Ademas del nombre y el tipo, guarda los metadatos que el generador
 * necesita para emitir las anotaciones JPA y de validacion del backend:
 * cual es el identificador, que campos son obligatorios o unicos y que
 * longitud declara cada columna.
 */
@Entity
@Table(name = "atributo_uml")
@Getter
@Setter
public class AtributoUml extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private ClaseUml clase;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Visibilidad visibilidad = Visibilidad.PRIVADO;

    @Column(name = "es_identificador", nullable = false)
    private boolean esIdentificador = false;

    @Column(name = "es_requerido", nullable = false)
    private boolean esRequerido = false;

    @Column(name = "es_unico", nullable = false)
    private boolean esUnico = false;

    private Integer longitud;

    @Column(name = "valor_defecto", length = 120)
    private String valorDefecto;

    @Column(nullable = false)
    private int orden = 0;
}
