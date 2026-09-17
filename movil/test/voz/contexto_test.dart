import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';

/// El espejo de ContextoDelDiagrama.java.
///
/// Lo que se prueba aca no es el parser sino la resolucion de nombres, que es
/// la pieza de la que depende todo lo demas: sin ella, "Paciente tiene muchas
/// Consultas" y "Paciente tiene muchas deudas" son indistinguibles.
void main() {
  group('clave', () {
    test('quita acentos, signos y mayusculas', () {
      expect(clave('Médico'), 'medico');
      expect(clave('Historia Clínica'), 'historiaclinica');
      expect(clave('Paciente.'), 'paciente');
      expect(clave('Niño'), 'nino');
    });
  });

  group('resolver', () {
    final contexto = ContextoDelDiagrama.de(const [
      ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
      ClaseConocida(id: 'id-consulta', nombre: 'Consulta'),
      ClaseConocida(id: 'id-historia', nombre: 'HistoriaClinica'),
    ]);

    test('encuentra por coincidencia exacta sin importar acentos ni caja', () {
      expect(contexto.resolver('paciente')?.id, 'id-paciente');
      expect(contexto.resolver('Pacienté')?.id, 'id-paciente');
      expect(contexto.resolver('PACIENTE')?.id, 'id-paciente');
    });

    test('admite una coincidencia parcial cuando es unica', () {
      // Dictando es facil que "historia" quiera decir "HistoriaClinica".
      expect(contexto.resolver('historia')?.id, 'id-historia');
      expect(contexto.resolver('historia clinica')?.id, 'id-historia');
    });

    test('un plural dictado encuentra la clase en singular', () {
      // Se dice "muchas Consultas" pero la clase se llama Consulta.
      expect(contexto.resolver('Consultas')?.id, 'id-consulta');
    });

    test('no adivina cuando la coincidencia parcial es ambigua', () {
      final dos = ContextoDelDiagrama.de(const [
        ClaseConocida(id: 'a', nombre: 'Consulta'),
        ClaseConocida(id: 'b', nombre: 'ConsultaMedica'),
      ]);
      expect(dos.resolver('consul'), isNull);
    });

    test('un nombre que no esta devuelve nulo', () {
      expect(contexto.resolver('Factura'), isNull);
      expect(contexto.resolver(''), isNull);
      expect(contexto.resolver(null), isNull);
    });
  });

  group('nombres', () {
    test('devuelve los nombres en el orden en que se cargaron', () {
      final contexto = ContextoDelDiagrama.de(const [
        ClaseConocida(id: 'a', nombre: 'Paciente'),
        ClaseConocida(id: 'b', nombre: 'Consulta'),
      ]);
      // El orden importa: las sugerencias del parser toman el primero como
      // ejemplo y el segundo como el otro extremo de una relacion.
      expect(contexto.nombres(), ['Paciente', 'Consulta']);
    });

    test('un contexto vacio no tiene nombres', () {
      expect(ContextoDelDiagrama.vacio().nombres(), isEmpty);
    });
  });
}
