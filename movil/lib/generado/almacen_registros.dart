import 'dart:convert';
import 'dart:io';
import 'dart:math';

/// Las filas del backend generado y los cambios que todavia no llegaron.
///
/// Es hermano de Almacen y repite sus dos decisiones a proposito: archivos JSON
/// -lo que se guarda es un documento entero, no algo que se consulte por
/// partes- y escritura atomica, porque morir a mitad de guardar la cola seria
/// perder justo lo que la cola existe para proteger.
class OperacionPendiente {
  const OperacionPendiente({
    required this.id,
    required this.clase,
    required this.verbo,
    this.registroId,
    this.datos = const {},
  });

  /// Identificador generado en el aparato. Es lo que permite reenviar la cola
  /// tras un corte sin duplicar: el servidor reconoce el repetido.
  final String id;
  final String clase;

  /// 'crear', 'actualizar' o 'borrar'.
  final String verbo;
  final String? registroId;
  final Map<String, dynamic> datos;

  factory OperacionPendiente.desdeJson(Map<String, dynamic> json) => OperacionPendiente(
        id: json['id'] as String,
        clase: json['clase'] as String,
        verbo: json['verbo'] as String,
        registroId: json['registroId'] as String?,
        datos: Map<String, dynamic>.from(json['datos'] as Map? ?? {}),
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'clase': clase,
        'verbo': verbo,
        'registroId': registroId,
        'datos': datos,
      };
}

class AlmacenRegistros {
  AlmacenRegistros(this.carpeta);

  final Directory carpeta;
  Future<void> _escrituraPendiente = Future.value();

  File _archivo(String nombre) => File('${carpeta.path}${Platform.pathSeparator}$nombre');

  /// Une TODA escritura de esta clase a una sola cadena, sin excepcion: dos
  /// llamadas concurrentes -dos teclas seguidas guardando la direccion del
  /// backend, o un `encolar` que pisa el `guardarFilas` de otra clase- no
  /// pueden competer por el mismo archivo temporal ni terminar escribiendo en
  /// un orden distinto del que se pidieron. Antes esto vivia solo dentro de
  /// `encolar`; un metodo de guardado nuevo que no pasara por aca repetiria el
  /// mismo defecto, asi que ahora es `_guardar` mismo el que serializa y cada
  /// metodo publico de escritura pasa por el.
  ///
  /// El futuro que queda en el campo nunca puede terminar en error: si
  /// quedara, toda escritura posterior encadenada detras se saltearia su
  /// cuerpo en silencio. El que se le devuelve a quien llamo si propaga su
  /// propio fallo -por eso son dos futuros distintos y no el mismo.
  Future<T> _serializado<T>(Future<T> Function() tarea) {
    final propia = _escrituraPendiente.then((_) => tarea());
    _escrituraPendiente = propia.then((_) {}, onError: (_) {});
    return propia;
  }

  /// La escritura de archivo en si, sin serializar: la llaman `_guardar` -para
  /// quien pide una escritura suelta- y las tareas que ya corren dentro de
  /// `_serializado` -para quien necesita leer y escribir como una sola unidad,
  /// como `encolar`- porque esas no pueden volver a pasar por `_serializado`
  /// sin encadenarse sobre si mismas.
  Future<void> _escribirArchivo(String nombre, Object contenido) async {
    final id = Random().nextInt(1000000);
    final temporal = _archivo('$nombre.$id.tmp');
    try {
      await temporal.writeAsString(jsonEncode(contenido), flush: true);
      await temporal.rename(_archivo(nombre).path);
    } catch (_) {
      // Sin esto el .tmp queda huerfano en la carpeta cuando el rename falla.
      if (await temporal.exists()) {
        try {
          await temporal.delete();
        } catch (_) {
          // Si ni el temporal se puede borrar, no hay nada mas que hacer aca:
          // se repropaga el error original igual.
        }
      }
      rethrow;
    }
  }

  Future<void> _guardar(String nombre, Object contenido) =>
      _serializado(() => _escribirArchivo(nombre, contenido));

  Future<dynamic> _leer(String nombre) async {
    final archivo = _archivo(nombre);
    if (!await archivo.exists()) return null;
    try {
      return jsonDecode(await archivo.readAsString());
    } catch (_) {
      await archivo.delete();
      return null;
    }
  }

  Future<void> guardarFilas(String clase, List<Map<String, dynamic>> filas) =>
      _guardar('filas-$clase.json', filas);

  Future<List<Map<String, dynamic>>> leerFilas(String clase) async {
    final json = await _leer('filas-$clase.json');
    if (json == null) return [];
    return (json as List<dynamic>).map((f) => Map<String, dynamic>.from(f as Map)).toList();
  }

  /// La cola escrita sin serializar: la usan `encolar` y `reemplazarCola`, que
  /// ya corren adentro de `_serializado`. Existe para que las dos escriban el
  /// mismo archivo con el mismo formato en un solo lugar.
  Future<void> _escribirCola(List<OperacionPendiente> cola) =>
      _escribirArchivo('cola-registros.json', cola.map((o) => o.aJson()).toList());

  Future<void> encolar(OperacionPendiente operacion) => _serializado(() async {
        final cola = await leerCola();
        await _escribirCola([...cola, operacion]);
      });

  Future<List<OperacionPendiente>> leerCola() async {
    final json = await _leer('cola-registros.json');
    if (json == null) return [];
    return (json as List<dynamic>)
        .map((o) => OperacionPendiente.desdeJson(Map<String, dynamic>.from(o as Map)))
        .toList();
  }

  Future<void> reemplazarCola(List<OperacionPendiente> cola) =>
      _serializado(() => _escribirCola(cola));

  /// La direccion del backend generado no se puede dejar fija: el dia de la
  /// demostracion el telefono prende su punto de acceso y la laptop recibe
  /// una IP de ese DHCP, distinta en cada sitio. Se guarda igual que el resto
  /// -JSON, escritura atomica y serializada- y no aparte, para no inventar un
  /// tercer mecanismo de persistencia en esta misma clase.
  Future<void> guardarDireccionBackend(String? direccion) => _serializado(() async {
        if (direccion == null || direccion.isEmpty) {
          final archivo = _archivo('direccion-backend.json');
          if (await archivo.exists()) await archivo.delete();
          return;
        }
        await _escribirArchivo('direccion-backend.json', direccion);
      });

  Future<String?> leerDireccionBackend() async {
    final json = await _leer('direccion-backend.json');
    return json as String?;
  }
}
