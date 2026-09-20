package bo.forja.backend.agente;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.servicio.ServicioUso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El agente contesta aunque no se pueda anotar la consulta.
 * <p>
 * Nace de un 500 real, visto el 20 de septiembre de 2026 contra el servidor
 * corriendo. La cadena era esta: al preguntar, el agente anota el uso en
 * {@code uso_herramienta}, que tiene clave ajena a {@code usuario}; si esa fila
 * no esta -un token sigue siendo valido doce horas aunque la persona ya no
 * exista, porque la sesion no tiene estado- la insercion viola la clave ajena y
 * Postgres marca la transaccion como de solo reversion. {@code ServicioUso}
 * atrapaba la excepcion para seguir igual, pero el {@code catch} vive DENTRO
 * del limite transaccional: la {@code UnexpectedRollbackException} la lanza el
 * proxy despues, al cerrar. Resultado, el agente devolvia 500.
 * <p>
 * Importa mas de lo que parece: el argumento de diseno escrito en
 * {@code ControladorGuia} es que el agente no puede quedar mudo delante de un
 * aula, y el disparador mas probable es justamente el dia de la defensa, al
 * rehacer la base con un navegador que todavia guarda su token.
 */
@SpringBootTest
@DisplayName("El agente guia no se cae")
class AgenteNoSeCaeTest {

    @Autowired
    private ServicioAgente agente;

    @Autowired
    private ServicioUso uso;

    /** Un usuario que no existe: es lo que deja un token que sobrevive a su fila. */
    private static final UUID FANTASMA = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    @Test
    @DisplayName("responde aunque el usuario ya no exista")
    void respondeSinUsuario() {
        assertThat(agente.responder(FANTASMA, "como uso la aplicacion", null))
                .isNotEmpty();
    }

    @Test
    @DisplayName("anotar un uso imposible no rompe a quien lo llamo")
    void anotarNoContagia() {
        assertThatCode(() -> uso.anotar(FANTASMA, Herramienta.AGENTE_CONSULTADO))
                .doesNotThrowAnyException();
    }
}
