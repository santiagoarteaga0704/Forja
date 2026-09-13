package bo.forja.backend.agente;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.OrigenOperacion;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de la base de conocimiento del agente guia.
 * <p>
 * Las reglas no consultan nada: reciben una observacion y concluyen. Eso permite
 * armar aqui el diagrama exacto que cada regla necesita -incluido el que NO debe
 * dispararla- y revisar las catorce en milisegundos, sin Postgres.
 * <p>
 * De cada regla se comprueban las dos mitades. Que se dispare cuando debe es la
 * facil; la que importa es que se calle cuando no corresponde, porque un agente
 * que avisa de cosas que estan bien deja de leerse a la segunda vez.
 */
@DisplayName("Base de conocimiento del agente guia")
class BaseDeConocimientoTest {

    private final BaseDeConocimiento base = new BaseDeConocimiento();

    // ---------- Descubrimiento ----------------------------------------------

    @Nested
    @DisplayName("Ensenar la herramienta")
    class Descubrimiento {

        @Test
        @DisplayName("un diagrama vacio recibe el primer paso, y con la frase para dictarlo")
        void diagramaVacio() {
            List<Consejo> consejos = evaluar(observacion(List.of(), List.of()));

            assertThat(ids(consejos)).contains("diagrama-vacio");
            Consejo primero = consejos.get(0);
            assertThat(primero.categoria()).isEqualTo(Consejo.Categoria.DESCUBRIMIENTO);
            assertThat(primero.comoSeHace()).contains("crea la clase");
            // Lo urgente va primero: un diagrama vacio no necesita consejos de diseno.
            assertThat(primero.id()).isEqualTo("diagrama-vacio");
        }

        @Test
        @DisplayName("con clases pero sin relaciones ensena a relacionarlas")
        void sinRelaciones() {
            List<Consejo> consejos = evaluar(observacion(
                    List.of(clase("Paciente", "nombre:String"), clase("Consulta", "fecha:Date")),
                    List.of()));

            assertThat(ids(consejos)).contains("sin-relaciones");
            Consejo consejo = buscar(consejos, "sin-relaciones");
            assertThat(consejo.comoSeHace()).contains("tiene muchas");
            assertThat(consejo.porQueImporta()).contains("claves ajenas");
        }

        @Test
        @DisplayName("con una sola clase no insiste con las relaciones")
        void unaSolaClaseNoPideRelaciones() {
            assertThat(ids(evaluar(observacion(List.of(clase("Paciente", "nombre:String")), List.of()))))
                    .doesNotContain("sin-relaciones");
        }

        @Test
        @DisplayName("si nunca se dicto nada lo propone, pero no en el primer cambio")
        void proponeElDictado() {
            List<ClaseUml> clases = List.of(clase("Paciente", "nombre:String"));

            // Con un solo cambio hecho todavia no: seria ruido antes de que la
            // persona sepa que esta haciendo.
            assertThat(ids(evaluar(conOperaciones(clases, List.of(), Map.of(OrigenOperacion.LIENZO, 1L)))))
                    .doesNotContain("nunca-dicto");

            assertThat(ids(evaluar(conOperaciones(clases, List.of(), Map.of(OrigenOperacion.LIENZO, 5L)))))
                    .contains("nunca-dicto");

            // Y si ya dicto, se calla.
            assertThat(ids(evaluar(conOperaciones(clases, List.of(),
                    Map.of(OrigenOperacion.LIENZO, 5L, OrigenOperacion.VOZ, 2L)))))
                    .doesNotContain("nunca-dicto");
        }

        @Test
        @DisplayName("avisa que el modelo ya alcanza para generar el backend")
        void listoParaGenerar() {
            assertThat(ids(evaluar(observacion(
                    List.of(clase("Paciente", "nombre:String"), clase("Consulta", "fecha:Date")),
                    List.of()))))
                    .contains("listo-para-generar");

            // Con clases pero sin atributos, generar no tendria sentido todavia.
            assertThat(ids(evaluar(observacion(List.of(clase("Paciente"), clase("Consulta")), List.of()))))
                    .doesNotContain("listo-para-generar");
        }

        @Test
        @DisplayName("invita a colaborar solo si esta solo en el proyecto")
        void invitaACollaborar() {
            List<ClaseUml> clases = List.of(clase("Paciente", "nombre:String"), clase("Consulta"));

            assertThat(ids(evaluar(new Observacion(UUID.randomUUID(), "D", clases, List.of(), 1,
                    Map.of())))).contains("proyecto-de-uno");
            assertThat(ids(evaluar(new Observacion(UUID.randomUUID(), "D", clases, List.of(), 2,
                    Map.of())))).doesNotContain("proyecto-de-uno");
        }
    }

    // ---------- Modelo ------------------------------------------------------

    @Nested
    @DisplayName("Senalar lo que afecta al codigo generado")
    class Modelo {

        @Test
        @DisplayName("una clase sin atributos se avisa con su consecuencia")
        void claseSinAtributos() {
            ClaseUml vacia = clase("Paciente");
            List<Consejo> consejos = evaluar(observacion(List.of(vacia, clase("Consulta", "fecha:Date")),
                    List.of()));

            Consejo consejo = buscar(consejos, "clase-sin-atributos:" + vacia.getId());
            assertThat(consejo.elementoId())
                    .as("el lienzo tiene que poder senalar de que clase habla")
                    .isEqualTo(vacia.getId());
            assertThat(consejo.porQueImporta()).contains("una sola columna");
        }

        @Test
        @DisplayName("no se le exigen atributos a una interfaz ni a una clase abstracta")
        void noExigeAtributosDondeNoCorresponde() {
            ClaseUml interfaz = clase("Auditable");
            interfaz.setEstereotipo("interface");
            ClaseUml abstracta = clase("Persona");
            abstracta.setEsAbstracta(true);

            List<String> ids = ids(evaluar(observacion(List.of(interfaz, abstracta), List.of())));
            assertThat(ids).doesNotContain("clase-sin-atributos:" + interfaz.getId());
            assertThat(ids).doesNotContain("clase-sin-atributos:" + abstracta.getId());
        }

        @Test
        @DisplayName("una clase que no se relaciona con nada se senala")
        void claseAislada() {
            ClaseUml paciente = clase("Paciente", "nombre:String");
            ClaseUml consulta = clase("Consulta", "fecha:Date");
            ClaseUml suelta = clase("Auditoria", "detalle:String");

            List<String> ids = ids(evaluar(observacion(
                    List.of(paciente, consulta, suelta),
                    List.of(relacion(paciente, consulta, TipoRelacion.ASOCIACION)))));

            assertThat(ids).contains("clase-aislada:" + suelta.getId());
            assertThat(ids).doesNotContain("clase-aislada:" + paciente.getId());
            assertThat(ids).doesNotContain("clase-aislada:" + consulta.getId());
        }

        @Test
        @DisplayName("una interfaz con atributos es lo mas urgente que puede avisar")
        void interfazConAtributos() {
            ClaseUml interfaz = clase("Auditable", "detalle:String");
            interfaz.setEstereotipo("interface");

            List<Consejo> consejos = evaluar(observacion(List.of(interfaz), List.of()));
            Consejo consejo = buscar(consejos, "interfaz-con-atributos:" + interfaz.getId());

            assertThat(consejo.porQueImporta())
                    .as("tiene que decir que el codigo generado pierde esos atributos")
                    .contains("se pierden");
            // Rompe lo generado: tiene que ir antes que cualquier consejo de estilo.
            assertThat(consejo.prioridad()).isGreaterThanOrEqualTo(90);
        }

        @Test
        @DisplayName("no reclama clave a una clase que la hereda")
        void laClaveHeredadaNoSeReclama() {
            ClaseUml persona = clase("Persona", "nombre:String");
            ClaseUml paciente = clase("Paciente", "historia:String");

            List<String> ids = ids(evaluar(observacion(List.of(persona, paciente),
                    List.of(relacion(paciente, persona, TipoRelacion.HERENCIA)))));

            assertThat(ids).doesNotContain("sin-clave-propia:" + paciente.getId());
            assertThat(ids).contains("sin-clave-propia:" + persona.getId());
        }

        @Test
        @DisplayName("si el atributo ya esta marcado como clave no dice nada")
        void conClaveNoAvisa() {
            ClaseUml paciente = clase("Paciente", "historia:String:clave");
            assertThat(ids(evaluar(observacion(List.of(paciente), List.of()))))
                    .doesNotContain("sin-clave-propia:" + paciente.getId());
        }
    }

    // ---------- Diseno ------------------------------------------------------

    @Nested
    @DisplayName("Sugerir mejoras que el modelo deja ver")
    class Diseno {

        @Test
        @DisplayName("una raiz con dos subclases se propone abstracta")
        void raizSinAbstracta() {
            ClaseUml persona = clase("Persona", "nombre:String");
            ClaseUml paciente = clase("Paciente", "historia:String");
            ClaseUml medico = clase("Medico", "matricula:String");

            List<String> ids = ids(evaluar(observacion(List.of(persona, paciente, medico), List.of(
                    relacion(paciente, persona, TipoRelacion.HERENCIA),
                    relacion(medico, persona, TipoRelacion.HERENCIA)))));

            assertThat(ids).contains("raiz-sin-abstracta:" + persona.getId());
        }

        @Test
        @DisplayName("con una sola subclase no lo propone, y tampoco si ya es abstracta")
        void noProponeAbstractaSinMotivo() {
            ClaseUml persona = clase("Persona", "nombre:String");
            ClaseUml paciente = clase("Paciente", "historia:String");

            assertThat(ids(evaluar(observacion(List.of(persona, paciente),
                    List.of(relacion(paciente, persona, TipoRelacion.HERENCIA))))))
                    .doesNotContain("raiz-sin-abstracta:" + persona.getId());

            ClaseUml yaAbstracta = clase("Persona", "nombre:String");
            yaAbstracta.setEsAbstracta(true);
            ClaseUml medico = clase("Medico", "matricula:String");
            assertThat(ids(evaluar(observacion(List.of(yaAbstracta, paciente, medico), List.of(
                    relacion(paciente, yaAbstracta, TipoRelacion.HERENCIA),
                    relacion(medico, yaAbstracta, TipoRelacion.HERENCIA))))))
                    .doesNotContain("raiz-sin-abstracta:" + yaAbstracta.getId());
        }

        @Test
        @DisplayName("dos clases que repiten atributos sugieren una superclase")
        void atributosRepetidos() {
            // Es la unica regla que infiere algo que no esta dibujado.
            ClaseUml paciente = clase("Paciente", "nombre:String", "fechaNacimiento:Date",
                    "historia:String");
            ClaseUml medico = clase("Medico", "nombre:String", "fechaNacimiento:Date",
                    "matricula:String");

            List<Consejo> consejos = evaluar(observacion(List.of(paciente, medico), List.of()));
            Consejo consejo = buscar(consejos, "atributos-repetidos:" + paciente.getId()
                    + ":" + medico.getId());

            assertThat(consejo.queNote()).contains("nombre").contains("fechanacimiento");
            assertThat(consejo.porQueImporta()).contains("superclase");
        }

        @Test
        @DisplayName("un solo atributo repetido no alcanza para inferir nada")
        void unSoloRepetidoNoAlcanza() {
            ClaseUml paciente = clase("Paciente", "nombre:String", "historia:String");
            ClaseUml medico = clase("Medico", "nombre:String", "matricula:String");

            assertThat(ids(evaluar(observacion(List.of(paciente, medico), List.of()))))
                    .noneMatch(id -> id.startsWith("atributos-repetidos"));
        }

        @Test
        @DisplayName("si ya comparten jerarquia no sugiere extraer nada")
        void laJerarquiaExistenteCalla() {
            ClaseUml persona = clase("Persona", "nombre:String", "fechaNacimiento:Date");
            ClaseUml paciente = clase("Paciente", "nombre:String", "fechaNacimiento:Date");

            assertThat(ids(evaluar(observacion(List.of(persona, paciente),
                    List.of(relacion(paciente, persona, TipoRelacion.HERENCIA))))))
                    .noneMatch(id -> id.startsWith("atributos-repetidos"));
        }

        @Test
        @DisplayName("un atributo que la subclase vuelve a declarar se senala")
        void atributoYaHeredado() {
            ClaseUml persona = clase("Persona", "nombre:String");
            ClaseUml paciente = clase("Paciente", "nombre:String", "historia:String");

            List<Consejo> consejos = evaluar(observacion(List.of(persona, paciente),
                    List.of(relacion(paciente, persona, TipoRelacion.HERENCIA))));

            Consejo consejo = buscar(consejos, "atributo-ya-heredado:" + paciente.getId());
            assertThat(consejo.queNote()).contains("nombre").contains("Persona");
            assertThat(consejo.porQueImporta()).contains("dos veces");
        }

        @Test
        @DisplayName("reconoce un nombre en plural sin equivocarse con los que terminan en s")
        void nombreEnPlural() {
            ClaseUml plural = clase("Consultas", "fecha:Date");
            assertThat(ids(evaluar(observacion(List.of(plural), List.of()))))
                    .contains("nombre-en-plural:" + plural.getId());

            // Analisis y Campus terminan en s y son singulares: un consejo
            // equivocado sobre el nombre resta credito a todos los demas.
            // Solo -is y -us son terminaciones singulares fiables. "Estres" queda
            // fuera del alcance a proposito: por eso el consejo se redacta como
            // condicion y no como afirmacion.
            for (String singular : List.of("Analisis", "Campus", "Virus")) {
                ClaseUml clase = clase(singular, "dato:String");
                assertThat(ids(evaluar(observacion(List.of(clase), List.of()))))
                        .as("no debe marcar " + singular)
                        .doesNotContain("nombre-en-plural:" + clase.getId());
            }
        }
    }

    // ---------- Auxiliares ---------------------------------------------------

    private List<Consejo> evaluar(Observacion observacion) {
        return base.reglas().stream()
                .flatMap(regla -> regla.evaluar(observacion).stream())
                .sorted((a, b) -> Integer.compare(b.prioridad(), a.prioridad()))
                .toList();
    }

    private List<String> ids(List<Consejo> consejos) {
        return consejos.stream().map(Consejo::id).toList();
    }

    private Consejo buscar(List<Consejo> consejos, String id) {
        return consejos.stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no se emitio el consejo " + id + "; se emitieron " + ids(consejos)));
    }

    private Observacion observacion(List<ClaseUml> clases, List<RelacionUml> relaciones) {
        // Dos miembros por omision: asi la regla de invitar no aparece en las
        // pruebas que no la estan mirando.
        return new Observacion(UUID.randomUUID(), "Diagrama", clases, relaciones, 2,
                new EnumMap<>(OrigenOperacion.class));
    }

    private Observacion conOperaciones(List<ClaseUml> clases, List<RelacionUml> relaciones,
                                       Map<OrigenOperacion, Long> operaciones) {
        return new Observacion(UUID.randomUUID(), "Diagrama", clases, relaciones, 2, operaciones);
    }

    /** Construye una clase en memoria. Cada atributo es "nombre:tipo[:clave]". */
    private ClaseUml clase(String nombre, String... atributos) {
        ClaseUml clase = new ClaseUml();
        clase.setNombre(nombre);
        clase.setAtributos(new ArrayList<>());

        int orden = 0;
        for (String declarado : atributos) {
            String[] partes = declarado.split(":");
            AtributoUml atributo = new AtributoUml();
            atributo.setClase(clase);
            atributo.setNombre(partes[0]);
            atributo.setTipo(partes.length > 1 ? partes[1] : "String");
            atributo.setEsIdentificador(partes.length > 2 && partes[2].equals("clave"));
            atributo.setVisibilidad(Visibilidad.PRIVADO);
            atributo.setOrden(orden++);
            clase.getAtributos().add(atributo);
        }
        return clase;
    }

    private RelacionUml relacion(ClaseUml origen, ClaseUml destino, TipoRelacion tipo) {
        RelacionUml relacion = new RelacionUml();
        relacion.setOrigen(origen);
        relacion.setDestino(destino);
        relacion.setTipo(tipo);
        return relacion;
    }
}
