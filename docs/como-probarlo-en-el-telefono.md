# Como probar FORJA movil en un telefono de verdad

Guia para seguir con el telefono en la mano. Rescatada de los informes de
`.superpowers/sdd/2026-09-22-el-telefono-opera-el-backend-generado/` (carpeta
ignorada por git y borrada despues de esta consolidacion).

## 1. Que se instala

El APK correcto es el de **arm64-v8a**, que pesa **81,3 MB**
(`app-arm64-v8a-release.apk`, 85 253 428 bytes). Es el que corresponde a la
arquitectura de la gran mayoria de telefonos Android actuales.

Se compila con:

```
cd movil && flutter build apk --release --split-per-abi
```

Queda en:

```
movil/build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

`--split-per-abi` genera tres APK (armeabi-v7a 34,8 MB, arm64-v8a 81,3 MB,
x86_64 49,1 MB); el que va al telefono es el de arm64-v8a.

**Por que no el de depuracion:** el APK debug pesa **282 MB** (295 532 120
bytes) porque incluye el motor de inferencia sin optimizar para las tres ABIs
de una vez, mas un `.so` de vision que no se usa. Ademas el de release necesito
reglas de ProGuard propias (`movil/android/app/proguard-rules.pro`, con tres
`-dontwarn` para clases que MediaPipe referencia mencionadas pero no trae en su
`.aar`) para que `flutter build apk --release` no falle con "Missing classes
detected while running R8". Sin esas reglas el de release no compila.

Para instalarlo con el telefono conectado por cable:

```
adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
```

## 2. Antes de salir de casa, con internet

Dos cosas que hay que hacer **antes**, porque despues no va a haber red:

### 2.1 Bajar el diagrama una vez

Con conexion, abrir la app, entrar con la credencial, abrir el proyecto y bajar
el diagrama. Confirmar que quedo guardado localmente: cerrar la app y volver a
abrirla (tiene que seguir ahi sin pedir red). A partir de ahi el diagrama vive
en el telefono y no hace falta mas conexion para verlo ni para cargar
registros contra el.

### 2.2 Descargar el paquete de espanol para reconocimiento sin conexion

**Estado actual: `onDevice: true` esta SACADO del codigo.** Se probo en un
telefono real y el dictado se quedaba en "Escuchando..." sin transcribir
nada. La causa: ese telefono no tiene el paquete de espanol para
reconocimiento sin conexion instalado (se reviso la lista de idiomas
descargables y espanol no aparece). Sin ese paquete, `onDevice: true` deja al
reconocedor sin motor. Por eso, **en ese telefono, el dictado necesita
conexion** (WiFi o datos): el resto de la app -diagrama, registros a mano,
sincronizacion- sigue andando sin red.

Si el telefono que se va a usar SI tiene el paquete instalado, se puede volver
a activar el reconocimiento sin conexion: agregar `onDevice: true` de nuevo
a `listenOptions` en `movil/lib/voz/hoja_de_dictado.dart` (esta comentado ahi
mismo, con la explicacion). Para comprobar si un telefono tiene el paquete
antes de intentarlo:

1. En el telefono, abrir **Ajustes**
2. Entrar en **Sistema** -> **Idiomas y entrada**
3. Buscar **Reconocimiento de voz** (puede llamarse "Reconocimiento de voz de
   Google")
4. Entrar en **Reconocimiento sin conexion** (u "Offline recognition")
5. En la lista de idiomas, buscar **Espanol**
6. Tocar sobre Espanol. Debe decir "Instalado" o "Downloaded"; si no lo esta,
   descargarlo, esperar a que termine, y recien ahi agregar `onDevice: true`

## 3. El modelo (tercer escalon del dictado, opcional)

El dictado tiene tres escalones: la gramatica determinista, despues (si la
gramatica no entendio) un modelo de lenguaje local, y siempre una confirmacion
del usuario antes de crear nada. Sin el modelo, la app funciona completa, solo
que se pierde ese escalon intermedio.

**De donde sale el `.task`:** es `gemma3-1b-it-int4.task` (529 MB), de
Hugging Face, repositorio `google/gemma-3-1b-it-qat-q4_0-gguf`, variante
MediaPipe `.task` (la pagina pide aceptar la licencia de Gemma y tener cuenta).
El mismo archivo se publica en Kaggle bajo `google/gemma-3/tfLite/gemma3-1b-it-int4`.

**Advertencia: abrir la app una vez antes.** La carpeta de destino en el
telefono la crea Android recien cuando la app corre por primera vez. Si se
intenta el `adb push` antes de eso, falla. Primero instalar y abrir la app
(o `flutter run` una vez), despues empujar el modelo.

**El `adb push` con la ruta real:**

```
adb push gemma3-1b-it-int4.task /sdcard/Android/data/bo.forja.forja_movil/files/
```

Ojo con esta ruta: no es la que aparece en documentacion generica de Flutter
(`getApplicationDocumentsDirectory()`), que en Android cae en
`/data/user/0/bo.forja.forja_movil/app_flutter/`, **dentro** del sandbox de la
app. `adb push` no puede escribir ahi sin `run-as` ni root. Por eso el codigo
busca el `.task` primero en la carpeta de almacenamiento **externo**
(`getExternalStorageDirectory()`, que es exactamente la ruta de arriba) y
recien despues en la de documentos.

Son 529 MB: tarda varios minutos y `adb` no muestra progreso hasta el final.

Para comprobar que llego:

```
adb shell ls -lh /sdcard/Android/data/bo.forja.forja_movil/files/
```

## 4. Probar sin conexion (modo avion)

**Con `onDevice: true` sacado (ver 2.2), el dictado NO funciona en modo avion
en un telefono sin el paquete de espanol.** Este paso prueba lo que SI sigue
andando sin red: el diagrama descargado y la carga manual de registros.

1. Activar el **modo avion** en el telefono (WiFi y datos moviles apagados)
2. Abrir la app FORJA
3. Entrar a una entidad del diagrama (la pantalla de registros de esa clase)
4. Cargar dos o tres registros a mano

**Que tiene que pasar:**

- Los registros quedan con la **marca de pendiente**
- Si se vuelve a la pantalla anterior y despues se entra de nuevo, el contador
  de pendientes tiene que reflejar los que se cargaron, no quedarse en 0

Al terminar, desactivar el modo avion.

### 4.1 Probar el dictado (necesita conexion en este telefono)

Con WiFi o datos moviles activos (no en modo avion):

1. Entrar a una entidad del diagrama (la pantalla de registros de esa clase)
2. Tocar el boton de microfono ("Toca para dictar") y decir una frase en
   espanol, por ejemplo "un Paciente tiene muchas Consultas" en el lienzo o
   "agrega un paciente llamado Juan" en registros

**Que tiene que pasar:** el texto dictado aparece en tiempo real mientras se
habla y, al aplicarlo, el registro queda con la **marca de pendiente** igual
que uno cargado a mano.

Si el telefono usado en la defensa SI tiene el paquete de espanol sin
conexion instalado, y se volvio a poner `onDevice: true` (ver 2.2), este paso
se puede repetir en modo avion, como el resto.

## 5. Probar la sincronizacion

1. Prender el **punto de acceso (hotspot)** del telefono
2. Conectar la laptop a ese punto de acceso
3. En la laptop, levantar el backend generado (el que arma FORJA a partir del
   diagrama)
4. Ver la IP que le asigno el hotspot a la laptop (ver aviso mas abajo)
5. En la app, escribir esa direccion en el campo del backend
6. Tocar **Sincronizar**

**Que tiene que pasar:** la marca de pendiente desaparece de los tres
registros, y esos registros quedan en la base de datos del backend generado
(se puede confirmar consultando el backend directamente).

## 6. Avisos que evitan perder tiempo

- La direccion del backend hay que escribirla **con `http://` adelante**; sin
  el esquema el cliente la rechaza y el cartel dice otra cosa.
- **Un solo diagrama por corrida**: el almacen no separa por diagrama, asi que
  con dos los datos se pisan en silencio.
- El formulario **todavia no convierte tipos**: una fecha se tipea
  `2026-03-12`, un booleano `true`/`false`, un decimal con punto. Otra cosa la
  rechaza el backend con 400.
- El dictado en modo avion se probo en un telefono real y no reconocia nada
  porque faltaba el paquete de espanol: se saco `onDevice: true` de
  `movil/lib/voz/hoja_de_dictado.dart` y quedo anotado (ver 2.2 y 4.1). Si en
  OTRO telefono pasa lo mismo -se queda en "Escuchando..." sin transcribir-,
  es la misma causa: revisar el paquete de espanol antes de sospechar de otra
  cosa.
- Sobre el punto de acceso: la laptop recibe una IP del DHCP del telefono
  (algo como `192.168.43.x`), distinta de la de cualquier red de casa. Hay
  que mirarla en la laptop y escribirla en la app.

## 7. Que NO esta probado todavia

El modelo (tercer escalon del dictado) **nunca se ejecuto**: los 529 MB del
`.task` no estan en la maquina de desarrollo. Por eso son desconocidos:

- La calidad de lo que el modelo propone cuando traduce una frase libre a la
  forma canonica
- Si el limite de 30 segundos por consulta alcanza en un telefono real

Si el tercer escalon (el modelo) no acierta o no esta presente, los otros dos
escalones (la gramatica determinista y la confirmacion manual) funcionan
igual: la app entera no depende del modelo para andar.

## Anotado tambien

- Hay una carrera de escritura de archivos **en Windows** (no en Android) que
  puede hacer parpadear una prueba automatizada relacionada con guardar la
  direccion del backend letra por letra; es un defecto del anfitrion de
  desarrollo, no del producto, y no afecta esta prueba manual en el telefono.
- El backend generado no tiene idempotencia real: si un POST llega pero la
  respuesta se pierde, la proxima sincronizacion puede duplicar la fila. No
  hay mitigacion todavia, solo queda anotado.
