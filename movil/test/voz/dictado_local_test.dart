import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/dictado_local.dart';

/// El camino completo del dictado en el aparato, sin red y sin pantalla.
void main() {
  var n = 0;
  String token() => 'token-${n++}';

  setUp(() => n = 0);

  test('una frase dictada se convierte en comandos listos para encolar', () {
    final resultado = interpretarDictado(
      'a Paciente agregale el atributo nombre de tipo texto',
      ContextoDelDiagrama.de(const [
        ClaseConocida(id: 'id-paciente', nombre: 'Paciente'),
      ]),
      token,
    );

    expect(resultado.interpretacion.entendida, isTrue);
    expect(resultado.comandos, hasLength(1));
    expect(resultado.comandos.single.tipo, 'ATRIBUTO_AGREGAR');
    expect(resultado.comandos.single.origen, 'VOZ');
    expect(resultado.comandos.single.carga['claseId'], 'id-paciente');
  });

  test('una frase que no se entiende no produce comandos y devuelve sugerencias', () {
    final resultado =
        interpretarDictado('bla bla bla', ContextoDelDiagrama.vacio(), token);

    expect(resultado.interpretacion.entendida, isFalse);
    expect(resultado.comandos, isEmpty);
    expect(resultado.interpretacion.sugerencias, isNotEmpty);
  });

  group('cuadricula', () {
    test('una clase nueva no queda en el origen: toma la casilla que sigue', () {
      // Dos clases existentes, asi que la nueva va a la casilla 2.
      final resultado = interpretarDictado(
        'crea la clase Factura',
        ContextoDelDiagrama.de(const [
          ClaseConocida(id: 'a', nombre: 'Paciente'),
          ClaseConocida(id: 'b', nombre: 'Consulta'),
        ]),
        token,
      );

      expect(resultado.comandos.single.carga['posX'], 760);
      expect(resultado.comandos.single.carga['posY'], 0);
    });

    test('la quinta casilla baja a la segunda fila', () {
      final resultado = interpretarDictado(
        'crea la clase Factura',
        ContextoDelDiagrama.de(const [
          ClaseConocida(id: 'a', nombre: 'Uno'),
          ClaseConocida(id: 'b', nombre: 'Dos'),
          ClaseConocida(id: 'c', nombre: 'Tres'),
          ClaseConocida(id: 'd', nombre: 'Cuatro'),
        ]),
        token,
      );

      expect(resultado.comandos.single.carga['posX'], 0);
      expect(resultado.comandos.single.carga['posY'], 260);
    });

    test('sobre un diagrama vacio la primera clase va al origen', () {
      final resultado =
          interpretarDictado('crea la clase Paciente', ContextoDelDiagrama.vacio(), token);
      expect(resultado.comandos.single.carga['posX'], 0);
      expect(resultado.comandos.single.carga['posY'], 0);
    });

    test('solo se ubican las altas de clase, no los atributos', () {
      // "crea la clase X con los atributos ..." produce tres comandos y solo el
      // primero lleva coordenadas.
      final resultado = interpretarDictado(
        'crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal',
        ContextoDelDiagrama.vacio(),
        token,
      );

      expect(resultado.comandos, hasLength(3));
      expect(resultado.comandos[0].carga['posX'], 0);
      expect(resultado.comandos[1].carga.containsKey('posX'), isFalse);
      expect(resultado.comandos[2].carga.containsKey('posX'), isFalse);
    });

    test('los atributos siguen colgando de la clase recien ubicada', () {
      final resultado = interpretarDictado(
        'crea la clase Factura con los atributos numero de tipo texto',
        ContextoDelDiagrama.vacio(),
        token,
      );

      expect(resultado.comandos[1].carga['claseId'], resultado.comandos[0].carga['claseId']);
    });
  });

  test('cada comando lleva su propio token', () {
    final resultado = interpretarDictado(
      'crea la clase Factura con los atributos numero de tipo texto y total de tipo decimal',
      ContextoDelDiagrama.vacio(),
      token,
    );
    expect(resultado.comandos.map((c) => c.tokenCliente).toSet(), hasLength(3));
  });
}
