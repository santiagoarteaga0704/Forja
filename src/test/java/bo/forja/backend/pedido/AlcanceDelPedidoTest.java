package bo.forja.backend.pedido;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La IA ayuda a modelar; no modela por vos.
 * <p>
 * El pedido en lenguaje libre nacio devolviendo el diagrama entero: una frase
 * y veinte clases. Es una decision de la materia, no una limitacion tecnica,
 * que la herramienta no haga el trabajo que se esta evaluando. Asi que el
 * pedido queda acotado a UN elemento por vez -una clase con lo suyo, o una
 * relacion- y lo que sobra se descarta ANTES de tocar el diagrama.
 * <p>
 * El limite vive en el codigo y no en el prompt porque el prompt no garantiza
 * nada: el 21 de septiembre de 2026 se midio que el modelo desobedece sus
 * instrucciones en la mayoria de las corridas.
 */
@DisplayName("La IA propone de a un elemento")
class AlcanceDelPedidoTest {

    private static final UUID PACIENTE = UUID.randomUUID();
    private static final UUID CONSULTA = UUID.randomUUID();

    private ComandoOperacion crearClase(UUID id, String nombre) {
        return new ComandoOperacion.CrearClase(id, nombre, null, false, 0, 0);
    }

    private ComandoOperacion atributo(UUID claseId, String nombre) {
        return new ComandoOperacion.AgregarAtributo(claseId, UUID.randomUUID(), nombre,
                "String", Visibilidad.PRIVADO, false, false, false, null);
    }

    private ComandoOperacion metodo(UUID claseId, String nombre) {
        return new ComandoOperacion.AgregarMetodo(claseId, UUID.randomUUID(), nombre,
                "void", Visibilidad.PUBLICO, false, false, List.of());
    }

    private ComandoOperacion relacion(UUID origen, UUID destino) {
        return new ComandoOperacion.CrearRelacion(UUID.randomUUID(), origen, destino,
                TipoRelacion.ASOCIACION, "1", "0..*", null, null, null);
    }

    @Test
    @DisplayName("una clase con sus atributos y metodos pasa entera")
    void unaClaseConLoSuyoPasaEntera() {
        List<ComandoOperacion> propuesta = List.of(
                crearClase(PACIENTE, "Paciente"),
                atributo(PACIENTE, "nombre"),
                atributo(PACIENTE, "edad"),
                metodo(PACIENTE, "calcularEdad"));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(4);
        assertThat(recorte.seRecorto()).isFalse();
    }

    @Test
    @DisplayName("de dos clases propuestas queda la primera, con lo suyo")
    void deDosClasesQuedaLaPrimera() {
        List<ComandoOperacion> propuesta = List.of(
                crearClase(PACIENTE, "Paciente"),
                atributo(PACIENTE, "nombre"),
                crearClase(CONSULTA, "Consulta"),
                atributo(CONSULTA, "fecha"));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(2);
        assertThat(recorte.admitidos().get(0)).isInstanceOf(ComandoOperacion.CrearClase.class);
        assertThat(((ComandoOperacion.CrearClase) recorte.admitidos().get(0)).nombre())
                .isEqualTo("Paciente");
        assertThat(recorte.seRecorto()).isTrue();
        assertThat(recorte.descartados()).isEqualTo(2);
    }

    @Test
    @DisplayName("un diagrama entero se reduce a su primera clase")
    void unDiagramaEnteroSeReduce() {
        UUID receta = UUID.randomUUID();
        List<ComandoOperacion> propuesta = List.of(
                crearClase(PACIENTE, "Paciente"),
                atributo(PACIENTE, "nombre"),
                crearClase(CONSULTA, "Consulta"),
                crearClase(receta, "Receta"),
                relacion(PACIENTE, CONSULTA),
                relacion(CONSULTA, receta));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(2);
        assertThat(recorte.descartados()).isEqualTo(4);
    }

    @Test
    @DisplayName("atributos sobre una clase que ya existe: pasan los de esa clase")
    void atributosSobreUnaClaseExistente() {
        List<ComandoOperacion> propuesta = List.of(
                atributo(PACIENTE, "nombre"),
                atributo(PACIENTE, "edad"),
                atributo(CONSULTA, "fecha"));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(2);
        assertThat(recorte.descartados()).isEqualTo(1);
    }

    @Test
    @DisplayName("una sola relacion pasa")
    void unaRelacionPasa() {
        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(
                List.of(relacion(PACIENTE, CONSULTA)));

        assertThat(recorte.admitidos()).hasSize(1);
        assertThat(recorte.seRecorto()).isFalse();
    }

    @Test
    @DisplayName("de varias relaciones queda una sola")
    void deVariasRelacionesQuedaUna() {
        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(List.of(
                relacion(PACIENTE, CONSULTA),
                relacion(CONSULTA, PACIENTE)));

        assertThat(recorte.admitidos()).hasSize(1);
        assertThat(recorte.descartados()).isEqualTo(1);
    }

    @Test
    @DisplayName("la clase gana a la relacion cuando vienen las dos")
    void laClaseGanaALaRelacion() {
        List<ComandoOperacion> propuesta = List.of(
                crearClase(PACIENTE, "Paciente"),
                atributo(PACIENTE, "nombre"),
                relacion(PACIENTE, CONSULTA));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(2);
        assertThat(recorte.admitidos()).noneMatch(c -> c instanceof ComandoOperacion.CrearRelacion);
        assertThat(recorte.descartados()).isEqualTo(1);
    }

    @Test
    @DisplayName("una propuesta vacia no rompe")
    void vaciaNoRompe() {
        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(List.of());

        assertThat(recorte.admitidos()).isEmpty();
        assertThat(recorte.seRecorto()).isFalse();
    }

    @Test
    @DisplayName("los atributos de la clase admitida pasan aunque vengan despues de otra clase")
    void losAtributosDeLaPrimeraPasanAunqueLleguenTarde() {
        List<ComandoOperacion> propuesta = List.of(
                crearClase(PACIENTE, "Paciente"),
                crearClase(CONSULTA, "Consulta"),
                atributo(CONSULTA, "fecha"),
                atributo(PACIENTE, "nombre"));

        AlcanceDelPedido.Recorte recorte = AlcanceDelPedido.recortar(propuesta);

        assertThat(recorte.admitidos()).hasSize(2);
        assertThat(recorte.descartados()).isEqualTo(2);
    }
}
