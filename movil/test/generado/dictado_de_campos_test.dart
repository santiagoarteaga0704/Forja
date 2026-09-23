import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/dictado_de_campos.dart';
import 'package:forja_movil/generado/tipos_de_campo.dart';
import 'package:forja_movil/tipos.dart';

/// Lo que se probo en un telefono de verdad y salio mal.
///
/// El formulario de Paciente tiene tres campos obligatorios y el dictado sabia
/// llenar uno solo, asi que dictar creaba registros que el backend generado
/// contestaba con 500; y como un 500 se reintenta, la cola no avanzaba nunca.
/// De ahi salen casi todas las pruebas de este archivo: cada una es una frase
/// que el telefono escucho.
void main() {
  const ci = Atributo(id: 'a1', nombre: 'ci', tipo: 'String');
  const nombre = Atributo(id: 'a2', nombre: 'nombre', tipo: 'String');
  const nacimiento = Atributo(id: 'a3', nombre: 'nacimiento', tipo: 'LocalDate');
  const paciente = [ci, nombre, nacimiento];

  const vacio = {'ci': '', 'nombre': '', 'nacimiento': ''};

  DictadoDeFormulario dictar(String frase, [Map<String, String> valores = vacio]) =>
      ubicarDictado(frase: frase, campos: paciente, valores: valores);

  group('donde cae lo dictado', () {
    test('un valor suelto llena el primer campo vacio', () {
      final puesto = dictar('1234567');

      expect(puesto.esAlta, isFalse);
      expect(puesto.campos.single.campo, 'ci');
      expect(puesto.campos.single.valor, '1234567');
    });

    test('el siguiente valor suelto llena el siguiente vacio, no el mismo', () {
      final puesto = dictar('Juan Perez', const {'ci': '1234567', 'nombre': '', 'nacimiento': ''});

      expect(puesto.campos.single.campo, 'nombre');
      expect(puesto.campos.single.valor, 'Juan Perez');
    });

    test('nombrar el campo manda el valor a ese campo aunque este vacio otro antes', () {
      final puesto = dictar('nacimiento 23 de marzo del 2000');

      expect(puesto.campos.single.campo, 'nacimiento');
      expect(puesto.campos.single.valor, '2000-03-23');
    });

    test('nombrar un campo ya lleno lo corrige', () {
      final puesto = dictar(
        'nombre Ana',
        const {'ci': '1234567', 'nombre': 'Juan', 'nacimiento': ''},
      );

      expect(puesto.campos.single.campo, 'nombre');
      expect(puesto.campos.single.valor, 'Ana');
    });

    test('el campo se reconoce sin acentos y sin mayusculas, como en el diagrama', () {
      final campos = [
        const Atributo(id: 'b1', nombre: 'fechaNacimiento', tipo: 'LocalDate'),
      ];
      final puesto = ubicarDictado(
        frase: 'Fecha Nacimiento 16 de julio de 2004',
        campos: campos,
        valores: const {'fechaNacimiento': ''},
      );

      expect(puesto.campos.single.campo, 'fechaNacimiento');
      expect(puesto.campos.single.valor, '2004-07-16');
    });

    test('la frase corrida que el telefono metio entera en ci llena los tres campos', () {
      // El registro basura exacto: `ci = "77 8954 nombre Santiago nacimiento 16
      // de julio del 2004"`. Toda la frase en un campo, los otros dos vacios,
      // y el backend contestando 500 para siempre.
      final puesto = dictar('77 8954 nombre Santiago nacimiento 16 de julio del 2004');

      expect(puesto.campos.map((c) => c.campo).toList(), ['ci', 'nombre', 'nacimiento']);
      expect(puesto.campos.map((c) => c.valor).toList(),
          ['77 8954', 'Santiago', '2004-07-16']);
    });

    test('sin ningun hueco libre y sin campo nombrado no se pisa nada', () {
      final puesto = dictar(
        'Pedro',
        const {'ci': '1', 'nombre': 'Juan', 'nacimiento': '2000-01-01'},
      );

      expect(puesto.campos, isEmpty);
      expect(puesto.vacio, isTrue);
    });

    test('nombrar un campo sin decir el valor no llena nada', () {
      final puesto = dictar('nacimiento');

      expect(puesto.campos, isEmpty);
    });

    test('una frase vacia no hace nada', () {
      expect(dictar('   ').vacio, isTrue);
    });

    test('sin campos editables no hay donde poner nada', () {
      final puesto = ubicarDictado(frase: 'Juan', campos: const [], valores: const {});

      expect(puesto.vacio, isTrue);
    });
  });

  group('la orden de alta', () {
    test('«agregar» crea, no llena', () {
      final puesto = dictar('agregar');

      expect(puesto.esAlta, isTrue);
      expect(puesto.campos, isEmpty);
    });

    test('el imperativo voseante y «guardar» valen igual', () {
      // El reconocedor de Android escribe «agregá» con tilde tanto como sin
      // ella: las dos formas tienen que disparar lo mismo.
      for (final orden in ['agrega', 'agregá', 'Agregar', 'guardar', 'guardá', 'listo']) {
        expect(esOrdenDeAlta(orden), isTrue, reason: orden);
      }
    });

    test('un verbo con algo detras es un valor, no una orden', () {
      // «agregar» solo crea; «agregar sal» es texto que alguien esta dictando.
      expect(esOrdenDeAlta('agregar sal'), isFalse);
      expect(dictar('agregar sal').campos.single.campo, 'ci');
    });
  });

  group('fechas en castellano', () {
    String fecha(String dicho) => valorParaCampo(dicho, ClaseDeCampo.fecha);

    test('«23 de marzo del 2000»', () => expect(fecha('23 de marzo del 2000'), '2000-03-23'));
    test('«16 de julio de 2004»', () => expect(fecha('16 de julio de 2004'), '2004-07-16'));
    test('«del» y «de» valen igual', () {
      expect(fecha('1 de enero del 2020'), fecha('1 de enero de 2020'));
    });

    test('los doce meses', () {
      const meses = {
        'enero': '01', 'febrero': '02', 'marzo': '03', 'abril': '04',
        'mayo': '05', 'junio': '06', 'julio': '07', 'agosto': '08',
        'septiembre': '09', 'octubre': '10', 'noviembre': '11', 'diciembre': '12',
      };
      meses.forEach((mes, numero) {
        expect(fecha('5 de $mes de 1999'), '1999-$numero-05', reason: mes);
      });
      // Como se dice y se escribe de este lado del continente.
      expect(fecha('5 de setiembre de 1999'), '1999-09-05');
    });

    test('lo que ya viene en ISO pasa tal cual', () {
      expect(fecha('2000-03-23'), '2000-03-23');
    });

    test('escrito con barras, dia primero', () {
      expect(fecha('23/03/2000'), '2000-03-23');
    });

    test('el anio dictado en palabras', () {
      expect(fecha('16 de julio de dos mil cuatro'), '2004-07-16');
    });

    test('el dia dictado en palabras', () {
      expect(fecha('primero de enero de 2020'), '2020-01-01');
      expect(fecha('veinticinco de diciembre de 2021'), '2021-12-25');
    });

    test('el relleno de una fecha hablada no estorba', () {
      expect(fecha('el dia 3 de mayo del ano 2010'), '2010-05-03');
    });

    test('sin anio no se inventa uno: queda el texto dictado', () {
      // Poner el anio de hoy seria un dato plausible y falso en la base, que
      // es exactamente lo que no puede pasar.
      expect(fecha('23 de marzo'), '23 de marzo');
    });

    test('un dia que no existe queda como texto, no se acomoda solo', () {
      // DateTime(2001, 2, 30) contesta el 2 de marzo sin chistar. Guardar eso
      // seria guardar un dia que nadie dicto.
      expect(fecha('30 de febrero de 2001'), '30 de febrero de 2001');
    });

    test('lo que no es una fecha se deja como texto', () {
      expect(fecha('Juan'), 'Juan');
      expect(fecha('no me acuerdo'), 'no me acuerdo');
    });

    test('un campo de fecha y hora lleva la hora en cero', () {
      expect(valorParaCampo('16 de julio de 2004', ClaseDeCampo.fechaHora),
          '2004-07-16T00:00:00');
      expect(valorParaCampo('2004-07-16T09:30:00', ClaseDeCampo.fechaHora),
          '2004-07-16T09:30:00');
    });
  });

  group('numeros y logicos', () {
    test('un entero dictado en grupos se pega', () {
      // El reconocedor corta los numeros largos: un carnet vuelve con espacios.
      expect(valorParaCampo('77 8954', ClaseDeCampo.entero), '778954');
      expect(valorParaCampo('1.234', ClaseDeCampo.entero), '1234');
    });

    test('un entero dictado en palabras', () {
      expect(valorParaCampo('treinta y cinco', ClaseDeCampo.entero), '35');
      expect(valorParaCampo('dos mil cuatro', ClaseDeCampo.entero), '2004');
      expect(valorParaCampo('ciento veinte', ClaseDeCampo.entero), '120');
    });

    test('un texto en un campo entero no se recorta: queda el texto', () {
      // Sacarle los digitos de adentro dejaria «calle 5» valiendo 5.
      expect(valorParaCampo('Juan', ClaseDeCampo.entero), 'Juan');
      expect(valorParaCampo('calle 5', ClaseDeCampo.entero), 'calle 5');
    });

    test('la coma decimal se vuelve punto, que es lo unico que el backend parsea', () {
      expect(valorParaCampo('12,5', ClaseDeCampo.decimal), '12.5');
      expect(valorParaCampo('12.5', ClaseDeCampo.decimal), '12.5');
      expect(valorParaCampo('1.234,56', ClaseDeCampo.decimal), '1234.56');
    });

    test('un decimal dictado en palabras', () {
      expect(valorParaCampo('doce coma cinco', ClaseDeCampo.decimal), '12.5');
      expect(valorParaCampo('doce coma cero cinco', ClaseDeCampo.decimal), '12.05');
      expect(valorParaCampo('12 coma 5', ClaseDeCampo.decimal), '12.5');
    });

    test('un entero tambien sirve de decimal', () {
      expect(valorParaCampo('7', ClaseDeCampo.decimal), '7');
    });

    test('si y no en un campo logico', () {
      expect(valorParaCampo('sí', ClaseDeCampo.booleano), 'true');
      expect(valorParaCampo('si', ClaseDeCampo.booleano), 'true');
      expect(valorParaCampo('No', ClaseDeCampo.booleano), 'false');
      expect(valorParaCampo('verdadero', ClaseDeCampo.booleano), 'true');
      expect(valorParaCampo('falso', ClaseDeCampo.booleano), 'false');
      expect(valorParaCampo('mas o menos', ClaseDeCampo.booleano), 'mas o menos');
    });

    test('el texto se deja como se dicto', () {
      expect(valorParaCampo('  Juan Perez  ', ClaseDeCampo.texto), 'Juan Perez');
      expect(valorParaCampo('16 de julio', ClaseDeCampo.texto), '16 de julio');
    });

    test('enteroDePalabras no convierte lo que no es numero', () {
      // Sin esto «Juan» valdria cero, que es la clase de dato falso que
      // despues nadie encuentra.
      expect(enteroDePalabras('Juan'), isNull);
      expect(enteroDePalabras(''), isNull);
      expect(enteroDePalabras('doce Juan'), isNull);
    });
  });

  test('el valor dictado se convierte segun el tipo que el campo tiene en el diagrama',
      () {
    // La prueba de que las dos mitades estan enchufadas: el mismo texto cae en
    // un campo de fecha y sale en ISO, y en uno de texto sale tal cual.
    final comoFecha = ubicarDictado(
      frase: '16 de julio del 2004',
      campos: const [Atributo(id: 'x', nombre: 'nacimiento', tipo: 'LocalDate')],
      valores: const {'nacimiento': ''},
    );
    final comoTexto = ubicarDictado(
      frase: '16 de julio del 2004',
      campos: const [Atributo(id: 'x', nombre: 'nacimiento', tipo: 'String')],
      valores: const {'nacimiento': ''},
    );

    expect(comoFecha.campos.single.valor, '2004-07-16');
    expect(comoTexto.campos.single.valor, '16 de julio del 2004');
  });
}
