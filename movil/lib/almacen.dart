import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'comandos.dart';
import 'tipos.dart';

/// Guarda en el telefono lo necesario para abrir un diagrama sin conexion: la
/// credencial, la ultima copia conocida del modelo y la cola de cambios que
/// todavia no llegaron al servidor.
///
/// Se usan archivos JSON y no una base de datos porque lo que hay que guardar es
/// un documento completo y una lista, no algo que haya que consultar por partes.
/// Una base de datos aportaria indices y consultas que nadie va a usar, y a
/// cambio pediria un esquema con sus migraciones.
///
/// La escritura es **atomica**: se escribe un archivo temporal y se lo renombra
/// encima del definitivo. Sin eso, que la aplicacion muera a mitad de guardar la
/// cola dejaria un JSON truncado, y al abrir de nuevo se perderian todos los
/// cambios pendientes: justo lo que la cola existe para evitar.
class Almacen {
  Almacen(this.carpeta);

  final Directory carpeta;

  /// La carpeta que Android reserva para la aplicacion. En las pruebas se pasa
  /// una temporal, y por eso la dependencia esta en el constructor.
  static Future<Almacen> enElTelefono() async =>
      Almacen(await getApplicationDocumentsDirectory());

  File _archivo(String nombre) => File('${carpeta.path}${Platform.pathSeparator}$nombre');

  Future<void> _guardar(String nombre, Object contenido) async {
    final definitivo = _archivo(nombre);
    final temporal = _archivo('$nombre.tmp');
    await temporal.writeAsString(jsonEncode(contenido), flush: true);
    await temporal.rename(definitivo.path);
  }

  Future<dynamic> _leer(String nombre) async {
    final archivo = _archivo(nombre);
    if (!await archivo.exists()) return null;
    try {
      return jsonDecode(await archivo.readAsString());
    } catch (_) {
      // Un archivo ilegible se descarta: es preferible volver a pedir el
      // diagrama al servidor que arrastrar un estado corrupto.
      await archivo.delete();
      return null;
    }
  }

  // ---------- Credencial ---------------------------------------------------

  Future<void> guardarCredencial(Credencial? credencial) async {
    if (credencial == null) {
      final archivo = _archivo('credencial.json');
      if (await archivo.exists()) await archivo.delete();
      return;
    }
    await _guardar('credencial.json', credencial.aJson());
  }

  Future<Credencial?> leerCredencial() async {
    final json = await _leer('credencial.json');
    if (json == null) return null;
    final credencial = Credencial.desdeJson(Map<String, dynamic>.from(json as Map));
    // Un token vencido no sirve, pero el diagrama guardado si: se borra la
    // credencial y se deja el resto para poder seguir mirando sin conexion.
    return credencial.vencida ? null : credencial;
  }

  // ---------- Lista de diagramas ------------------------------------------

  Future<void> guardarResumenes(List<ResumenDiagrama> resumenes) =>
      _guardar('diagramas.json', resumenes.map((r) => r.aJson()).toList());

  Future<List<ResumenDiagrama>> leerResumenes() async {
    final json = await _leer('diagramas.json');
    if (json == null) return [];
    return (json as List<dynamic>)
        .map((d) => ResumenDiagrama.desdeJson(Map<String, dynamic>.from(d as Map)))
        .toList();
  }

  // ---------- Diagrama -----------------------------------------------------

  Future<void> guardarDiagrama(Diagrama diagrama) =>
      _guardar('diagrama-${diagrama.id}.json', diagrama.aJson());

  Future<Diagrama?> leerDiagrama(String diagramaId) async {
    final json = await _leer('diagrama-$diagramaId.json');
    if (json == null) return null;
    return Diagrama.desdeJson(Map<String, dynamic>.from(json as Map));
  }

  // ---------- Cola de cambios pendientes -----------------------------------

  Future<void> guardarCola(String diagramaId, List<Comando> pendientes) =>
      _guardar('cola-$diagramaId.json', pendientes.map((c) => c.aJsonGuardado()).toList());

  Future<List<Comando>> leerCola(String diagramaId) async {
    final json = await _leer('cola-$diagramaId.json');
    if (json == null) return [];
    return (json as List<dynamic>)
        .map((c) => Comando.desdeJson(Map<String, dynamic>.from(c as Map)))
        .toList();
  }

  Future<void> borrarTodo() async {
    if (!await carpeta.exists()) return;
    await for (final archivo in carpeta.list()) {
      if (archivo is File) await archivo.delete();
    }
  }
}
