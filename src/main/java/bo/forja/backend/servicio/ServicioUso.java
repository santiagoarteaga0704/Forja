package bo.forja.backend.servicio;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.UsoHerramienta;
import bo.forja.backend.repositorio.UsoHerramientaRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    public ServicioUso(UsoHerramientaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Anota un uso.
     * <p>
     * En transaccion PROPIA por dos motivos. Uno tecnico: se llama desde
     * acciones de solo lectura -exportar, mirar el codigo generado- y escribir
     * dentro de una transaccion marcada de solo lectura falla. Uno de criterio:
     * llevar la cuenta de lo que alguien probo no puede hacer fracasar la accion
     * que estaba haciendo, asi que el fallo se registra y se sigue.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void anotar(UUID usuarioId, Herramienta herramienta) {
        try {
            repositorio.anotar(usuarioId, herramienta.name());
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
