# La app móvil, frontend del backend generado

Diseño — 22 de septiembre de 2026.

Sucede al diseño del 16 de septiembre (`2026-09-16-ia-local-en-el-movil-design.md`), del que
hereda el principio central y al que le corrige el destinatario: aquel documento daba por hecho
que el cliente móvil era un cliente **de FORJA**, y no lo es.

## El problema

El enunciado pide una app móvil que **consuma el backend generado**, con IA en la interacción y
operativa sin conexión con sincronización.

Hoy la app móvil no consume el backend generado. Es un cliente de FORJA: entra con una credencial
(`pantallas/entrar.dart`), lista y baja diagramas (`pantallas/diagramas.dart`), los dibuja
(`pantallas/lienzo.dart`) y los dicta (`voz/`). Veinte archivos Dart que hablan con FORJA y con
nadie más. El backend que FORJA genera —la razón de ser de la herramienta— **no lo toca ningún
cliente**: se genera, se descarga y ahí termina.

Dicho de otro modo: la mitad del requisito que habla de *offline* y *voz* está cumplida y probada;
la mitad que dice *consume el backend generado* no existe.

Y hay una segunda deuda. El diseño del 16 de septiembre decidió que en el teléfono corriera
**Gemma 3 1B**. Nunca se implementó:

- `movil/pubspec.yaml` declara `http`, `path_provider`, `web_socket_channel` y `speech_to_text`.
  Ninguna librería de inferencia.
- No hay ningún `.task`, `.gguf` ni `.tflite` en el repositorio.
- `movil/lib/modelo_local.dart` engaña por el nombre: es el aplicador local de comandos sobre el
  diagrama —«modelo» de datos—, no un modelo de lenguaje.

Lo que sí existe, y funciona, son las 947 líneas de `movil/lib/voz/`, de las cuales 504 son la
gramática determinista de `parser_voz.dart`.

## Qué se decide acá

1. **La app móvil pasa a ser el frontend del backend generado.** Las pantallas CRUD se derivan del
   diagrama **en tiempo de ejecución**, no se generan como código: así una sola app sirve para
   cualquier diagrama sin recompilar.
2. **Viajan dos cosas distintas por caminos distintos.** La *definición* (el diagrama) baja de
   FORJA; los *datos* (los registros que se cargan) van al backend generado.
3. **Sin conexión funciona todo menos la primera bajada de la definición.**
4. **Tres escalones de interacción**, de más barato a más caro: tocar, hablar, preguntar.
5. **Se hereda el principio del 16 de septiembre**: el LLM no emite comandos, **propone frases
   canónicas**, y el parser determinista es lo único que escribe. El modelo nunca aplica nada por
   su cuenta.
6. **El modelo es Gemma 3 1B int4** (529 MB, formato `.task`) por `flutter_gemma`. El binario no
   entra al repositorio; el código que lo carga y lo usa, sí.

## La arquitectura

FORJA genera un backend con una API completamente predecible —`EscritorCapas.java` emite siempre
la misma forma— y sin capa de seguridad:

```
GET    /api/<entidad>          POST   /api/<entidad>
GET    /api/<entidad>/{id}     PUT    /api/<entidad>/{id}
                               DELETE /api/<entidad>/{id}
```

Esa previsibilidad es lo que hace innecesario cualquier descubrimiento en tiempo de ejecución: el
teléfono deduce las rutas del mismo diagrama del que salió el backend. Nada de OpenAPI, nada de
introspección, nada que se pueda desincronizar.

| Del diagrama | Sale en el teléfono |
|---|---|
| cada clase | una pantalla de lista y un formulario |
| el nombre de la clase | la ruta `/api/<entidad>` |
| cada atributo y su tipo | un campo del formulario, con su validación |

## De dónde sale cada cosa

**La definición** baja de FORJA, que está desplegada y es alcanzable por HTTPS desde cualquier
lado. El teléfono entra con su credencial, elige el proyecto y guarda el diagrama en el almacén
local. Esto requiere conexión **una vez**; después queda en el aparato.

**Los datos** van al backend generado, que corre en la máquina de quien demuestra. El teléfono lo
alcanza por red local —basta el punto de acceso del propio teléfono, que no necesita internet— y
mientras no lo alcance, encola.

La separación importa porque las dos mitades tienen disponibilidades distintas: la definición se
consigue una vez y con comodidad; los datos tienen que poder escribirse siempre, haya o no red.

## Sin conexión

Es la restricción que ordena el diseño, no una característica que se agrega al final.

Funcionan sin red: abrir la app, ver las pantallas, crear, editar y borrar registros, el dictado,
el modelo, y el encolado de los cambios pendientes.

No funciona sin red: la primera bajada de la definición.

La maquinaria de almacén y cola ya está escrita (`almacen.dart`, `sincronizador.dart`); hoy apunta
a FORJA y hay que darle un segundo destino. Se conserva el identificador de operación, pero contra
el backend generado sirve sólo puertas adentro del teléfono: marca la fila local como pendiente y
ordena la cola. **El backend generado no tiene idempotencia.** `ApiGenerada.crear()` manda
únicamente `datos`, y el `@PostMapping` que emite `EscritorCapas.java` recibe un `@RequestBody` de
la entidad pelado: no tiene dónde recibir ese identificador ni con qué compararlo. La consecuencia
es concreta: un POST que llega pero cuya respuesta se pierde deja la operación encolada, y la
próxima sincronización duplica la fila. La idempotencia por token existe en FORJA —hay una tabla
`operacion` con unicidad por `(diagrama_id, token_cliente)`— y el supuesto se copió desde ahí a un
backend que no tiene esa tabla.

## La voz y el modelo

**Primer escalón, tocar.** Listas y formularios corrientes.

**Segundo escalón, hablar.** El reconocedor de Android convierte audio en texto y el parser
determinista lo convierte en una operación. El vocabulario se muda del dominio del diagrama al de
los registros: donde hoy entiende «creá una clase Paciente», pasa a entender «agregá un paciente
llamado Juan».

Aquí hay un defecto que corregir. `pantallas/lienzo.dart:763` llama a `listen()` con
`SpeechListenOptions(localeId, partialResults, cancelOnError)` y **sin `onDevice: true`**. El
comentario de `voz/dictado_local.dart` afirma que el dictado funciona en modo avión, pero la
llamada no lo garantiza: sin esa opción el reconocedor puede salir a la red, y con
`cancelOnError: true` el primer fallo corta el dictado. Se agrega la opción y **se comprueba en
modo avión con el teléfono en la mano**; no hay forma de verificarlo leyendo código.

**Tercer escalón, preguntar.** Cuando el parser no entiende, entra Gemma 3 1B. Y entra como se
decidió el 16 de septiembre: **propone una frase canónica**, que el parser determinista vuelve a
interpretar, y el usuario confirma antes de que se aplique. El modelo no escribe en la base ni
emite operaciones.

Esa barrera no es ceremonia. En la web ya ocurrió: un respaldo por IA aplicaba sin revisión y
creaba clases fantasma en silencio. El modelo propone; las reglas disponen.

**Dónde vive el modelo.** El `.task` de 529 MB se empuja al almacenamiento de la app con
`adb push`. No entra al repositorio: GitHub rechaza archivos de más de 100 MB, y Git LFS —aunque
el archivo entra en el giga gratuito— gasta 529 MB de descarga por clonación, de modo que a la
segunda clonación del mes el repositorio deja de poder clonarse. Al repositorio van el plugin, el
cargador, el prompt, la integración y sus pruebas.

**Si el modelo no está, o no carga, la app funciona igual**: pierde el tercer escalón y nada más.
El modelo no puede ser nunca el camino principal.

## Lo que no entra

- **No se genera un frontend web.** Es la alternativa atractiva —«genera backend *y* frontend»—
  pero es un generador nuevo entero, con plantillas propias, y no aporta nada al requisito de
  funcionar sin conexión. Queda como extra posterior.
- **No se despliega el backend generado a la nube.** La instancia tiene 1 GB de memoria y ya corre
  FORJA con su PostgreSQL.
- **No se implementa autenticación contra el backend generado**, porque el backend generado no la
  tiene.
- **No se toca el generador.** Lo que emite hoy alcanza.

## Riesgos conocidos

| Riesgo | Por qué importa | Cómo se cierra |
|---|---|---|
| El APK nunca corrió en un teléfono | Todo lo demás depende de que arranque | Instalarlo primero, antes que cualquier otra cosa |
| `onDevice` ausente en el dictado | El requisito central es funcionar sin red | Agregarlo y probar en modo avión |
| El paquete de español puede no estar en el aparato | Sin él no hay reconocimiento sin red | Descargarlo hoy, mientras hay internet |
| 529 MB por `adb push` | Es lento y hay que hacerlo con el cable | Hoy, con el teléfono a mano |
| El teléfono no alcanza la máquina | Sin eso no se ve la sincronización | Punto de acceso del teléfono, que no depende de la red del lugar |

## Orden de trabajo

El modelo va último, y es deliberado: es la pieza más cara y la única prescindible. Primero que la
app corra en el teléfono, después que consuma el backend generado, después la voz, y el modelo al
final, cuando todo lo que no depende de él ya esté probado.

El plan de implementación detalla los pasos.
