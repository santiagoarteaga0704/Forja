import 'dart:math';

/// Identificadores de las cosas del modelo: clases, atributos, metodos,
/// relaciones.
///
/// Van aparte del token de la cola a proposito, porque son dos cosas distintas
/// que se venian confundiendo. El token existe para que reenviar una operacion
/// despues de un corte no la duplique, y le alcanza con no repetirse. El
/// identificador de modelo, en cambio, tiene un contrato con el servidor: los
/// comandos de ComandoOperacion.java declaran sus ids como UUID de Java, asi
/// que cualquier otra forma hace que la operacion se rechace al sincronizar.
/// Como la cola se detiene en el primer tropiezo, una sola mal formada traba
/// todo lo que se hizo sin conexion.
///
/// Se arma a mano y no con el paquete `uuid` porque son diez lineas y no vale
/// una dependencia mas en un proyecto que tiene cinco.

final _azar = Random.secure();

/// Un UUID version 4, en la forma que acepta UUID.fromString.
String nuevoIdDeModelo() {
  final bytes = List<int>.generate(16, (_) => _azar.nextInt(256));

  // Los cuatro bits de la version y los dos de la variante no son al azar: son
  // lo que distingue un UUID v4 de dieciseis bytes cualesquiera.
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}'
      '-${hex.substring(16, 20)}-${hex.substring(20)}';
}
