package bo.forja.backend.pedido;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que el modelo propuso, guardado hasta que la persona decide.
 * <p>
 * Existe por una medicion, no por teoria: el mismo pedido da propuestas
 * distintas en cada corrida aun con {@code temperature} en cero. Volver a
 * consultar al modelo al aplicar hacia que entrara al diagrama algo que nadie
 * habia mirado, y con eso el paso de revision -que es la razon por la que el
 * pedido tiene dos pasos y no uno- no garantizaba nada.
 * <p>
 * El cliente sigue sin decidir que entra: no manda comandos, manda el token de
 * su lectura y el servidor busca lo que el mismo propuso. Si no lo encuentra
 * -porque vencio, o porque el proceso se reinicio- se vuelve a consultar al
 * modelo, que es el comportamiento de antes y no deja a nadie sin funcion.
 * <p>
 * Vive en memoria a proposito. Es una antesala de minutos entre dos clicks, no
 * un dato del sistema: llevarla a la base costaria una migracion y un barrido
 * para algo que puede perderse sin consecuencia.
 */
@Component
public class PropuestasEnRevision {

    /**
     * Cuanto se recuerda. Lo bastante para leer una propuesta de veinte frases
     * con calma, y lo bastante poco para que el diagrama no haya cambiado tanto
     * que aplicarla sea absurdo.
     */
    static final Duration VIGENCIA = Duration.ofMinutes(15);

    /** Techo de propuestas vivas. Con una sola EC2 y un aula, sobra. */
    static final int TECHO = 200;

    private final Clock reloj;

    /**
     * Acceso sincronizado y no un mapa concurrente: el desalojo por antiguedad
     * necesita mirar el tamano y borrar en el mismo paso, que es justo lo que
     * un {@code ConcurrentHashMap} no serializa. El mapa se toca dos veces por
     * pedido, asi que el candado no compite con nada.
     */
    private final Map<Llave, Guardada> propuestas = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Llave, Guardada> laMasVieja) {
            return size() > TECHO;
        }
    };

    public PropuestasEnRevision(Clock reloj) {
        this.reloj = reloj;
    }

    /** La llave lleva quien y donde, para que un token no cruce de persona ni de diagrama. */
    private record Llave(UUID usuarioId, UUID diagramaId, String token) {
    }

    private record Guardada(Pedido propuesta, Instant vence) {
    }

    public void guardar(UUID usuarioId, UUID diagramaId, String token, Pedido propuesta) {
        if (token == null || token.isBlank()) {
            return;
        }
        Guardada guardada = new Guardada(propuesta, reloj.instant().plus(VIGENCIA));
        synchronized (propuestas) {
            propuestas.put(new Llave(usuarioId, diagramaId, token.trim()), guardada);
        }
    }

    /**
     * No se consume al recuperarla: el {@code tokenLectura} existe para que el
     * cliente pueda repetir el envio tras un corte, y si desapareciera en el
     * primer intento el reintento volveria a consultar al modelo -de nuevo
     * aplicando algo no revisado-, que es el defecto que esto cierra.
     */
    public Optional<Pedido> recuperar(UUID usuarioId, UUID diagramaId, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Llave llave = new Llave(usuarioId, diagramaId, token.trim());
        synchronized (propuestas) {
            Guardada guardada = propuestas.get(llave);
            if (guardada == null) {
                return Optional.empty();
            }
            if (reloj.instant().isAfter(guardada.vence())) {
                propuestas.remove(llave);
                return Optional.empty();
            }
            return Optional.of(guardada.propuesta());
        }
    }
}
