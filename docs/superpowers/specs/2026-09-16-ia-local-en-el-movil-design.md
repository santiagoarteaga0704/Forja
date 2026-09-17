# La IA de FORJA: local en el teléfono, local en la máquina

Diseño — 16 de septiembre de 2026. Bloque C del plan.
Revisado el mismo día: la IA de la web pasó de remota (Claude por API) a local (Ollama), y el
traductor pasó de devolver una frase a devolver varias.

## El problema

El enunciado exige que la IA del cliente móvil sea **local en el aparato**. La del cliente web
puede ser cualquiera. Y la web tiene que tener una IA que **tome comandos y haga lo que se le
pida**, aparte del agente guía que enseña a usar la herramienta.

Hoy el móvil no cumple. El dictado captura la voz con el reconocedor de Android y después
**manda la frase al servidor** para que la interprete `ParserVoz.java`. Está escrito en el propio
código:

> `movil/lib/pantallas/lienzo.dart:178-180` — *"El dictado lo interpreta el servidor: la gramática
> vive allí para que el teléfono y la web entiendan exactamente lo mismo. Sin conexión todavía no
> funciona."*

Y el usuario lo ve como un rechazo (`lienzo.dart:191-193`): *"El dictado necesita conexión por ahora."*

El agente guía del móvil tampoco es local: pregunta al servidor (`movil/lib/api.dart:159-163`).

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
3. **El LLM no emite comandos: propone frases canónicas**, y la gramática determinista es la única
   que toca el diagrama.
4. **En la web el LLM propone varias frases de una vez**, así un pedido se convierte en un diagrama.
5. **Los dos LLM son locales**: Gemma 3 1B en el teléfono, Gemma 3 4B en la máquina. Ninguna clave
   de API, ningún costo por token, ninguna dependencia de la red el día de la defensa.
6. **El agente guía del móvil se porta a Dart**, para que el teléfono no le pregunte nada a nadie.

El punto 3 cambia lo anotado el 12 de septiembre ("el LLM traduce la frase a un comando JSON"). El
punto 5 cambia la primera versión de este documento, que usaba la API de Claude: se descartó porque
la suscripción del usuario no cubre llamadas de API —son productos con facturación separada— y
porque depender de internet el día de la defensa era un riesgo que ya estaba anotado.

## Arquitectura

Un solo concepto, dos encarnaciones:

```
frase (dictada o escrita)
  │
  └─> gramática determinista          ParserVoz.java  /  parser_voz.dart
        ├── entendida ──────────────> pasos ──> aplicador
        └── no entendida
              │
              └─> traductor local  ──> 0..N frases canónicas
                    │                    │
                    │                    └─> cada una entra por la gramática otra vez
                    │                          ├── parsea ──> pasos
                    │                          └── no parsea ──> se descarta, sin ruido
                    └── apagado, sin modelo, o se venció ──> sugerencias de la gramática
```

La gramática va primero **siempre**, en los dos clientes. La aplicación entera funciona aunque el
traductor no exista, que es la propiedad que hace que este bloque sea recortable sin dejar sin app.

### Por qué el traductor propone frases y no emite comandos

- **El motor determinista sigue siendo el único que toca el diagrama.** La validación sale gratis:
  si una frase propuesta no parsea, se descarta. No hay que escribir un validador de JSON generado,
  y el modelo no puede inventar un id, un tipo de operación ni un nombre de campo.
- **Un modelo chico acierta mucho más** en "decí esto de la otra forma" que en emitir JSON válido.
  Esto importa el doble ahora que los dos modelos son de 1B y 4B.
- **Un solo contrato para los dos lados**, y la misma forma de prueba en Java y en Dart.
- El costo es que el traductor queda limitado a lo que la gramática sabe expresar. Como la gramática
  cubre las 19 formas, ese techo no aprieta.

### El contrato

```
Traductor:
    List<String> aFrasesCanonicas(String pedido, List<String> clasesConocidas)
```

Una lista vacía significa "no pude", y siempre es una respuesta aceptable. Devolver una lista y no
una frase es lo que convierte *"armá un diagrama de una veterinaria con dueños, mascotas y
consultas"* en cinco operaciones:

```
crea la clase Dueno con los atributos nombre de tipo texto y telefono de tipo texto
crea la clase Mascota con los atributos nombre de tipo texto y especie de tipo texto
crea la clase Consulta con el atributo fecha de tipo fecha
Dueno tiene muchas Mascotas
Mascota tiene muchas Consultas
```

Tres implementaciones:

| Implementación | Dónde | Qué usa |
|---|---|---|
| `TraductorNulo` | los dos | devuelve lista vacía; es el valor por omisión |
| `TraductorOllama` | backend Java | Gemma 3 4B por HTTP a `localhost:11434` |
| `TraductorGemma` | móvil Dart | Gemma 3 1B por MediaPipe, en el aparato |

### Dos presupuestos de tiempo, no uno

Un presupuesto único no sirve para las dos cosas que hace el traductor:

| Camino | Presupuesto | Al vencerse |
|---|---|---|
| **Dictado en vivo** — hablaste y estás esperando | **3 s duros** | sugerencias de la gramática, nunca un indicador colgado |
| **Pedido explícito** — "hacé esto", es una acción deliberada | **30 s con progreso y botón de cancelar** | se cancela y no se aplica nada |

El pedido explícito existe solo en la web. En el teléfono el trabajo es dictar, no pedir diagramas.

### El pedido explícito muestra antes de aplicar

Un modelo de 4B se equivoca más que uno grande, así que lo que propone **se previsualiza antes de
tocar el diagrama**, exactamente como ya hace la lectura de la foto de pizarra: `Foto.tsx` tiene un
flujo de tres pasos con revisión humana en el medio (`Foto.tsx:41`, `:154-163`) y muestra hasta las
líneas que ignoró (`ResumenDeLectura`, `Foto.tsx:225-274`).

El pedido explícito reusa ese patrón: se muestran las frases que el modelo propuso, cuáles parsearon
y cuáles no, y recién con un botón se aplican. No es una pantalla nueva ni un patrón nuevo; es el
que la aplicación ya tiene para la otra vía de entrada incierta.

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

### 3. El agente guía del móvil, portado a Dart — `movil/lib/agente/`

Hoy el teléfono pide los consejos al servidor. El alcance del port es **acotado**: el móvil consume
un solo endpoint del agente, `GET /api/diagramas/{id}/agente` (`api.dart:159-163`), que devuelve las
observaciones de `BaseDeConocimiento` sobre el diagrama. El recorrido de 8 pasos y el catálogo de
preguntas son de la web y **no se portan**.

Son las 15 reglas de inferencia de `BaseDeConocimiento.java:46-68` sobre el modelo local, más la
heurística de singular/plural de `:34-45`. Sin modelo de lenguaje: es el paradigma simbólico, y por
eso es el caso fácil — no depende del puente frágil.

Mismo tratamiento contra la deriva: **`compartido/corpus-agente.json`** con los casos de
`BaseDeConocimientoTest.java` (19 pruebas), leído por las dos suites.

### 4. El traductor local del móvil — `movil/lib/voz/traductor_gemma.dart` + canal Kotlin

El puente a MediaPipe se escribe como **canal de plataforma propio en Kotlin**, no con el plugin
comunitario `flutter_gemma`. Dos razones: el paso 4 de la evaluación es defensa de autoría, y no
queda atado a que un tercero siga el ritmo de MediaPipe. Es ~100 líneas contra
`com.google.mediapipe.tasks.genai.llminference`.

- El `.task` de Gemma 3 1B int4 (~550 MB) **no va dentro del APK**: se copia al teléfono y queda en
  `path_provider`.
- Si el archivo no está, `TraductorGemma` se comporta como `TraductorNulo`. La aplicación no cambia
  de comportamiento, solo pierde el respaldo.

### 5. El traductor local del backend — `bo.forja.backend.ia`

- **Ollama en `localhost:11434`**, modelo `gemma3:4b`. El tamaño lo decide el hardware de la máquina
  de la demostración: 16 GB de RAM y una RTX 3050 de **4 GB de VRAM**. Un 7B en Q4 pide ~5 GB y se
  desbordaría a CPU; un 4B entra entero en la placa.
- **Sin dependencias nuevas en el `pom.xml`.** Ollama habla HTTP y JSON; alcanza con el
  `RestClient` que ya trae Spring. El backend hoy no hace ninguna llamada saliente, así que esta es
  la primera, y es a `localhost`.
- Configuración en `application.yml` bajo `forja.ia.local`, **apagada por omisión**. Sin
  configuración el bean que se registra es `TraductorNulo` y el backend se comporta exactamente como
  hoy — eso es lo que mantiene verdes las 209 pruebas actuales sin tocarlas.
- `format: json` y `temperature: 0` en la llamada a Ollama, para que la salida sea estable.

El prompt le da las clases que existen y las formas canónicas con ejemplos, y le pide una frase por
línea. Lo que no parsea se descarta.

### 6. El cableado

**Móvil (`lienzo.dart::_dictar`)** — deja de llamar a `api.dictar(...)`. El texto que devuelve
`SpeechToText` entra al parser local, y los comandos salen por el `Sincronizador` que ya existe:
se aplican al modelo local al instante y se encolan. En modo avión el dictado funciona completo.

**Backend (`ServicioVoz.dictar`)** — cuando `Interpretacion.entendida()` es falso y hay traductor,
propone frases y las vuelve a interpretar, una sola vuelta. Sin traductor, hace lo de hoy.

**Web (`Dictado.tsx`)** — el campo de texto libre que ya existe gana el camino del pedido explícito,
con previsualización antes de aplicar.

El endpoint `POST /api/diagramas/{id}/voz` no cambia de forma. El pedido explícito estrena uno
nuevo, hermano del de la foto: `POST /api/diagramas/{id}/pedido/lectura` para previsualizar y
`POST /api/diagramas/{id}/pedido` para aplicar.

## Qué no cambia

- La gramática de `ParserVoz.java`: no se toca ni una expresión regular. El espejo se escribe
  contra lo que hay.
- El formato de los comandos, los tipos de operación y la bitácora.
- Los endpoints existentes y sus contratos.
- El agente guía de la web, que sigue siendo simbólico y no se mezcla con el LLM.

## Pruebas

| Qué | Dónde | Cómo se verifica |
|---|---|---|
| La gramática, en Java | `ParserVozCorpusTest.java` | lee `compartido/corpus-voz.json` |
| La gramática, en Dart | `test/voz/parser_voz_corpus_test.dart` | lee el mismo archivo |
| Las reglas del agente, en Java | `BaseDeConocimientoCorpusTest.java` | lee `compartido/corpus-agente.json` |
| Las reglas del agente, en Dart | `test/agente/agente_corpus_test.dart` | lee el mismo archivo |
| Lo que no entra en el corpus | `ParserVozTest.java` | casos propios de Java que no se puedan expresar como datos |
| Traductor de Ollama | `TraductorOllamaTest.java` | servidor HTTP de prueba local; el modelo real no entra |
| Traductor de Gemma | `traductor_gemma_test.dart` | canal falso; el modelo real no entra |
| Dictado sin conexión | `test/voz/dictado_offline_test.dart` | frase → comandos → cola, sin API |

Ninguna prueba automatizada invoca un modelo de verdad: los dos traductores se prueban contra un
doble. El modelo real se ejercita a mano, en el ensayo.

La migración de los 28 casos se hace **uno por uno**: cada caso movido al corpus tiene que seguir
pasando en Java antes de que el Dart lo mire. Si un caso no se puede expresar como datos, se queda
en `ParserVozTest.java` y se anota por qué.

## Orden y riesgo

El orden está puesto para que el requisito quede cumplido **antes** de que empiece la parte frágil.

| # | Paso | Deja | Riesgo |
|---|---|---|---|
| 1 | Corpus de voz + migración de los 28 casos en Java | la red de seguridad | bajo |
| 2 | Parser en Dart hasta pasar el corpus | el teléfono entiende solo | bajo, mecánico |
| 3 | Cablearlo en `lienzo.dart` | **el requisito cumplido**: dicta en modo avión | bajo |
| 4 | Corpus de agente + reglas en Dart | el teléfono no le pregunta nada a nadie | bajo, mecánico |
| 5 | `TraductorOllama` + pedido explícito con previsualización | **la IA que hace lo que le pedís** | medio |
| 6 | Canal Kotlin + Gemma 1B en el móvil | IA generativa local en el teléfono | **alto** |

Después del paso 3 el enunciado está satisfecho. Después del 5 hay IA generativa demostrable aunque
el 6 no salga. El paso 6 es el único que puede recortarse, y recortarlo no deja sin aplicación ni
obliga a reescribir el documento: obliga a no prometerlo.

El riesgo declarado sigue siendo el mismo y no es el modelo: es el puente Flutter↔MediaPipe. Tres
trampas conocidas que hay que ensayar y no descubrir el 23:

1. El reconocimiento de voz de Android solo anda sin red si el **paquete de español está descargado
   en el teléfono**. Bajarlo en el A56 y ensayar en modo avión.
2. El `.task` de Gemma tiene que estar en el aparato antes de la defensa, con copia en USB.
3. **`ollama pull gemma3:4b` son ~3,3 GB y se baja antes, no ese día.** Misma regla que el `.task`.
   Y Ollama tiene que estar corriendo cuando arranca el backend; si no está, el traductor queda nulo
   y la aplicación sigue funcionando con la gramática.

## Pregunta abierta

Queda una sola: **si la EC2 lleva el traductor o no.** Ollama no corre en una instancia chica, así
que la versión desplegada tendría la gramática sola y la versión local tendría además el LLM. Se
puede vivir con eso —el enunciado pide llevar las dos versiones y solo el móvil tiene que operar
sin conexión— pero hay que decidir si el documento lo dice o si se busca otra cosa para la nube.

Lo que el teléfono hace cuando **sí** hay red ya está decidido y no se reabre: interpreta local
siempre. La alternativa —preguntarle al servidor cuando hay conexión— se descartó porque haría que
el teléfono se comporte distinto según la red, y eso en una demostración en vivo es lo peor que
puede pasar.
