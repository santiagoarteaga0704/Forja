import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/voz_registros.dart';
import 'package:forja_movil/tipos.dart';

Diagrama _clinica() => Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente', atributos: const [
        Atributo(id: 'a1', nombre: 'ci', tipo: 'texto', esIdentificador: true),
        Atributo(id: 'a2', nombre: 'nombre', tipo: 'texto'),
      ]),
      Clase(id: 'c2', nombre: 'Médico', atributos: const [
        Atributo(id: 'a3', nombre: 'nombre', tipo: 'texto'),
      ]),
      Clase(id: 'c3', nombre: 'Consulta', atributos: const [
        Atributo(id: 'a4', nombre: 'motivo', tipo: 'texto'),
        Atributo(id: 'a5', nombre: 'fecha', tipo: 'fecha'),
      ]),
    ]);

void main() {
  test('«llamado» cae en el primer campo de texto que no sea el identificador', () {
    final pedido = interpretarPedidoDeRegistro('agregá un paciente llamado Juan', _clinica());

    expect(pedido!.clase, 'Paciente');
    // Ni 'ci', que es la clave: pedirla por voz no tendria sentido.
    expect(pedido.datos, {'nombre': 'Juan'});
  });

  test('el reconocedor escribe sin acento y la entidad se encuentra igual', () {
    final pedido = interpretarPedidoDeRegistro('nuevo medico llamado Ana', _clinica());

    expect(pedido!.clase, 'Médico');
  });

  test('«con <campo> <valor>» nombra el campo explicitamente', () {
    final pedido =
        interpretarPedidoDeRegistro('agregá una consulta con motivo control', _clinica());

    expect(pedido!.clase, 'Consulta');
    expect(pedido.datos, {'motivo': 'control'});
  });

  test('una entidad que no esta en el diagrama no se inventa', () {
    expect(interpretarPedidoDeRegistro('agregá una factura llamada 001', _clinica()), isNull);
  });

  test('un campo que la clase no tiene no se inventa', () {
    expect(
        interpretarPedidoDeRegistro('agregá un paciente con domicilio Sucre', _clinica()), isNull);
  });

  test('una frase que no pide un alta no devuelve nada', () {
    expect(interpretarPedidoDeRegistro('hola que tal', _clinica()), isNull);
  });
}
