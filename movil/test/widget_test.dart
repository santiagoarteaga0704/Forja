import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/api.dart';
import 'package:forja_movil/pantallas/entrar.dart';
import 'package:forja_movil/tipos.dart';

/// Api que finge no tener red, para comprobar que la pantalla lo explica en
/// lugar de quedarse girando.
class ApiSinRed extends Api {
  ApiSinRed() : super(base: 'http://pruebas');

  @override
  Future<Credencial> entrar(String email, String password) async => throw SinConexion();
}

void main() {
  testWidgets('la pantalla de entrada pide correo, contrasena y servidor', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: PantallaEntrar(api: ApiSinRed(), alEntrar: (_) async {}),
    ));

    expect(find.text('Entrar'), findsWidgets);
    // La direccion del servidor es editable a proposito: cambia entre el
    // emulador, un telefono real y el despliegue.
    expect(find.widgetWithText(TextField, 'http://pruebas'), findsOneWidget);
    expect(find.text('Crear una cuenta'), findsOneWidget);
  });

  testWidgets('sin red se explica que hacer, no se queda esperando', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: PantallaEntrar(api: ApiSinRed(), alEntrar: (_) async {}),
    ));

    await tester.enterText(find.byType(TextField).first, 'ana@forja.test');
    await tester.tap(find.widgetWithText(FilledButton, 'Entrar'));
    await tester.pumpAndSettle();

    expect(find.textContaining('No se pudo llegar al servidor'), findsOneWidget);
  });
}
