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
 * Vinculo entre dos clasificadores del diagrama.
 * <p>
 * La multiplicidad se guarda en notacion UML ({@code 1}, {@code 0..1},
 * {@code 1..*}, {@code *}) porque es la forma en que se dicta por voz,
 * se dibuja en el lienzo y se serializa en XMI. Es el generador el que
 * la interpreta para decidir entre {@code @ManyToOne}, {@code @OneToMany}
 * y {@code @ManyToMany}.
 */
@Entity
@Table(name = "relacion_uml")
@Getter
@Setter
public class RelacionUml extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagrama_id", nullable = false)
    private Diagrama diagrama;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origen_id", nullable = false)
    private ClaseUml origen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destino_id", nullable = false)
    private ClaseUml destino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoRelacion tipo = TipoRelacion.ASOCIACION;

    @Column(name = "multiplicidad_origen", nullable = false, length = 10)
    private String multiplicidadOrigen = "1";

    @Column(name = "multiplicidad_destino", nullable = false, length = 10)
    private String multiplicidadDestino = "1";

    @Column(name = "rol_origen", length = 120)
    private String rolOrigen;

    @Column(name = "rol_destino", length = 120)
    private String rolDestino;

    @Column(length = 150)
    private String etiqueta;
}
