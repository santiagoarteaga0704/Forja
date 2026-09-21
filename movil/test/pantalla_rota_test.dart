import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/pantalla_rota.dart';

/// Lo que se ve cuando algo se rompe.
///
/// El cliente web tenia este mismo hueco y se vio de la peor manera: un error
/// dentro del lienzo dejaba la pantalla COMPLETAMENTE NEGRA, sin mensaje y sin
/// forma de volver. En el telefono es peor todavia, porque en una compilacion
/// de release Flutter muestra un recuadro gris que no dice nada.
///
/// Un cartel feo del que se pueda salir es mejor que una pantalla muerta
/// delante de un tribunal.
void main() {
  /// Rompe un widget a proposito y devuelve lo que se dibujo en su lugar.
  ///
  /// El arnes de Flutter exige que ErrorWidget.builder quede como estaba
  /// ANTES de que termine la prueba -lo comprueba el, no un teardown-, asi que
  /// se restaura aca adentro. Y guarda la excepcion para volver a lanzarla al
  /// final si nadie la recoge, por eso se consume con takeException.
  Future<void> alRomperse(WidgetTester tester, String mensaje) async {
    final original = ErrorWidget.builder;
    instalarPantallaRota();
    await tester.pumpWidget(MaterialApp(
      home: Builder(builder: (_) => throw StateError(mensaje)),
    ));
    ErrorWidget.builder = original;
    expect(tester.takeException(), isA<StateError>());
  }

  testWidgets('el cartel dice que se rompio y muestra que fue', (tester) async {
    await alRomperse(tester, 'se rompio la cosa');

    expect(find.text('Se rompió algo'), findsOneWidget);
    expect(find.textContaining('se rompio la cosa'), findsOneWidget);
  });

  testWidgets('avisa que lo aplicado esta guardado', (tester) async {
    await alRomperse(tester, 'otra cosa');

    // Es cierto: cada cambio se escribe en el momento en que se hace -al
    // servidor, o al almacen local si no hay red-, asi que un error no se
    // lleva el diagrama.
    expect(find.textContaining('guardado'), findsOneWidget);
  });

  testWidgets('no tapa la pantalla con un Scaffold anidado', (tester) async {
    await alRomperse(tester, 'da igual');

    // Este widget aparece en el lugar de cualquier otro, incluso dentro de uno
    // que ya es un Scaffold. Anidar dos deja la pantalla en blanco.
    expect(find.byType(Scaffold), findsNothing);
    expect(find.byType(PantallaRota), findsOneWidget);
  });
}
