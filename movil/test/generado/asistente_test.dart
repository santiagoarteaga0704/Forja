import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/asistente.dart';

void main() {
  test('devuelve la frase que propuso el modelo', () async {
    final asistente = Asistente(
      preguntarAlModelo: (_) async => 'agrega un paciente llamado Juan',
    );

    expect(await asistente.fraseCanonica('anota a Juan como paciente'),
        'agrega un paciente llamado Juan');
  });

  test('si el modelo falla, devuelve null y no rompe la app', () async {
    // El modelo es prescindible: sin el se pierde un escalon, no la app.
    final asistente = Asistente(
      preguntarAlModelo: (_) async => throw Exception('modelo no cargado'),
    );

    expect(await asistente.fraseCanonica('lo que sea'), isNull);
  });

  test('una respuesta vacia se trata como que no hubo propuesta', () async {
    final asistente = Asistente(preguntarAlModelo: (_) async => '   ');

    expect(await asistente.fraseCanonica('lo que sea'), isNull);
  });

  test('el pedido del usuario viaja dentro del prompt', () async {
    // Sin esto el prompt podria armarse mal y el modelo contestaria sobre otra
    // cosa sin que ninguna prueba se entere.
    String? visto;
    final asistente = Asistente(preguntarAlModelo: (prompt) async {
      visto = prompt;
      return 'agrega un paciente llamado Juan';
    });

    await asistente.fraseCanonica('anota a Juan como paciente');

    expect(visto, contains('anota a Juan como paciente'));
  });

  test('se queda con la primera linea y le saca las comillas', () async {
    // Un modelo de mil millones de parametros agrega explicaciones aunque se le
    // pida que no: la gramatica no entiende «Orden: "..."» ni un parrafo, asi
    // que lo que sobra se recorta aca y no en el parser.
    final asistente = Asistente(
      preguntarAlModelo: (_) async =>
          '"agrega un paciente llamado Juan"\nEspero que te sirva.',
    );

    expect(await asistente.fraseCanonica('lo que sea'),
        'agrega un paciente llamado Juan');
  });

  test('el prompt enumera las entidades del diagrama', () async {
    // Sin la lista, el modelo traduce a ciegas: propondria «cliente» sobre un
    // diagrama que tiene «Paciente» y el parser lo rechazaria con razon.
    String? visto;
    final asistente = Asistente(preguntarAlModelo: (prompt) async {
      visto = prompt;
      return 'agrega un paciente llamado Juan';
    });

    await asistente.fraseCanonica('anota a Juan', entidades: ['Paciente', 'Medico']);

    expect(visto, contains('Paciente, Medico'));
  });

  test('sin entidades el prompt no inventa una lista vacia', () async {
    String? visto;
    final asistente = Asistente(preguntarAlModelo: (prompt) async {
      visto = prompt;
      return 'agrega un paciente llamado Juan';
    });

    await asistente.fraseCanonica('anota a Juan');

    expect(visto, isNot(contains('tiene que ser una de estas')));
  });

  test('el prompt ensena las dos formas que el parser entiende', () async {
    // El parser tambien acepta «con <campo> <valor>»: ensenar solo «llamado»
    // desperdicia la mitad de la gramatica.
    String? visto;
    final asistente = Asistente(preguntarAlModelo: (prompt) async {
      visto = prompt;
      return 'agrega un paciente llamado Juan';
    });

    await asistente.fraseCanonica('lo que sea');

    expect(visto, contains('llamado <valor>'));
    expect(visto, contains('con <campo> <valor>'));
  });

  test('le saca el «Orden:» que el propio prompt le dejo servido', () async {
    // El prompt termina justo en «Orden:», que es la etiqueta que estos modelos
    // repiten antes de contestar. Con ella pegada adelante, el parser falla.
    final asistente = Asistente(
      preguntarAlModelo: (_) async => 'Orden: agrega un paciente llamado Juan',
    );

    expect(await asistente.fraseCanonica('lo que sea'),
        'agrega un paciente llamado Juan');
  });

  test('el «Orden:» tambien sale cuando viene adentro de las comillas', () async {
    final asistente = Asistente(
      preguntarAlModelo: (_) async => '"Orden: agrega un paciente llamado Juan"',
    );

    expect(await asistente.fraseCanonica('lo que sea'),
        'agrega un paciente llamado Juan');
  });

  test('un modelo que no vuelve nunca no cuelga el dictado', () async {
    // Sin el limite, un generateChatResponse() trabado deja _dictar sin
    // terminar para siempre y el boton del microfono mudo.
    final asistente = Asistente(
      preguntarAlModelo: (_) => Completer<String>().future,
      limite: const Duration(milliseconds: 50),
    );

    expect(await asistente.fraseCanonica('lo que sea'), isNull);
  });
}
