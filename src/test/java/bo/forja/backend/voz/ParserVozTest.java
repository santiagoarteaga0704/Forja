package bo.forja.backend.voz;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.TipoOperacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de la gramatica del dictado.
 * <p>
 * Es una prueba unitaria sin contexto de Spring ni base de datos: el parser no
 * depende de nada mas que de la frase y de los nombres de las clases que
 * existen. Eso permite cubrir muchas maneras de decir lo mismo por muy poco
 * costo, que es exactamente lo que hace falta aqui, porque el riesgo de un
 * parser no esta en la frase que su autor probo sino en las veinte variantes
 * que no se le ocurrieron.
 * <p>
 * Los casos que mas importan son los de ambigueda: "Paciente tiene muchas
 * Consultas" tiene que ser una relacion y "Paciente tiene un nombre de tipo
 * texto" un atributo, y las dos frases empiezan igual.
 */
@DisplayName("Dictado por voz")
class ParserVozTest {

    private final ParserVoz parser = new ParserVoz();

    private static final UUID ID_PACIENTE = UUID.randomUUID();
    private static final UUID ID_CONSULTA = UUID.randomUUID();
    private static final UUID ID_PERSONA = UUID.randomUUID();
    private static final UUID ID_MEDICO = UUID.randomUUID();
    private static final UUID ID_AUDITABLE = UUID.randomUUID();

    /** Diagrama de referencia: es el contexto contra el que se interpreta. */
    private final ContextoVoz contexto = ContextoVoz.de(List.of(
            new ContextoVoz.ClaseConocida(ID_PACIENTE, "Paciente"),
            new ContextoVoz.ClaseConocida(ID_CONSULTA, "Consulta"),
            new ContextoVoz.ClaseConocida(ID_PERSONA, "Persona"),
            new ContextoVoz.ClaseConocida(ID_MEDICO, "Medico"),
            new ContextoVoz.ClaseConocida(ID_AUDITABLE, "Auditable")));

    // ---------- Clases ------------------------------------------------------

    @Nested
    @DisplayName("Crear clases")
    class CrearClases {

        @Test
        @DisplayName("reconoce las formas usuales de pedir una clase nueva")
        void variasFormasDeCrear() {
            for (String frase : List.of(
                    "crea la clase Factura",
                    "crear clase Factura",
                    "nueva clase Factura",
                    "agrega una clase llamada Factura",
                    "creame una clase Factura",
                    "dibuja la clase Factura")) {

                ComandoOperacion.CrearClase comando = unico(frase, ComandoOperacion.CrearClase.class);
                assertThat(comando.nombre()).as("con la frase: " + frase).isEqualTo("Factura");
                assertThat(comando.esAbstracta()).isFalse();
                assertThat(comando.estereotipo()).isNull();
            }
        }

        @Test
        @DisplayName("un nombre de varias palabras se junta en camello")
        void nombreDeVariasPalabras() {
            // Dictando no se puede pronunciar la mayuscula intermedia.
            ComandoOperacion.CrearClase comando =
                    unico("crea la clase historia clinica", ComandoOperacion.CrearClase.class);
            assertThat(comando.nombre()).isEqualTo("historiaClinica");
        }

        @Test
        @DisplayName("tolera acentos y la puntuacion que agrega el reconocedor de voz")
        void aguantaAcentosYPuntuacion() {
            ComandoOperacion.CrearClase comando =
                    unico("creá la clase Médico.", ComandoOperacion.CrearClase.class);
            assertThat(comando.nombre()).isEqualTo("Medico");
        }

        @Test
        @DisplayName("una interfaz se crea con su estereotipo")
        void crearInterfaz() {
            ComandoOperacion.CrearClase comando =
                    unico("crea la interfaz Auditable", ComandoOperacion.CrearClase.class);
            assertThat(comando.nombre()).isEqualTo("Auditable");
            assertThat(comando.estereotipo()).isEqualTo("interface");
        }

        @Test
        @DisplayName("una frase puede crear la clase y sus atributos de una vez")
        void crearConAtributos() {
            Interpretacion resultado = parser.interpretar(
                    "crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal",
                    contexto);

            assertThat(resultado.entendida()).isTrue();
            assertThat(resultado.pasos()).hasSize(3);
            assertThat(resultado.pasos().get(0).tipo()).isEqualTo(TipoOperacion.CLASE_CREAR);
            assertThat(resultado.pasos().get(1).tipo()).isEqualTo(TipoOperacion.ATRIBUTO_AGREGAR);
            assertThat(resultado.pasos().get(2).tipo()).isEqualTo(TipoOperacion.ATRIBUTO_AGREGAR);

            // Los atributos deben quedar colgados de la clase que se acaba de crear.
            UUID claseId = ((ComandoOperacion.CrearClase) resultado.pasos().get(0).comando()).claseId();
            ComandoOperacion.AgregarAtributo primero =
                    (ComandoOperacion.AgregarAtributo) resultado.pasos().get(1).comando();
            assertThat(primero.claseId()).isEqualTo(claseId);
            assertThat(primero.nombre()).isEqualTo("numero");
            assertThat(primero.tipo()).isEqualTo("String");

            ComandoOperacion.AgregarAtributo segundo =
                    (ComandoOperacion.AgregarAtributo) resultado.pasos().get(2).comando();
            assertThat(segundo.nombre()).isEqualTo("total");
            assertThat(segundo.tipo()).isEqualTo("Decimal");
        }

        @Test
        @DisplayName("renombrar y eliminar resuelven la clase contra el diagrama")
        void renombrarYEliminar() {
            ComandoOperacion.RenombrarClase renombre =
                    unico("renombra Paciente a Interno", ComandoOperacion.RenombrarClase.class);
            assertThat(renombre.claseId()).isEqualTo(ID_PACIENTE);
            assertThat(renombre.nombre()).isEqualTo("Interno");

            ComandoOperacion.EliminarClase baja =
                    unico("elimina la clase Consulta", ComandoOperacion.EliminarClase.class);
            assertThat(baja.claseId()).isEqualTo(ID_CONSULTA);
        }

        @Test
        @DisplayName("se puede marcar abstracta de forma imperativa o declarativa")
        void marcarAbstracta() {
            for (String frase : List.of("marca Persona como abstracta", "Persona es abstracta",
                    "declara la clase Persona como abstracta")) {
                ComandoOperacion.MarcarClase comando =
                        unico(frase, ComandoOperacion.MarcarClase.class);
                assertThat(comando.claseId()).as("con la frase: " + frase).isEqualTo(ID_PERSONA);
                assertThat(comando.esAbstracta()).isTrue();
            }
        }
    }

    // ---------- Atributos ---------------------------------------------------

    @Nested
    @DisplayName("Atributos")
    class Atributos {

        @Test
        @DisplayName("acepta el destino al principio o al final de la frase")
        void dosOrdenes() {
            ComandoOperacion.AgregarAtributo conDestinoPrimero = unico(
                    "a Paciente agregale el atributo nombre de tipo texto",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(conDestinoPrimero.claseId()).isEqualTo(ID_PACIENTE);
            assertThat(conDestinoPrimero.nombre()).isEqualTo("nombre");
            assertThat(conDestinoPrimero.tipo()).isEqualTo("String");

            ComandoOperacion.AgregarAtributo conDestinoUltimo = unico(
                    "agrega el atributo edad de tipo entero a Paciente",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(conDestinoUltimo.claseId()).isEqualTo(ID_PACIENTE);
            assertThat(conDestinoUltimo.nombre()).isEqualTo("edad");
            assertThat(conDestinoUltimo.tipo()).isEqualTo("Integer");
        }

        @Test
        @DisplayName("la forma declarativa tambien se entiende")
        void formaDeclarativa() {
            ComandoOperacion.AgregarAtributo comando = unico(
                    "el Paciente tiene un telefono de tipo texto",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(comando.claseId()).isEqualTo(ID_PACIENTE);
            assertThat(comando.nombre()).isEqualTo("telefono");
        }

        @Test
        @DisplayName("un nombre de varias palabras no se corta al declarar el tipo")
        void nombreDeVariasPalabrasConTipo() {
            // Aparecio dictando de verdad: el nombre quedaba en "fecha" y el tipo
            // se perdia, porque el final de la expresion se tragaba el resto.
            ComandoOperacion.AgregarAtributo comando = unico(
                    "a Paciente agregale el atributo fecha de nacimiento de tipo fecha",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(comando.nombre()).isEqualTo("fechaDeNacimiento");
            assertThat(comando.tipo()).isEqualTo("Date");
        }

        @Test
        @DisplayName("sin tipo, el nombre llega hasta la primera palabra de marca")
        void nombreDeVariasPalabrasSinTipo() {
            ComandoOperacion.AgregarAtributo comando = unico(
                    "a Paciente agregale el atributo historia clinica clave",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(comando.nombre()).isEqualTo("historiaClinica");
            assertThat(comando.esIdentificador()).isTrue();
            assertThat(comando.tipo()).as("sin tipo declarado se asume texto").isEqualTo("String");
        }

        @Test
        @DisplayName("el articulo una no se parte en un mas a")
        void elArticuloNoSeParte() {
            // "una Consulta" se leia como el articulo "un" mas la clase "a
            // Consulta", que no existe, y la frase se rechazaba.
            ComandoOperacion.AgregarAtributo comando = unico(
                    "una Consulta tiene un importe de tipo decimal",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(comando.claseId()).isEqualTo(ID_CONSULTA);
            assertThat(comando.nombre()).isEqualTo("importe");
            assertThat(comando.tipo()).isEqualTo("Decimal");
        }

        @Test
        @DisplayName("reconoce clave, obligatorio, unico y longitud")
        void marcasDeAlmacenamiento() {
            ComandoOperacion.AgregarAtributo clave = unico(
                    "a Paciente agregale el atributo codigo de tipo texto clave de longitud 20",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(clave.esIdentificador()).isTrue();
            assertThat(clave.longitud()).isEqualTo(20);
            // Una clave es obligatoria y unica por definicion, aunque no se diga.
            assertThat(clave.esRequerido()).isTrue();
            assertThat(clave.esUnico()).isTrue();

            ComandoOperacion.AgregarAtributo obligatorio = unico(
                    "a Paciente agregale el atributo nombre de tipo texto obligatorio",
                    ComandoOperacion.AgregarAtributo.class);
            assertThat(obligatorio.esRequerido()).isTrue();
            assertThat(obligatorio.esIdentificador()).isFalse();
            assertThat(obligatorio.esUnico()).isFalse();
        }

        @Test
        @DisplayName("los tipos dichos en castellano se normalizan")
        void tiposEnCastellano() {
            assertThat(tipoDictado("de tipo texto")).isEqualTo("String");
            assertThat(tipoDictado("de tipo entero")).isEqualTo("Integer");
            assertThat(tipoDictado("de tipo importe")).isEqualTo("Decimal");
            assertThat(tipoDictado("de tipo booleano")).isEqualTo("Boolean");
            assertThat(tipoDictado("de tipo fecha")).isEqualTo("Date");
            assertThat(tipoDictado("de tipo fechayhora")).isEqualTo("DateTime");
            // Un tipo desconocido se respeta: puede ser otra clase o un enumerado.
            assertThat(tipoDictado("de tipo EstadoCivil")).isEqualTo("EstadoCivil");
        }

        private String tipoDictado(String sufijo) {
            return unico("a Paciente agregale el atributo campo " + sufijo,
                    ComandoOperacion.AgregarAtributo.class).tipo();
        }
    }

    // ---------- Operaciones -------------------------------------------------

    @Nested
    @DisplayName("Operaciones")
    class Operaciones {

        @Test
        @DisplayName("reconoce el nombre y el tipo de retorno")
        void metodoSimple() {
            ComandoOperacion.AgregarMetodo comando = unico(
                    "a Paciente agregale el metodo calcularEdad que devuelve entero",
                    ComandoOperacion.AgregarMetodo.class);
            assertThat(comando.claseId()).isEqualTo(ID_PACIENTE);
            assertThat(comando.nombre()).isEqualTo("calcularEdad");
            assertThat(comando.tipoRetorno()).isEqualTo("Integer");
            assertThat(comando.parametrosOVacio()).isEmpty();
        }

        @Test
        @DisplayName("sin retorno declarado la operacion es void")
        void metodoSinRetorno() {
            ComandoOperacion.AgregarMetodo comando = unico(
                    "a Paciente agregale la operacion internar",
                    ComandoOperacion.AgregarMetodo.class);
            assertThat(comando.nombre()).isEqualTo("internar");
            assertThat(comando.tipoRetorno()).isEqualTo("void");
        }

        @Test
        @DisplayName("reconoce la firma completa con parametros")
        void metodoConParametros() {
            ComandoOperacion.AgregarMetodo comando = unico(
                    "a Paciente agregale el metodo registrarConsulta con parametros motivo de tipo "
                            + "texto y monto de tipo importe que devuelve booleano",
                    ComandoOperacion.AgregarMetodo.class);

            assertThat(comando.nombre()).isEqualTo("registrarConsulta");
            assertThat(comando.tipoRetorno()).isEqualTo("Boolean");
            assertThat(comando.parametrosOVacio())
                    .extracting(ComandoOperacion.Parametro::nombre, ComandoOperacion.Parametro::tipo)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("motivo", "String"),
                            org.assertj.core.groups.Tuple.tuple("monto", "Decimal"));
        }
    }

    // ---------- Relaciones --------------------------------------------------

    @Nested
    @DisplayName("Relaciones")
    class Relaciones {

        @Test
        @DisplayName("una asociacion con muchos lleva multiplicidad cero o mas")
        void asociacionConMuchos() {
            ComandoOperacion.CrearRelacion comando = unico(
                    "un Paciente tiene muchas Consultas", ComandoOperacion.CrearRelacion.class);

            assertThat(comando.tipo()).isEqualTo(TipoRelacion.ASOCIACION);
            assertThat(comando.origenId()).isEqualTo(ID_PACIENTE);
            assertThat(comando.destinoId()).isEqualTo(ID_CONSULTA);
            assertThat(comando.multiplicidadOrigen()).isEqualTo("1");
            // "muchas" se lee como cero o mas: es la lectura que no inventa una
            // restriccion que nadie pidio.
            assertThat(comando.multiplicidadDestino()).isEqualTo("0..*");
        }

        @Test
        @DisplayName("al menos una se distingue de muchas")
        void asociacionConAlMenosUna() {
            ComandoOperacion.CrearRelacion comando = unico(
                    "un Paciente tiene al menos una Consulta", ComandoOperacion.CrearRelacion.class);
            assertThat(comando.multiplicidadDestino()).isEqualTo("1..*");
        }

        @Test
        @DisplayName("el plural dictado resuelve la clase en singular")
        void resuelvePlural() {
            // Se dice "muchos Medicos" pero la clase se llama Medico.
            ComandoOperacion.CrearRelacion comando = unico(
                    "una Consulta tiene muchos Medicos", ComandoOperacion.CrearRelacion.class);
            assertThat(comando.destinoId()).isEqualTo(ID_MEDICO);
        }

        @Test
        @DisplayName("cada tipo de relacion tiene su forma de decirse")
        void todosLosTipos() {
            assertThat(unico("Paciente hereda de Persona", ComandoOperacion.CrearRelacion.class).tipo())
                    .isEqualTo(TipoRelacion.HERENCIA);
            assertThat(unico("Medico implementa Auditable", ComandoOperacion.CrearRelacion.class).tipo())
                    .isEqualTo(TipoRelacion.REALIZACION);
            assertThat(unico("Paciente se compone de muchas Consultas",
                    ComandoOperacion.CrearRelacion.class).tipo())
                    .isEqualTo(TipoRelacion.COMPOSICION);
            assertThat(unico("Medico agrupa muchas Consultas",
                    ComandoOperacion.CrearRelacion.class).tipo())
                    .isEqualTo(TipoRelacion.AGREGACION);
            assertThat(unico("Consulta depende de Persona", ComandoOperacion.CrearRelacion.class).tipo())
                    .isEqualTo(TipoRelacion.DEPENDENCIA);
        }

        @Test
        @DisplayName("la herencia apunta de la subclase a la superclase")
        void sentidoDeLaHerencia() {
            ComandoOperacion.CrearRelacion comando = unico(
                    "Paciente hereda de Persona", ComandoOperacion.CrearRelacion.class);
            // El generador lee el origen como la subclase: si se invirtiera, la
            // jerarquia saldria al reves en el codigo.
            assertThat(comando.origenId()).isEqualTo(ID_PACIENTE);
            assertThat(comando.destinoId()).isEqualTo(ID_PERSONA);
        }
    }

    // ---------- Ambigueda y errores -----------------------------------------

    @Nested
    @DisplayName("Ambigueda y frases que no se entienden")
    class Ambigueda {

        @Test
        @DisplayName("tiene muchas Consultas es una relacion; tiene un nombre es un atributo")
        void elMismoVerboSegunElContexto() {
            // Las dos frases empiezan igual. Lo que las separa es si lo que sigue
            // al verbo es una clase del diagrama.
            assertThat(parser.interpretar("Paciente tiene muchas Consultas", contexto)
                    .pasos().get(0).tipo())
                    .isEqualTo(TipoOperacion.RELACION_CREAR);

            assertThat(parser.interpretar("Paciente tiene un peso de tipo decimal", contexto)
                    .pasos().get(0).tipo())
                    .isEqualTo(TipoOperacion.ATRIBUTO_AGREGAR);
        }

        @Test
        @DisplayName("nombrar una clase que no existe se explica, no se traga")
        void claseDesconocida() {
            Interpretacion resultado = parser.interpretar(
                    "a Factura agregale el atributo total de tipo decimal", contexto);

            assertThat(resultado.entendida()).isFalse();
            assertThat(resultado.sugerencias())
                    .as("debe decir cual es el nombre que no reconoce")
                    .anyMatch(s -> s.contains("Factura"))
                    .as("y ofrecer el camino para arreglarlo")
                    .anyMatch(s -> s.contains("crea la clase"));
            assertThat(resultado.sugerencias())
                    .anyMatch(s -> s.contains("Paciente") && s.contains("Consulta"));
        }

        @Test
        @DisplayName("una frase sin sentido devuelve ejemplos de lo que si funciona")
        void fraseSinSentido() {
            Interpretacion resultado = parser.interpretar("hola que tal como andas", contexto);

            assertThat(resultado.entendida()).isFalse();
            assertThat(resultado.pasos()).isEmpty();
            // No alcanza con decir que no se entendio: hay que ensenar la forma.
            assertThat(resultado.sugerencias()).isNotEmpty()
                    .anyMatch(s -> s.startsWith("crea la clase"));
        }

        @Test
        @DisplayName("una frase vacia no revienta")
        void fraseVacia() {
            assertThat(parser.interpretar("", contexto).entendida()).isFalse();
            assertThat(parser.interpretar("   ", contexto).entendida()).isFalse();
            assertThat(parser.interpretar(null, contexto).entendida()).isFalse();
        }

        @Test
        @DisplayName("sobre un diagrama vacio se puede crear pero no referenciar")
        void diagramaVacio() {
            ContextoVoz vacio = ContextoVoz.vacio();

            assertThat(parser.interpretar("crea la clase Paciente", vacio).entendida()).isTrue();
            assertThat(parser.interpretar("a Paciente agregale el atributo nombre de tipo texto",
                    vacio).entendida()).isFalse();
        }

        @Test
        @DisplayName("la misma frase produce siempre la misma interpretacion")
        void esDeterminista() {
            String frase = "a Paciente agregale el atributo nombre de tipo texto obligatorio";
            ComandoOperacion.AgregarAtributo primera =
                    unico(frase, ComandoOperacion.AgregarAtributo.class);
            ComandoOperacion.AgregarAtributo segunda =
                    unico(frase, ComandoOperacion.AgregarAtributo.class);

            // Los identificadores son nuevos en cada interpretacion -son de
            // elementos nuevos-, pero todo lo demas tiene que coincidir.
            assertThat(segunda.claseId()).isEqualTo(primera.claseId());
            assertThat(segunda.nombre()).isEqualTo(primera.nombre());
            assertThat(segunda.tipo()).isEqualTo(primera.tipo());
            assertThat(segunda.esRequerido()).isEqualTo(primera.esRequerido());
        }
    }

    // ---------- Auxiliar ----------------------------------------------------

    /** Interpreta la frase y devuelve su unico comando, ya con el tipo esperado. */
    private <T extends ComandoOperacion> T unico(String frase, Class<T> esperado) {
        Interpretacion resultado = parser.interpretar(frase, contexto);
        assertThat(resultado.entendida())
                .as("no se entendio: \"" + frase + "\" (sugerencias: " + resultado.sugerencias() + ")")
                .isTrue();
        assertThat(resultado.pasos()).as("con la frase: " + frase).hasSize(1);
        assertThat(resultado.pasos().get(0).comando()).isInstanceOf(esperado);
        return esperado.cast(resultado.pasos().get(0).comando());
    }
}
