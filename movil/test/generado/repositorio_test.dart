import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';
import 'package:forja_movil/generado/api_generada.dart';
import 'package:forja_movil/generado/repositorio.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  late Directory carpeta;
  late AlmacenRegistros almacen;

  setUp(() {
    carpeta = Directory.systemTemp.createTempSync('forja-repo');
    almacen = AlmacenRegistros(carpeta);
  });

  tearDown(() => carpeta.deleteSync(recursive: true));

  test('sin backend, crear deja la fila visible igual', () async {
    // Esto es el requisito entero: sin red la app no se degrada a solo lectura.
    final repo = Repositorio(almacen: almacen, api: null);

    await repo.crear('Paciente', {'nombre': 'Ana'});

    final filas = await repo.filas('Paciente');
    expect(filas.single['nombre'], 'Ana');
    expect((await almacen.leerCola()).single.verbo, 'crear');
  });

  test('sincronizar vacia la cola contra el backend', () async {
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((_) async => http.Response('{"id":1}', 201,
            headers: {'content-type': 'application/json; charset=utf-8'})),
      ),
    );
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final vaciadas = (await repo.sincronizar()).vaciadas;

    expect(vaciadas, 1);
    expect(await almacen.leerCola(), isEmpty);
  });

  test('si el backend no contesta, la cola queda intacta', () async {
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((_) async => throw const SocketException('sin ruta')),
      ),
    );
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final resultado = await repo.sincronizar();

    expect(resultado.vaciadas, 0);
    expect((await almacen.leerCola()).length, 1);
    // No contesto: no hay nada que descartar, y el corte dice que fue la red.
    expect(resultado.rechazadas, isEmpty);
    expect(resultado.corte, contains('No se pudo llegar'));
  });

  test('al sincronizar con exito, la marca _pendiente desaparece de la fila', () async {
    // Este es el defecto que el brief dejaba sin cerrar: crear() marca la fila
    // y sincronizar() vacia la cola, pero nadie volvia a pedir esa fila para
    // sacarle la marca. Se crea sin backend (queda marcada) y despues se
    // sincroniza con uno que responde: la fila local tiene que quedar limpia.
    final repo = Repositorio(almacen: almacen, api: null);
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final filaAntes = (await repo.filas('Paciente')).single;
    expect(filaAntes['_pendiente'], isNotNull);

    final repoConectado = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((peticion) async {
          if (peticion.method == 'GET') {
            return http.Response('[{"id":"1","nombre":"Ana"}]', 200,
                headers: {'content-type': 'application/json; charset=utf-8'});
          }
          return http.Response('{"id":"1","nombre":"Ana"}', 201,
              headers: {'content-type': 'application/json; charset=utf-8'});
        }),
      ),
    );

    final vaciadas = (await repoConectado.sincronizar()).vaciadas;

    expect(vaciadas, 1);
    final filaDespues = (await repoConectado.filas('Paciente')).single;
    expect(filaDespues.containsKey('_pendiente'), isFalse);
    expect(filaDespues['id'], '1');
  });

  test('si el pedido de filas tras sincronizar falla, la marca no se toca', () async {
    // Una via y un respaldo que no hace nada: si listar() falla despues de
    // vaciar la cola, la fila local se queda como estaba -marcada- para que la
    // proxima sincronizacion la arregle. No hay que perder la marca por error.
    final repo = Repositorio(almacen: almacen, api: null);
    await repo.crear('Paciente', {'nombre': 'Ana'});

    var primeraLlamada = true;
    final repoConectado = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((peticion) async {
          if (peticion.method == 'GET') {
            throw const SocketException('sin ruta');
          }
          primeraLlamada = false;
          return http.Response('{"id":"1"}', 201,
              headers: {'content-type': 'application/json; charset=utf-8'});
        }),
      ),
    );

    final vaciadas = (await repoConectado.sincronizar()).vaciadas;

    expect(primeraLlamada, isFalse);
    expect(vaciadas, 1);
    expect(await almacen.leerCola(), isEmpty);
    final fila = (await repoConectado.filas('Paciente')).single;
    expect(fila['_pendiente'], isNotNull);
  });

  test('si la segunda de dos operaciones de la misma clase falla, la primera '
      'no dispara un refetch que la borre de filas', () async {
    // Este es el defecto critico que encontro la revision: agrupar por clase
    // apenas UNA operacion tiene exito -en vez de esperar a que no quede
    // ninguna de esa clase en la cola- hacia que un refetch pisara la copia
    // local con una version del servidor que todavia no tenia a la que fallo.
    final metodosVistos = <String>[];
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((peticion) async {
          metodosVistos.add(peticion.method);
          if (peticion.method == 'GET') {
            return http.Response('[]', 200,
                headers: {'content-type': 'application/json; charset=utf-8'});
          }
          final cuerpo = jsonDecode(peticion.body) as Map;
          if (cuerpo['nombre'] == 'Beto') {
            return http.Response('error del servidor', 500);
          }
          return http.Response('{"id":"1"}', 201,
              headers: {'content-type': 'application/json; charset=utf-8'});
        }),
      ),
    );

    await repo.crear('Paciente', {'nombre': 'Ana'});
    await repo.crear('Paciente', {'nombre': 'Beto'});

    final vaciadas = (await repo.sincronizar()).vaciadas;

    expect(vaciadas, 1);
    final cola = await almacen.leerCola();
    expect(cola.single.datos['nombre'], 'Beto');

    final filas = await repo.filas('Paciente');
    expect(filas.map((f) => f['nombre']), containsAll(['Ana', 'Beto']));
    expect(metodosVistos, isNot(contains('GET')));
  });

  test('una operacion que el backend rechaza se descarta, se nombra, y las de '
      'atras siguen entrando', () async {
    // El defecto: un `catch` pelado trataba igual a un SocketException y a un
    // 400. Con un rechazo en la cabeza, todo lo que estaba detras quedaba
    // muerto para siempre y el aviso acusaba a la red. El criterio es el que
    // ya usa Sincronizador._vaciarCola para la otra cola de la app: definitivo
    // se descarta y se informa, y la pasada sigue.
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((peticion) async {
          if (peticion.method == 'GET') {
            return http.Response('[{"id":"1","nombre":"Ana"}]', 200,
                headers: {'content-type': 'application/json; charset=utf-8'});
          }
          final cuerpo = jsonDecode(peticion.body) as Map;
          if (cuerpo['nombre'] == 'Beto') {
            return http.Response('falta la fecha de nacimiento', 400);
          }
          return http.Response('{"id":"1"}', 201,
              headers: {'content-type': 'application/json; charset=utf-8'});
        }),
      ),
    );

    await repo.crear('Paciente', {'nombre': 'Ana'});
    await repo.crear('Paciente', {'nombre': 'Beto'});
    await repo.crear('Paciente', {'nombre': 'Caro'});

    final resultado = await repo.sincronizar();

    // Caro entro: no quedo trabada detras de Beto.
    expect(resultado.vaciadas, 2);
    expect(await almacen.leerCola(), isEmpty);
    // Y se dice cual se perdio, con nombre y con el motivo del servidor.
    expect(resultado.rechazadas.single, contains('Beto'));
    expect(resultado.rechazadas.single, contains('400'));
    expect(resultado.rechazadas.single, contains('falta la fecha de nacimiento'));
    // Lo que no se puede decir es que fue la red: el servidor contesto.
    expect(resultado.corte, isNull);
  });

  test('descartar deja de marcar como pendiente una fila que no va a entrar', () async {
    // Si la operacion se va de la cola y la fila local se queda con
    // '_pendiente', la pantalla muestra para siempre una fila esperando un
    // envio que ya no existe. Con la clase sin pendientes se vuelve a pedir la
    // lista al backend, que es la unica version verdadera.
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((peticion) async {
          if (peticion.method == 'GET') {
            return http.Response('[]', 200,
                headers: {'content-type': 'application/json; charset=utf-8'});
          }
          return http.Response('nombre repetido', 409);
        }),
      ),
    );

    await repo.crear('Paciente', {'nombre': 'Ana'});
    expect((await repo.filas('Paciente')).single['_pendiente'], isNotNull);

    final resultado = await repo.sincronizar();

    expect(resultado.vaciadas, 0);
    expect(resultado.rechazadas, hasLength(1));
    expect(await almacen.leerCola(), isEmpty);
    expect(await repo.filas('Paciente'), isEmpty);
  });

  test('un 500 conserva la operacion y corta, pero sin acusar a la red', () async {
    // 500, 401 y 429 son "todavia no", no "nunca": el servidor puede cambiar de
    // opinion, asi que la operacion se conserva. Pero se llego al backend, y
    // decir que no se llego seria la misma mentira que se vino a corregir.
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((_) async => http.Response('se cayo la base', 500)),
      ),
    );
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final resultado = await repo.sincronizar();

    expect(resultado.vaciadas, 0);
    expect(resultado.rechazadas, isEmpty);
    expect((await almacen.leerCola()).length, 1);
    expect(resultado.corte, contains('500'));
    expect(resultado.corte, isNot(contains('No se pudo llegar')));
  });
}
