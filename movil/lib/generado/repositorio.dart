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
    // quedaria vacia y pareceria que no se guardo nada.
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
    final clasesExitosas = <String>{};
    var vaciadas = 0;

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
        clasesExitosas.add(operacion.clase);
      } catch (_) {
        // Se corta en la primera que falla y se conserva el resto en orden,
        // por indice explicito y no por igualdad de objeto: OperacionPendiente
        // no sobrescribe == ni hashCode hoy, pero si algun dia lo hiciera,
        // cola.indexOf(operacion) podria encontrar el elemento equivocado y
        // reordenar la cola en silencio.
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
    final clasesARefrescar = clasesExitosas.difference(clasesPendientes);

    for (final clase in clasesARefrescar) {
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
