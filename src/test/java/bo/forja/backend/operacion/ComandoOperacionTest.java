package bo.forja.backend.operacion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El contrato de los identificadores con los clientes.
 * <p>
 * Los comandos declaran sus ids como {@link java.util.UUID}, y eso no es una
 * preferencia de formato sino una condicion para que la operacion entre: un id
 * con cualquier otra forma no se deserializa y la operacion se rechaza. En el
 * cliente movil eso es especialmente caro, porque la cola de pendientes se
 * detiene en el primer tropiezo: una sola operacion mal formada deja trabado
 * todo lo que se hizo sin conexion.
 * <p>
 * Esta prueba existe porque el telefono estuvo generando sus ids con el mismo
 * metodo que usa para los tokens de la cola, que no tiene forma de UUID. Las
 * dos cosas se parecen -las dos son cadenas que no se repiten- y son distintas:
 * el token es asunto de la cola, el id es asunto del modelo.
 */
@DisplayName("Identificadores de los comandos")
class ComandoOperacionTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("acepta un id con forma de UUID")
    void aceptaUnUuid() {
        String carga = """
                {"claseId":"5f3c9a8e-1b2d-4c7a-9e11-7d4e5f6a8b90","nombre":"Paciente",
                 "estereotipo":null,"esAbstracta":false,"posX":0,"posY":0}
                """;

        ComandoOperacion.CrearClase comando =
                json.readValue(carga, ComandoOperacion.CrearClase.class);

        assertThat(comando.nombre()).isEqualTo("Paciente");
        assertThat(comando.claseId()).hasToString("5f3c9a8e-1b2d-4c7a-9e11-7d4e5f6a8b90");
    }

    @Test
    @DisplayName("rechaza el token de la cola usado como id, que es lo que trababa el telefono")
    void rechazaUnTokenDeCola() {
        // La forma que produce Sincronizador.nuevoToken() en el movil: base 36,
        // no hexadecimal, y sin los cinco grupos de un UUID.
        String carga = """
                {"claseId":"2kf3j1a-1x9dq","nombre":"Paciente",
                 "estereotipo":null,"esAbstracta":false,"posX":0,"posY":0}
                """;

        assertThatThrownBy(() -> json.readValue(carga, ComandoOperacion.CrearClase.class))
                .hasMessageContaining("2kf3j1a-1x9dq");
    }
}
