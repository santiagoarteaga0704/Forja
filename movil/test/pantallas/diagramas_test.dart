import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/testing.dart';
import 'package:forja_movil/almacen.dart';
import 'package:forja_movil/api.dart';
import 'package:forja_movil/pantallas/diagramas.dart';
import 'package:forja_movil/pantallas/lienzo.dart';
import 'package:forja_movil/tipos.dart';

/// `_abrirEntidades` pide `getApplicationDocumentsDirectory()` -de
/// `path_provider`- para abrir el backend generado, y ese canal no tiene
/// implementacion en `flutter test`: sin este mock, la prueba de la fila
/// revienta con `MissingPluginException` en vez de ejercer la navegacion que
/// hay que probar.
const _canalPathProvider = MethodChannel('plugins.flutter.io/path_provider');

/// Mismo patron que `entidades_test.dart`: `testWidgets` corre en una zona de
/// tiempo falso, asi que la E/S de archivos real -almacen, path_provider- no
/// se entrega ahi adentro y hay que pasarla por `runAsync` o la prueba cuelga.
Future<void> _abrir(WidgetTester tester, Widget pantalla) async {
  await tester.runAsync(() async {
    await tester.pumpWidget(MaterialApp(home: pantalla));
    await Future<void>.delayed(const Duration(milliseconds: 50));
  });
  await tester.pump();
}

/// Da vueltas al bucle de eventos real hasta que el buscado aparece, o hasta
/// que se acaba el limite. Ver `entidades_test.dart` para el porque.
Future<void> _esperar(
  WidgetTester tester,
  Finder buscado, {
  Duration limite = const Duration(seconds: 15),
}) async {
  final fin = DateTime.now().add(limite);
  while (DateTime.now().isBefore(fin)) {
    await tester.pump();
    if (buscado.evaluate().isNotEmpty) return;
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
  }
  await tester.pump();
}

void main() {
  late Directory carpeta;

  setUp(() {
    carpeta = Directory.systemTemp.createTempSync('forja-diagramas');
    // La misma carpeta que usa `Almacen` en esta prueba: en el telefono real
    // los dos -`Almacen` y el `AlmacenRegistros` que arma `_abrirEntidades`-
    // comparten la carpeta de documentos de la aplicacion.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_canalPathProvider, (MethodCall llamada) async {
      if (llamada.method == 'getApplicationDocumentsDirectory') return carpeta.path;
      return null;
    });
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_canalPathProvider, null);
    carpeta.deleteSync(recursive: true);
  });

  testWidgets(
      'tocar la fila abre el backend generado, no el lienzo -es la regresion '
      'que dejo esa funcionalidad escondida detras de un icono sin etiqueta-',
      (tester) async {
    final almacen = Almacen(carpeta);
    const resumen = ResumenDiagrama(id: 'd1', nombre: 'Clinica', version: 1);
    final diagrama = Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente'),
    ]);

    // Guardado local para que abrir la fila no dependa de la red: tiene que
    // estar como queda despues de la primera sincronizacion.
    await tester.runAsync(() async {
      await almacen.guardarResumenes([resumen]);
      await almacen.guardarDiagrama(diagrama);
    });

    // Sin red: si algo intentara pedir el diagrama al servidor en vez de usar
    // el guardado, esto lo delataria.
    final api = Api(cliente: MockClient((_) async => throw const SocketException('sin red')));

    await _abrir(
      tester,
      PantallaDiagramas(
        api: api,
        almacen: almacen,
        credencial: const Credencial(
          token: 't',
          usuarioId: 'u1',
          nombre: 'Ana',
          email: 'ana@example.com',
        ),
        sesionId: 's1',
        alSalir: () async {},
      ),
    );
    await _esperar(tester, find.text('Clinica'));

    await tester.tap(find.text('Clinica'));
    await tester.pump();
    // `_abrirEntidades` es async y arma el repositorio -con E/S real de
    // archivo, incluida `getApplicationDocumentsDirectory`- antes de empujar
    // la ruta, igual que en `entidades_test.dart`: hay que esperar el
    // resultado en vez de un tiempo fijo.
    await _esperar(tester, find.text('Paciente'));

    // La fila abrio las entidades del backend generado...
    expect(find.text('Paciente'), findsOneWidget);
    // ...y no el lienzo.
    expect(find.byType(PantallaLienzo), findsNothing);
  });

  testWidgets('el icono de trailing sigue abriendo el lienzo, como accion secundaria',
      (tester) async {
    const resumen = ResumenDiagrama(id: 'd1', nombre: 'Clinica', version: 1);
    final almacen = Almacen(carpeta);
    await tester.runAsync(() => almacen.guardarResumenes([resumen]));

    final api = Api(cliente: MockClient((_) async => throw const SocketException('sin red')));

    await _abrir(
      tester,
      PantallaDiagramas(
        api: api,
        almacen: almacen,
        credencial: const Credencial(
          token: 't',
          usuarioId: 'u1',
          nombre: 'Ana',
          email: 'ana@example.com',
        ),
        sesionId: 's1',
        alSalir: () async {},
      ),
    );
    await _esperar(tester, find.text('Clinica'));

    await tester.tap(find.byTooltip('Diagrama'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));

    expect(find.byType(PantallaLienzo), findsOneWidget);
  });
}
