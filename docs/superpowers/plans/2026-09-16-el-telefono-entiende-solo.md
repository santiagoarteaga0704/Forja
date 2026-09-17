# El teléfono entiende solo — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el dictado del cliente móvil se interprete en el aparato, con paridad total con el servidor, para que dictar funcione en modo avión.

**Architecture:** La gramática de `ParserVoz.java` se porta a Dart como espejo. Lo que impide que las dos copias se separen es un corpus de casos en JSON que leen las dos suites de pruebas: si alguien toca una expresión regular de un lado, la prueba del otro lado se pone roja. El parser Dart produce los mismos `Paso` que el Java, y la capa de cableado los convierte en los `Comando` que el móvil ya sabe aplicar y encolar.

**Tech Stack:** Java 21 + JUnit 5 + Jackson (ya en el proyecto, alcance de prueba) · Dart 3 + `flutter_test` + `dart:convert` (ya en el proyecto). **Ninguna dependencia nueva.**

**Spec:** `docs/superpowers/specs/2026-09-16-ia-local-en-el-movil-design.md`

Este plan cubre los pasos 1 a 3 del diseño. El agente guía portado (paso 4), el traductor de Ollama (paso 5) y Gemma en el móvil (paso 6) van en planes aparte, cuando éste esté andando.

## Global Constraints

- **`ParserVoz.java` no se toca**: ni una expresión regular, ni un manejador. El espejo se escribe contra lo que hay. Lo único que cambia del backend es que sus pruebas pasan a leer el corpus.
- **El formato de los comandos no cambia**: los 11 tipos de `TipoOperacion.java:15-25` y los nombres de campo de sus cargas son los mismos en los dos lados y en la bitácora.
- **Ninguna dependencia nueva** en `pom.xml` ni en `movil/pubspec.yaml`.
- **Ninguna prueba invoca un modelo de lenguaje.** En este plan no hay ningún LLM: es todo determinista.
- **Convención de escritura del repositorio**: los comentarios y los nombres en el código van **sin acentos**; los textos que ve el usuario van **con acentos**. Seguir lo que ya hacen los archivos vecinos.
- **El corpus vive en `compartido/`**, en la raíz del repositorio, fuera de `src/` y de `movil/`. Maven corre con el directorio de trabajo en la raíz; `flutter test` corre con el directorio en `movil/`, así que desde Dart la ruta es `../compartido/`.
- **Los ids no se comparan literal.** El parser inventa UUID. El corpus usa `@Nombre` para el id de una clase que ya existe y `$nueva1` para uno recién inventado.

---

### Task 1: El corpus y su verificador en Java

Una rebanada vertical con **un solo caso**, para que el mecanismo quede probado antes de mover 28 casos a él.

**Files:**
- Create: `compartido/corpus-voz.json`
- Create: `src/test/java/bo/forja/backend/voz/CorpusDeVoz.java`
- Create: `src/test/java/bo/forja/backend/voz/ParserVozCorpusTest.java`

**Interfaces:**
- Consumes: `ParserVoz.interpretar(String, ContextoDelDiagrama)` → `Interpretacion` (existente, `ParserVoz.java:153`); `ContextoDelDiagrama.de(List<ClaseConocida>)` (`ContextoDelDiagrama.java:74`).
- Produces: `CorpusDeVoz.cargar()` → `List<CorpusDeVoz.Caso>`; `CorpusDeVoz.Caso.contextoResuelto()` → `ContextoDelDiagrama`; `CorpusDeVoz.verificar(Caso, String frase, Interpretacion)` que lanza `AssertionError` con un mensaje que nombra el caso y la frase. La Task 9 escribe el gemelo en Dart contra este mismo archivo JSON.

- [ ] **Step 1: Escribir el corpus con un caso**

`compartido/corpus-voz.json`:

```json
{
  "_comentario": "Casos de la gramatica de dictado. Los leen ParserVozCorpusTest (Java) y parser_voz_corpus_test (Dart). Si tocas una regla, los dos lados tienen que seguir dando lo mismo.",
  "contextos": {
    "clinica": { "clases": ["Paciente", "Consulta", "Persona", "Medico", "Auditable"] },
    "vacio": { "clases": [] }
  },
  "casos": [
    {
      "id": "crear-clase-formas-usuales",
      "contexto": "clinica",
      "frases": [
        "crea la clase Factura",
        "crear clase Factura",
        "nueva clase Factura",
        "agrega una clase llamada Factura",
        "creame una clase Factura",
        "dibuja la clase Factura"
      ],
      "entendida": true,
      "pasos": [
        {
          "tipo": "CLASE_CREAR",
          "comando": {
            "claseId": "$nueva1",
            "nombre": "Factura",
            "estereotipo": null,
            "esAbstracta": false,
            "posX": 0,
            "posY": 0
          }
        }
      ]
    }
  ]
}
```

- [ ] **Step 2: Escribir la prueba que lo lee**

`src/test/java/bo/forja/backend/voz/ParserVozCorpusTest.java`:

```java
package bo.forja.backend.voz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * La gramatica contra el corpus compartido.
 * <p>
 * Estos casos no viven aca sino en compartido/corpus-voz.json, porque los lee
 * tambien la suite de Dart. Es lo unico que impide que la copia del telefono y
 * la del servidor se separen sin que nadie se entere: tocar una regla de un
 * lado pone roja la prueba del otro.
 */
@DisplayName("Dictado por voz contra el corpus compartido")
class ParserVozCorpusTest {

    private final ParserVoz parser = new ParserVoz();

    @TestFactory
    List<DynamicTest> elCorpusEntero() {
        List<DynamicTest> pruebas = new ArrayList<>();
        for (CorpusDeVoz.Caso caso : CorpusDeVoz.cargar()) {
            for (String frase : caso.frases()) {
                pruebas.add(DynamicTest.dynamicTest(
                        caso.id() + ": " + frase,
                        () -> CorpusDeVoz.verificar(caso, frase,
                                parser.interpretar(frase, caso.contextoResuelto()))));
            }
        }
        return pruebas;
    }
}
```

- [ ] **Step 3: Correr la prueba y verificar que falla**

Run: `./mvnw -q test -Dtest=ParserVozCorpusTest`
Expected: FALLA al compilar, con `cannot find symbol: class CorpusDeVoz`.

- [ ] **Step 4: Escribir el cargador y el verificador**

`src/test/java/bo/forja/backend/voz/CorpusDeVoz.java`:

```java
package bo.forja.backend.voz;

import bo.forja.backend.operacion.ContextoDelDiagrama;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lee compartido/corpus-voz.json y compara lo esperado con lo que salio.
 * <p>
 * Los identificadores no se comparan literal porque el parser inventa UUID. Hay
 * dos convenciones: "@Paciente" es el id de una clase que ya estaba en el
 * contexto, y "$nueva1" es uno que el parser acaba de inventar -se liga a lo que
 * haya salido la primera vez y despues tiene que coincidir, que es como se
 * verifica que los atributos cuelguen de la clase recien creada-.
 */
final class CorpusDeVoz {

    /** Maven corre con el directorio de trabajo en la raiz del repositorio. */
    private static final Path ARCHIVO = Path.of("compartido", "corpus-voz.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Caso(String id, List<String> frases, boolean entendida,
                JsonNode pasos, List<String> sugerencias,
                Map<String, UUID> clasesDelContexto) {

        ContextoDelDiagrama contextoResuelto() {
            List<ContextoDelDiagrama.ClaseConocida> clases = new ArrayList<>();
            clasesDelContexto.forEach((nombre, id) ->
                    clases.add(new ContextoDelDiagrama.ClaseConocida(id, nombre)));
            return ContextoDelDiagrama.de(clases);
        }
    }

    static List<Caso> cargar() {
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(Files.readString(ARCHIVO));
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("No pude leer " + ARCHIVO.toAbsolutePath(), e);
        }

        Map<String, Map<String, UUID>> contextos = new LinkedHashMap<>();
        raiz.get("contextos").fields().forEachRemaining(entrada -> {
            Map<String, UUID> clases = new LinkedHashMap<>();
            entrada.getValue().get("clases").forEach(nombre ->
                    clases.put(nombre.asText(), UUID.randomUUID()));
            contextos.put(entrada.getKey(), clases);
        });

        List<Caso> casos = new ArrayList<>();
        for (JsonNode caso : raiz.get("casos")) {
            List<String> frases = new ArrayList<>();
            caso.get("frases").forEach(f -> frases.add(f.asText()));
            List<String> sugerencias = new ArrayList<>();
            if (caso.has("sugerencias")) {
                caso.get("sugerencias").forEach(s -> sugerencias.add(s.asText()));
            }
            casos.add(new Caso(
                    caso.get("id").asText(),
                    frases,
                    caso.get("entendida").asBoolean(),
                    caso.get("pasos"),
                    sugerencias,
                    contextos.get(caso.get("contexto").asText())));
        }
        return casos;
    }

    static void verificar(Caso caso, String frase, Interpretacion obtenida) {
        String donde = caso.id() + " / \"" + frase + "\"";

        assertThat(obtenida.entendida()).as(donde + ": entendida").isEqualTo(caso.entendida());

        if (!caso.entendida()) {
            if (!caso.sugerencias().isEmpty()) {
                assertThat(obtenida.sugerencias()).as(donde + ": sugerencias")
                        .containsExactlyElementsOf(caso.sugerencias());
            }
            return;
        }

        JsonNode esperados = caso.pasos();
        assertThat(obtenida.pasos()).as(donde + ": cantidad de pasos").hasSize(esperados.size());

        Map<String, String> ligados = new HashMap<>();
        for (int i = 0; i < esperados.size(); i++) {
            JsonNode esperado = esperados.get(i);
            Interpretacion.Paso paso = obtenida.pasos().get(i);
            String enElPaso = donde + " / paso " + i;

            assertThat(paso.tipo().name()).as(enElPaso + ": tipo")
                    .isEqualTo(esperado.get("tipo").asText());

            JsonNode salio = MAPPER.valueToTree(paso.comando());
            esperado.get("comando").fields().forEachRemaining(campo ->
                    compararCampo(enElPaso, campo.getKey(), campo.getValue(),
                            salio.get(campo.getKey()), caso, ligados));
        }
    }

    private static void compararCampo(String donde, String campo, JsonNode esperado,
                                      JsonNode salio, Caso caso, Map<String, String> ligados) {
        String etiqueta = donde + " / " + campo;
        assertThat(salio).as(etiqueta + ": el campo no vino").isNotNull();

        if (esperado.isTextual() && esperado.asText().startsWith("@")) {
            String nombre = esperado.asText().substring(1);
            UUID id = caso.clasesDelContexto().get(nombre);
            assertThat(id).as(etiqueta + ": el contexto no tiene la clase " + nombre).isNotNull();
            assertThat(salio.asText()).as(etiqueta + ": id de " + nombre).isEqualTo(id.toString());
            return;
        }

        if (esperado.isTextual() && esperado.asText().startsWith("$")) {
            String marca = esperado.asText();
            String yaLigado = ligados.get(marca);
            if (yaLigado == null) {
                assertThat(salio.asText()).as(etiqueta + ": " + marca + " tiene que ser un id")
                        .isNotBlank();
                ligados.put(marca, salio.asText());
            } else {
                assertThat(salio.asText()).as(etiqueta + ": " + marca + " tiene que repetirse")
                        .isEqualTo(yaLigado);
            }
            return;
        }

        if (esperado.isNumber()) {
            assertThat(salio.asDouble()).as(etiqueta).isEqualTo(esperado.asDouble());
            return;
        }

        assertThat(salio).as(etiqueta).isEqualTo(esperado);
    }

    private CorpusDeVoz() {
    }
}
```

- [ ] **Step 5: Correr la prueba y verificar que pasa**

Run: `./mvnw -q test -Dtest=ParserVozCorpusTest`
Expected: PASA, 6 pruebas dinámicas (una por frase del caso).

- [ ] **Step 6: Romperlo a propósito para comprobar que el corpus sirve**

Cambiar en el corpus `"nombre": "Factura"` por `"nombre": "Facturaa"`, correr, **verificar que falla** con un mensaje que nombra el caso y la frase, y **revertir el cambio**. Un corpus que no puede fallar no protege nada.

Run: `./mvnw -q test -Dtest=ParserVozCorpusTest`
Expected: FALLA con `crear-clase-formas-usuales / "crea la clase Factura" / paso 0 / nombre`.

- [ ] **Step 7: Commit**

```bash
git add compartido/corpus-voz.json src/test/java/bo/forja/backend/voz/CorpusDeVoz.java src/test/java/bo/forja/backend/voz/ParserVozCorpusTest.java
git commit -m "Sacar los casos de la gramatica a un corpus que lean los dos lados"
```

---

### Task 2: Migrar los 28 casos de `ParserVozTest` al corpus

**Files:**
- Modify: `compartido/corpus-voz.json`
- Modify: `src/test/java/bo/forja/backend/voz/ParserVozTest.java`

**Interfaces:**
- Consumes: `CorpusDeVoz.cargar()` y `CorpusDeVoz.verificar(...)` de la Task 1.
- Produces: el corpus completo, que es la especificación ejecutable contra la que se escribe el parser Dart de las Tasks 5 a 8. Los ids de caso que quedan definidos acá son los que nombran las pruebas de Dart.

**Regla de migración:** un caso por vez. Cada caso movido al corpus tiene que seguir pasando **en Java** antes de tocar el siguiente. Si un caso no se puede expresar como datos, se queda en `ParserVozTest.java` y se le escribe encima un comentario que diga por qué.

- [ ] **Step 1: Migrar el grupo `CrearClases`**

Son los casos de `ParserVozTest.java:51-153`. El primero (`variasFormasDeCrear`) ya está en el corpus desde la Task 1. Agregar los otros cuatro:

```json
    {
      "id": "nombre-de-varias-palabras-en-camello",
      "contexto": "clinica",
      "frases": ["crea la clase historia clinica"],
      "entendida": true,
      "pasos": [
        { "tipo": "CLASE_CREAR",
          "comando": { "claseId": "$nueva1", "nombre": "historiaClinica",
                       "estereotipo": null, "esAbstracta": false, "posX": 0, "posY": 0 } }
      ]
    },
    {
      "id": "aguanta-acentos-y-puntuacion-del-reconocedor",
      "contexto": "clinica",
      "frases": ["creá la clase Médico."],
      "entendida": true,
      "pasos": [
        { "tipo": "CLASE_CREAR",
          "comando": { "claseId": "$nueva1", "nombre": "Medico",
                       "estereotipo": null, "esAbstracta": false, "posX": 0, "posY": 0 } }
      ]
    },
    {
      "id": "interfaz-con-su-estereotipo",
      "contexto": "clinica",
      "frases": ["crea la interfaz Auditable"],
      "entendida": true,
      "pasos": [
        { "tipo": "CLASE_CREAR",
          "comando": { "claseId": "$nueva1", "nombre": "Auditable",
                       "estereotipo": "interface", "esAbstracta": false, "posX": 0, "posY": 0 } }
      ]
    },
    {
      "id": "una-frase-crea-la-clase-y-sus-atributos",
      "contexto": "clinica",
      "frases": ["crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal"],
      "entendida": true,
      "pasos": [
        { "tipo": "CLASE_CREAR",
          "comando": { "claseId": "$nueva1", "nombre": "Factura",
                       "estereotipo": null, "esAbstracta": false, "posX": 0, "posY": 0 } },
        { "tipo": "ATRIBUTO_AGREGAR",
          "comando": { "claseId": "$nueva1", "nombre": "numero", "tipo": "texto" } },
        { "tipo": "ATRIBUTO_AGREGAR",
          "comando": { "claseId": "$nueva1", "nombre": "total", "tipo": "decimal" } }
      ]
    }
```

El último es el que justifica la ligadura de `$nueva1`: el mismo marcador en los tres pasos exige que los atributos cuelguen de la clase que se acaba de crear, que es exactamente lo que afirma `ParserVozTest.java:108-113`.

**Antes de escribirlo**, abrir `ComandoOperacion.java` y copiar los nombres de campo reales de `AgregarAtributo` — el bloque de arriba muestra solo los tres que la prueba de Java verifica, y el verificador compara **solo los campos que el corpus nombra**, así que agregar los que falten es opcional pero conviene para que el corpus documente la carga entera.

- [ ] **Step 2: Correr y verificar que pasan**

Run: `./mvnw -q test -Dtest=ParserVozCorpusTest`
Expected: PASA, 10 pruebas dinámicas.

- [ ] **Step 3: Borrar del `ParserVozTest` los casos ya migrados**

Borrar los cinco `@Test` de la clase anidada `CrearClases` (`ParserVozTest.java:51-153`) y la clase anidada si queda vacía. No borrar el contexto compartido ni los helpers (`unico(...)`), que los usan los grupos que faltan.

- [ ] **Step 4: Correr la suite entera del backend**

Run: `./mvnw -q test`
Expected: PASA. El total baja en 5 pruebas de `ParserVozTest` y sube en 10 dinámicas de `ParserVozCorpusTest`.

- [ ] **Step 5: Commit**

```bash
git add compartido/corpus-voz.json src/test/java/bo/forja/backend/voz/ParserVozTest.java
git commit -m "Migrar los casos de crear clases al corpus"
```

- [ ] **Step 6: Repetir los pasos 1 a 5 con el grupo `Atributos`**

Empieza en `ParserVozTest.java:154`. Incluye los casos difíciles: `:187` (nombre multipalabra que no se corta al declarar el tipo) y `:210` ("una" que no se parte en "un" + "a"). Esos dos son los que más valen: son los que el port a Dart va a romper primero.

- [ ] **Step 7: Repetir los pasos 1 a 5 con el grupo de métodos**

- [ ] **Step 8: Repetir los pasos 1 a 5 con el grupo de relaciones**

Incluye `:339` (plural dicho contra clase en singular).

- [ ] **Step 9: Repetir los pasos 1 a 5 con el grupo de frases no entendidas**

Estos casos llevan `"entendida": false` y `"sugerencias": [...]` en vez de `pasos`. Las sugerencias son las de `ParserVoz.java:448-461`, parametrizadas con las clases del contexto: con el contexto `clinica`, el ejemplo es `Paciente` y el otro es `Consulta`.

- [ ] **Step 10: Anotar lo que no se pudo migrar**

En `ParserVozTest.java`, sobre cada `@Test` que haya quedado, escribir un comentario de una línea que diga por qué no entra en el corpus. Si no quedó ninguno, borrar el archivo.

Run: `./mvnw -q test`
Expected: PASA.

```bash
git add -A src/test/java/bo/forja/backend/voz compartido/corpus-voz.json
git commit -m "Terminar de migrar la gramatica al corpus"
```

---

### Task 3: `contexto.dart`, el espejo de `ContextoDelDiagrama`

**Files:**
- Create: `movil/lib/voz/contexto.dart`
- Test: `movil/test/voz/contexto_test.dart`

**Interfaces:**
- Consumes: nada del móvil todavía.
- Produces: `class ClaseConocida { final String id; final String nombre; }`; `class ContextoDelDiagrama` con `ContextoDelDiagrama.de(List<ClaseConocida> clases)`, `ClaseConocida? resolver(String texto)`, `List<String> nombres()`, y la función de nivel superior `String clave(String texto)`. Las Tasks 5 a 8 y la Task 9 lo usan. `id` es `String` y no `UUID` porque en el móvil los identificadores viajan como texto en la carga del comando (`comandos.dart:67`).

- [ ] **Step 1: Escribir las pruebas que fallan**

`movil/test/voz/contexto_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';

void main() {
  group('clave', () {
    test('quita acentos, signos y mayusculas', () {
      expect(clave('Médico'), 'medico');
      expect(clave('Historia Clínica'), 'historiaclinica');
      expect(clave('Paciente.'), 'paciente');
    });
  });

  group('resolver', () {
    final contexto = ContextoDelDiagrama.de(const [
      ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
      ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
      ClaseConocida(id: 'id-historia', nombre: 'HistoriaClinica'),
    ]);

    test('encuentra por coincidencia exacta sin importar acentos ni caja', () {
      expect(contexto.resolver('paciente')?.id, 'id-paciente');
      expect(contexto.resolver('Pacienté')?.id, 'id-paciente');
    });

    test('admite una coincidencia parcial cuando es unica', () {
      expect(contexto.resolver('historia')?.id, 'id-historia');
    });

    test('no adivina cuando la coincidencia parcial es ambigua', () {
      final dos = ContextoDelDiagrama.de(const [
        ClaseConocida(id: 'a', nombre: 'Consulta'),
        ClaseConocida(id: 'b', nombre: 'ConsultaMedica'),
      ]);
      expect(dos.resolver('consul'), isNull);
    });

    test('un nombre que no esta devuelve nulo', () {
      expect(contexto.resolver('Factura'), isNull);
    });
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/contexto_test.dart`
Expected: FALLA con `Error: Couldn't resolve the package 'forja_movil'` o `Target of URI doesn't exist`.

Si el nombre del paquete no es `forja_movil`, leerlo de la primera línea de `movil/pubspec.yaml` y corregir el import en todas las pruebas de este plan.

- [ ] **Step 3: Escribir la implementación mínima**

`movil/lib/voz/contexto.dart`:

```dart
/// Las clases que ya estan en el diagrama, para resolver las referencias que
/// llegan dictadas.
///
/// Es el espejo de ContextoDelDiagrama.java. La unica diferencia real es que
/// aca el identificador es texto: en el telefono los ids viajan dentro de la
/// carga del comando, que es un mapa JSON.

/// Acentos que hay que sacar. Dart no trae la normalizacion NFD de Java, asi
/// que la tabla va explicita: es corta, es castellano, y equivocarse se nota
/// enseguida en el corpus.
const _acentos = {
  'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ü': 'u',
  'Á': 'A', 'É': 'E', 'Í': 'I', 'Ó': 'O', 'Ú': 'U', 'Ü': 'U',
  'ñ': 'n', 'Ñ': 'N',
};

String sinAcentos(String texto) {
  final salida = StringBuffer();
  for (final letra in texto.split('')) {
    salida.write(_acentos[letra] ?? letra);
  }
  return salida.toString();
}

/// Forma comparable de un nombre: sin acentos, sin nada que no sea letra o
/// digito, en minuscula.
String clave(String texto) =>
    sinAcentos(texto).replaceAll(RegExp(r'[^\p{L}\p{N}]', unicode: true), '').toLowerCase();

class ClaseConocida {
  const ClaseConocida({required this.id, required this.nombre});

  final String id;
  final String nombre;
}

class ContextoDelDiagrama {
  ContextoDelDiagrama._(this._porNombre);

  factory ContextoDelDiagrama.de(List<ClaseConocida> clases) {
    final indice = <String, ClaseConocida>{};
    for (final clase in clases) {
      indice[clave(clase.nombre)] = clase;
    }
    return ContextoDelDiagrama._(indice);
  }

  factory ContextoDelDiagrama.vacio() => ContextoDelDiagrama._({});

  final Map<String, ClaseConocida> _porNombre;

  /// Primero exacto. Si no, parcial, pero SOLO cuando es unica: dictando es
  /// facil que "historia" quiera decir "HistoriaClinica", pero con dos clases
  /// que empiezan igual, elegir una seria adivinar.
  ClaseConocida? resolver(String? texto) {
    if (texto == null || texto.trim().isEmpty) return null;
    final buscada = clave(texto);

    final exacta = _porNombre[buscada];
    if (exacta != null) return exacta;

    final parciales = _porNombre.entries
        .where((e) => e.key.startsWith(buscada) || buscada.startsWith(e.key))
        .map((e) => e.value)
        .toList();

    return parciales.length == 1 ? parciales.first : null;
  }

  List<String> nombres() => _porNombre.values.map((c) => c.nombre).toList();
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/contexto_test.dart`
Expected: PASA, 5 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/voz/contexto.dart movil/test/voz/contexto_test.dart
git commit -m "Portar a Dart el contexto del diagrama"
```

---

### Task 4: `interpretacion.dart` y el puente a `Comando`

**Files:**
- Create: `movil/lib/voz/interpretacion.dart`
- Test: `movil/test/voz/interpretacion_test.dart`

**Interfaces:**
- Consumes: `Comando` de `movil/lib/comandos.dart:15` (su constructor toma `tipo`, `carga`, `tokenCliente`, `origen`).
- Produces: `class Paso { final String tipo; final Map<String, dynamic> comando; }`; `class Interpretacion` con los constructores de fábrica `Interpretacion.entendida(frase, explicacion, pasos)` e `Interpretacion.noEntendida(frase, sugerencias)`; y `List<Comando> comandosDe(Interpretacion interpretacion, String Function() token)`. Las Tasks 5 a 8 devuelven `Interpretacion`; la Task 10 usa `comandosDe`.

`tipo` es `String` —`'CLASE_CREAR'`, `'ATRIBUTO_AGREGAR'`…— y no un enum, por la misma razón que `Comando` usa texto (`comandos.dart:8-13`): es exactamente la forma en que viaja por la red y en que el servidor lo guarda.

- [ ] **Step 1: Escribir la prueba que falla**

`movil/test/voz/interpretacion_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/interpretacion.dart';

void main() {
  test('una interpretacion entendida no trae sugerencias', () {
    final i = Interpretacion.entendida('crea la clase Factura', 'Cree la clase Factura', const [
      Paso(tipo: 'CLASE_CREAR', comando: {'claseId': 'x', 'nombre': 'Factura'}),
    ]);
    expect(i.entendida, isTrue);
    expect(i.pasos, hasLength(1));
    expect(i.sugerencias, isEmpty);
  });

  test('una interpretacion no entendida no trae pasos', () {
    final i = Interpretacion.noEntendida('bla bla', const ['crea la clase Paciente']);
    expect(i.entendida, isFalse);
    expect(i.pasos, isEmpty);
    expect(i.sugerencias, hasLength(1));
  });

  test('los pasos se convierten en comandos con origen VOZ y un token cada uno', () {
    var n = 0;
    final comandos = comandosDe(
      Interpretacion.entendida('x', 'y', const [
        Paso(tipo: 'CLASE_CREAR', comando: {'claseId': 'a', 'nombre': 'Factura'}),
        Paso(tipo: 'ATRIBUTO_AGREGAR', comando: {'claseId': 'a', 'nombre': 'total'}),
      ]),
      () => 'token-${n++}',
    );

    expect(comandos.map((c) => c.tipo), ['CLASE_CREAR', 'ATRIBUTO_AGREGAR']);
    expect(comandos.map((c) => c.origen), ['VOZ', 'VOZ']);
    expect(comandos.map((c) => c.tokenCliente).toSet(), hasLength(2));
    expect(comandos.first.carga['nombre'], 'Factura');
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/interpretacion_test.dart`
Expected: FALLA, `Target of URI doesn't exist: 'package:forja_movil/voz/interpretacion.dart'`.

- [ ] **Step 3: Escribir la implementación**

`movil/lib/voz/interpretacion.dart`:

```dart
import '../comandos.dart';

/// Lo que el parser entendio de una frase. Espejo de Interpretacion.java.
///
/// Cuando no entiende no devuelve un error sino sugerencias: "no te entendi"
/// deja a la persona probando al azar, y una forma que si funciona le ensena
/// como se dice.

class Paso {
  const Paso({required this.tipo, required this.comando});

  /// El mismo texto que TipoOperacion en el backend: 'CLASE_CREAR', etc.
  final String tipo;

  /// La carga, con los mismos nombres de campo que el registro de Java.
  final Map<String, dynamic> comando;
}

class Interpretacion {
  const Interpretacion({
    required this.frase,
    required this.entendida,
    required this.pasos,
    required this.explicacion,
    required this.sugerencias,
  });

  factory Interpretacion.entendida(String frase, String explicacion, List<Paso> pasos) =>
      Interpretacion(
        frase: frase,
        entendida: true,
        pasos: pasos,
        explicacion: explicacion,
        sugerencias: const [],
      );

  factory Interpretacion.noEntendida(String frase, List<String> sugerencias) => Interpretacion(
        frase: frase,
        entendida: false,
        pasos: const [],
        explicacion: 'No reconoci esa instruccion',
        sugerencias: sugerencias,
      );

  final String frase;
  final bool entendida;
  final List<Paso> pasos;
  final String explicacion;
  final List<String> sugerencias;
}

/// Convierte los pasos en comandos listos para aplicar y encolar.
///
/// El token lo pone quien llama y no el parser, por la misma razon por la que
/// el parser no sabe de la red: el token es lo que hace que reenviar tras un
/// corte no duplique la operacion, y eso es asunto de la cola.
List<Comando> comandosDe(Interpretacion interpretacion, String Function() token) =>
    interpretacion.pasos
        .map((paso) => Comando(
              tipo: paso.tipo,
              carga: paso.comando,
              tokenCliente: token(),
              origen: 'VOZ',
            ))
        .toList();
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/interpretacion_test.dart`
Expected: PASA, 3 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/voz/interpretacion.dart movil/test/voz/interpretacion_test.dart
git commit -m "Portar a Dart la interpretacion y el puente a los comandos"
```

---

### Task 5: Las piezas de la gramática y los auxiliares

Antes de portar un solo patrón hay que portar los ladrillos con los que están hechos, porque son lo que comparten los 19.

**Files:**
- Create: `movil/lib/voz/gramatica.dart`
- Test: `movil/test/voz/gramatica_test.dart`

**Interfaces:**
- Consumes: `sinAcentos` de `movil/lib/voz/contexto.dart` (Task 3).
- Produces: las constantes `nom`, `nomFin`, `crear`, `agregar`, `cantidad`, `marcas`; `RegExp regla(String expresion)`; y las funciones `String limpiar(String frase)`, `String limpiarNombre(String? bruto)`, `List<String> separarEnumeracion(String texto)`. Las Tasks 6, 7 y 8 las usan todas.

**Tres trampas del port**, y las tres están cubiertas por las pruebas de abajo:

1. Java compila con `CASE_INSENSITIVE | UNICODE_CASE`; en Dart es `RegExp(..., caseSensitive: false, unicode: true)`. Sin `unicode: true` las clases `\p{L}` **no compilan**.
2. Java ancla con `matches()` y además `regla()` agrega `^` y `$`. En Dart hay que usar `firstMatch` y depender del `^...$` del patrón, que ya viene.
3. `String.split` de Java **descarta los vacíos del final**; el de Dart **no**. Afecta a `limpiarNombre` y a `separarEnumeracion`, y por eso las dos filtran vacíos a mano.

- [ ] **Step 1: Escribir las pruebas que fallan**

`movil/test/voz/gramatica_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/gramatica.dart';

void main() {
  group('limpiar', () {
    test('saca acentos, la puntuacion del reconocedor y los espacios de sobra', () {
      expect(limpiar('  creá   la clase Médico. '), 'crea la clase Medico');
      expect(limpiar('¿crea la clase Factura?'), 'crea la clase Factura');
    });
  });

  group('limpiarNombre', () {
    test('junta las palabras en camello', () {
      expect(limpiarNombre('historia clinica'), 'historiaClinica');
    });

    test('la primera palabra conserva su caja', () {
      expect(limpiarNombre('Historia clinica'), 'HistoriaClinica');
      expect(limpiarNombre('Factura'), 'Factura');
    });

    test('aguanta nulo, vacio y espacios de mas', () {
      expect(limpiarNombre(null), '');
      expect(limpiarNombre('   '), '');
      expect(limpiarNombre('  historia   clinica  '), 'historiaClinica');
    });
  });

  group('separarEnumeracion', () {
    test('separa por coma, por y, y por e', () {
      expect(separarEnumeracion('a, b y c'), ['a', 'b', 'c']);
      expect(separarEnumeracion('uno e dos'), ['uno', 'dos']);
    });

    test('no deja partes vacias', () {
      expect(separarEnumeracion('a, b,'), ['a', 'b']);
    });
  });

  group('regla', () {
    test('ancla la expresion entera y no distingue mayusculas', () {
      final r = regla('crea la clase $nomFin');
      expect(r.firstMatch('CREA LA CLASE Factura')?.group(1), 'Factura');
      expect(r.firstMatch('y crea la clase Factura'), isNull);
    });
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/gramatica_test.dart`
Expected: FALLA, `Target of URI doesn't exist`.

- [ ] **Step 3: Escribir la implementación**

`movil/lib/voz/gramatica.dart`. Las cadenas de los patrones se copian de `ParserVoz.java:45-51` cambiando solo el escapado: en Java las barras van dobles dentro de una cadena Java (`"\\s"`), en Dart se usan cadenas crudas (`r'\s'`) y van simples.

```dart
import 'contexto.dart';

/// Los ladrillos con los que estan hechos los 19 patrones de la gramatica.
///
/// Es el espejo de ParserVoz.java:45-51 y de sus auxiliares. Lo unico que
/// cambia es el escapado: donde Java escribe "\\s" dentro de una cadena, aca va
/// una cadena cruda con \s.

/// Nombre de clase o de miembro: letras, digitos y espacios intermedios.
const nom = r'([\p{L}][\p{L}\p{N}_]*(?:\s+[\p{L}][\p{L}\p{N}_]*)*?)';
const nomFin = r'([\p{L}][\p{L}\p{N}_]*(?:\s+[\p{L}][\p{L}\p{N}_]*)*)';
const crear = r'(?:crea|crear|cree|creame|agrega|agregar|agregame|anade|anadir|nueva|nuevo|dibuja|dibujar)';
const agregar = r'(?:agrega|agregale|agregar|anade|anadile|anadir|pone|ponele|poner|sumale)';
const cantidad = r'(muchas|muchos|varias|varios|una|uno|un|cero\s+o\s+mas|al\s+menos\s+una?|al\s+menos\s+uno)';
const marcas = r'(?:clave|identificador|primaria|unico|unica|obligatorio|obligatoria'
    r'|requerido|requerida|no\s+nulo|longitud\s+\d+)';

/// unicode: true no es opcional: sin eso las clases \p{L} no compilan.
RegExp regla(String expresion) =>
    RegExp('^$expresion\$', caseSensitive: false, unicode: true);

/// Quita acentos, signos de puntuacion y espacios de sobra.
String limpiar(String frase) => sinAcentos(frase)
    // El reconocimiento de voz agrega puntos y comas donde le parece.
    .replaceAll(RegExp(r'''[.,;:!?¿¡"']'''), ' ')
    .replaceAll(RegExp(r'\s+'), ' ')
    .trim();

/// Nombre listo para el modelo: sin espacios y en mayuscula inicial por
/// palabra, salvo la primera, que conserva su caja para no estropear un nombre
/// que el cliente ya escribio bien. Dictando no se pronuncia el camello.
String limpiarNombre(String? bruto) {
  if (bruto == null) return '';
  final palabras = bruto.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  final salida = StringBuffer();
  for (var i = 0; i < palabras.length; i++) {
    final palabra = palabras[i];
    if (i == 0) {
      salida.write(palabra);
    } else {
      salida.write(palabra[0].toUpperCase());
      salida.write(palabra.substring(1));
    }
  }
  return salida.toString();
}

/// Separa "a, b y c" en sus partes. El split de Dart no descarta los vacios del
/// final como el de Java, asi que se filtran a mano.
List<String> separarEnumeracion(String texto) => texto
    .split(RegExp(r'\s*(?:,|\sy\s|\se\s)\s*'))
    .map((p) => p.trim())
    .where((p) => p.isNotEmpty)
    .toList();
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/gramatica_test.dart`
Expected: PASA, 8 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/voz/gramatica.dart movil/test/voz/gramatica_test.dart
git commit -m "Portar a Dart las piezas de la gramatica"
```

---

### Task 6: El parser en Dart — las clases

**Files:**
- Create: `movil/lib/voz/parser_voz.dart`
- Test: `movil/test/voz/parser_clases_test.dart`

**Interfaces:**
- Consumes: `regla`, `nom`, `nomFin`, `crear`, `limpiar`, `limpiarNombre` de `gramatica.dart` (Task 5); `ContextoDelDiagrama` de `contexto.dart` (Task 3); `Interpretacion`, `Paso` de `interpretacion.dart` (Task 4).
- Produces: `class ParserVoz` con `Interpretacion interpretar(String fraseOriginal, ContextoDelDiagrama contexto)`, más los auxiliares privados `_uno`, `_noConozco`, `_sugerencias`, `_nuevoId`. Las Tasks 7 y 8 agregan ramas a `interpretar` y las Tasks 9 y 10 lo consumen.

Cubre seis patrones de `ParserVoz.java:57-76`: `CREAR_INTERFAZ`, `CREAR_CLASE_CON_ATRIBUTOS` (solo el reconocimiento; los atributos los completa la Task 7), `CREAR_CLASE`, `RENOMBRAR`, `ELIMINAR`, `MARCAR_ABSTRACTA_IMPERATIVO`, `MARCAR_ABSTRACTA_DECLARATIVO` y `MARCAR_INTERFAZ`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`movil/test/voz/parser_clases_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-persona', nombre: 'Persona'),
  ]);

  test('crea una clase en todas las formas usuales', () {
    for (final frase in [
      'crea la clase Factura',
      'crear clase Factura',
      'nueva clase Factura',
      'agrega una clase llamada Factura',
      'dibuja la clase Factura',
    ]) {
      final i = parser.interpretar(frase, contexto);
      expect(i.entendida, isTrue, reason: frase);
      expect(i.pasos.single.tipo, 'CLASE_CREAR');
      expect(i.pasos.single.comando['nombre'], 'Factura', reason: frase);
      expect(i.pasos.single.comando['estereotipo'], isNull);
      expect(i.pasos.single.comando['esAbstracta'], isFalse);
    }
  });

  test('una interfaz lleva su estereotipo', () {
    final i = parser.interpretar('crea la interfaz Auditable', contexto);
    expect(i.pasos.single.comando['estereotipo'], 'interface');
  });

  test('renombra una clase que existe', () {
    final i = parser.interpretar('renombra la clase Paciente a Cliente', contexto);
    expect(i.pasos.single.tipo, 'CLASE_RENOMBRAR');
    expect(i.pasos.single.comando['claseId'], 'id-paciente');
    expect(i.pasos.single.comando['nombre'], 'Cliente');
  });

  test('marca abstracta en las dos formas', () {
    for (final frase in ['marca Persona como abstracta', 'Persona es abstracta']) {
      final i = parser.interpretar(frase, contexto);
      expect(i.pasos.single.tipo, 'CLASE_MARCAR', reason: frase);
      expect(i.pasos.single.comando['esAbstracta'], isTrue, reason: frase);
    }
  });

  test('nombrar una clase que no existe explica cual falta, no dice "no entendi"', () {
    final i = parser.interpretar('elimina la clase Factura', contexto);
    expect(i.entendida, isFalse);
    expect(i.sugerencias.first, contains('Factura'));
    expect(i.sugerencias.last, contains('crea la clase Factura'));
  });

  test('una frase vacia devuelve las sugerencias generales', () {
    final i = parser.interpretar('   ', contexto);
    expect(i.entendida, isFalse);
    expect(i.sugerencias, isNotEmpty);
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/parser_clases_test.dart`
Expected: FALLA, `Target of URI doesn't exist: 'package:forja_movil/voz/parser_voz.dart'`.

- [ ] **Step 3: Escribir la implementación**

`movil/lib/voz/parser_voz.dart`. La cascada respeta el orden de `ParserVoz.java:164` en adelante: **lo más específico primero**.

```dart
import 'contexto.dart';
import 'gramatica.dart';
import 'interpretacion.dart';

/// La gramatica de dictado, en el aparato.
///
/// Espejo de ParserVoz.java. No hay modelo de lenguaje: son expresiones
/// regulares en cascada, de lo mas especifico a lo mas general, y ese orden es
/// parte de la gramatica -"tiene" puede empezar una relacion o un atributo, y
/// solo se decide mirando si el destino es una clase conocida-.
///
/// Los casos que prueban que esto dice lo mismo que el servidor no viven aca
/// sino en compartido/corpus-voz.json, que leen las dos suites.
class ParserVoz {
  static final _crearInterfaz = regla(
      '$crear' r'\s+(?:(?:el|la|los|las|un|una|unos|unas)\s+)?(?:interfaz|interface)\s+(?:llamada\s+)?' '$nomFin');
  static final _crearClase = regla(
      '$crear' r'\s+(?:(?:el|la|los|las|un|una|unos|unas)\s+)?clase\s+(?:llamada\s+)?' '$nomFin');
  static final _renombrar = regla(
      r'(?:renombra|renombrar|cambia\s+el\s+nombre\s+de|cambiar\s+el\s+nombre\s+de)'
      r'\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\s+)?clase\s+)?' '$nom' r'\s+(?:a|por|como)\s+' '$nomFin');
  static final _eliminar = regla(
      r'(?:elimina|eliminar|borra|borrar|quita|quitar)\s+(?:la\s+)?clase\s+' '$nomFin');
  static final _marcarAbstractaImperativo = regla(
      r'(?:marca|marcar|hace|hacer|pone|poner|declara|declarar)'
      r'\s+(?:a\s+|la\s+clase\s+)?' '$nom' r'\s+como\s+(?:clase\s+)?abstracta');
  static final _marcarAbstractaDeclarativo = regla(
      r'(?:(?:(?:el|la|los|las|un|una|unos|unas)\s+)?clase\s+)?' '$nom' r'\s+es\s+(?:una\s+clase\s+)?abstracta');
  static final _marcarInterfaz = regla(
      r'(?:(?:(?:el|la|los|las|un|una|unos|unas)\s+)?clase\s+)?' '$nom' r'\s+es\s+una\s+(?:interfaz|interface)');

  var _siguienteId = 0;

  /// Los ids los inventa el parser, igual que en el servidor. No hace falta que
  /// sean UUID: solo que no se repitan dentro del aparato, y el servidor los
  /// acepta como texto.
  String _nuevoId() =>
      'local-${DateTime.now().microsecondsSinceEpoch}-${_siguienteId++}';

  Interpretacion interpretar(String fraseOriginal, ContextoDelDiagrama contexto) {
    if (fraseOriginal.trim().isEmpty) {
      return Interpretacion.noEntendida('', _sugerencias(contexto));
    }
    final frase = limpiar(fraseOriginal);
    return _primeraQueEncaje(frase, contexto) ??
        Interpretacion.noEntendida(fraseOriginal, _sugerencias(contexto));
  }

  Interpretacion? _primeraQueEncaje(String frase, ContextoDelDiagrama contexto) {
    var m = _crearInterfaz.firstMatch(frase);
    if (m != null) {
      final nombre = limpiarNombre(m.group(1));
      return _uno(frase, 'Cree la interfaz $nombre', 'CLASE_CREAR', {
        'claseId': _nuevoId(),
        'nombre': nombre,
        'estereotipo': 'interface',
        'esAbstracta': false,
        'posX': 0,
        'posY': 0,
      });
    }

    m = _crearClase.firstMatch(frase);
    if (m != null) {
      final nombre = limpiarNombre(m.group(1));
      return _uno(frase, 'Cree la clase $nombre', 'CLASE_CREAR', {
        'claseId': _nuevoId(),
        'nombre': nombre,
        'estereotipo': null,
        'esAbstracta': false,
        'posX': 0,
        'posY': 0,
      });
    }

    m = _renombrar.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      final nuevo = limpiarNombre(m.group(2));
      return _uno(frase, 'Renombre ${clase.nombre} a $nuevo', 'CLASE_RENOMBRAR', {
        'claseId': clase.id,
        'nombre': nuevo,
      });
    }

    m = _eliminar.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, 'Elimine la clase ${clase.nombre}', 'CLASE_ELIMINAR', {
        'claseId': clase.id,
      });
    }

    m = _marcarAbstractaImperativo.firstMatch(frase) ??
        _marcarAbstractaDeclarativo.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, 'Marque ${clase.nombre} como abstracta', 'CLASE_MARCAR', {
        'claseId': clase.id,
        'estereotipo': null,
        'esAbstracta': true,
      });
    }

    m = _marcarInterfaz.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, '${clase.nombre} ahora es una interfaz', 'CLASE_MARCAR', {
        'claseId': clase.id,
        'estereotipo': 'interface',
        'esAbstracta': false,
      });
    }

    return null;
  }

  Interpretacion _uno(String frase, String explicacion, String tipo, Map<String, dynamic> comando) =>
      Interpretacion.entendida(frase, explicacion, [Paso(tipo: tipo, comando: comando)]);

  /// No se entendio porque se nombro una clase que no existe. Decirlo asi -y no
  /// "no te entendi"- es la diferencia entre que la persona corrija el nombre y
  /// que repita la misma frase mas fuerte.
  Interpretacion _noConozco(String frase, String nombre, ContextoDelDiagrama contexto) {
    final pistas = <String>['No hay ninguna clase llamada "${nombre.trim()}" en el diagrama'];
    if (contexto.nombres().isNotEmpty) {
      pistas.add('Las que hay son: ${contexto.nombres().join(', ')}');
    }
    pistas.add('Proba primero: crea la clase ${limpiarNombre(nombre)}');
    return Interpretacion.noEntendida(frase, pistas);
  }

  List<String> _sugerencias(ContextoDelDiagrama contexto) {
    final nombres = contexto.nombres();
    final ejemplo = nombres.isEmpty ? 'Paciente' : nombres[0];
    final otro = nombres.length > 1 ? nombres[1] : 'Consulta';
    return [
      'crea la clase $ejemplo',
      'a $ejemplo agregale el atributo nombre de tipo texto obligatorio',
      'a $ejemplo agregale el atributo codigo de tipo texto clave de longitud 20',
      'un $ejemplo tiene muchas $otro',
      '$ejemplo hereda de Persona',
      'marca Persona como abstracta',
      'a $ejemplo agregale el metodo calcularEdad que devuelve entero',
    ];
  }
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/parser_clases_test.dart`
Expected: PASA, 6 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/voz/parser_voz.dart movil/test/voz/parser_clases_test.dart
git commit -m "Portar a Dart los patrones de clase"
```

---

### Task 7: El parser en Dart — atributos y métodos

**Files:**
- Modify: `movil/lib/voz/parser_voz.dart`
- Test: `movil/test/voz/parser_miembros_test.dart`

**Interfaces:**
- Consumes: todo lo de la Task 6, más `marcas`, `separarEnumeracion` de `gramatica.dart`.
- Produces: en `ParserVoz`, las ramas de `ATRIBUTO_DESTINO_PRIMERO`, `ATRIBUTO_DESTINO_ULTIMO`, `ATRIBUTO_DECLARATIVO`, `METODO_DESTINO_PRIMERO`, `METODO_DESTINO_ULTIMO`, el completado de `CREAR_CLASE_CON_ATRIBUTOS`, y los privados `_detalleDeAtributo(String, String claseId)` → `Paso` y `_detalleDeMetodo(String, String claseId)` → `Paso`. La Task 8 los deja intactos.

Los patrones se copian de `ParserVoz.java:77-104` y `:136-145`. Los nombres de campo de las cargas se copian de `ComandoOperacion.AgregarAtributo` y `ComandoOperacion.AgregarMetodo` — **abrir `ComandoOperacion.java` y leerlos, no adivinarlos**; `ParserVoz.java:420-422` muestra el orden de los argumentos de `AgregarMetodo`.

**La trampa que hay que respetar:** el detalle del atributo son **dos** patrones y no uno. Con el tipo opcional y un grupo final que acepta cualquier cosa, el nombre queda siempre en su forma más corta: "fecha de nacimiento de tipo fecha" produce un atributo llamado "fecha". Exigiendo el tipo en el primer intento (`DETALLE_ATRIBUTO_CON_TIPO`) el nombre se extiende hasta donde corresponde, y recién si eso no encaja se prueba el sin tipo. Está documentado en `ParserVoz.java:127-134`.

- [ ] **Step 1: Escribir las pruebas que fallan**

`movil/test/voz/parser_miembros_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
  ]);

  test('agrega un atributo con el destino adelante y atras', () {
    for (final frase in [
      'a Paciente agregale el atributo nombre de tipo texto',
      'agrega el atributo nombre de tipo texto a Paciente',
    ]) {
      final i = parser.interpretar(frase, contexto);
      expect(i.entendida, isTrue, reason: frase);
      expect(i.pasos.single.tipo, 'ATRIBUTO_AGREGAR', reason: frase);
      expect(i.pasos.single.comando['claseId'], 'id-paciente', reason: frase);
      expect(i.pasos.single.comando['nombre'], 'nombre', reason: frase);
      expect(i.pasos.single.comando['tipo'], 'texto', reason: frase);
    }
  });

  test('un nombre de varias palabras no se corta al declarar el tipo', () {
    final i = parser.interpretar(
        'a Paciente agregale el atributo fecha de nacimiento de tipo fecha', contexto);
    expect(i.pasos.single.comando['nombre'], 'fechaDeNacimiento');
    expect(i.pasos.single.comando['tipo'], 'fecha');
  });

  test('sin tipo declarado se asume texto', () {
    final i = parser.interpretar('a Paciente agregale el atributo apodo', contexto);
    expect(i.pasos.single.comando['tipo'], 'texto');
  });

  test('"una Consulta" no se parte en "un" + "a Consulta"', () {
    final i = parser.interpretar('una Consulta tiene un motivo de tipo texto', contexto);
    expect(i.entendida, isTrue);
    expect(i.pasos.single.comando['claseId'], 'id-consulta');
    expect(i.pasos.single.comando['nombre'], 'motivo');
  });

  test('crea la clase y sus atributos de una sola vez, colgados de ella', () {
    final i = parser.interpretar(
        'crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal',
        contexto);
    expect(i.pasos.map((p) => p.tipo),
        ['CLASE_CREAR', 'ATRIBUTO_AGREGAR', 'ATRIBUTO_AGREGAR']);
    final claseId = i.pasos.first.comando['claseId'];
    expect(i.pasos[1].comando['claseId'], claseId);
    expect(i.pasos[2].comando['claseId'], claseId);
  });

  test('agrega un metodo con su retorno', () {
    final i = parser.interpretar(
        'a Paciente agregale el metodo calcularEdad que devuelve entero', contexto);
    expect(i.pasos.single.tipo, 'METODO_AGREGAR');
    expect(i.pasos.single.comando['nombre'], 'calcularEdad');
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/parser_miembros_test.dart`
Expected: FALLA. Las frases de atributo caen al final de la cascada y devuelven `entendida: false`.

- [ ] **Step 3: Escribir la implementación**

En `parser_voz.dart`, agregar los patrones como campos estáticos —copiando las expresiones de `ParserVoz.java:77-104` y `:136-145` con el escapado de Dart, igual que en la Task 6— y las ramas correspondientes en `_primeraQueEncaje`, **después** de las de clase y **antes** del `return null`. El detalle de un atributo va en su propio método:

```dart
  /// Detalle de un atributo. Son dos patrones y no uno por una razon concreta:
  /// con el tipo opcional y un final que acepta cualquier cosa, el nombre queda
  /// siempre en su forma mas corta y el resto se lo traga ese final. "fecha de
  /// nacimiento de tipo fecha" producia un atributo llamado "fecha" de tipo
  /// texto. Exigiendo el tipo en el primer intento, el nombre se extiende hasta
  /// donde corresponde.
  Paso? _detalleDeAtributo(String texto, String claseId) {
    var m = _detalleAtributoConTipo.firstMatch(texto);
    var tipo = 'texto';
    if (m != null) {
      tipo = m.group(2)!;
    } else {
      m = _detalleAtributoSinTipo.firstMatch(texto);
      if (m == null) return null;
    }
    final marcasDichas = (m.groupCount >= 3 ? m.group(3) : '') ?? '';
    return Paso(tipo: 'ATRIBUTO_AGREGAR', comando: {
      'claseId': claseId,
      'atributoId': _nuevoId(),
      'nombre': limpiarNombre(m.group(1)),
      'tipo': tipo,
      // Los nombres de estos campos se copian de ComandoOperacion.AgregarAtributo.
      'esClave': _marcaPresente(marcasDichas, ['clave', 'identificador', 'primaria']),
      'esUnico': _marcaPresente(marcasDichas, ['unico', 'unica']),
      'esObligatorio': _marcaPresente(
          marcasDichas, ['obligatorio', 'obligatoria', 'requerido', 'requerida', 'no nulo']),
      'longitud': _longitudDicha(marcasDichas),
    });
  }

  bool _marcaPresente(String texto, List<String> palabras) =>
      palabras.any((p) => texto.toLowerCase().contains(p));

  int? _longitudDicha(String texto) {
    final m = RegExp(r'longitud\s+(\d+)', caseSensitive: false).firstMatch(texto);
    return m == null ? null : int.parse(m.group(1)!);
  }
```

`limpiarNombre` sobre el nombre del atributo es lo que convierte "fecha de nacimiento" en `fechaDeNacimiento`.

Para `CREAR_CLASE_CON_ATRIBUTOS`, la rama crea el paso de la clase, guarda su `claseId` en una variable local y llama a `_detalleDeAtributo(parte, claseId)` por cada parte que devuelva `separarEnumeracion(m.group(2)!)`, descartando las que den `null`.

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/parser_miembros_test.dart`
Expected: PASA, 6 pruebas.

- [ ] **Step 5: Correr todas las pruebas del móvil**

Run: `cd movil && flutter test`
Expected: PASA. Las de la Task 6 no se rompieron: las ramas nuevas van después de las de clase.

- [ ] **Step 6: Commit**

```bash
git add movil/lib/voz/parser_voz.dart movil/test/voz/parser_miembros_test.dart
git commit -m "Portar a Dart los patrones de atributo y de metodo"
```

---

### Task 8: El parser en Dart — las relaciones y el orden de la cascada

**Files:**
- Modify: `movil/lib/voz/parser_voz.dart`
- Test: `movil/test/voz/parser_relaciones_test.dart`

**Interfaces:**
- Consumes: todo lo de las Tasks 6 y 7, más `cantidad` de `gramatica.dart`.
- Produces: en `ParserVoz`, las ramas de `HERENCIA`, `REALIZACION`, `COMPOSICION`, `AGREGACION`, `DEPENDENCIA` y `ASOCIACION`, y el privado `Interpretacion? _comoRelacion(String frase, ContextoDelDiagrama contexto)`. Con esto `ParserVoz` queda completo y la Task 9 lo corre contra el corpus entero.

**Lo más importante de esta tarea no son los patrones sino dónde se enchufan.** En el Java, `comoRelacion` se evalúa **antes** que los atributos (`ParserVoz.java:227-230`), con este comentario:

> *"Las relaciones van antes que los atributos: 'tiene' puede empezar las dos y solo se decide mirando si el destino es una clase conocida."*

O sea: `_comoRelacion` devuelve `null` —y deja pasar al atributo— cuando alguno de los dos extremos no resuelve contra el contexto. "Paciente tiene muchas Consultas" es una relación porque `Consulta` existe; "Paciente tiene muchas deudas" es un atributo porque `deudas` no existe. Mover esa llamada de lugar rompe la mitad de la gramática sin romper la compilación.

- [ ] **Step 1: Escribir las pruebas que fallan**

`movil/test/voz/parser_relaciones_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
    ClaseConocida(id: 'id-persona', nombre: 'Persona'),
    ClaseConocida(id: 'id-auditable', nombre: 'Auditable'),
  ]);

  test('cada conector produce su tipo de relacion', () {
    final esperados = {
      'Paciente hereda de Persona': 'HERENCIA',
      'Paciente implementa Auditable': 'REALIZACION',
      'Paciente se compone de muchas Consultas': 'COMPOSICION',
      'Paciente agrupa muchas Consultas': 'AGREGACION',
      'Paciente depende de Consulta': 'DEPENDENCIA',
      'un Paciente tiene muchas Consultas': 'ASOCIACION',
    };
    esperados.forEach((frase, tipoEsperado) {
      final i = parser.interpretar(frase, contexto);
      expect(i.entendida, isTrue, reason: frase);
      expect(i.pasos.single.tipo, 'RELACION_CREAR', reason: frase);
      expect(i.pasos.single.comando['tipo'], tipoEsperado, reason: frase);
    });
  });

  test('un plural dicho encuentra la clase en singular', () {
    final i = parser.interpretar('un Paciente tiene muchas Consultas', contexto);
    expect(i.pasos.single.comando['destinoId'], 'id-consulta');
  });

  test('"tiene" con un destino que NO es clase cae en atributo, no en relacion', () {
    final i = parser.interpretar('Paciente tiene un saldo de tipo decimal', contexto);
    expect(i.entendida, isTrue);
    expect(i.pasos.single.tipo, 'ATRIBUTO_AGREGAR');
    expect(i.pasos.single.comando['nombre'], 'saldo');
  });

  test('"tiene" con un destino que SI es clase gana como relacion', () {
    final i = parser.interpretar('un Paciente tiene muchas Consultas', contexto);
    expect(i.pasos.single.tipo, 'RELACION_CREAR');
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/parser_relaciones_test.dart`
Expected: FALLA. Las tres primeras porque las ramas no existen; la cuarta porque hoy "tiene" cae en atributo.

- [ ] **Step 3: Escribir la implementación**

Agregar los seis patrones copiados de `ParserVoz.java:105-120` y el método:

```dart
  /// Las relaciones se evaluan ANTES que los atributos: "tiene" puede empezar
  /// las dos y lo unico que las separa es si el destino es una clase que ya
  /// existe. "Paciente tiene muchas Consultas" relaciona; "Paciente tiene
  /// muchas deudas" describe un atributo. Por eso este metodo devuelve null
  /// -y deja pasar al atributo- cuando algun extremo no resuelve.
  Interpretacion? _comoRelacion(String frase, ContextoDelDiagrama contexto) {
    final candidatos = <RegExp, String>{
      _herencia: 'HERENCIA',
      _realizacion: 'REALIZACION',
      _composicion: 'COMPOSICION',
      _agregacion: 'AGREGACION',
      _dependencia: 'DEPENDENCIA',
      _asociacion: 'ASOCIACION',
    };

    for (final entrada in candidatos.entries) {
      final m = entrada.key.firstMatch(frase);
      if (m == null) continue;

      // El grupo de cantidad, cuando el patron lo tiene, va en el medio: el
      // destino es siempre el ultimo grupo.
      final origen = contexto.resolver(m.group(1));
      final destino = contexto.resolver(m.group(m.groupCount));
      if (origen == null || destino == null) continue;

      return _uno(frase, '${origen.nombre} ${entrada.value.toLowerCase()} ${destino.nombre}',
          'RELACION_CREAR', {
        'relacionId': _nuevoId(),
        'origenId': origen.id,
        'destinoId': destino.id,
        'tipo': entrada.value,
        // Los demas campos de ComandoOperacion.CrearRelacion se copian de su
        // registro; ParserVoz.java los llena en comoRelacion.
      });
    }
    return null;
  }
```

Y en `_primeraQueEncaje`, **entre** las ramas de clase y las de atributo, exactamente donde lo pone `ParserVoz.java:227-230`:

```dart
    final relacion = _comoRelacion(frase, contexto);
    if (relacion != null) return relacion;
```

Antes de escribir la carga, abrir `ComandoOperacion.CrearRelacion` y copiar sus nombres de campo y cómo `ParserVoz.java` traduce el grupo de cantidad a multiplicidad.

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/parser_relaciones_test.dart`
Expected: PASA, 4 pruebas.

- [ ] **Step 5: Correr todas las pruebas del móvil**

Run: `cd movil && flutter test`
Expected: PASA. Si se rompió alguna de la Task 7, la llamada a `_comoRelacion` quedó en el lugar equivocado.

- [ ] **Step 6: Commit**

```bash
git add movil/lib/voz/parser_voz.dart movil/test/voz/parser_relaciones_test.dart
git commit -m "Portar a Dart los patrones de relacion"
```

---

### Task 9: El corpus entero, en verde, en los dos lados

Hasta acá el Dart pasa **sus** pruebas. Esta tarea es la que comprueba que dice lo mismo que el servidor.

**Files:**
- Create: `movil/test/voz/corpus_de_voz.dart`
- Create: `movil/test/voz/parser_voz_corpus_test.dart`

**Interfaces:**
- Consumes: `compartido/corpus-voz.json` (Tasks 1 y 2), `ParserVoz` completo (Tasks 6, 7 y 8).
- Produces: nada que consuma otra tarea. Es la compuerta.

- [ ] **Step 1: Escribir el cargador y el verificador en Dart**

`movil/test/voz/corpus_de_voz.dart`. Es el gemelo de `CorpusDeVoz.java`, con las mismas dos convenciones de identificador.

```dart
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/interpretacion.dart';

/// Lee el mismo compartido/corpus-voz.json que la suite de Java.
///
/// flutter test corre con el directorio de trabajo en movil/, de ahi el ../.

class CasoDelCorpus {
  CasoDelCorpus(this.id, this.frases, this.entendida, this.pasos, this.sugerencias, this.clases);

  final String id;
  final List<String> frases;
  final bool entendida;
  final List<dynamic> pasos;
  final List<String> sugerencias;
  final Map<String, String> clases;

  ContextoDelDiagrama get contexto => ContextoDelDiagrama.de(
      clases.entries.map((e) => ClaseConocida(id: e.value, nombre: e.key)).toList());
}

List<CasoDelCorpus> cargarCorpus() {
  final crudo = File('../compartido/corpus-voz.json').readAsStringSync();
  final raiz = jsonDecode(crudo) as Map<String, dynamic>;

  final contextos = <String, Map<String, String>>{};
  (raiz['contextos'] as Map<String, dynamic>).forEach((nombre, cuerpo) {
    final clases = <String, String>{};
    for (final clase in (cuerpo as Map)['clases'] as List) {
      clases[clase as String] = 'id-${clase.toLowerCase()}';
    }
    contextos[nombre] = clases;
  });

  return [
    for (final caso in raiz['casos'] as List)
      CasoDelCorpus(
        caso['id'] as String,
        (caso['frases'] as List).cast<String>(),
        caso['entendida'] as bool,
        (caso['pasos'] as List?) ?? const [],
        ((caso['sugerencias'] as List?) ?? const []).cast<String>(),
        contextos[caso['contexto'] as String]!,
      ),
  ];
}

void verificarCaso(CasoDelCorpus caso, String frase, Interpretacion obtenida) {
  final donde = '${caso.id} / "$frase"';

  expect(obtenida.entendida, caso.entendida, reason: '$donde: entendida');
  if (!caso.entendida) {
    if (caso.sugerencias.isNotEmpty) {
      expect(obtenida.sugerencias, caso.sugerencias, reason: '$donde: sugerencias');
    }
    return;
  }

  expect(obtenida.pasos, hasLength(caso.pasos.length), reason: '$donde: cantidad de pasos');

  final ligados = <String, String>{};
  for (var i = 0; i < caso.pasos.length; i++) {
    final esperado = caso.pasos[i] as Map<String, dynamic>;
    final paso = obtenida.pasos[i];
    final enElPaso = '$donde / paso $i';

    expect(paso.tipo, esperado['tipo'], reason: '$enElPaso: tipo');

    (esperado['comando'] as Map<String, dynamic>).forEach((campo, valorEsperado) {
      final salio = paso.comando[campo];
      final etiqueta = '$enElPaso / $campo';

      if (valorEsperado is String && valorEsperado.startsWith('@')) {
        final nombre = valorEsperado.substring(1);
        expect(salio, caso.clases[nombre], reason: '$etiqueta: id de $nombre');
      } else if (valorEsperado is String && valorEsperado.startsWith(r'$')) {
        final yaLigado = ligados[valorEsperado];
        if (yaLigado == null) {
          expect(salio, isNotNull, reason: '$etiqueta: tiene que ser un id');
          ligados[valorEsperado] = salio as String;
        } else {
          expect(salio, yaLigado, reason: '$etiqueta: $valorEsperado tiene que repetirse');
        }
      } else if (valorEsperado is num) {
        expect((salio as num).toDouble(), valorEsperado.toDouble(), reason: etiqueta);
      } else {
        expect(salio, valorEsperado, reason: etiqueta);
      }
    });
  }
}
```

- [ ] **Step 2: Escribir la prueba que corre el corpus**

`movil/test/voz/parser_voz_corpus_test.dart`:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/parser_voz.dart';

import 'corpus_de_voz.dart';

/// La gramatica del telefono contra el mismo corpus que la del servidor.
///
/// Si esta prueba se pone roja y la de Java sigue verde, el telefono se separo.
void main() {
  for (final caso in cargarCorpus()) {
    for (final frase in caso.frases) {
      test('${caso.id}: $frase', () {
        verificarCaso(caso, frase, ParserVoz().interpretar(frase, caso.contexto));
      });
    }
  }
}
```

- [ ] **Step 3: Correr y ver qué falla**

Run: `cd movil && flutter test test/voz/parser_voz_corpus_test.dart`
Expected: FALLAN algunos casos. Es lo normal y es el punto de la tarea: son las diferencias entre las dos gramáticas que las pruebas escritas a mano no encontraron.

- [ ] **Step 4: Arreglar el Dart caso por caso hasta el verde**

Por cada caso rojo: leer el mensaje —nombra el caso, la frase, el paso y el campo—, ir al patrón correspondiente en `ParserVoz.java`, y corregir el Dart. **El Java no se toca**: si el desacuerdo parece un defecto del servidor, se anota y se pregunta, no se arregla acá.

Los sospechosos habituales, en orden de probabilidad: el escapado de una expresión regular; `unicode: true` faltando; un `split` que dejó una parte vacía; y el orden de una rama en la cascada.

Run: `cd movil && flutter test test/voz/parser_voz_corpus_test.dart`
Expected: PASA, tantas pruebas como frases tenga el corpus.

- [ ] **Step 5: Correr las dos suites enteras**

Run: `cd movil && flutter test` y después `cd /c/dev/forja && ./mvnw -q test`
Expected: las dos PASAN. Este es el momento en que el teléfono y el servidor dicen lo mismo, y hay una prueba que lo sostiene.

- [ ] **Step 6: Commit**

```bash
git add movil/test/voz/corpus_de_voz.dart movil/test/voz/parser_voz_corpus_test.dart movil/lib/voz/parser_voz.dart
git commit -m "Correr el corpus compartido tambien en el telefono"
```

---

### Task 10: Cablear el dictado del móvil al parser local

**Files:**
- Modify: `movil/lib/pantallas/lienzo.dart:160-196` (`_dictar`) y `:178-193` (los avisos)
- Test: `movil/test/voz/dictado_offline_test.dart`

**Interfaces:**
- Consumes: `ParserVoz`, `ContextoDelDiagrama`, `ClaseConocida`, `comandosDe` (Tasks 3, 4, 6, 7, 8).
- Produces: el camino completo sin red. Nada lo consume: es el final del plan.

- [ ] **Step 1: Escribir la prueba que falla**

`movil/test/voz/dictado_offline_test.dart`. Prueba la función pura que se va a extraer, sin widgets ni red:

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/dictado_local.dart';

void main() {
  test('una frase dictada se convierte en comandos listos para encolar, sin red', () {
    var n = 0;
    final resultado = interpretarDictado(
      'a Paciente agregale el atributo nombre de tipo texto',
      ContextoDelDiagrama.de(const [ClaseConocida(id: 'id-paciente', nombre: 'Paciente')]),
      () => 'token-${n++}',
    );

    expect(resultado.interpretacion.entendida, isTrue);
    expect(resultado.comandos, hasLength(1));
    expect(resultado.comandos.single.tipo, 'ATRIBUTO_AGREGAR');
    expect(resultado.comandos.single.origen, 'VOZ');
    expect(resultado.comandos.single.carga['claseId'], 'id-paciente');
  });

  test('una frase que no se entiende no produce comandos y devuelve sugerencias', () {
    final resultado = interpretarDictado(
      'bla bla bla',
      ContextoDelDiagrama.vacio(),
      () => 'token',
    );

    expect(resultado.interpretacion.entendida, isFalse);
    expect(resultado.comandos, isEmpty);
    expect(resultado.interpretacion.sugerencias, isNotEmpty);
  });
}
```

- [ ] **Step 2: Correr y verificar que falla**

Run: `cd movil && flutter test test/voz/dictado_offline_test.dart`
Expected: FALLA, `Target of URI doesn't exist: 'package:forja_movil/voz/dictado_local.dart'`.

- [ ] **Step 3: Escribir `dictado_local.dart`**

`movil/lib/voz/dictado_local.dart`:

```dart
import '../comandos.dart';
import 'contexto.dart';
import 'interpretacion.dart';
import 'parser_voz.dart';

/// Lo que sale de dictar: lo que se entendio, para mostrarlo, y los comandos,
/// para aplicarlos.
class DictadoLocal {
  const DictadoLocal({required this.interpretacion, required this.comandos});

  final Interpretacion interpretacion;
  final List<Comando> comandos;
}

final _parser = ParserVoz();

/// Interpreta una frase dictada en el aparato. No toca la red: el telefono
/// entiende solo, tenga o no conexion.
DictadoLocal interpretarDictado(
    String frase, ContextoDelDiagrama contexto, String Function() token) {
  final interpretacion = _parser.interpretar(frase, contexto);
  return DictadoLocal(
    interpretacion: interpretacion,
    comandos: comandosDe(interpretacion, token),
  );
}
```

- [ ] **Step 4: Correr y verificar que pasa**

Run: `cd movil && flutter test test/voz/dictado_offline_test.dart`
Expected: PASA, 2 pruebas.

- [ ] **Step 5: Cablearlo en la pantalla**

En `movil/lib/pantallas/lienzo.dart`, dentro de `_dictar` (`:160-196`):

1. Armar el contexto con las clases del modelo local que la pantalla ya tiene en memoria, mapeando cada una a `ClaseConocida(id: ..., nombre: ...)`.
2. Reemplazar la llamada a `api.dictar(...)` por `interpretarDictado(frase, contexto, () => <el mismo generador de token que ya usa la pantalla para encolar>)`.
3. Aplicar los comandos por el `Sincronizador`, por el mismo camino que ya usan los cambios hechos con el dedo.
4. Cuando `interpretacion.entendida` es falso, mostrar `interpretacion.sugerencias` en vez de un error.
5. Borrar el `catch (SinConexion)` que mostraba *"El dictado necesita conexión por ahora."* (`:191-193`): ya no puede pasar.
6. Corregir el comentario de `:178-180`, que dice que la gramática vive en el servidor. Ahora vive en los dos, y lo que las mantiene iguales es el corpus.

- [ ] **Step 6: Correr todo y compilar el APK**

Run: `cd movil && flutter analyze && flutter test && flutter build apk --debug`
Expected: sin advertencias, todas las pruebas en verde, APK construido.

- [ ] **Step 7: Probarlo a mano en el teléfono, en modo avión**

Esto no lo cubre ninguna prueba automatizada y es el único paso que demuestra que el requisito está cumplido:

1. Instalar el APK en el A56.
2. Comprobar que el **paquete de español del reconocedor de Android** está descargado (Ajustes → Administración general → Idioma y entrada → Voz).
3. **Poner el teléfono en modo avión.**
4. Abrir un diagrama ya sincronizado y dictar: *"crea la clase Factura"*, *"a Factura agregale el atributo total de tipo decimal"*, *"un Paciente tiene muchas Consultas"*.
5. Verificar que el diagrama cambia al instante y que no aparece ningún aviso de conexión.
6. Salir de modo avión y comprobar que la cola se vacía contra el servidor.

Si el paso 4 falla porque el reconocedor no arranca sin red, el problema es el paquete de idioma, no el parser: el paso 3 de esta tarea ya probó que la interpretación no toca la red.

- [ ] **Step 8: Commit**

```bash
git add movil/lib/voz/dictado_local.dart movil/test/voz/dictado_offline_test.dart movil/lib/pantallas/lienzo.dart
git commit -m "Dictar sin conexion: el telefono interpreta en el aparato"
```

---

## Lo que queda para los planes siguientes

- **Plan 2** — el agente guía del móvil portado a Dart (paso 4 del diseño): `compartido/corpus-agente.json` con los casos de `BaseDeConocimientoTest.java`, y las 15 reglas de `BaseDeConocimiento.java:46-68` en `movil/lib/agente/`.
- **Plan 3** — `TraductorOllama` y el pedido explícito con previsualización en la web (paso 5).
- **Plan 4** — el canal Kotlin y Gemma 3 1B en el móvil (paso 6), el único recortable.
