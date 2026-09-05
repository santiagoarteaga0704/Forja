package bo.forja.backend.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo UML sobre el que trabajan simultaneamente varios usuarios.
 * <p>
 * {@code version} es un contador monotono que se incrementa con cada
 * operacion aceptada. Cumple dos funciones: sirve de reloj logico para
 * ordenar los cambios y permite que un cliente que estuvo sin conexion
 * pida unicamente el delta posterior a la version que ya conoce.
 */
@Entity
@Table(name = "diagrama")
@Getter
@Setter
public class Diagrama extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDiagrama tipo = TipoDiagrama.CLASES;

    @Column(nullable = false)
    private long version = 0L;

    @OneToMany(mappedBy = "diagrama", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClaseUml> clases = new ArrayList<>();

    @OneToMany(mappedBy = "diagrama", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RelacionUml> relaciones = new ArrayList<>();

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() {
        this.actualizadoEn = Instant.now();
    }
}
