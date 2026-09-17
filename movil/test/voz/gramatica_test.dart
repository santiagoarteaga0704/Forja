import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/gramatica.dart';
import 'package:forja_movil/voz/tipos_declarados.dart';

/// Los ladrillos con los que estan hechos los 19 patrones de la gramatica.
///
/// Se prueban aparte porque son lo que comparten todos: un error aca no rompe
/// una forma de decir las cosas, las rompe todas a la vez.
void main() {
  group('limpiar', () {
    test('saca acentos, la puntuacion del reconocedor y los espacios de sobra', () {
      expect(limpiar('  creá   la clase Médico. '), 'crea la clase Medico');
      expect(limpiar('¿crea la clase Factura?'), 'crea la clase Factura');
      expect(limpiar('a Paciente, agregale el atributo nombre'),
          'a Paciente agregale el atributo nombre');
    });
  });

  group('limpiarNombre', () {
    test('junta las palabras en camello', () {
      expect(limpiarNombre('historia clinica'), 'historiaClinica');
      expect(limpiarNombre('fecha de nacimiento'), 'fechaDeNacimiento');
    });

    test('la primera palabra conserva su caja', () {
      // Para no estropear un nombre que el cliente escribio bien.
      expect(limpiarNombre('Historia clinica'), 'HistoriaClinica');
      expect(limpiarNombre('Factura'), 'Factura');
    });

    test('aguanta nulo, vacio y espacios de mas', () {
      expect(limpiarNombre(null), '');
      expect(limpiarNombre('   '), '');
      expect(limpiarNombre('  historia   clinica  '), 'historiaClinica');
    });
  });

  group('separarEnumeracion', () {
    test('separa por coma, por y, y por e', () {
      expect(separarEnumeracion('a, b y c'), ['a', 'b', 'c']);
      expect(separarEnumeracion('uno e dos'), ['uno', 'dos']);
    });

    test('no deja partes vacias', () {
      // El split de Java descarta los vacios del final; el de Dart no.
      expect(separarEnumeracion('a, b,'), ['a', 'b']);
    });

    test('un solo elemento sigue siendo una lista de uno', () {
      expect(separarEnumeracion('numero de tipo texto'), ['numero de tipo texto']);
    });
  });

  group('regla', () {
    test('ancla la expresion entera y no distingue mayusculas', () {
      final r = regla('crea la clase $nomFin');
      expect(r.firstMatch('CREA LA CLASE Factura')?.group(1), 'Factura');
      expect(r.firstMatch('y crea la clase Factura'), isNull);
      expect(r.firstMatch('crea la clase Factura ahora')?.group(1), 'Factura ahora');
    });
  });

  group('normalizarTipo', () {
    test('lleva lo que se dice en castellano a un nombre unico', () {
      expect(normalizarTipo('texto'), 'String');
      expect(normalizarTipo('entero'), 'Integer');
      expect(normalizarTipo('importe'), 'Decimal');
      expect(normalizarTipo('booleano'), 'Boolean');
      expect(normalizarTipo('fecha'), 'Date');
      expect(normalizarTipo('fechayhora'), 'DateTime');
      expect(normalizarTipo('hora'), 'Time');
      expect(normalizarTipo('uuid'), 'UUID');
    });

    test('un tipo desconocido se respeta con la inicial en mayuscula', () {
      // Puede ser otra clase del modelo o un enumerado que se va a crear
      // despues. Inventar una equivalencia seria peor que no tener ninguna.
      expect(normalizarTipo('EstadoCivil'), 'EstadoCivil');
      expect(normalizarTipo('estadoCivil'), 'EstadoCivil');
    });

    test('sin tipo declarado se asume texto', () {
      expect(normalizarTipo(null), 'String');
      expect(normalizarTipo('  '), 'String');
    });
  });
}
