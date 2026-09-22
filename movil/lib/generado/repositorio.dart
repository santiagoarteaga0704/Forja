import '../sincronizador.dart';
import 'almacen_registros.dart';
import 'api_generada.dart';

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
    // quedaria vacia y parecería que no se guardo nada.
    final filas = await almacen.leerFilas(clase);
    await almacen.guardarFilas(clase, [...filas, {...datos, '_pendiente': operacion.id}]);
    await almacen.encolar(operacion);
  }

  /// Devuelve cuantas operaciones se vaciaron. Cero es un resultado normal, no
  /// un error: quiere decir que no habia nada o que no se llego al backend.
  Future<int> sincronizar() async {
    final backend = api;
    if (backend == null) return 0;

    final cola = await almacen.leerCola();
    final quedan = <OperacionPendiente>[];
    var vaciadas = 0;
    final clasesVaciadas = <String>{};

    for (final operacion in cola) {
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
        clasesVaciadas.add(operacion.clase);
      } catch (_) {
        // Se corta en la primera que falla y se conserva el resto en orden: si
        // se saltearan las siguientes, un alta posterior podria llegar antes
        // que la que la precede.
        quedan.addAll(cola.skip(cola.indexOf(operacion)));
        break;
      }
    }

    await almacen.reemplazarCola(quedan);

    // Las filas que se guardaron antes de enviarlas quedaron marcadas con
    // '_pendiente'. Ya se mandaron: hay que pedirle al backend la version
    // definitiva -con sus identificadores de verdad- y reemplazar la copia
    // local, que es la unica forma de que la marca desaparezca. Si este pedido
    // falla no se toca nada: las marcas quedan puestas y la proxima
    // sincronizacion, si logra vaciar la cola de nuevo, lo intenta otra vez.
    for (final clase in clasesVaciadas) {
      try {
        final filas = await backend.listar(clase);
        await almacen.guardarFilas(clase, filas);
      } catch (_) {
        // Una via y un respaldo que no hace nada: la marca sigue mostrando
        // "esto todavia no llego" hasta que se pueda confirmar lo contrario.
      }
    }

    return vaciadas;
  }
}
