package bo.forja.backend.servicio;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.UsoHerramienta;
import bo.forja.backend.repositorio.UsoHerramientaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Anota que funciones de la aplicacion probo cada persona.
 * <p>
 * Lo consume el agente guia: sin esto no puede distinguir a quien nunca
 * descubrio la exportacion a Enterprise Architect de quien la usa todos los
 * dias, y terminaria dando el mismo consejo a los dos.
 */
@Service
public class ServicioUso {

    private static final Logger log = LoggerFactory.getLogger(ServicioUso.class);

    private final UsoHerramientaRepositorio repositorio;
    private final AnotadorDeUso anotador;

    public ServicioUso(UsoHerramientaRepositorio repositorio, AnotadorDeUso anotador) {
        this.repositorio = repositorio;
        this.anotador = anotador;
    }

    /**
     * Anota un uso. No falla nunca: llevar la cuenta de lo que alguien probo no
     * puede hacer fracasar la accion que estaba haciendo.
     * <p>
     * Este metodo NO es transaccional y es a proposito. La transaccion la abre
     * y la cierra {@link AnotadorDeUso}, asi que el catch de aca envuelve
     * tambien el cierre. Cuando el catch vivia dentro del metodo anotado no
     * alcanzaba: una violacion de restriccion marca la transaccion de solo
     * reversion y el proxy lanza {@code UnexpectedRollbackException} al cerrar,
     * fuera del alcance del catch. Eso hacia que el agente guia devolviera 500
     * en vez de contestar -exactamente lo que el diseno promete que no pasa- y
     * se disparaba con un token todavia valido de un usuario que ya no estaba.
     */
    public void anotar(UUID usuarioId, Herramienta herramienta) {
        try {
            anotador.anotar(usuarioId, herramienta);
        } catch (RuntimeException e) {
            log.warn("No se pudo anotar el uso de {} por {}: {}", herramienta, usuarioId, e.toString());
        }
    }

    /** Cuantas veces uso cada funcion, con cero para las que nunca toco. */
    @Transactional(readOnly = true)
    public Map<Herramienta, Integer> deUsuario(UUID usuarioId) {
        Map<Herramienta, Integer> cuenta = new EnumMap<>(Herramienta.class);
        for (Herramienta herramienta : Herramienta.values()) {
            cuenta.put(herramienta, 0);
        }
        for (UsoHerramienta uso : repositorio.deUsuario(usuarioId)) {
            cuenta.put(uso.getId().getHerramienta(), uso.getVeces());
        }
        return cuenta;
    }
}
