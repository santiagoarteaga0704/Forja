package bo.forja.backend.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Clasificador del diagrama: la unidad que el generador traduce a una
 * entidad JPA con su repositorio, servicio y controlador.
 * <p>
 * Las coordenadas y dimensiones no son decoracion: son estado compartido
 * del lienzo colaborativo y viajan en las operaciones de movimiento.
 */
@Entity
@Table(name = "clase_uml")
@Getter
@Setter
public class ClaseUml extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagrama_id", nullable = false)
    private Diagrama diagrama;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 60)
    private String estereotipo;

    @Column(name = "es_abstracta", nullable = false)
    private boolean esAbstracta = false;

    @Column(name = "pos_x", nullable = false)
    private double posX = 0;

    @Column(name = "pos_y", nullable = false)
    private double posY = 0;

    @Column(nullable = false)
    private double ancho = 200;

    @Column(nullable = false)
    private double alto = 120;

    @OneToMany(mappedBy = "clase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<AtributoUml> atributos = new ArrayList<>();

    @OneToMany(mappedBy = "clase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<MetodoUml> metodos = new ArrayList<>();

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();
}
