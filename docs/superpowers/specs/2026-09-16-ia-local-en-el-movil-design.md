# IA local en el móvil, respaldo remoto en la web

Diseño — 16 de septiembre de 2026. Bloque C del plan.

## El problema

El enunciado exige que la IA del cliente móvil sea **local en el aparato**. La del cliente web
puede ser cualquiera, incluida una remota.

Hoy el móvil no cumple. El dictado captura la voz con el reconocedor de Android y después
**manda la frase al servidor** para que la interprete `ParserVoz.java`. Está escrito en el propio
código:

> `movil/lib/pantallas/lienzo.dart:178-180` — *"El dictado lo interpreta el servidor: la gramática
> vive allí para que el teléfono y la web entiendan exactamente lo mismo. Sin conexión todavía no
> funciona."*

Y el usuario lo ve como un rechazo (`lienzo.dart:191-193`): *"El dictado necesita conexión por ahora."*

De fondo hay algo más grande: **no existe ningún LLM en el repositorio**. Ni en la web, ni en el
backend, ni en el móvil. Lo que hay es reconocimiento de voz del sistema operativo, OCR con
tesseract.js dentro del navegador, dos parsers deterministas por expresiones regulares en Java
(~1.100 líneas, con ~2.100 de pruebas) y el agente guía simbólico de reglas. Gemma 3 1B aparece una
sola vez en todo el repositorio, como pendiente en `movil/README.md:84`.

La prosa del backend ya promete lo que el código todavía no tiene. `ServicioAgente.java:41-43` dice
que están "los dos paradigmas de IA": el simbólico existe y está probado, el generativo no existe.
El paso 3 de la evaluación es correspondencia total documento ↔ aplicación, así que esa frase hoy
es un pasivo.

## Qué se decide acá

1. **El teléfono interpreta solo, sin red, con paridad total.** Las 19 formas de frase de la
   gramática, más los 5 patrones auxiliares que usan sus manejadores.
2. **La gramática se porta a Dart como espejo**, y lo que impide que las dos copias se separen es un
   **corpus de casos compartido** que corren las dos suites de pruebas.
3. **Hay un traductor de respaldo para frases libres**: local (Gemma 3 1B) en el teléfono por
   requisito, remoto (Claude) en la web porque está permitido y sirve de seguro.
4. **El traductor no emite comandos.** Reescribe la frase a una forma canónica y la gramática la
   vuelve a interpretar.

El punto 4 cambia lo que estaba anotado el 12 de septiembre, que era "el LLM traduce la frase a un
comando JSON". Las razones del cambio están en la sección de arquitectura.

## Arquitectura

Un solo concepto, dos encarnaciones:

```
frase (dictada o escrita)
  │
  └─> gramática determinista          ParserVoz.java  /  parser_voz.dart
        ├── entendida ──────────────> pasos ──> aplicador
        └── no entendida
              │
              └─> traductor de respaldo   (presupuesto duro de 3 s)
                    ├── devuelve frase canónica ──> gramática otra vez
                    │        ├── entendida ──────> pasos ──> aplicador
                    │        └── no entendida ──> sugerencias
                    └── apagado, sin modelo, o se venció ──> sugerencias
```

La gramática va primero **siempre**, en los dos clientes. La aplicación entera funciona aunque el
traductor no exista, que es la propiedad que hace que este bloque sea recortable sin dejar sin app.

### Por qué el traductor reescribe la frase y no emite comandos

- **El motor determinista sigue siendo el único que toca el diagrama.** La validación sale gratis:
  si la frase reescrita no parsea, se descarta. No hay que escribir un validador de JSON generado.
- **Un modelo de 1B acierta mucho más** en "decí esto de la otra forma" que en emitir JSON válido
  con UUIDs, tipos de operación y nombres de campo exactos.
- **Un solo contrato para los dos lados**, y la misma forma de prueba en Java y en Dart.
- El costo es que el traductor queda limitado a lo que la gramática sabe expresar. Como la gramática
  cubre las 19 formas, ese techo no aprieta.

### El contrato

```
Traductor:
    Optional<String> aFraseCanonica(String frase, List<String> clasesConocidas)
```

Vacío significa "no pude", y siempre es una respuesta aceptable. Tres implementaciones:

| Implementación | Dónde | Qué usa |
|---|---|---|
| `TraductorNulo` | los dos | devuelve vacío; es el valor por omisión |
| `TraductorRemoto` | backend Java | Claude por el SDK oficial |
| `TraductorGemma` | móvil Dart | Gemma 3 1B por MediaPipe, en el aparato |

## Componentes

### 1. `compartido/corpus-voz.json` — nuevo

El corpus es la defensa contra la deriva. Hoy los 28 casos de `ParserVozTest.java` están escritos
en Java; se sacan a datos y los leen **las dos** suites.

```json
{
  "contextos": {
    "clinica": { "clases": ["Paciente", "Consulta", "Persona", "Medico", "Auditable"] },
    "vacio":   { "clases": [] }
  },
  "casos": [
    {
      "id": "crear-clase-formas-usuales",
      "contexto": "clinica",
      "frases": ["crea la clase Factura", "crear clase Factura", "nueva clase Factura"],
      "entendida": true,
      "pasos": [
        { "tipo": "CLASE_CREAR",
          "comando": { "claseId": "$nueva1", "nombre": "Factura", "estereotipo": null,
                       "esAbstracta": false, "posX": 0, "posY": 0 } }
      ]
    }
  ]
}
```

Dos clases de identificador aparecen en la salida del parser y las dos necesitan una convención,
porque un UUID generado no se puede comparar literal:

- **`@Paciente`** — el id de una clase que ya existe. Se resuelve contra el contexto del caso.
- **`$nueva1`** — un id que el parser inventó. El verificador lo **liga** a lo que haya salido la
  primera vez y después exige que coincida. Así el caso "los atributos cuelgan de la clase recién
  creada" queda verificado sin conocer el UUID.

Un caso con `"entendida": false` no lleva `pasos`: lleva `"sugerencias"` con las que tienen que
volver, que es la otra mitad del comportamiento y también tiene que ser igual en los dos clientes.

El verificador es la única pieza que se escribe dos veces: unas 40 líneas en cada lado que recorren
esperado contra obtenido resolviendo `@` y ligando `$`.

### 2. El parser en Dart — `movil/lib/voz/`

- `contexto.dart` — espejo de `ContextoDelDiagrama` (`operacion/ContextoDelDiagrama.java`):
  normalización `clave()` sin acentos, y `resolver()` con coincidencia parcial **solo cuando es
  única**.
- `interpretacion.dart` — espejo de `Interpretacion`: `{frase, entendida, pasos, explicacion,
  sugerencias}`.
- `parser_voz.dart` — la cascada de 19 patrones, en el mismo orden y con los mismos nombres, más
  los 5 auxiliares (`DETALLE_ATRIBUTO_CON_TIPO`, `DETALLE_ATRIBUTO_SIN_TIPO`, `DETALLE_METODO`,
  `PARAMETRO`, `LONGITUD`) que usan los manejadores.

La salida se convierte a los `Comando` que el móvil **ya sabe aplicar y encolar**: `comandos.dart`
tiene constructores de fábrica para los 11 tipos, con los mismos nombres de campo que el backend, y
ya está ejercitado por `sincronizador_test.dart`. El port es solo la gramática; el modelo de
comandos no se toca.

Diferencias de lenguaje a cuidar en el port, y por eso están en el corpus:

- `Normalizer.Form.NFD` de Java no existe igual en Dart. La normalización sin acentos se hace con
  una tabla de reemplazo explícita, y el corpus la prueba ("creá la clase Médico." → `Medico`).
- Java usa `matches()` (ancla la expresión entera); en Dart hay que anclar con `^...$` a mano.
- Dart no tiene `Optional`; se usa nulable.

### 3. El traductor local — `movil/lib/voz/traductor_gemma.dart` + canal Kotlin

El puente a MediaPipe se escribe como **canal de plataforma propio en Kotlin**, no con el plugin
comunitario `flutter_gemma`. Dos razones: el paso 4 de la evaluación es defensa de autoría, y no
queda atado a que un tercero siga el ritmo de MediaPipe. Es ~100 líneas contra
`com.google.mediapipe.tasks.genai.llminference`.

- El `.task` de Gemma 3 1B int4 (~550 MB) **no va dentro del APK**: se descarga en el primer
  arranque o se copia a mano al teléfono, y queda en `path_provider`.
- Bandera de configuración: si el archivo no está, `TraductorGemma` se comporta como
  `TraductorNulo`. La aplicación no cambia de comportamiento, solo pierde el respaldo.
- **Presupuesto duro de 3 s.** Al vencerse se cancela y se muestran las sugerencias de la gramática.
  Nunca un indicador de progreso colgado.

### 4. El traductor remoto — `bo.forja.backend.ia`

- Dependencia nueva: `com.anthropic:anthropic-java:2.34.0`. Es la única que se agrega al `pom.xml`.
- Modelo `claude-opus-5`, esfuerzo `low` (la tarea es reescribir una frase corta).
- **`maxRetries(0)`**: el SDK reintenta los timeouts por omisión, y con reintentos el presupuesto de
  3 s se convierte en 9 s de reloj. Hay que apagarlos explícitamente.
- Configuración en `application.yml` bajo `forja.ia.remota`, **apagado por omisión**, con la clave
  leída del ambiente. Sin clave, el bean que se registra es `TraductorNulo` y el backend se comporta
  exactamente como hoy — eso es lo que mantiene verdes las 209 pruebas actuales sin tocarlas.

El prompt le da las clases que existen en el diagrama y las formas canónicas, y le pide una sola
línea de salida. Si devuelve algo que la gramática no parsea, se descarta sin ruido.

### 5. El cableado

**Móvil (`lienzo.dart::_dictar`)** — deja de llamar a `api.dictar(...)`. El texto que devuelve
`SpeechToText` entra al parser local, y los comandos salen por el `Sincronizador` que ya existe:
se aplican al modelo local al instante y se encolan. En modo avión el dictado funciona completo.

**Backend (`ServicioVoz.dictar`)** — cuando `Interpretacion.entendida()` es falso y hay traductor,
reescribe y vuelve a interpretar una sola vez. Sin traductor, el método hace exactamente lo de hoy.

El endpoint `POST /api/diagramas/{id}/voz` no cambia de forma. El móvil simplemente deja de usarlo
para interpretar; lo sigue usando el cliente web.

## Qué no cambia

- La gramática de `ParserVoz.java`: no se toca ni una expresión regular. El espejo se escribe
  contra lo que hay.
- El formato de los comandos, los tipos de operación y la bitácora.
- Los endpoints y sus contratos.
- El flujo del cliente web, salvo que ahora entiende más frases.
- El agente guía, que sigue siendo simbólico y no se mezcla con esto.

## Pruebas

| Qué | Dónde | Cómo se verifica |
|---|---|---|
| La gramática, en Java | `ParserVozCorpusTest.java` | lee `compartido/corpus-voz.json` |
| La gramática, en Dart | `test/voz/parser_voz_corpus_test.dart` | lee el mismo archivo |
| Lo que no entra en el corpus | `ParserVozTest.java` | casos propios de Java que no se puedan expresar como datos |
| Traductor remoto | `TraductorRemotoTest.java` | servidor de prueba local, sin red |
| Traductor local | `traductor_gemma_test.dart` | canal falso; el modelo real no entra en las pruebas |
| Dictado sin conexión | `test/voz/dictado_offline_test.dart` | frase → comandos → cola, sin API |

La migración de los 28 casos se hace **uno por uno**: cada caso movido al corpus tiene que seguir
pasando en Java antes de que el Dart lo mire. Si un caso no se puede expresar como datos, se queda
en `ParserVozTest.java` y se anota por qué.

## Orden y riesgo

El orden está puesto para que el requisito quede cumplido **antes** de que empiece la parte frágil.

| # | Paso | Deja | Riesgo |
|---|---|---|---|
| 1 | Corpus + migración de los 28 casos en Java | la red de seguridad | bajo |
| 2 | Parser en Dart hasta pasar el corpus | el teléfono entiende solo | bajo, mecánico |
| 3 | Cablearlo en `lienzo.dart` | **el requisito cumplido**: dicta en modo avión | bajo |
| 4 | Traductor remoto en la web | el seguro: IA generativa demostrable | bajo, aislado |
| 5 | Canal Kotlin + Gemma en el móvil | IA generativa local | **alto** |

Después del paso 3 el enunciado está satisfecho. Después del 4 hay algo generativo que mostrar
aunque el 5 no salga. El paso 5 es el único que puede recortarse, y recortarlo no deja sin
aplicación ni obliga a reescribir el documento: obliga a no prometerlo.

El riesgo declarado sigue siendo el mismo de siempre y no es el modelo: es el puente
Flutter↔MediaPipe. Dos trampas conocidas que hay que ensayar y no descubrir el 23:

1. El reconocimiento de voz de Android solo anda sin red si el **paquete de español está descargado
   en el teléfono**. Hay que bajarlo en el A56 y ensayar en modo avión.
2. El `.task` tiene que estar en el aparato antes de la defensa, con copia en USB. No se descarga
   nada en la red de la universidad ese día.

## Preguntas abiertas

- **El modelo remoto.** Queda `claude-opus-5` porque es el que corresponde por omisión. Para
  reescribir una frase corta con presupuesto de 3 s, `claude-haiku-4-5` es más rápido y más barato;
  es decisión del usuario y se cambia en una línea.
Queda una sola pregunta abierta. Lo que el teléfono hace cuando **sí** hay red ya está decidido más
arriba y no se reabre: interpreta local siempre. La alternativa —preguntarle al servidor cuando hay
conexión— se descartó porque haría que el teléfono se comporte distinto según la red, y eso en una
demostración en vivo es lo peor que puede pasar.
