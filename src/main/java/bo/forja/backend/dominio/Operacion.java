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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Registro inmutable de un cambio aplicado sobre un diagrama.
 * <p>
 * El modelo no se sincroniza enviando el diagrama completo, sino la
 * secuencia de operaciones que lo produjeron. Cada una lleva un numero
 * de secuencia por diagrama, de modo que un cliente que estuvo sin
 * conexion solicita las operaciones posteriores a la ultima que conoce y
 * reproduce solo el delta.
 * <p>
 * {@code tokenCliente} es generado por el cliente antes de encolar la
 * operacion y hace idempotente el reenvio: si la red se corta despues de
 * que el servidor persistio el cambio pero antes de que llegara la
 * respuesta, el reintento choca contra la restriccion de unicidad y se
 * resuelve devolviendo la operacion ya registrada, sin duplicarla.
 */
@Entity
@Table(name = "operacion")
@Getter
@Setter
public class Operacion extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagrama_id", nullable = false)
    private Diagrama diagrama;

    @Column(nullable = false)
    private long secuencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 40)
    private String tipo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String carga;

    @Column(name = "token_cliente", nullable = false, length = 80)
    private String tokenCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenOperacion origen = OrigenOperacion.LIENZO;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();
}
