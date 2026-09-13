import 'dart:math';

import 'package:flutter/foundation.dart';

import 'almacen.dart';
import 'api.dart';
import 'comandos.dart';
import 'modelo_local.dart';
import 'tipos.dart';

enum EstadoDeSincronizacion {
  /// Todo lo hecho en el telefono ya esta en el servidor.
  alDia,

  /// Hay cambios hechos que todavia no salieron.
  pendiente,

  /// Se estan enviando.
  enviando,

  /// No hay como hablar con el servidor. Se sigue trabajando igual.
  sinConexion,
}

/// El corazon del trabajo sin conexion.
///
/// Cada cambio se aplica **primero** sobre la copia local y se encola; el envio
/// es una consecuencia posterior y puede fallar sin que el usuario se entere. Esa
/// inversion es lo que permite modelar en un aula sin senal: la aplicacion nunca
/// espera al servidor para dibujar.
///
/// Cuando vuelve la conexion ocurren dos cosas en este orden, y el orden importa:
/// primero se vacia la cola y despues se pide el delta. Si se hiciera al revés,
/// el delta traeria un estado que todavia no incluye los cambios propios y
/// habria que decidir como mezclarlos; enviando primero, el delta ya los incluye
/// y reproducirlo es suficiente.
///
/// **La cola se vacia en orden estricto y se detiene en el primer tropiezo.** No
/// es una limitacion: los comandos dependen unos de otros -no se puede agregar un
/// atributo a una clase que todavia no llego- asi que saltear uno y seguir
/// produciria una cascada de rechazos.
class Sincronizador extends ChangeNotifier {
  Sincronizador({
    required this.api,
    required this.almacen,
    required this.sesionId,
    this.maximosIntentos = 5,
  });

  final Api api;
  final Almacen almacen;

  /// Identifica a esta instalacion ante el servidor. Junto con el usuario
  /// determina quien posee un bloqueo.
  final String sesionId;

  /// Tras cuantos rechazos por bloqueo se deja de insistir con una operacion.
  final int maximosIntentos;

  Diagrama? _diagrama;
  List<Comando> _pendientes = [];
  EstadoDeSincronizacion _estado = EstadoDeSincronizacion.alDia;
  String? _aviso;
  final List<String> _rechazados = [];

  /// Marca interna: algo se descarto, asi que la copia local dejo de coincidir
  /// con el servidor y hay que pedirla entera.
  ///
  /// Va aparte de [_rechazados] a proposito. Antes eran lo mismo, y limpiar la
  /// marca despues de resincronizar borraba tambien el aviso que la persona
  /// tenia que leer: se enteraba de que perdio un cambio solo si miraba la
  /// pantalla en el instante justo.
  bool _hayQuePedirTodo = false;

  Diagrama? get diagrama => _diagrama;
  int get cuantosPendientes => _pendientes.length;
  EstadoDeSincronizacion get estado => _estado;
  String? get aviso => _aviso;

  /// Descripciones de los cambios que el servidor rechazo definitivamente. Se
  /// muestran porque son trabajo que la persona hizo y se perdio: callarlo seria
  /// hacerle creer que su modelo dice algo que no dice.
  List<String> get rechazados => List.unmodifiable(_rechazados);

  static String nuevoToken() {
    final azar = Random();
    return '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}'
        '-${azar.nextInt(1 << 32).toRadixString(36)}';
  }

  // ---------- Abrir --------------------------------------------------------

  /// Abre el diagrama: primero lo que haya en el telefono -para que la pantalla
  /// no quede en blanco esperando la red- y despues se intenta actualizar.
  Future<void> abrir(String diagramaId) async {
    _diagrama = await almacen.leerDiagrama(diagramaId);
    _pendientes = await almacen.leerCola(diagramaId);

    // Los cambios que quedaron en la cola de una sesion anterior ya estan
    // reflejados en la copia guardada: no se vuelven a aplicar, solo se reenvian.
    _estado = _pendientes.isEmpty
        ? EstadoDeSincronizacion.alDia
        : EstadoDeSincronizacion.pendiente;
    notifyListeners();

    await sincronizar(diagramaId: diagramaId);
  }

  // ---------- Ejecutar un cambio ------------------------------------------

  /// Aplica el cambio en el telefono y lo deja encolado. No espera al servidor.
  Future<void> ejecutar(Comando comando) async {
    final diagrama = _diagrama;
    if (diagrama == null) return;

    aplicar(diagrama, comando);
    _pendientes.add(comando);
    _estado = EstadoDeSincronizacion.pendiente;
    notifyListeners();

    await almacen.guardarDiagrama(diagrama);
    await almacen.guardarCola(diagrama.id, _pendientes);

    // Se intenta enviar pero no se espera el resultado para responderle a la
    // interfaz: si no hay red, el cambio ya esta guardado y dibujado.
    await sincronizar();
  }

  // ---------- Sincronizar --------------------------------------------------

  Future<void> sincronizar({String? diagramaId}) async {
    final id = diagramaId ?? _diagrama?.id;
    if (id == null) return;

    _estado = EstadoDeSincronizacion.enviando;
    _aviso = null;
    notifyListeners();

    final seVacioLaCola = await _vaciarCola(id);
    if (!seVacioLaCola) {
      // Quedaron cambios sin enviar: no se pide el delta, porque reproducirlo
      // sobre un modelo que tiene cambios locales sin confirmar mezclaria dos
      // versiones de la verdad.
      await almacen.guardarCola(id, _pendientes);
      notifyListeners();
      return;
    }

    await _traerDelta(id);
    await almacen.guardarCola(id, _pendientes);
    notifyListeners();
  }

  /// Envia los pendientes en orden. Devuelve si la cola quedo vacia.
  Future<bool> _vaciarCola(String diagramaId) async {
    while (_pendientes.isNotEmpty) {
      final comando = _pendientes.first;
      try {
        final resultado = await api.enviar(diagramaId, comando, sesionId);

        if (resultado.seCompleto) {
          _pendientes.removeAt(0);
          continue;
        }

        if (resultado.rechazadaPorBloqueo) {
          comando.intentos++;
          if (comando.intentos >= maximosIntentos) {
            // Insistir para siempre con un elemento que otro no suelta dejaria
            // el resto de la cola detenido detras. Se descarta y se avisa.
            _pendientes.removeAt(0);
            _rechazados.add('${_describir(comando)}: lo tenia tomado '
                '${resultado.retenidoPor ?? "otro usuario"}');
            _hayQuePedirTodo = true;
            continue;
          }
          _estado = EstadoDeSincronizacion.pendiente;
          _aviso = '${resultado.retenidoPor ?? "Otro usuario"} tiene tomado un elemento. '
              'Se reintenta despues.';
          return false;
        }
      } on SinConexion {
        _estado = EstadoDeSincronizacion.sinConexion;
        return false;
      } on ErrorApi catch (e) {
        if (e.estado == 401) {
          _estado = EstadoDeSincronizacion.sinConexion;
          _aviso = 'La sesion vencio: hay que volver a entrar para sincronizar.';
          return false;
        }
        if (e.esDefinitivo) {
          // El servidor no va a aceptarlo nunca: reintentar solo bloquearia la
          // cola. Se descarta, se informa, y al final se vuelve a pedir el
          // diagrama porque la copia local dejo de coincidir.
          _pendientes.removeAt(0);
          _rechazados.add('${_describir(comando)}: ${e.mensaje}');
          _hayQuePedirTodo = true;
          continue;
        }
        _estado = EstadoDeSincronizacion.sinConexion;
        return false;
      }
    }
    return true;
  }

  /// Trae lo que hicieron los demas y lo reproduce sobre la copia local.
  Future<void> _traerDelta(String diagramaId) async {
    final diagrama = _diagrama;
    try {
      if (diagrama == null || _hayQuePedirTodo) {
        // Sin copia previa, o habiendo descartado algo, el delta no alcanza: se
        // pide el diagrama entero, que es una sola consulta y deja el telefono
        // en el mismo estado que el servidor.
        _diagrama = await api.diagrama(diagramaId);
        _hayQuePedirTodo = false;
      } else {
        final nuevas = await api.delta(diagramaId, diagrama.version);
        reproducir(diagrama, nuevas);
      }
      _estado = EstadoDeSincronizacion.alDia;
      await almacen.guardarDiagrama(_diagrama!);
    } on SinConexion {
      // Se sigue con la copia local: es exactamente para esto que se guarda.
      _estado = EstadoDeSincronizacion.sinConexion;
    } on ErrorApi catch (e) {
      _estado = EstadoDeSincronizacion.sinConexion;
      _aviso = e.mensaje;
    }
  }

  String _describir(Comando comando) {
    final nombre = comando.carga['nombre'];
    return nombre is String ? '${comando.tipo} ($nombre)' : comando.tipo;
  }

  void olvidarRechazados() {
    _rechazados.clear();
    notifyListeners();
  }
}
