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

import java.time.Instant;
import java.util.UUID;

/**
 * Bloqueo de edicion sobre un elemento del diagrama: el mecanismo de
 * exclusion mutua del lienzo colaborativo.
 * <p>
 * La restriccion de unicidad sobre {@code (elemento_tipo, elemento_id)}
 * es deliberada: hace que sea la propia base de datos quien arbitre la
 * competencia por un elemento. Dos peticiones simultaneas de bloqueo se
 * resuelven en una insercion ganadora y una violacion de unicidad, sin
 * depender de sincronizacion en memoria, que dejaria de ser correcta en
 * cuanto el backend se despliegue con mas de una instancia.
 * <p>
 * {@code expiraEn} evita el interbloqueo por abandono: si un cliente
 * pierde la conexion sin liberar, el bloqueo caduca y el elemento vuelve
 * a estar disponible.
 */
@Entity
@Table(name = "bloqueo_elemento")
@Getter
@Setter
public class BloqueoElemento extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagrama_id", nullable = false)
    private Diagrama diagrama;

    @Enumerated(EnumType.STRING)
    @Column(name = "elemento_tipo", nullable = false, length = 20)
    private TipoElemento elementoTipo;

    @Column(name = "elemento_id", nullable = false)
    private UUID elementoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Sesion WebSocket que sostiene el bloqueo. */
    @Column(name = "sesion_id", nullable = false, length = 80)
    private String sesionId;

    @Column(name = "adquirido_en", nullable = false)
    private Instant adquiridoEn = Instant.now();

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    public boolean estaVencido(Instant momento) {
        return expiraEn.isBefore(momento);
    }
}
