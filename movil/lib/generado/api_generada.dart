import 'dart:convert';

import 'package:http/http.dart' as http;

import 'nombres.dart';

/// Cliente del backend que FORJA genera.
///
/// No hay descubrimiento de ningun tipo: el generador emite siempre la misma
/// forma -EscritorCapas.java- asi que la ruta se deduce del nombre de la clase
/// y el resto es CRUD. Tampoco hay autenticacion, porque el backend generado no
/// la tiene: no es un olvido de este cliente.
/// El backend generado contesto, y contesto que no.
///
/// Se distingue de un fallo de red -una SocketException, un tiempo agotado-
/// porque las consecuencias son opuestas, igual que [ErrorApi] frente a
/// [SinConexion] del lado de FORJA: un rechazo no mejora reintentando y la
/// falta de red se resuelve sola esperando. La cola de operaciones pendientes
/// decide con esa diferencia si conserva o descarta, asi que confundirlas
/// significa o perder trabajo o dejar la cola trabada para siempre.
class ErrorDelBackendGenerado implements Exception {
  ErrorDelBackendGenerado(this.codigo, this.cuerpo);

  final int codigo;
  final String cuerpo;

  /// Cierto si volver a intentar no puede cambiar el resultado.
  ///
  /// Mismo criterio que `ErrorApi.esDefinitivo`, y a proposito: las dos colas
  /// de la app tienen que tratar igual al mismo servidor. 401 y 429 quedan
  /// afuera porque son "todavia no", no "nunca".
  bool get esDefinitivo => codigo >= 400 && codigo < 500 && codigo != 401 && codigo != 429;

  @override
  String toString() => 'El backend generado respondio $codigo: $cuerpo';
}

class ApiGenerada {
  ApiGenerada({required String base, http.Client? cliente})
      : _base = base.endsWith('/') ? base.substring(0, base.length - 1) : base,
        _cliente = cliente ?? http.Client();

  final String _base;
  final http.Client _cliente;

  Uri _uri(String clase, [String? id]) =>
      Uri.parse('$_base/api/${rutaDe(clase)}${id == null ? '' : '/$id'}');

  Future<List<Map<String, dynamic>>> listar(String clase) async {
    final respuesta = await _cliente.get(_uri(clase));
    final cuerpo = _leer(respuesta);
    return (cuerpo as List<dynamic>).map((f) => Map<String, dynamic>.from(f as Map)).toList();
  }

  Future<Map<String, dynamic>> crear(String clase, Map<String, dynamic> datos) async {
    final respuesta = await _cliente.post(_uri(clase),
        headers: {'Content-Type': 'application/json'}, body: jsonEncode(datos));
    return Map<String, dynamic>.from(_leer(respuesta) as Map);
  }

  Future<Map<String, dynamic>> actualizar(
      String clase, String id, Map<String, dynamic> datos) async {
    final respuesta = await _cliente.put(_uri(clase, id),
        headers: {'Content-Type': 'application/json'}, body: jsonEncode(datos));
    return Map<String, dynamic>.from(_leer(respuesta) as Map);
  }

  Future<void> borrar(String clase, String id) async {
    final respuesta = await _cliente.delete(_uri(clase, id));
    if (respuesta.statusCode >= 400) {
      throw ErrorDelBackendGenerado(respuesta.statusCode, respuesta.body);
    }
  }

  dynamic _leer(http.Response respuesta) {
    if (respuesta.statusCode >= 400) {
      throw ErrorDelBackendGenerado(respuesta.statusCode, respuesta.body);
    }
    if (respuesta.body.isEmpty) return null;
    return jsonDecode(utf8.decode(respuesta.bodyBytes));
  }
}
