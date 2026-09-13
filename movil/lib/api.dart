import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import 'comandos.dart';
import 'modelo_local.dart';
import 'tipos.dart';

/// El servidor rechazo la peticion: contesto, y contesto que no.
///
/// Se distingue de [SinConexion] porque las consecuencias son opuestas. Un
/// rechazo del servidor no mejora reintentando -un nombre repetido va a seguir
/// repetido- mientras que la falta de red se resuelve sola esperando. La cola de
/// cambios pendientes decide si conserva o descarta una operacion exactamente
/// con esa diferencia, asi que confundirlas significaria o perder trabajo o
/// reintentar para siempre algo imposible.
class ErrorApi implements Exception {
  ErrorApi(this.estado, this.mensaje);

  final int estado;
  final String mensaje;

  /// Cierto si volver a intentar no puede cambiar el resultado.
  bool get esDefinitivo => estado >= 400 && estado < 500 && estado != 401 && estado != 429;

  @override
  String toString() => mensaje;
}

/// No se pudo hablar con el servidor. No dijo que no: no dijo nada.
class SinConexion implements Exception {
  SinConexion([this.detalle = '']);

  final String detalle;

  @override
  String toString() => 'Sin conexion con el servidor';
}

class Api {
  Api({String? base, http.Client? cliente})
      : base = base ?? predeterminada,
        _cliente = cliente ?? http.Client();

  /// En el emulador de Android, 10.0.2.2 es la maquina que lo hospeda; desde un
  /// telefono real hay que poner la direccion de la red local, y por eso la
  /// pantalla de entrada permite cambiarla sin recompilar.
  static const String predeterminada = 'http://10.0.2.2:8080';

  String base;
  final http.Client _cliente;
  String? _token;

  static const Duration _espera = Duration(seconds: 12);

  void fijarToken(String? token) => _token = token;
  String? get token => _token;

  Map<String, String> get _cabeceras => {
        'Content-Type': 'application/json',
        if (_token != null) 'Authorization': 'Bearer $_token',
      };

  Future<dynamic> _pedir(String metodo, String ruta, {Object? cuerpo}) async {
    final uri = Uri.parse('$base$ruta');
    try {
      final respuesta = await switch (metodo) {
        'GET' => _cliente.get(uri, headers: _cabeceras),
        'DELETE' => _cliente.delete(uri, headers: _cabeceras),
        _ => _cliente.post(uri, headers: _cabeceras, body: jsonEncode(cuerpo)),
      }
          .timeout(_espera);

      if (respuesta.statusCode >= 400) {
        throw ErrorApi(respuesta.statusCode, _mensajeDe(respuesta));
      }
      if (respuesta.body.isEmpty) return null;
      return jsonDecode(utf8.decode(respuesta.bodyBytes));
    } on SocketException catch (e) {
      throw SinConexion(e.message);
    } on TimeoutException {
      throw SinConexion('tardo mas de ${_espera.inSeconds} segundos');
    } on http.ClientException catch (e) {
      throw SinConexion(e.message);
    }
  }

  /// El backend responde con ProblemDetail, asi que hay un mensaje pensado para
  /// una persona. Usarlo en lugar de "error 422" es la diferencia entre que el
  /// usuario sepa que corregir y que no.
  String _mensajeDe(http.Response respuesta) {
    try {
      final cuerpo = jsonDecode(utf8.decode(respuesta.bodyBytes)) as Map<String, dynamic>;
      return (cuerpo['detail'] ?? cuerpo['title'] ?? respuesta.reasonPhrase) as String;
    } catch (_) {
      return 'Error ${respuesta.statusCode}';
    }
  }

  // ---------- Autenticacion -----------------------------------------------

  Future<Credencial> entrar(String email, String password) async {
    final json = await _pedir('POST', '/api/auth/sesion',
        cuerpo: {'email': email, 'password': password});
    final credencial = Credencial.desdeJson(json as Map<String, dynamic>);
    fijarToken(credencial.token);
    return credencial;
  }

  Future<Credencial> registrarse(String email, String nombre, String password) async {
    final json = await _pedir('POST', '/api/auth/registro',
        cuerpo: {'email': email, 'nombre': nombre, 'password': password});
    final credencial = Credencial.desdeJson(json as Map<String, dynamic>);
    fijarToken(credencial.token);
    return credencial;
  }

  // ---------- Proyectos y diagramas ---------------------------------------

  Future<List<Proyecto>> proyectos() async {
    final json = await _pedir('GET', '/api/proyectos') as List<dynamic>;
    return json.map((p) => Proyecto.desdeJson(p as Map<String, dynamic>)).toList();
  }

  Future<List<ResumenDiagrama>> diagramas(String proyectoId) async {
    final json = await _pedir('GET', '/api/proyectos/$proyectoId/diagramas') as List<dynamic>;
    return json.map((d) => ResumenDiagrama.desdeJson(d as Map<String, dynamic>)).toList();
  }

  Future<Diagrama> diagrama(String diagramaId) async {
    final json = await _pedir('GET', '/api/diagramas/$diagramaId');
    return Diagrama.desdeJson(json as Map<String, dynamic>);
  }

  // ---------- Operaciones --------------------------------------------------

  Future<ResultadoOperacion> enviar(String diagramaId, Comando comando, String sesionId) async {
    final json = await _pedir('POST', '/api/diagramas/$diagramaId/operaciones',
        cuerpo: comando.aJson(sesionId: sesionId));
    return ResultadoOperacion.desdeJson(json as Map<String, dynamic>);
  }

  /// Cambios posteriores a la version que el telefono conoce. Es la via por la
  /// que se pone al dia despues de estar sin conexion.
  Future<List<OperacionRemota>> delta(String diagramaId, int desde) async {
    final json = await _pedir('GET', '/api/diagramas/$diagramaId/operaciones?desde=$desde')
        as List<dynamic>;
    return json.map((o) => OperacionRemota.desdeJson(o as Map<String, dynamic>)).toList();
  }

  Future<Map<String, dynamic>> dictar(String diagramaId, String frase, String sesionId) async {
    final json = await _pedir('POST', '/api/diagramas/$diagramaId/voz',
        cuerpo: {'frase': frase, 'sesionId': sesionId});
    return Map<String, dynamic>.from(json as Map);
  }

  Future<List<Map<String, dynamic>>> consejos(String diagramaId) async {
    final json = await _pedir('GET', '/api/diagramas/$diagramaId/agente') as List<dynamic>;
    return json.map((c) => Map<String, dynamic>.from(c as Map)).toList();
  }

  void cerrar() => _cliente.close();
}

/// Desenlace del registro de una operacion, tal como lo devuelve el servidor.
class ResultadoOperacion {
  const ResultadoOperacion({
    required this.estado,
    required this.secuencia,
    this.retenidoPor,
  });

  /// APLICADA, DUPLICADA o RECHAZADA_POR_BLOQUEO.
  final String estado;
  final int secuencia;
  final String? retenidoPor;

  factory ResultadoOperacion.desdeJson(Map<String, dynamic> json) => ResultadoOperacion(
        estado: json['estado'] as String,
        secuencia: (json['secuencia'] as num?)?.toInt() ?? 0,
        retenidoPor: (json['bloqueo'] as Map<String, dynamic>?)?['poseedorNombre'] as String?,
      );

  /// Un reenvio que el servidor reconoce no es un error: es la garantia de
  /// idempotencia funcionando, y la operacion se puede sacar de la cola.
  bool get seCompleto => estado == 'APLICADA' || estado == 'DUPLICADA';
  bool get rechazadaPorBloqueo => estado == 'RECHAZADA_POR_BLOQUEO';
}
