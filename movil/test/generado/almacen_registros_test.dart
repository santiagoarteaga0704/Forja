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

  test('dos encolar concurrentes sin await no pierden operaciones', () async {
    final op1 = OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'});
    final op2 = OperacionPendiente(
        id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'});

    await Future.wait([almacen.encolar(op1), almacen.encolar(op2)]);

    final cola = await almacen.leerCola();

    expect(cola.length, 2);
    expect(cola.map((o) => o.id), ['op-1', 'op-2']);
  });

  test('una cola corrupta se descarta en lugar de arrastrarse', () async {
    File('${carpeta.path}${Platform.pathSeparator}cola-registros.json')
        .writeAsStringSync('[ esto no es json');

    expect(await almacen.leerCola(), isEmpty);
  });

  test('un encolar fallido no traba los posteriores', () async {
    // Convertir carpeta en archivo para forzar fallo de escritura
    await carpeta.delete(recursive: true);
    File(carpeta.path).writeAsStringSync('no soy una carpeta');

    // Este encolar debe fallar
    bool fallo = false;
    try {
      await almacen.encolar(OperacionPendiente(
          id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'}));
    } catch (e) {
      fallo = true;
    }
    expect(fallo, true);

    // Restaurar carpeta
    File(carpeta.path).deleteSync();
    await carpeta.create();

    // Este encolar debe funcionar sin restricciones
    await almacen.encolar(OperacionPendiente(
        id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'}));

    final cola = await almacen.leerCola();

    // Solo op-2 debe estar en la cola (op-1 fallo y no se encolo)
    expect(cola.length, 1);
    expect(cola.first.id, 'op-2');
  });
}
