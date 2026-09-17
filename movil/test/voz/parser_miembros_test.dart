import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/parser_voz.dart';

void main() {
  final parser = ParserVoz();
  final contexto = ContextoDelDiagrama.de(const [
    ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
    ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
  ]);

  group('atributos', () {
    test('acepta el destino al principio y al final de la frase', () {
      final primero = parser.interpretar(
          'a Paciente agregale el atributo nombre de tipo texto', contexto);
      expect(primero.pasos.single.tipo, 'ATRIBUTO_AGREGAR');
      expect(primero.pasos.single.comando['claseId'], 'id-paciente');
      expect(primero.pasos.single.comando['nombre'], 'nombre');
      expect(primero.pasos.single.comando['tipo'], 'String');

      final ultimo = parser.interpretar(
          'agrega el atributo edad de tipo entero a Paciente', contexto);
      expect(ultimo.pasos.single.comando['claseId'], 'id-paciente');
      expect(ultimo.pasos.single.comando['nombre'], 'edad');
      expect(ultimo.pasos.single.comando['tipo'], 'Integer');
    });

    test('la forma declarativa tambien se entiende', () {
      final resultado =
          parser.interpretar('el Paciente tiene un telefono de tipo texto', contexto);
      expect(resultado.pasos.single.tipo, 'ATRIBUTO_AGREGAR');
      expect(resultado.pasos.single.comando['claseId'], 'id-paciente');
      expect(resultado.pasos.single.comando['nombre'], 'telefono');
    });

    test('un nombre de varias palabras no se corta al declarar el tipo', () {
      // Aparecio dictando de verdad: el nombre quedaba en "fecha" y el tipo se
      // perdia, porque el final de la expresion se tragaba el resto.
      final resultado = parser.interpretar(
          'a Paciente agregale el atributo fecha de nacimiento de tipo fecha', contexto);
      expect(resultado.pasos.single.comando['nombre'], 'fechaDeNacimiento');
      expect(resultado.pasos.single.comando['tipo'], 'Date');
    });

    test('sin tipo, el nombre llega hasta la primera palabra de marca', () {
      final resultado = parser.interpretar(
          'a Paciente agregale el atributo historia clinica clave', contexto);
      expect(resultado.pasos.single.comando['nombre'], 'historiaClinica');
      expect(resultado.pasos.single.comando['esIdentificador'], isTrue);
      expect(resultado.pasos.single.comando['tipo'], 'String');
    });

    test('el articulo "una" no se parte en "un" mas "a"', () {
      final resultado = parser.interpretar(
          'una Consulta tiene un importe de tipo decimal', contexto);
      expect(resultado.entendida, isTrue);
      expect(resultado.pasos.single.comando['claseId'], 'id-consulta');
      expect(resultado.pasos.single.comando['nombre'], 'importe');
      expect(resultado.pasos.single.comando['tipo'], 'Decimal');
    });

    test('una clave es obligatoria y unica aunque no se diga', () {
      final resultado = parser.interpretar(
          'a Paciente agregale el atributo codigo de tipo texto clave de longitud 20',
          contexto);
      expect(resultado.pasos.single.comando['esIdentificador'], isTrue);
      expect(resultado.pasos.single.comando['longitud'], 20);
      expect(resultado.pasos.single.comando['esRequerido'], isTrue);
      expect(resultado.pasos.single.comando['esUnico'], isTrue);
    });

    test('obligatorio no implica clave ni unico', () {
      final resultado = parser.interpretar(
          'a Paciente agregale el atributo nombre de tipo texto obligatorio', contexto);
      expect(resultado.pasos.single.comando['esRequerido'], isTrue);
      expect(resultado.pasos.single.comando['esIdentificador'], isFalse);
      expect(resultado.pasos.single.comando['esUnico'], isFalse);
    });

    test('crea la clase y sus atributos de una sola vez, colgados de ella', () {
      final resultado = parser.interpretar(
          'crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal',
          contexto);

      expect(resultado.pasos.map((p) => p.tipo),
          ['CLASE_CREAR', 'ATRIBUTO_AGREGAR', 'ATRIBUTO_AGREGAR']);

      final claseId = resultado.pasos.first.comando['claseId'];
      expect(resultado.pasos[1].comando['claseId'], claseId);
      expect(resultado.pasos[2].comando['claseId'], claseId);
      expect(resultado.pasos[1].comando['nombre'], 'numero');
      expect(resultado.pasos[2].comando['tipo'], 'Decimal');
    });
  });

  group('metodos', () {
    test('reconoce el nombre y el tipo de retorno', () {
      final resultado = parser.interpretar(
          'a Paciente agregale el metodo calcularEdad que devuelve entero', contexto);
      expect(resultado.pasos.single.tipo, 'METODO_AGREGAR');
      expect(resultado.pasos.single.comando['claseId'], 'id-paciente');
      expect(resultado.pasos.single.comando['nombre'], 'calcularEdad');
      expect(resultado.pasos.single.comando['tipoRetorno'], 'Integer');
      expect(resultado.pasos.single.comando['parametros'], isEmpty);
    });

    test('sin retorno declarado la operacion es void', () {
      final resultado =
          parser.interpretar('a Paciente agregale la operacion internar', contexto);
      expect(resultado.pasos.single.comando['nombre'], 'internar');
      expect(resultado.pasos.single.comando['tipoRetorno'], 'void');
    });

    test('reconoce la firma completa con parametros', () {
      final resultado = parser.interpretar(
          'a Paciente agregale el metodo registrarConsulta con parametros motivo de '
          'tipo texto y monto de tipo importe que devuelve booleano',
          contexto);

      expect(resultado.pasos.single.comando['nombre'], 'registrarConsulta');
      expect(resultado.pasos.single.comando['tipoRetorno'], 'Boolean');

      final parametros = resultado.pasos.single.comando['parametros'] as List;
      expect(parametros.map((p) => p['nombre']), ['motivo', 'monto']);
      expect(parametros.map((p) => p['tipo']), ['String', 'Decimal']);
    });
  });

  test('referenciar una clase que no existe se explica', () {
    final resultado = parser.interpretar(
        'a Factura agregale el atributo total de tipo decimal', contexto);
    expect(resultado.entendida, isFalse);
    expect(resultado.sugerencias.first, contains('Factura'));
  });
}
