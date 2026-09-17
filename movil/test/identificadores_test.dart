import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/identificadores.dart';

/// Los identificadores que inventa el telefono tienen que ser UUID de verdad.
///
/// No es una preferencia de formato: los comandos de ComandoOperacion declaran
/// sus ids como UUID de Java, asi que un identificador con cualquier otra forma
/// hace que el servidor rechace la operacion al sincronizar. Y como la cola se
/// detiene en el primer tropiezo, una sola operacion mal formada traba todo lo
/// que se hizo sin conexion.
void main() {
  // 8-4-4-12 en hexadecimal, con el 4 de la version y el 8, 9, a o b de la
  // variante. Es lo mismo que acepta UUID.fromString.
  final formaDeUuid = RegExp(
      r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$');

  test('tiene la forma de un UUID version 4', () {
    for (var i = 0; i < 200; i++) {
      final id = nuevoIdDeModelo();
      expect(formaDeUuid.hasMatch(id), isTrue, reason: 'no es un UUID v4: $id');
    }
  });

  test('no se repite', () {
    final vistos = {for (var i = 0; i < 2000; i++) nuevoIdDeModelo()};
    expect(vistos, hasLength(2000));
  });

  test('el token de la cola NO sirve como identificador de modelo', () {
    // Esta es la confusion que trabo la sincronizacion: el token existe para
    // que reenviar una operacion no la duplique, y nunca tuvo forma de UUID.
    expect(formaDeUuid.hasMatch('2kf3j1a-1x9dq'), isFalse);
  });
}
