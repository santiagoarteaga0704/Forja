# FORJA movil

Cliente Android de la herramienta CASE. Modela el diagrama **sin conexion** y
sincroniza cuando vuelve la red.

## Como correrlo

```
flutter pub get
flutter run           # con un telefono conectado o un emulador abierto
flutter test          # las pruebas del nucleo, sin dispositivo
flutter build apk --debug
```

**La direccion del servidor se escribe en la pantalla de entrada**, no en el
codigo. Es a proposito, porque cambia segun donde se pruebe:

| Donde corre la aplicacion | Direccion del backend |
|---|---|
| Emulador de Android | `http://10.0.2.2:8080` (es la PC vista desde el emulador) |
| Telefono real en la misma red | `http://<IP-de-la-PC>:8080`, por ejemplo `http://192.168.0.12:8080` |
| Desplegado | la direccion de la EC2 |

Para averiguar la IP de la PC en Windows: `ipconfig`, y se busca la
`Direccion IPv4` del adaptador de la red en uso. El backend tiene que estar
escuchando en todas las interfaces, no solo en `localhost`.

## Dos cosas que hay que saber antes de probar en el telefono

**1. El trafico sin cifrar.** Desde Android 9 el sistema bloquea HTTP sin TLS. El
manifiesto lleva `android:usesCleartextTraffic="true"` para que la aplicacion
pueda hablar con el backend de desarrollo. **Cuando el backend quede en AWS con
TLS, hay que quitar esa bandera** y usar `https`: dejarla en una aplicacion
publicada permitiria que la conexion viaje en claro.

**2. El dictado necesita el paquete de idioma descargado.** El reconocimiento lo
hace Android, no la aplicacion, y funciona sin conexion **solo si el idioma esta
bajado en el telefono**. Es una condicion del sistema:

> Ajustes → Administracion general → Idioma y entrada → Reconocimiento de voz →
> descargar el castellano para usar sin conexion

Conviene hacerlo y ensayar en modo avion antes de cualquier demostracion.

## Como esta armado

| Archivo | Que hace |
|---|---|
| `tipos.dart` | El modelo del diagrama y su conversion a JSON |
| `comandos.dart` | Los cambios, listos para enviarse y para guardarse |
| `modelo_local.dart` | Aplica un comando sobre la copia local |
| `almacen.dart` | Guarda credencial, diagrama y cola en archivos |
| `sincronizador.dart` | El motor de trabajo sin conexion |
| `api.dart` | Cliente HTTP, distinguiendo "sin red" de "el servidor dijo no" |
| `lienzo/pintor.dart` | Dibuja el diagrama con la notacion de UML |
| `pantallas/` | Entrar, elegir diagrama, lienzo |

### Lo que sostiene el trabajo sin conexion

Cada cambio se **aplica primero** sobre la copia local y se encola; enviarlo es
una consecuencia posterior que puede fallar sin que nadie se entere. La pantalla
nunca espera al servidor.

Al volver la conexion se hacen dos cosas **en este orden**: primero se vacia la
cola, despues se pide el delta. Al reves, el delta traeria un estado que todavia
no incluye los cambios propios y habria que decidir como mezclarlos.

La cola se vacia **en orden estricto y se detiene en el primer tropiezo**, porque
los comandos dependen unos de otros: no se puede agregar un atributo a una clase
que todavia no llego.

Cada comando lleva un **token generado en el telefono**. Es lo que hace que
reenviar tras un corte no duplique nada: el servidor reconoce el reenvio y lo
responde como ya hecho.

La escritura de la cola es **atomica** -archivo temporal y renombre-. Sin eso,
que la aplicacion muera a mitad de guardar dejaria un JSON truncado y se
perderian todos los cambios pendientes: justo lo que la cola existe para evitar.

## Lo que falta

- **Dictado sin conexion.** Hoy la frase la interpreta el servidor, asi que
  dictar necesita red. Falta portar a Dart la gramatica determinista que ya
  existe en el backend (`voz/ParserVoz.java`), y despues montar Gemma 3 1B
  delante de ella: el modelo traduce una frase libre a una de las formas que la
  gramatica reconoce, y la gramatica es la unica que toca el diagrama.
- **Foto de pizarra.** En la web el reconocimiento de texto corre en el
  navegador; aqui haria falta ML Kit.
- **Consumir el backend generado**, que es requisito explicito del enunciado.
- El canal en vivo por WebSocket: por ahora el telefono se sincroniza al abrir,
  al hacer un cambio y con el boton de sincronizar.
