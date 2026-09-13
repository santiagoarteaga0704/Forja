package bo.forja.backend.xmi;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.servicio.ResultadoOperacion;
import bo.forja.backend.servicio.ServicioModelo;
import bo.forja.backend.servicio.ServicioOperaciones;
import bo.forja.backend.servicio.ServicioProyectos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Intercambio de modelos con Enterprise Architect via XMI.
 * <p>
 * La importacion no escribe en la base de datos: traduce el documento a
 * comandos y los entrega al registro de operaciones, uno por uno. La
 * consecuencia es la que importa para el proyecto: un modelo que llega de
 * otra herramienta queda en la <b>misma bitacora</b> que los trazos del
 * lienzo y los dictados, con origen {@code IMPORTACION}, y respeta la
 * exclusion mutua de los elementos que alguien este editando en ese momento.
 * Importar sobre un diagrama abierto no atropella a quien lo esta usando.
 * <p>
 * Cada comando va en su propia transaccion. Es deliberado: si una clase del
 * documento viola una regla del modelo, se informa y el resto se importa
 * igual. Un archivo de cuarenta clases no deberia perderse entero porque una
 * traiga un nombre repetido, y el resumen dice exactamente que no entro.
 */
@Service
public class ServicioXmi {

    private static final Logger log = LoggerFactory.getLogger(ServicioXmi.class);

    private final ServicioProyectos proyectos;
    private final ServicioModelo modelo;
    private final ServicioOperaciones operaciones;
    private final ExportadorXmi exportador;
    private final ImportadorXmi importador;

    public ServicioXmi(ServicioProyectos proyectos,
                       ServicioModelo modelo,
                       ServicioOperaciones operaciones,
                       ExportadorXmi exportador,
                       ImportadorXmi importador) {
        this.proyectos = proyectos;
        this.modelo = modelo;
        this.operaciones = operaciones;
        this.exportador = exportador;
        this.importador = importador;
    }

    @Transactional(readOnly = true)
    public String exportar(UUID diagramaId, UUID usuarioId) {
        Diagrama diagrama = proyectos.diagramaAccesible(diagramaId, usuarioId);
        return exportador.exportar(diagrama,
                modelo.clasesCompletas(diagramaId),
                modelo.relacionesDe(diagramaId));
    }

    /**
     * Importa el documento sobre el diagrama indicado.
     *
     * @param tokenImportacion identificador de este envio; si el cliente lo
     *                         repite tras un corte de red, los comandos se
     *                         reconocen como ya registrados en lugar de
     *                         duplicar el modelo
     */
    public ResumenImportacion importar(UUID diagramaId, UUID usuarioId, String sesionId,
                                       String xmi, String tokenImportacion) {

        // Se comprueba el acceso antes de gastar tiempo interpretando el archivo.
        proyectos.diagramaAccesible(diagramaId, usuarioId);

        List<ComandoOperacion> comandos = importador.interpretar(xmi);
        String token = tokenImportacion == null || tokenImportacion.isBlank()
                ? "xmi-" + UUID.randomUUID()
                : tokenImportacion.trim();

        int aplicadas = 0;
        int duplicadas = 0;
        int rechazadasPorBloqueo = 0;
        List<String> problemas = new ArrayList<>();

        for (int i = 0; i < comandos.size(); i++) {
            ComandoOperacion comando = comandos.get(i);
            try {
                ResultadoOperacion resultado = operaciones.registrar(diagramaId, usuarioId,
                        sesionId, comando, OrigenOperacion.IMPORTACION, token + "-" + i);

                switch (resultado.estado()) {
                    case APLICADA -> aplicadas++;
                    case DUPLICADA -> duplicadas++;
                    case RECHAZADA_POR_BLOQUEO -> rechazadasPorBloqueo++;
                }
            } catch (ComandoInvalido e) {
                problemas.add(comando.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        long version = operaciones.versionActual(diagramaId);
        log.info("Importacion XMI en el diagrama {}: {} aplicadas, {} duplicadas, {} bloqueadas, "
                + "{} con problemas", diagramaId, aplicadas, duplicadas, rechazadasPorBloqueo,
                problemas.size());

        return new ResumenImportacion(comandos.size(), aplicadas, duplicadas,
                rechazadasPorBloqueo, problemas, version);
    }

    /**
     * Que entro y que no.
     *
     * @param problemas descripcion de los comandos que el modelo rechazo; se
     *                  devuelven al cliente en lugar de registrarse solo en el
     *                  log, porque quien importa necesita saber que corregir
     */
    public record ResumenImportacion(
            int comandosLeidos,
            int aplicadas,
            int duplicadas,
            int rechazadasPorBloqueo,
            List<String> problemas,
            long versionDelDiagrama) {

        public boolean entroTodo() {
            return problemas.isEmpty() && rechazadasPorBloqueo == 0;
        }
    }
}
