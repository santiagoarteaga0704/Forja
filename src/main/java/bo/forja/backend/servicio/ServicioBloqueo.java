package bo.forja.backend.servicio;

import bo.forja.backend.dominio.BloqueoElemento;
import bo.forja.backend.dominio.TipoElemento;
import bo.forja.backend.repositorio.BloqueoElementoRepositorio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Exclusion mutua sobre los elementos de un diagrama compartido.
 * <p>
 * Traslada al entorno virtual la regla implicita de una pizarra fisica:
 * dos personas no escriben a la vez sobre el mismo recuadro. Antes de
 * modificar una clase o una relacion, el cliente debe obtener su
 * bloqueo; mientras lo retiene, el resto ve el elemento marcado como
 * ocupado y sus intentos de edicion son rechazados.
 * <p>
 * El arbitraje no se resuelve en memoria sino en la base de datos, a
 * traves de la restriccion de unicidad de {@code bloqueo_elemento}. La
 * diferencia es determinante: un mecanismo en memoria seria correcto con
 * una sola instancia del backend y dejaria de serlo en cuanto el
 * despliegue escale horizontalmente, mientras que la restriccion sigue
 * siendo valida para cualquier numero de instancias.
 * <p>
 * Este servicio presume que la autorizacion del usuario sobre el
 * proyecto ya fue verificada por la capa que lo invoca.
 */
@Service
public class ServicioBloqueo {

    private static final Logger log = LoggerFactory.getLogger(ServicioBloqueo.class);

    private final BloqueoElementoRepositorio bloqueos;
    private final Duration vigencia;

    public ServicioBloqueo(BloqueoElementoRepositorio bloqueos,
                           @Value("${forja.bloqueo.vigencia-segundos:60}") long vigenciaSegundos) {
        this.bloqueos = bloqueos;
        this.vigencia = Duration.ofSeconds(vigenciaSegundos);
    }

    /**
     * Solicita el bloqueo de un elemento.
     * <p>
     * El orden de los pasos importa. Primero se intenta renovar, porque
     * lo mas frecuente es que quien pide ya sea el poseedor y solo este
     * prolongando su edicion. Recien despues se descarta un bloqueo
     * vencido y se compite por el elemento libre.
     */
    @Transactional
    public ResultadoBloqueo adquirir(UUID diagramaId,
                                     TipoElemento elementoTipo,
                                     UUID elementoId,
                                     UUID usuarioId,
                                     String sesionId) {

        Instant momento = Instant.now();
        Instant expiraEn = momento.plus(vigencia);

        if (bloqueos.renovar(elementoTipo, elementoId, usuarioId, sesionId, expiraEn) == 1) {
            return new ResultadoBloqueo(ResultadoBloqueo.Estado.RENOVADO,
                    elementoTipo, elementoId, usuarioId, null, expiraEn);
        }

        int liberados = bloqueos.purgarSiVencio(elementoTipo, elementoId, momento);
        if (liberados > 0) {
            log.debug("Bloqueo vencido liberado sobre {} {}", elementoTipo, elementoId);
        }

        int concedido = bloqueos.intentarAdquirir(UUID.randomUUID(), diagramaId,
                elementoTipo.name(), elementoId, usuarioId, sesionId, momento, expiraEn);

        if (concedido == 1) {
            return new ResultadoBloqueo(ResultadoBloqueo.Estado.CONCEDIDO,
                    elementoTipo, elementoId, usuarioId, null, expiraEn);
        }

        // La insercion no prospero: otra transaccion gano la carrera.
        return rechazoCon(elementoTipo, elementoId);
    }

    /**
     * Construye el rechazo informando quien retiene el elemento. Si en el
     * instante de consultar el poseedor ya lo solto, se responde igual
     * como rechazo: el cliente reintentara y obtendra el bloqueo.
     */
    private ResultadoBloqueo rechazoCon(TipoElemento elementoTipo, UUID elementoId) {
        Optional<BloqueoElemento> vigente =
                bloqueos.findByElementoTipoAndElementoId(elementoTipo, elementoId);

        return vigente
                .map(b -> new ResultadoBloqueo(ResultadoBloqueo.Estado.RECHAZADO,
                        elementoTipo, elementoId,
                        b.getUsuario().getId(), b.getUsuario().getNombre(), b.getExpiraEn()))
                .orElseGet(() -> new ResultadoBloqueo(ResultadoBloqueo.Estado.RECHAZADO,
                        elementoTipo, elementoId, null, null, null));
    }

    /** Libera el bloqueo si quien lo pide es efectivamente su poseedor. */
    @Transactional
    public boolean liberar(TipoElemento elementoTipo, UUID elementoId,
                           UUID usuarioId, String sesionId) {
        return bloqueos.liberar(elementoTipo, elementoId, usuarioId, sesionId) == 1;
    }

    /**
     * Libera todo lo que retenia una sesion. Se invoca al cerrarse el
     * canal WebSocket para devolver los elementos de inmediato, sin
     * obligar al resto a esperar el vencimiento.
     */
    @Transactional
    public int liberarSesion(String sesionId) {
        int liberados = bloqueos.liberarPorSesion(sesionId);
        if (liberados > 0) {
            log.debug("Sesion {} cerrada: {} bloqueos liberados", sesionId, liberados);
        }
        return liberados;
    }

    @Transactional(readOnly = true)
    public List<BloqueoElemento> vigentesDe(UUID diagramaId) {
        return bloqueos.findByDiagramaId(diagramaId);
    }

    /**
     * Red de seguridad: barre los bloqueos que vencieron sin que nadie
     * los reclamara. El camino habitual de liberacion es el cierre de
     * sesion; este barrido cubre las caidas abruptas.
     */
    @Scheduled(fixedDelayString = "${forja.bloqueo.barrido-ms:30000}")
    @Transactional
    public void barrerVencidos() {
        int purgados = bloqueos.purgarVencidos(Instant.now());
        if (purgados > 0) {
            log.info("Barrido periodico: {} bloqueos vencidos eliminados", purgados);
        }
    }
}
