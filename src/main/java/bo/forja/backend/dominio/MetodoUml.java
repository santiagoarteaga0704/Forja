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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Operacion declarada sobre una clase del modelo. */
@Entity
@Table(name = "metodo_uml")
@Getter
@Setter
public class MetodoUml extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clase_id", nullable = false)
    private ClaseUml clase;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "tipo_retorno", nullable = false, length = 80)
    private String tipoRetorno = "void";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Visibilidad visibilidad = Visibilidad.PUBLICO;

    @Column(name = "es_abstracto", nullable = false)
    private boolean esAbstracto = false;

    @Column(name = "es_estatico", nullable = false)
    private boolean esEstatico = false;

    @OneToMany(mappedBy = "metodo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<ParametroUml> parametros = new ArrayList<>();

    @Column(nullable = false)
    private int orden = 0;
}
