import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/interpretacion.dart';

void main() {
  test('una interpretacion entendida no trae sugerencias', () {
    final resultado = Interpretacion.entendida(
      'crea la clase Factura',
      'Cree la clase Factura',
      const [
        Paso(tipo: 'CLASE_CREAR', comando: {'claseId': 'x', 'nombre': 'Factura'}),
      ],
    );

    expect(resultado.entendida, isTrue);
    expect(resultado.pasos, hasLength(1));
    expect(resultado.sugerencias, isEmpty);
    expect(resultado.explicacion, 'Cree la clase Factura');
  });

  test('una interpretacion no entendida no trae pasos, pero si que probar', () {
    final resultado =
        Interpretacion.noEntendida('bla bla', const ['crea la clase Paciente']);

    expect(resultado.entendida, isFalse);
    expect(resultado.pasos, isEmpty);
    expect(resultado.sugerencias, hasLength(1));
  });

  test('los pasos se convierten en comandos con origen VOZ y un token cada uno', () {
    var n = 0;
    final comandos = comandosDe(
      Interpretacion.entendida('x', 'y', const [
        Paso(tipo: 'CLASE_CREAR', comando: {'claseId': 'a', 'nombre': 'Factura'}),
        Paso(tipo: 'ATRIBUTO_AGREGAR', comando: {'claseId': 'a', 'nombre': 'total'}),
      ]),
      () => 'token-${n++}',
    );

    expect(comandos.map((c) => c.tipo), ['CLASE_CREAR', 'ATRIBUTO_AGREGAR']);
    expect(comandos.map((c) => c.origen), ['VOZ', 'VOZ']);
    expect(comandos.first.carga['nombre'], 'Factura');
  });

  test('cada comando lleva su propio token, que es lo que evita duplicar al reenviar', () {
    var n = 0;
    final comandos = comandosDe(
      Interpretacion.entendida('x', 'y', const [
        Paso(tipo: 'CLASE_CREAR', comando: {}),
        Paso(tipo: 'ATRIBUTO_AGREGAR', comando: {}),
        Paso(tipo: 'ATRIBUTO_AGREGAR', comando: {}),
      ]),
      () => 'token-${n++}',
    );

    expect(comandos.map((c) => c.tokenCliente).toSet(), hasLength(3));
  });

  test('una interpretacion sin pasos no produce comandos', () {
    expect(comandosDe(Interpretacion.noEntendida('x', const []), () => 't'), isEmpty);
  });
}
