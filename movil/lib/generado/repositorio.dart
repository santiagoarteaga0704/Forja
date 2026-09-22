import '../sincronizador.dart';
import 'almacen_registros.dart';
import 'api_generada.dart';

/// Lo que dejo una pasada de sincronizacion.
///
/// Antes esto era un `int` -cuantas se vaciaron- y por eso la pantalla no tenia
/// con que distinguir "no llegue al backend" de "el backend rechazo esto": los
/// dos eran cero, y los dos se anunciaban como un problema de red. Son tres
/// cosas distintas y viajan por separado.
class ResultadoDeSincronizacion {
  const ResultadoDeSincronizacion({
    required this.vaciadas,
    this.rechazadas = const [],
    this.corte,
  });

  /// Cuantas operaciones llegaron al backend. Cero es un resultado normal.
  final int vaciadas;

  /// Descripciones de lo que el backend rechazo de manera definitiva y se saco
  /// de la cola. Se nombran porque son datos que alguien cargo y no van a
  /// entrar nunca: callarlo lo deja esperando algo que ya no va a pasar.
  final List<String> rechazadas;

  /// Por que se corto la pasada, o null si se recorrio la cola entera. Es un
  /// texto para mostrar, igual que `Sincronizador.aviso`, para que las dos
  /// colas de la app se expliquen de la misma forma.
  final String? corte;
}

/// Lo unico que sabe si hay red.
///
/// La pantalla pide filas y crea registros sin preguntar por la conexion: si el
/// backend generado esta al alcance se vacia la cola, y si no, no. Que la app
/// funcione sin senal no es un modo aparte, es el comportamiento normal.
class Repositorio {
  Repositorio({required this.almacen, this.api});

  final AlmacenRegistros almacen;
  final ApiGenerada? api;

  Future<List<Map<String, dynamic>>> filas(String clase) => almacen.leerFilas(clase);

  Future<void> crear(String clase, Map<String, dynamic> datos) async {
    final operacion = OperacionPendiente(
      id: Sincronizador.nuevoToken(),
      clase: clase,
      verbo: 'crear',
      datos: datos,
    );
    // Se ve primero y se manda despues: al reves, sin senal la pantalla
    // quedaria vacia y pareceria que no se guardo nada.
    final filas = await almacen.leerFilas(clase);
    await almacen.guardarFilas(clase, [...filas, {...datos, '_pendiente': operacion.id}]);
    await almacen.encolar(operacion);
  }

  /// Vacia la cola contra el backend generado.
  ///
  /// El criterio de que hacer con una operacion que falla es el mismo que ya
  /// usa `Sincronizador._vaciarCola` para la cola de FORJA, y lo es a
  /// proposito: un fallo de red conserva la operacion y corta la pasada -la
  /// senal vuelve sola-, y un rechazo definitivo del servidor la saca de la
  /// cola, se informa nombrandola, y la pasada sigue con las de atras. Antes
  /// aca habia un `catch` pelado que trataba las dos igual: una operacion que
  /// el servidor no iba a aceptar nunca dejaba muerto todo lo que estaba
  /// detras, y encima el aviso acusaba a la red.
  Future<ResultadoDeSincronizacion> sincronizar() async {
    final backend = api;
    if (backend == null) return const ResultadoDeSincronizacion(vaciadas: 0);

    final cola = await almacen.leerCola();
    final quedan = <OperacionPendiente>[];
    final rechazadas = <String>[];
    final clasesARefrescar = <String>{};
    var vaciadas = 0;
    String? corte;

    for (var i = 0; i < cola.length; i++) {
      final operacion = cola[i];
      try {
        switch (operacion.verbo) {
          case 'crear':
            await backend.crear(operacion.clase, operacion.datos);
          case 'actualizar':
            await backend.actualizar(operacion.clase, operacion.registroId!, operacion.datos);
          case 'borrar':
            await backend.borrar(operacion.clase, operacion.registroId!);
        }
        vaciadas++;
        clasesARefrescar.add(operacion.clase);
      } on ErrorDelBackendGenerado catch (e) {
        if (e.esDefinitivo) {
          // El servidor no va a aceptarlo nunca: insistir solo trabaria la
          // cola. Se descarta, se informa, y se marca la clase para volver a
          // pedirla, porque la copia local tiene una fila marcada como
          // pendiente que ya no corresponde a nada.
          rechazadas.add('${_describir(operacion)}: ${e.codigo} ${e.cuerpo.trim()}'.trim());
          clasesARefrescar.add(operacion.clase);
          continue;
        }
        // 500, 401 o 429: el servidor contesto pero puede cambiar de opinion.
        // Se conserva y se corta, igual que sin red, pero sin decir que no se
        // llego: se llego, y contesto mal.
        corte = 'El backend generado no pudo con la cola ahora '
            '(respondio ${e.codigo}): sigue esperando.';
        quedan.addAll(cola.skip(i));
        break;
      } catch (_) {
        // No contesto: no dijo que no, no dijo nada. Se corta en la primera que
        // falla y se conserva el resto en orden, por indice explicito y no por
        // igualdad de objeto: OperacionPendiente no sobrescribe == ni hashCode
        // hoy, pero si algun dia lo hiciera, cola.indexOf(operacion) podria
        // encontrar el elemento equivocado y reordenar la cola en silencio.
        corte = 'No se pudo llegar al backend generado: la cola sigue esperando.';
        quedan.addAll(cola.skip(i));
        break;
      }
    }

    await almacen.reemplazarCola(quedan);

    // Las filas que se guardaron antes de enviarlas quedaron marcadas con
    // '_pendiente'. Una clase se refresca solo si termino la pasada sin
    // ninguna operacion suya sobreviviente en la cola: si quedara una
    // pendiente -por ejemplo porque la siguiente operacion de esa misma clase
    // fallo- pedir la lista ahora traeria una version del servidor que
    // todavia no la tiene, y esa fila desapareceria de la pantalla aunque
    // siga encolada. Con la clase realmente vacia, se le pide al backend la
    // version definitiva -con sus identificadores de verdad- y se reemplaza
    // la copia local, que es la unica forma de que la marca desaparezca. Si
    // este pedido falla no se toca nada: las marcas quedan puestas y la
    // proxima sincronizacion, si logra vaciar la cola de nuevo, lo intenta
    // otra vez.
    final clasesPendientes = quedan.map((o) => o.clase).toSet();

    for (final clase in clasesARefrescar.difference(clasesPendientes)) {
      try {
        final filas = await backend.listar(clase);
        await almacen.guardarFilas(clase, filas);
      } catch (_) {
        // Una via y un respaldo que no hace nada: la marca sigue mostrando
        // "esto todavia no llego" hasta que se pueda confirmar lo contrario.
      }
    }

    return ResultadoDeSincronizacion(
      vaciadas: vaciadas,
      rechazadas: List.unmodifiable(rechazadas),
      corte: corte,
    );
  }

  /// Como nombrar una operacion descartada para que se entienda cual fue.
  /// Mismo criterio que `Sincronizador._describir`: el verbo y, si lo hay, el
  /// nombre, que es el campo por el que una persona reconoce su fila.
  String _describir(OperacionPendiente operacion) {
    final nombre = operacion.datos['nombre'];
    return nombre is String && nombre.isNotEmpty
        ? '${operacion.verbo} ${operacion.clase} ($nombre)'
        : '${operacion.verbo} ${operacion.clase}';
  }
}
