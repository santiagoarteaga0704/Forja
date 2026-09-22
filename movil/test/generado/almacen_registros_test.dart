import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';

void main() {
  late Directory carpeta;
  late AlmacenRegistros almacen;

  setUp(() {
    carpeta = Directory.systemTemp.createTempSync('forja-registros');
    almacen = AlmacenRegistros(carpeta);
  });

  tearDown(() => carpeta.deleteSync(recursive: true));

  test('lo guardado se vuelve a leer igual', () async {
    await almacen.guardarFilas('Paciente', [
      {'id': 1, 'nombre': 'Juan'}
    ]);

    expect(await almacen.leerFilas('Paciente'), [
      {'id': 1, 'nombre': 'Juan'}
    ]);
  });

  test('una clase sin nada guardado devuelve vacio, no revienta', () async {
    expect(await almacen.leerFilas('Consulta'), isEmpty);
  });

  test('la cola conserva el orden en que se encolo', () async {
    await almacen.encolar(OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'}));
    await almacen.encolar(OperacionPendiente(
        id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'}));

    final cola = await almacen.leerCola();

    expect(cola.map((o) => o.id), ['op-1', 'op-2']);
    expect(cola.first.datos['nombre'], 'Ana');
  });

  test('un archivo corrupto se descarta en lugar de arrastrarse', () async {
    File('${carpeta.path}${Platform.pathSeparator}filas-Paciente.json')
        .writeAsStringSync('{ esto no es json');

    expect(await almacen.leerFilas('Paciente'), isEmpty);
  });
}
