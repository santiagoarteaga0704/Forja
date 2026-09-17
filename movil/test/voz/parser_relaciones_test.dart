import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
    ClaseConocida(id: 'id-persona', nombre: 'Persona'),
    ClaseConocida(id: 'id-medico', nombre: 'Medico'),
    ClaseConocida(id: 'id-auditable', nombre: 'Auditable'),
  ]);

  test('cada tipo de relacion tiene su forma de decirse', () {
    final esperados = {
      'Paciente hereda de Persona': 'HERENCIA',
      'Medico implementa Auditable': 'REALIZACION',
      'Paciente se compone de muchas Consultas': 'COMPOSICION',
      'Medico agrupa muchas Consultas': 'AGREGACION',
      'Consulta depende de Persona': 'DEPENDENCIA',
      'un Paciente tiene muchas Consultas': 'ASOCIACION',
    };

    esperados.forEach((frase, tipoEsperado) {
      final resultado = parser.interpretar(frase, contexto);
      expect(resultado.entendida, isTrue, reason: frase);
      expect(resultado.pasos.single.tipo, 'RELACION_CREAR', reason: frase);
      expect(resultado.pasos.single.comando['tipo'], tipoEsperado, reason: frase);
    });
  });

  test('la herencia apunta de la subclase a la superclase', () {
    // Si se invirtiera, la jerarquia saldria al reves en el codigo generado.
    final resultado = parser.interpretar('Paciente hereda de Persona', contexto);
    expect(resultado.pasos.single.comando['origenId'], 'id-paciente');
    expect(resultado.pasos.single.comando['destinoId'], 'id-persona');
  });

  test('"muchas" se lee como cero o mas, no como uno o mas', () {
    // Es la lectura que no inventa una restriccion que nadie pidio.
    final resultado = parser.interpretar('un Paciente tiene muchas Consultas', contexto);
    expect(resultado.pasos.single.comando['multiplicidadOrigen'], '1');
    expect(resultado.pasos.single.comando['multiplicidadDestino'], '0..*');
  });

  test('"al menos una" se distingue de "muchas"', () {
    final resultado =
        parser.interpretar('un Paciente tiene al menos una Consulta', contexto);
    expect(resultado.pasos.single.comando['multiplicidadDestino'], '1..*');
  });

  test('un plural dictado resuelve la clase en singular', () {
    final resultado = parser.interpretar('una Consulta tiene muchos Medicos', contexto);
    expect(resultado.pasos.single.comando['destinoId'], 'id-medico');
  });

  test('el identificador de la relacion es un UUID', () {
    final id = parser.interpretar('Paciente hereda de Persona', contexto)
        .pasos.single.comando['relacionId'] as String;
    expect(
        RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')
            .hasMatch(id),
        isTrue,
        reason: id);
  });

  group('la ambiguedad de "tiene"', () {
    test('con una clase conocida detras, es una relacion', () {
      final resultado = parser.interpretar('Paciente tiene muchas Consultas', contexto);
      expect(resultado.pasos.single.tipo, 'RELACION_CREAR');
      expect(resultado.pasos.single.comando['destinoId'], 'id-consulta');
    });

    test('con algo que no es clase detras, es un atributo', () {
      final resultado =
          parser.interpretar('Paciente tiene un peso de tipo decimal', contexto);
      expect(resultado.pasos.single.tipo, 'ATRIBUTO_AGREGAR');
      expect(resultado.pasos.single.comando['claseId'], 'id-paciente');
      expect(resultado.pasos.single.comando['nombre'], 'peso');
      expect(resultado.pasos.single.comando['tipo'], 'Decimal');
    });
  });
}
