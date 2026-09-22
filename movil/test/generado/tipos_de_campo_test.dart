import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/tipos_de_campo.dart';

void main() {
  group('claseDeCampoDe', () {
    test('acepta las formas en ingles y en castellano', () {
      expect(claseDeCampoDe('String'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('texto'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('int'), ClaseDeCampo.entero);
      expect(claseDeCampoDe('entero'), ClaseDeCampo.entero);
      expect(claseDeCampoDe('boolean'), ClaseDeCampo.booleano);
      expect(claseDeCampoDe('logico'), ClaseDeCampo.booleano);
    });

    test('no distingue mayusculas', () {
      expect(claseDeCampoDe('TEXTO'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('Fecha'), ClaseDeCampo.fecha);
    });

    test('separa las tres formas de tiempo, que se escriben distinto', () {
      expect(claseDeCampoDe('fecha'), ClaseDeCampo.fecha);
      expect(claseDeCampoDe('fechahora'), ClaseDeCampo.fechaHora);
      expect(claseDeCampoDe('hora'), ClaseDeCampo.hora);
    });

    test('lo monetario es decimal, no entero', () {
      expect(claseDeCampoDe('precio'), ClaseDeCampo.decimal);
      expect(claseDeCampoDe('importe'), ClaseDeCampo.decimal);
    });

    test('un tipo que no se reconoce no se inventa', () {
      // Puede ser otra clase del modelo o un enumerado que el usuario
      // agregara despues. Tratarlo como texto mentira sobre su naturaleza.
      expect(claseDeCampoDe('Direccion'), ClaseDeCampo.desconocido);
    });
  });
}
