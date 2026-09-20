package bo.forja.backend.servicio;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.repositorio.UsoHerramientaRepositorio;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * La escritura del uso, sola, en su propia transaccion.
 * <p>
 * Existe como bean aparte por una razon que no se ve leyendo el codigo: para
 * que el {@code try/catch} de {@link ServicioUso} quede FUERA del limite
 * transaccional.
 * <p>
 * Antes el catch estaba dentro del mismo metodo anotado, y no alcanzaba. Si la
 * insercion viola una restriccion, Postgres marca la transaccion como de solo
 * reversion; atrapar la excepcion no desmarca nada, y al cerrar la transaccion
 * el proxy de Spring lanza {@code UnexpectedRollbackException} DESPUES de que
 * el cuerpo del metodo termino. Esa segunda excepcion nacia fuera del alcance
 * del catch y se llevaba puesta la peticion entera: el agente guia devolvia 500
 * en vez de contestar.
 * <p>
 * Con la escritura en otro bean, la transaccion se abre y se cierra dentro de
 * {@link #anotar}, y quien llama puede envolver la llamada completa -cierre
 * incluido- en su propio catch.
 */
@Component
class AnotadorDeUso {

    private final UsoHerramientaRepositorio repositorio;

    AnotadorDeUso(UsoHerramientaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * En transaccion PROPIA por dos motivos. Uno tecnico: se llama desde
     * acciones de solo lectura -exportar, mirar el codigo generado- y escribir
     * dentro de una transaccion marcada de solo lectura falla. Uno de criterio:
     * llevar la cuenta de lo que alguien probo no puede hacer fracasar la accion
     * que estaba haciendo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void anotar(UUID usuarioId, Herramienta herramienta) {
        repositorio.anotar(usuarioId, herramienta.name());
    }
}
