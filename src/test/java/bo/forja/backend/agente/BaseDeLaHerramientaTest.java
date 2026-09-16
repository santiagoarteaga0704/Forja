package bo.forja.backend.agente;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.OrigenOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de las reglas que ensenan a usar la aplicacion.
 * <p>
 * Mismo criterio que las del modelo: se arma el panorama exacto que cada regla
 * necesita y se comprueban las dos mitades, que se dispare cuando debe y que se
 * calle cuando no. La segunda es la que importa, porque un agente que insiste
 * con algo ya hecho deja de leerse.
 * <p>
 * Hay ademas una tercera cosa que comprobar y que no existia antes: que estas
 * reglas <b>no repitan</b> lo que ya dice una regla del diagrama cuando hay un
 * lienzo abierto.
 */
@DisplayName("Reglas que ensenan a usar FORJA")
class BaseDeLaHerramientaTest {

    private final BaseDeLaHerramienta base = new BaseDeLaHerramienta();

    // ---------- Ayudas -------------------------------------------------------

    /** Panorama de una cuenta recien creada: nada hecho todavia. */
    private Panorama reciénLlegado() {
        return new Panorama(UUID.randomUUID(), 0, 0, 0, 0,
                new EnumMap<>(OrigenOperacion.class), sinUsos(), false);
    }

    private Map<Herramienta, Integer> sinUsos() {
        Map<Herramienta, Integer> usos = new EnumMap<>(Herramienta.class);
        for (Herramienta herramienta : Herramienta.values()) {
            usos.put(herramienta, 0);
        }
        return usos;
    }

    private Map<OrigenOperacion, Long> operaciones(OrigenOperacion origen, long cuantas) {
        Map<OrigenOperacion, Long> mapa = new EnumMap<>(OrigenOperacion.class);
        mapa.put(origen, cuantas);
        return mapa;
    }

    private List<Consejo> evaluar(Panorama panorama) {
        return base.reglas().stream()
                .flatMap(regla -> regla.evaluar(panorama).stream())
                .toList();
    }

    private List<String> identificadores(Panorama panorama) {
        return evaluar(panorama).stream().map(Consejo::id).toList();
    }

    // ---------- Los primeros pasos -------------------------------------------

    @Nested
    @DisplayName("Los primeros pasos")
    class PrimerosPasos {

        @Test
        @DisplayName("sin ningun proyecto, lo primero que se dice es como crear uno")
        void sinProyectos() {
            List<Consejo> consejos = evaluar(reciénLlegado());

            Consejo primero = consejos.stream()
                    .max((a, b) -> Integer.compare(a.prioridad(), b.prioridad()))
                    .orElseThrow();
            assertThat(primero.id()).isEqualTo("sin-proyectos");
            assertThat(primero.comoSeHace()).contains("Crear proyecto");
        }

        @Test
        @DisplayName("con un proyecto y ningun diagrama, se ensena a crear el diagrama")
        void sinDiagramas() {
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 0, 0,
                    new EnumMap<>(OrigenOperacion.class), sinUsos(), false);

            assertThat(identificadores(p))
                    .contains("sin-diagramas")
                    .doesNotContain("sin-proyectos");
        }

        @Test
        @DisplayName("el diagrama creado pero vacio se senala solo fuera del lienzo")
        void hojaEnBlanco() {
            Panorama fuera = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    new EnumMap<>(OrigenOperacion.class), sinUsos(), false);
            assertThat(identificadores(fuera)).contains("hoja-en-blanco");

            // Con el lienzo abierto lo dice "diagrama-vacio", que ademas mira el
            // diagrama concreto. Repetirlo seria gastar dos de los seis lugares
            // en el mismo aviso.
            Panorama dentro = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    new EnumMap<>(OrigenOperacion.class), sinUsos(), true);
            assertThat(identificadores(dentro)).doesNotContain("hoja-en-blanco");
        }
    }

    // ---------- Las otras vias de entrada ------------------------------------

    @Nested
    @DisplayName("Las otras vias de entrada")
    class OtrasVias {

        @Test
        @DisplayName("quien solo dibujo recibe el dictado y la pizarra")
        void soloDibujo() {
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.LIENZO, 6), sinUsos(), false);

            assertThat(identificadores(p))
                    .contains("nunca-dicto")
                    .contains("nunca-uso-la-pizarra");
        }

        @Test
        @DisplayName("quien ya dicto no vuelve a recibir el consejo del dictado")
        void yaDicto() {
            Map<OrigenOperacion, Long> mezcla = new EnumMap<>(OrigenOperacion.class);
            mezcla.put(OrigenOperacion.LIENZO, 6L);
            mezcla.put(OrigenOperacion.VOZ, 2L);
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0, mezcla, sinUsos(), false);

            assertThat(identificadores(p))
                    .doesNotContain("nunca-dicto")
                    .contains("nunca-uso-la-pizarra");
        }

        @Test
        @DisplayName("sin nada modelado todavia no se habla de dictar: falta lo anterior")
        void primeroLoPrimero() {
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    new EnumMap<>(OrigenOperacion.class), sinUsos(), false);

            assertThat(identificadores(p)).doesNotContain("nunca-dicto");
        }
    }

    // ---------- Las salidas --------------------------------------------------

    @Nested
    @DisplayName("Las salidas")
    class Salidas {

        @Test
        @DisplayName("un modelo con cuerpo sin generar recibe el aviso; uno de dos cambios no")
        void generar() {
            Panorama conCuerpo = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.LIENZO, 8), sinUsos(), false);
            assertThat(identificadores(conCuerpo)).contains("nunca-genero");

            // Con dos cambios la persona todavia esta probando: mandarla a
            // generar un proyecto de una clase vacia desacredita la funcion.
            Panorama apenasEmpezado = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.LIENZO, 2), sinUsos(), false);
            assertThat(identificadores(apenasEmpezado)).doesNotContain("nunca-genero");
        }

        @Test
        @DisplayName("quien ya genero no vuelve a recibirlo")
        void yaGenero() {
            Map<Herramienta, Integer> usos = sinUsos();
            usos.put(Herramienta.BACKEND_GENERADO, 3);
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.LIENZO, 8), usos, false);

            assertThat(identificadores(p)).doesNotContain("nunca-genero");
        }

        @Test
        @DisplayName("haber importado un XMI cuenta como intercambio, aunque no se haya exportado")
        void importarTambienCuenta() {
            Map<OrigenOperacion, Long> mezcla = new EnumMap<>(OrigenOperacion.class);
            mezcla.put(OrigenOperacion.LIENZO, 8L);
            mezcla.put(OrigenOperacion.IMPORTACION, 20L);
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0, mezcla, sinUsos(), false);

            assertThat(identificadores(p)).doesNotContain("nunca-intercambio");
        }
    }

    // ---------- Trabajar con otra gente y preguntar --------------------------

    @Nested
    @DisplayName("Colaborar y preguntar")
    class ColaborarYPreguntar {

        @Test
        @DisplayName("quien nunca invito recibe el aviso; quien ya invito, no")
        void invitar() {
            Panorama solo = new Panorama(UUID.randomUUID(), 2, 0, 2, 0,
                    operaciones(OrigenOperacion.LIENZO, 8), sinUsos(), false);
            assertThat(identificadores(solo)).contains("nunca-invito");

            Panorama acompanado = new Panorama(UUID.randomUUID(), 2, 0, 2, 1,
                    operaciones(OrigenOperacion.LIENZO, 8), sinUsos(), false);
            assertThat(identificadores(acompanado)).doesNotContain("nunca-invito");
        }

        @Test
        @DisplayName("el agente se ofrece hasta que le preguntan una vez")
        void preguntar() {
            assertThat(identificadores(reciénLlegado())).contains("nunca-pregunto");

            Map<Herramienta, Integer> usos = sinUsos();
            usos.put(Herramienta.AGENTE_CONSULTADO, 1);
            Panorama p = new Panorama(UUID.randomUUID(), 0, 0, 0, 0,
                    new EnumMap<>(OrigenOperacion.class), usos, false);
            assertThat(identificadores(p)).doesNotContain("nunca-pregunto");
        }

        @Test
        @DisplayName("con todo probado el agente lo dice y deja de ensenar la herramienta")
        void todoProbado() {
            Map<OrigenOperacion, Long> mezcla = new EnumMap<>(OrigenOperacion.class);
            mezcla.put(OrigenOperacion.LIENZO, 10L);
            mezcla.put(OrigenOperacion.VOZ, 4L);
            mezcla.put(OrigenOperacion.FOTO, 3L);
            Map<Herramienta, Integer> usos = sinUsos();
            usos.put(Herramienta.BACKEND_GENERADO, 1);
            usos.put(Herramienta.XMI_EXPORTADO, 1);
            usos.put(Herramienta.AGENTE_CONSULTADO, 1);

            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 2, mezcla, usos, false);

            List<String> ids = identificadores(p);
            assertThat(ids).containsExactly("ya-recorrio-todo");
        }
    }

    // ---------- El recorrido --------------------------------------------------

    @Nested
    @DisplayName("El recorrido guiado")
    class ElRecorrido {

        @Test
        @DisplayName("una cuenta nueva tiene los ocho pasos pendientes y el primero es el proyecto")
        void reciénEmpezado() {
            Recorrido recorrido = Recorrido.de(reciénLlegado());

            assertThat(recorrido.total()).isEqualTo(8);
            assertThat(recorrido.hechos()).isZero();
            assertThat(recorrido.completo()).isFalse();
            assertThat(recorrido.loQueSigue()).get()
                    .extracting(Recorrido.Paso::id).isEqualTo("proyecto");
        }

        @Test
        @DisplayName("los pasos se marcan con la evidencia, no con haber visto la pantalla")
        void seMarcanConEvidencia() {
            Map<Herramienta, Integer> usos = sinUsos();
            usos.put(Herramienta.BACKEND_GENERADO, 1);
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.VOZ, 5), usos, true);

            Recorrido recorrido = Recorrido.de(p);

            assertThat(recorrido.hechos()).isEqualTo(5);
            assertThat(recorrido.pasos()).filteredOn(Recorrido.Paso::hecho)
                    .extracting(Recorrido.Paso::id)
                    .containsExactly("proyecto", "diagrama", "modelar", "dictar", "generar");
        }

        @Test
        @DisplayName("lo que sigue es el primer hueco, no el paso posterior al ultimo hecho")
        void elPrimerHueco() {
            // Se puede dictar antes de dibujar nada: lo que hay que senalar
            // entonces es el hueco, no el paso siguiente al ultimo marcado.
            Panorama p = new Panorama(UUID.randomUUID(), 1, 0, 1, 0,
                    operaciones(OrigenOperacion.VOZ, 3), sinUsos(), false);

            assertThat(Recorrido.de(p).loQueSigue()).get()
                    .extracting(Recorrido.Paso::id).isEqualTo("pizarra");
        }
    }
}
