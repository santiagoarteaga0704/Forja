import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
    ClaseConocida(id: 'id-persona', nombre: 'Persona'),
  ]);

  test('crea una clase en todas las formas usuales', () {
    for (final frase in [
      'crea la clase Factura',
      'crear clase Factura',
      'nueva clase Factura',
      'agrega una clase llamada Factura',
      'creame una clase Factura',
      'dibuja la clase Factura',
    ]) {
      final resultado = parser.interpretar(frase, contexto);
      expect(resultado.entendida, isTrue, reason: frase);
      expect(resultado.pasos.single.tipo, 'CLASE_CREAR', reason: frase);
      expect(resultado.pasos.single.comando['nombre'], 'Factura', reason: frase);
      expect(resultado.pasos.single.comando['estereotipo'], isNull, reason: frase);
      expect(resultado.pasos.single.comando['esAbstracta'], isFalse, reason: frase);
    }
  });

  test('el identificador de una clase nueva es un UUID', () {
    // Si no lo fuera, la operacion se rechazaria al sincronizar.
    final id = parser.interpretar('crea la clase Factura', contexto)
        .pasos.single.comando['claseId'] as String;
    expect(
        RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
            .hasMatch(id),
        isTrue,
        reason: id);
  });

  test('un nombre de varias palabras se junta en camello', () {
    final resultado = parser.interpretar('crea la clase historia clinica', contexto);
    expect(resultado.pasos.single.comando['nombre'], 'HistoriaClinica');
  });

  test('el nombre de una clase siempre empieza en mayuscula', () {
    // El reconocedor de voz devuelve en minuscula lo que le parece, y una
    // clase llamada "factura" en un diagrama UML esta mal.
    final resultado = parser.interpretar('crea la clase factura', contexto);
    expect(resultado.pasos.single.comando['nombre'], 'Factura');
  });

  test('aguanta los acentos y la puntuacion del reconocedor', () {
    final resultado = parser.interpretar('creá la clase Médico.', contexto);
    expect(resultado.pasos.single.comando['nombre'], 'Medico');
  });

  test('una interfaz lleva su estereotipo', () {
    final resultado = parser.interpretar('crea la interfaz Auditable', contexto);
    expect(resultado.pasos.single.comando['nombre'], 'Auditable');
    expect(resultado.pasos.single.comando['estereotipo'], 'interface');
  });

  test('renombra y elimina resolviendo la clase contra el diagrama', () {
    final renombre = parser.interpretar('renombra Paciente a Interno', contexto);
    expect(renombre.pasos.single.tipo, 'CLASE_RENOMBRAR');
    expect(renombre.pasos.single.comando['claseId'], 'id-paciente');
    expect(renombre.pasos.single.comando['nombre'], 'Interno');

    final baja = parser.interpretar('elimina la clase Consulta', contexto);
    expect(baja.pasos.single.tipo, 'CLASE_ELIMINAR');
    expect(baja.pasos.single.comando['claseId'], 'id-consulta');
  });

  test('marca abstracta en las tres formas', () {
    for (final frase in [
      'marca Persona como abstracta',
      'Persona es abstracta',
      'declara la clase Persona como abstracta',
    ]) {
      final resultado = parser.interpretar(frase, contexto);
      expect(resultado.pasos.single.tipo, 'CLASE_MARCAR', reason: frase);
      expect(resultado.pasos.single.comando['claseId'], 'id-persona', reason: frase);
      expect(resultado.pasos.single.comando['esAbstracta'], isTrue, reason: frase);
    }
  });

  test('marcar una clase como interfaz le pone el estereotipo', () {
    final resultado = parser.interpretar('Persona es una interfaz', contexto);
    expect(resultado.pasos.single.tipo, 'CLASE_MARCAR');
    expect(resultado.pasos.single.comando['estereotipo'], 'interface');
    expect(resultado.pasos.single.comando['esAbstracta'], isFalse);
  });

  test('nombrar una clase que no existe explica cual falta, no dice "no entendi"', () {
    final resultado = parser.interpretar('elimina la clase Factura', contexto);
    expect(resultado.entendida, isFalse);
    expect(resultado.sugerencias.first, contains('Factura'));
    expect(resultado.sugerencias.last, contains('crea la clase Factura'));
    expect(resultado.sugerencias[1], contains('Paciente'));
  });

  test('una frase vacia devuelve las sugerencias generales', () {
    for (final frase in ['', '   ']) {
      final resultado = parser.interpretar(frase, contexto);
      expect(resultado.entendida, isFalse);
      expect(resultado.sugerencias, hasLength(7));
      expect(resultado.sugerencias.first, 'crea la clase Paciente');
    }
  });
}
