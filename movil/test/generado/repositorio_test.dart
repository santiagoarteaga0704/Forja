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

    final vaciadas = await repo.sincronizar();

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

    final vaciadas = await repo.sincronizar();

    expect(vaciadas, 0);
    expect((await almacen.leerCola()).length, 1);
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

    final vaciadas = await repoConectado.sincronizar();

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

    final vaciadas = await repoConectado.sincronizar();

    expect(primeraLlamada, isFalse);
    expect(vaciadas, 1);
    expect(await almacen.leerCola(), isEmpty);
    final fila = (await repoConectado.filas('Paciente')).single;
    expect(fila['_pendiente'], isNotNull);
  });
}
