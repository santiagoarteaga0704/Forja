import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/asistente.dart';
import 'package:forja_movil/generado/dictado_de_registros.dart';
import 'package:forja_movil/tipos.dart';

Diagrama _clinica() => Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente', atributos: const [
        Atributo(id: 'a1', nombre: 'id', tipo: 'Long', esIdentificador: true),
        Atributo(id: 'a2', nombre: 'nombre', tipo: 'String'),
      ]),
    ]);

void main() {
  test('lo que la gramatica entiende no llega al modelo', () async {
    var consultado = false;
    final asistente = Asistente(preguntarAlModelo: (_) async {
      consultado = true;
      return 'agrega un paciente llamado Otro';
    });

    final dictado = await resolverPedidoDictado(
      frase: 'agrega un paciente llamado Juan',
      diagrama: _clinica(),
      asistente: asistente,
      confirmar: (_, _) async => true,
    );

    expect(consultado, isFalse);
    expect(dictado.pedido?.datos['nombre'], 'Juan');
  });

  test('sin asistente, lo que no se entiende no se entiende y ya', () async {
    final dictado = await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      confirmar: (_, _) async => true,
    );

    expect(dictado.pedido, isNull);
    expect(dictado.cancelado, isFalse);
  });

  test('la propuesta del modelo vuelve a pasar por la gramatica', () async {
    final dictado = await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(
        preguntarAlModelo: (_) async => 'agrega un paciente llamado Juan',
      ),
      confirmar: (_, _) async => true,
    );

    expect(dictado.pedido?.clase, 'Paciente');
    expect(dictado.pedido?.datos, {'nombre': 'Juan'});
  });

  test('si el usuario no confirma, no se crea nada', () async {
    // El corazon de todo esto: el modelo propone y el usuario dispone. En la
    // version web un respaldo por IA aplicaba sin revision y creaba clases
    // fantasma en silencio.
    final dictado = await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(
        preguntarAlModelo: (_) async => 'agrega un paciente llamado Juan',
      ),
      confirmar: (_, _) async => false,
    );

    expect(dictado.pedido, isNull);
    expect(dictado.cancelado, isTrue);
  });

  test('una propuesta que la gramatica no entiende no se confirma siquiera', () async {
    // El modelo no puede inventar entidades: si lo que propuso no esta en el
    // diagrama, no hay nada que ofrecerle al usuario.
    var preguntado = false;
    final dictado = await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(preguntarAlModelo: (_) async => 'agrega un dinosaurio llamado Juan'),
      confirmar: (_, _) async {
        preguntado = true;
        return true;
      },
    );

    expect(preguntado, isFalse);
    expect(dictado.pedido, isNull);
    expect(dictado.cancelado, isFalse);
  });

  test('si el modelo se cae, el dictado termina sin entender y sin romperse', () async {
    final dictado = await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(preguntarAlModelo: (_) async => throw StateError('sin modelo')),
      confirmar: (_, _) async => true,
    );

    expect(dictado.pedido, isNull);
  });

  test('al confirmar se muestra la frase canonica, no la dictada', () async {
    // Lo que el usuario aprueba tiene que ser lo que se va a aplicar: mostrar
    // la frase original esconderia justo el paso que se le esta pidiendo
    // revisar.
    String? mostrada;
    await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(preguntarAlModelo: (_) async => 'agrega un paciente llamado Juan'),
      confirmar: (frase, _) async {
        mostrada = frase;
        return true;
      },
    );

    expect(mostrada, 'agrega un paciente llamado Juan');
  });

  test('las entidades del diagrama llegan al asistente', () async {
    // Es la mitad util del escalon: si el modelo no sabe que existe «Paciente»,
    // propone cualquier otra cosa y el parser la rechaza siempre.
    List<String>? recibidas;
    await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: _AsistenteEspia((entidades) => recibidas = entidades),
      confirmar: (_, _) async => true,
    );

    expect(recibidas, ['Paciente']);
  });

  test('avisa cuando empieza a pensar y cuando termina', () async {
    // El segundo escalon son varios segundos de pantalla quieta: sin este aviso
    // no hay con que dibujar la rueda.
    final avisos = <bool>[];
    await resolverPedidoDictado(
      frase: 'anota a Juan como paciente',
      diagrama: _clinica(),
      asistente: Asistente(preguntarAlModelo: (_) async => 'agrega un paciente llamado Juan'),
      alPensar: avisos.add,
      confirmar: (_, _) async => true,
    );

    expect(avisos, [true, false]);
  });

  test('lo que la gramatica entiende no enciende la rueda', () async {
    final avisos = <bool>[];
    await resolverPedidoDictado(
      frase: 'agrega un paciente llamado Juan',
      diagrama: _clinica(),
      asistente: Asistente(preguntarAlModelo: (_) async => 'lo que sea'),
      alPensar: avisos.add,
      confirmar: (_, _) async => true,
    );

    expect(avisos, isEmpty);
  });

  test('si el modelo explota, la rueda se apaga igual', () async {
    // El aviso va en un finally: un modelo que revienta no puede dejar el
    // microfono girando para siempre.
    final avisos = <bool>[];
    await expectLater(
      resolverPedidoDictado(
        frase: 'anota a Juan como paciente',
        diagrama: _clinica(),
        asistente: _AsistenteQueRevienta(),
        alPensar: avisos.add,
        confirmar: (_, _) async => true,
      ),
      // Se deja pasar a proposito: Asistente ya se traga todo lo que puede
      // pasar de verdad, asi que algo que llegue hasta aca es un error de
      // programacion y taparlo seria peor. Lo que no puede pasar es que la
      // rueda quede girando.
      throwsA(isA<StateError>()),
    );

    expect(avisos, [true, false]);
  });
}

/// Mira con que entidades se llamo al modelo. Es una subclase y no una funcion
/// porque lo que hay que espiar es el argumento de fraseCanonica, no el prompt.
class _AsistenteEspia extends Asistente {
  _AsistenteEspia(this.mirar) : super(preguntarAlModelo: _nunca);

  final void Function(List<String>) mirar;

  static Future<String> _nunca(String _) async => '';

  @override
  Future<String?> fraseCanonica(String pedido, {List<String> entidades = const []}) async {
    mirar(entidades);
    return 'agrega un paciente llamado Juan';
  }
}

/// Revienta en la cara del que llama, sin el try/catch de Asistente: es la
/// unica forma de comprobar que el aviso de «ya no estoy pensando» esta en un
/// finally y no despues del await.
class _AsistenteQueRevienta extends Asistente {
  _AsistenteQueRevienta() : super(preguntarAlModelo: _nunca);

  static Future<String> _nunca(String _) async => '';

  @override
  Future<String?> fraseCanonica(String pedido, {List<String> entidades = const []}) =>
      throw StateError('el modelo se cayo');
}
