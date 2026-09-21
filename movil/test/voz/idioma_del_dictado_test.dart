import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/idioma_del_dictado.dart';

/// Elegir con que idioma escuchar.
///
/// Nace de una medicion del 21 de septiembre de 2026: el dictado no funcionaba
/// en ninguna maquina porque se pedia `es_419`, el tag de macro-region de
/// Latinoamerica, y el reconocedor solo acepta variantes CON PAIS. Peor, el
/// rechazo no dice "idioma invalido": dice `network`, que manda a buscar el
/// problema en la red y en los permisos, donde no esta.
///
/// En el navegador se pudo fijar `es-BO` y comprobarlo. En el telefono no:
/// cada aparato trae su propia lista y el A56 no se probo nunca. Por eso aca
/// no se fija un tag a ciegas -seria cambiar una suposicion por otra- sino que
/// se elige de lo que el aparato dice tener.
void main() {
  group('Idioma del dictado', () {
    test('prefiere Bolivia cuando el aparato la tiene', () {
      expect(
        idiomaDelDictado(['en_US', 'es_ES', 'es_BO', 'es_MX']),
        'es_BO',
      );
    });

    test('si no esta Bolivia, toma otro castellano de America antes que el de Espana', () {
      expect(idiomaDelDictado(['en_US', 'es_ES', 'es_MX']), 'es_MX');
    });

    test('el castellano de Espana sirve si es el unico que hay', () {
      expect(idiomaDelDictado(['en_US', 'es_ES']), 'es_ES');
    });

    test('NUNCA devuelve una macro-region, que es el defecto que esto cierra', () {
      expect(idiomaDelDictado(['es_419', 'en_US']), isNull);
    });

    test('NUNCA devuelve un idioma sin pais, por la misma razon', () {
      expect(idiomaDelDictado(['es', 'en_US']), isNull);
    });

    test('sin ningun castellano devuelve nulo, y el aparato usa el suyo', () {
      expect(idiomaDelDictado(['en_US', 'pt_BR']), isNull);
    });

    test('la lista vacia no rompe', () {
      expect(idiomaDelDictado([]), isNull);
    });

    test('entiende los tags con guion ademas de los de guion bajo', () {
      expect(idiomaDelDictado(['es-BO', 'en-US']), 'es-BO');
    });

    test('no confunde otro idioma que empiece con es', () {
      expect(idiomaDelDictado(['est_EE', 'en_US']), isNull);
    });
  });
}
