package bo.forja.backend.foto;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verificacion de la lectura de una pizarra.
 * <p>
 * Es unitaria y sin base de datos: el parser recibe texto y los nombres de las
 * clases que ya existen, nada mas. Eso permite cubrir muchas formas de escribir
 * el mismo diagrama, que es donde esta el riesgo real: no en la pizarra que su
 * autor imagino, sino en las que dibuja cualquier otra persona.
 * <p>
 * Un grupo de casos merece atencion aparte: el <b>ruido del reconocimiento de
 * caracteres</b>. Sobre letra manuscrita, un uno sale como ele y un guion largo
 * reemplaza al corto. Si esas confusiones rompieran la lectura, la funcion no
 * serviria en la unica situacion para la que existe.
 */
@DisplayName("Lectura de pizarra")
class ParserPizarraTest {

    private final ParserPizarra parser = new ParserPizarra();
    private final ContextoDelDiagrama vacio = ContextoDelDiagrama.vacio();

    // ---------- Estructura de los recuadros ---------------------------------

    @Nested
    @DisplayName("Recuadros de clase")
    class Recuadros {

        @Test
        @DisplayName("lee un recuadro completo con sus tres compartimientos")
        void recuadroCompleto() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    -----------------
                    - historiaClinica: String {PK} (20)
                    + nombre: String *
                    - fechaNacimiento: Date
                    -----------------
                    + calcularEdad(): int
                    """, vacio);

            assertThat(lectura.ignoradas()).isEmpty();
            assertThat(lectura.clases()).singleElement().satisfies(clase -> {
                assertThat(clase.nombre()).isEqualTo("Paciente");
                assertThat(clase.atributos()).isEqualTo(3);
                assertThat(clase.operaciones()).isEqualTo(1);
                assertThat(clase.yaExistia()).isFalse();
            });

            List<ComandoOperacion.AgregarAtributo> atributos =
                    de(lectura, ComandoOperacion.AgregarAtributo.class);
            assertThat(atributos).hasSize(3);

            ComandoOperacion.AgregarAtributo clave = atributos.get(0);
            assertThat(clave.nombre()).isEqualTo("historiaClinica");
            assertThat(clave.tipo()).isEqualTo("String");
            assertThat(clave.esIdentificador()).isTrue();
            assertThat(clave.esUnico()).isTrue();
            assertThat(clave.longitud()).isEqualTo(20);
            assertThat(clave.visibilidad()).isEqualTo(Visibilidad.PRIVADO);

            ComandoOperacion.AgregarAtributo obligatorio = atributos.get(1);
            assertThat(obligatorio.visibilidad()).isEqualTo(Visibilidad.PUBLICO);
            assertThat(obligatorio.esRequerido())
                    .as("el asterisco es la marca usual de obligatorio")
                    .isTrue();

            assertThat(atributos.get(2).tipo()).isEqualTo("Date");

            ComandoOperacion.AgregarMetodo operacion =
                    de(lectura, ComandoOperacion.AgregarMetodo.class).get(0);
            assertThat(operacion.nombre()).isEqualTo("calcularEdad");
            assertThat(operacion.tipoRetorno()).isEqualTo("Integer");
        }

        @Test
        @DisplayName("sin separadores, la linea en blanco es la que cierra el recuadro")
        void sinSeparadores() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre: String
                    edad: int

                    Consulta
                    fecha: Date
                    motivo: String
                    """, vacio);

            assertThat(lectura.ignoradas()).isEmpty();
            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::nombre)
                    .containsExactly("Paciente", "Consulta");
            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::atributos)
                    .containsExactly(2, 2);
        }

        @Test
        @DisplayName("un atributo sin tipo se asume texto, y la mayuscula marca una clase nueva")
        void atributosSinTipo() {
            // Sin dos puntos, la convencion de UML es lo unico que distingue el
            // nombre de una clase del de un atributo.
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre
                    telefono

                    Consulta
                    motivo
                    """, vacio);

            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::nombre)
                    .containsExactly("Paciente", "Consulta");
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class))
                    .extracting(ComandoOperacion.AgregarAtributo::nombre)
                    .containsExactly("nombre", "telefono", "motivo");
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class))
                    .allMatch(a -> a.tipo().equals("String"));
        }

        @Test
        @DisplayName("reconoce el estereotipo en su propia linea y en la misma")
        void estereotipos() {
            Lectura enSuLinea = parser.interpretar("""
                    <<interface>>
                    Auditable
                    + registrar(): void
                    """, vacio);
            assertThat(enSuLinea.clases()).singleElement()
                    .satisfies(c -> {
                        assertThat(c.nombre()).isEqualTo("Auditable");
                        assertThat(c.estereotipo()).isEqualTo("interface");
                    });

            Lectura enLaMisma = parser.interpretar("<<interface>> Auditable", vacio);
            assertThat(enLaMisma.clases()).singleElement()
                    .satisfies(c -> assertThat(c.estereotipo()).isEqualTo("interface"));

            // Las comillas francesas son lo que produce el reconocimiento a veces.
            Lectura conComillas = parser.interpretar("«interface» Auditable", vacio);
            assertThat(conComillas.clases()).singleElement()
                    .satisfies(c -> assertThat(c.estereotipo()).isEqualTo("interface"));
        }

        @Test
        @DisplayName("abstract es un modificador, no un estereotipo")
        void abstracta() {
            Lectura lectura = parser.interpretar("""
                    <<abstract>>
                    Persona
                    nombre: String
                    """, vacio);

            assertThat(lectura.clases()).singleElement().satisfies(c -> {
                assertThat(c.esAbstracta()).isTrue();
                assertThat(c.estereotipo())
                        .as("no debe quedar como estereotipo del modelo")
                        .isNull();
            });
            assertThat(de(lectura, ComandoOperacion.CrearClase.class)).singleElement()
                    .satisfies(c -> assertThat(c.esAbstracta()).isTrue());
        }

        @Test
        @DisplayName("una clase que aparece dos veces no se duplica")
        void claseRepetidaEnLaFoto() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre: String

                    Paciente
                    edad: int
                    """, vacio);

            assertThat(de(lectura, ComandoOperacion.CrearClase.class)).hasSize(1);
            assertThat(lectura.clases()).singleElement()
                    .satisfies(c -> assertThat(c.atributos()).isEqualTo(2));
        }
    }

    // ---------- Relaciones ---------------------------------------------------

    @Nested
    @DisplayName("Relaciones")
    class Relaciones {

        @Test
        @DisplayName("cada conector expresa su tipo de relacion")
        void tiposDeConector() {
            assertThat(tipoLeido("Paciente --|> Persona")).isEqualTo(TipoRelacion.HERENCIA);
            assertThat(tipoLeido("Medico ..|> Auditable")).isEqualTo(TipoRelacion.REALIZACION);
            assertThat(tipoLeido("Paciente *-- Consulta")).isEqualTo(TipoRelacion.COMPOSICION);
            assertThat(tipoLeido("Medico o-- Especialidad")).isEqualTo(TipoRelacion.AGREGACION);
            assertThat(tipoLeido("Consulta ..> Persona")).isEqualTo(TipoRelacion.DEPENDENCIA);
            assertThat(tipoLeido("Paciente -- Consulta")).isEqualTo(TipoRelacion.ASOCIACION);
            assertThat(tipoLeido("Paciente --> Consulta")).isEqualTo(TipoRelacion.ASOCIACION);
        }

        @Test
        @DisplayName("la flecha invertida invierte los extremos")
        void conectorInvertido() {
            // Persona <|-- Paciente significa que Paciente hereda de Persona.
            Lectura lectura = parser.interpretar("""
                    Persona
                    Paciente

                    Persona <|-- Paciente
                    """, vacio);

            ComandoOperacion.CrearRelacion relacion =
                    de(lectura, ComandoOperacion.CrearRelacion.class).get(0);
            assertThat(relacion.tipo()).isEqualTo(TipoRelacion.HERENCIA);
            assertThat(nombreDe(lectura, relacion.origenId())).isEqualTo("Paciente");
            assertThat(nombreDe(lectura, relacion.destinoId())).isEqualTo("Persona");
        }

        @Test
        @DisplayName("las multiplicidades se toman del lado que les corresponde")
        void multiplicidades() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    Consulta

                    Paciente 1 -- 0..* Consulta
                    """, vacio);

            ComandoOperacion.CrearRelacion relacion =
                    de(lectura, ComandoOperacion.CrearRelacion.class).get(0);
            assertThat(nombreDe(lectura, relacion.origenId())).isEqualTo("Paciente");
            assertThat(relacion.multiplicidadOrigen()).isEqualTo("1");
            assertThat(relacion.multiplicidadDestino()).isEqualTo("0..*");
        }

        @Test
        @DisplayName("tambien se entienden las relaciones escritas con palabras")
        void conectoresEnPalabras() {
            assertThat(tipoLeido("Paciente hereda de Persona")).isEqualTo(TipoRelacion.HERENCIA);
            assertThat(tipoLeido("Medico implementa Auditable")).isEqualTo(TipoRelacion.REALIZACION);
            assertThat(tipoLeido("Consulta depende de Persona")).isEqualTo(TipoRelacion.DEPENDENCIA);
        }

        @Test
        @DisplayName("una relacion con una clase que no se reconocio se informa")
        void relacionConClaseDesconocida() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre: String

                    Paciente -- Factura
                    """, vacio);

            assertThat(de(lectura, ComandoOperacion.CrearRelacion.class)).isEmpty();
            assertThat(lectura.ignoradas()).anyMatch(i -> i.contains("Factura"));
        }
    }

    // ---------- Ruido del reconocimiento ------------------------------------

    @Nested
    @DisplayName("Tolerancia al reconocimiento de caracteres")
    class Ruido {

        @Test
        @DisplayName("los guiones tipograficos no rompen los conectores")
        void guionesTipograficos() {
            // El reconocimiento produce guion largo con frecuencia; sin
            // normalizarlo, ninguna relacion se leeria.
            assertThat(tipoLeido("Paciente —|> Persona")).isEqualTo(TipoRelacion.HERENCIA);
            assertThat(tipoLeido("Paciente –– Consulta")).isEqualTo(TipoRelacion.ASOCIACION);
        }

        @Test
        @DisplayName("una ele en la multiplicidad se lee como uno")
        void eleEnLaMultiplicidad() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    Consulta

                    Paciente l -- * Consulta
                    """, vacio);

            ComandoOperacion.CrearRelacion relacion =
                    de(lectura, ComandoOperacion.CrearRelacion.class).get(0);
            assertThat(relacion.multiplicidadOrigen()).isEqualTo("1");
            assertThat(relacion.multiplicidadDestino()).isEqualTo("*");
        }

        @Test
        @DisplayName("la correccion de caracteres no toca los nombres")
        void losNombresNoSeTocan() {
            // Si la correccion se aplicara a los nombres, "Local" se convertiria
            // en "1oca1", que es mucho peor que dejar la multiplicidad mal.
            Lectura lectura = parser.interpretar("""
                    Local
                    nombre: String
                    """, vacio);

            assertThat(lectura.clases()).singleElement()
                    .satisfies(c -> assertThat(c.nombre()).isEqualTo("Local"));
        }

        @Test
        @DisplayName("las vinetas y los espacios de sobra se descartan")
        void vinetasYEspacios() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    • nombre: String
                    ·   edad:   int
                    """, vacio);

            assertThat(lectura.ignoradas()).isEmpty();
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class))
                    .extracting(ComandoOperacion.AgregarAtributo::nombre)
                    .containsExactly("nombre", "edad");
        }

        @Test
        @DisplayName("una linea que no se entiende se informa con su numero")
        void lineaIncomprensible() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre: String
                    ###??? %%
                    """, vacio);

            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class)).hasSize(1);
            // No se descarta en silencio: se dice que linea fue y que decia.
            assertThat(lectura.ignoradas()).singleElement()
                    .satisfies(i -> assertThat(i).startsWith("3:"));
        }

        @Test
        @DisplayName("una linea en blanco de mas no separa al atributo de su clase")
        void lineasEnBlancoDentroDelRecuadro() {
            // El reconocimiento intercala lineas vacias entre renglones del
            // mismo recuadro: parte en parrafos lo que en la pizarra era un
            // bloque. Medido sobre una foto nitida, asi salen los atributos.
            Lectura lectura = parser.interpretar("""
                    Persona

                    + nombre: String

                    + ci: String
                    """, vacio);

            assertThat(lectura.ignoradas()).isEmpty();
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class))
                    .extracting(ComandoOperacion.AgregarAtributo::nombre)
                    .containsExactly("nombre", "ci");
        }

        @Test
        @DisplayName("una linea en blanco sigue separando dos clases")
        void lineasEnBlancoEntreRecuadros() {
            // El limite de la tolerancia: si lo que sigue empieza una clase, la
            // linea en blanco significa lo que siempre significo. Sin esto, la
            // segunda clase se leeria como un atributo de la primera.
            Lectura lectura = parser.interpretar("""
                    Persona
                    + nombre: String

                    Medico
                    + matricula: String
                    """, vacio);

            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::nombre)
                    .containsExactly("Persona", "Medico");
            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::atributos)
                    .containsExactly(1, 1);
        }

        @Test
        @DisplayName("lee el texto tal como lo devolvio el reconocimiento")
        void loQueDevolvioElReconocimiento() {
            // Copiado del reconocimiento de verdad, sin retocar una coma: es el
            // unico texto que esta funcion va a recibir alguna vez.
            Lectura lectura = parser.interpretar("""
                    Persona

                    + nombre: String

                    + ci: String

                    Paciente

                    + historiaClinica: String
                    + alergias: String

                    + calcularEdad(): int
                    Medico

                    + especialidad: String

                    + matricula: String
                    Consulta

                    + fecha: String

                    + motivo: String

                    Paciente --|> Persona
                    Medico --|> Persona
                    Paciente 1 *-- 0..* Consulta
                    Medico 1 -- 0..* Consulta
                    """, vacio);

            assertThat(lectura.ignoradas()).isEmpty();
            assertThat(lectura.clases()).extracting(Lectura.ClaseLeida::nombre)
                    .containsExactly("Persona", "Paciente", "Medico", "Consulta");
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class)).hasSize(8);
            assertThat(de(lectura, ComandoOperacion.AgregarMetodo.class)).hasSize(1);
            assertThat(de(lectura, ComandoOperacion.CrearRelacion.class)).hasSize(4);
        }
    }

    // ---------- Contra un diagrama que ya tiene clases -----------------------

    @Nested
    @DisplayName("Sobre un diagrama que ya existe")
    class SobreLoExistente {

        private final UUID idPaciente = UUID.randomUUID();
        private final ContextoDelDiagrama conPaciente = ContextoDelDiagrama.de(List.of(
                new ContextoDelDiagrama.ClaseConocida(idPaciente, "Paciente")));

        @Test
        @DisplayName("una clase que ya existe no se recrea ni se le tocan los miembros")
        void noReescribeLoExistente() {
            Lectura lectura = parser.interpretar("""
                    Paciente
                    nombre: String

                    Consulta
                    fecha: Date
                    """, conPaciente);

            // La foto agrega lo que falta; no reescribe lo que ya esta.
            assertThat(de(lectura, ComandoOperacion.CrearClase.class))
                    .extracting(ComandoOperacion.CrearClase::nombre)
                    .containsExactly("Consulta");
            assertThat(de(lectura, ComandoOperacion.AgregarAtributo.class))
                    .extracting(ComandoOperacion.AgregarAtributo::nombre)
                    .containsExactly("fecha");

            assertThat(lectura.clases())
                    .filteredOn(c -> c.nombre().equals("Paciente"))
                    .singleElement()
                    .satisfies(c -> assertThat(c.yaExistia()).isTrue());
        }

        @Test
        @DisplayName("una relacion ya trazada no se vuelve a trazar")
        void noRepiteRelacionesTrazadas() {
            UUID idConsulta = UUID.randomUUID();
            ContextoDelDiagrama conLasDos = ContextoDelDiagrama.de(
                    List.of(new ContextoDelDiagrama.ClaseConocida(idPaciente, "Paciente"),
                            new ContextoDelDiagrama.ClaseConocida(idConsulta, "Consulta")),
                    java.util.Set.of(ContextoDelDiagrama.claveDeRelacion(
                            idPaciente, idConsulta, "COMPOSICION")));

            // Es la contraparte de no recrear las clases: leer dos veces la misma
            // pizarra no debe dejar el diagrama con la relacion duplicada.
            Lectura lectura = parser.interpretar("Paciente 1 *-- 0..* Consulta", conLasDos);
            assertThat(de(lectura, ComandoOperacion.CrearRelacion.class)).isEmpty();

            // Una relacion distinta entre las mismas clases si se traza.
            Lectura otra = parser.interpretar("Paciente -- Consulta", conLasDos);
            assertThat(de(otra, ComandoOperacion.CrearRelacion.class)).hasSize(1);
        }

        @Test
        @DisplayName("una clase que ya existe si puede relacionarse con una nueva")
        void relacionaLoExistenteConLoNuevo() {
            Lectura lectura = parser.interpretar("""
                    Consulta
                    fecha: Date

                    Paciente 1 *-- 0..* Consulta
                    """, conPaciente);

            ComandoOperacion.CrearRelacion relacion =
                    de(lectura, ComandoOperacion.CrearRelacion.class).get(0);
            assertThat(relacion.tipo()).isEqualTo(TipoRelacion.COMPOSICION);
            assertThat(relacion.origenId())
                    .as("debe apuntar a la clase que ya estaba en el diagrama")
                    .isEqualTo(idPaciente);
        }
    }

    @Test
    @DisplayName("un texto vacio no revienta")
    void textoVacio() {
        assertThat(parser.interpretar("", vacio).seEntendioAlgo()).isFalse();
        assertThat(parser.interpretar("   \n\n  ", vacio).seEntendioAlgo()).isFalse();
        assertThat(parser.interpretar(null, vacio).seEntendioAlgo()).isFalse();
    }

    @Test
    @DisplayName("una operacion con parametros conserva la firma")
    void operacionConParametros() {
        Lectura lectura = parser.interpretar("""
                Paciente
                + registrar(motivo: String, monto: decimal): boolean
                """, vacio);

        ComandoOperacion.AgregarMetodo operacion =
                de(lectura, ComandoOperacion.AgregarMetodo.class).get(0);
        assertThat(operacion.nombre()).isEqualTo("registrar");
        assertThat(operacion.tipoRetorno()).isEqualTo("Boolean");
        assertThat(operacion.parametrosOVacio())
                .extracting(ComandoOperacion.Parametro::nombre, ComandoOperacion.Parametro::tipo)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("motivo", "String"),
                        org.assertj.core.groups.Tuple.tuple("monto", "Decimal"));
    }

    // ---------- Auxiliares ---------------------------------------------------

    private <T extends ComandoOperacion> List<T> de(Lectura lectura, Class<T> tipo) {
        return lectura.comandos().stream().filter(tipo::isInstance).map(tipo::cast).toList();
    }

    /** Lee una relacion suelta declarando antes sus dos clases. */
    private TipoRelacion tipoLeido(String lineaDeRelacion) {
        String[] partes = lineaDeRelacion.split("\\s+");
        String primera = partes[0];
        String ultima = partes[partes.length - 1];

        Lectura lectura = parser.interpretar(
                primera + "\n" + ultima + "\n\n" + lineaDeRelacion, vacio);

        List<ComandoOperacion.CrearRelacion> relaciones =
                de(lectura, ComandoOperacion.CrearRelacion.class);
        assertThat(relaciones)
                .as("no se leyo ninguna relacion de: " + lineaDeRelacion
                        + " (ignoradas: " + lectura.ignoradas() + ")")
                .hasSize(1);
        return relaciones.get(0).tipo();
    }

    private String nombreDe(Lectura lectura, UUID claseId) {
        return de(lectura, ComandoOperacion.CrearClase.class).stream()
                .filter(c -> c.claseId().equals(claseId))
                .map(ComandoOperacion.CrearClase::nombre)
                .findFirst()
                .orElse("(no creada en esta lectura)");
    }
}
