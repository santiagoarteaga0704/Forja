import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:forja_movil/generado/almacen_registros.dart';
import 'package:forja_movil/generado/api_generada.dart';
import 'package:forja_movil/generado/repositorio.dart';
import 'package:forja_movil/pantallas/entidades.dart';
import 'package:forja_movil/tipos.dart';

/// `testWidgets` corre adentro de una zona de tiempo falso: los eventos que
/// completan una lectura o una escritura de `dart:io` llegan por el bucle de
/// eventos real, y ahi adentro nunca se entregan. Esperar una de esas
/// operaciones sin `runAsync` no falla: cuelga la prueba para siempre. Por eso
/// todo lo que toca archivos en este archivo pasa por `tester.runAsync`.
///
/// La otra mitad del problema es que la pantalla no le devuelve a nadie los
/// futuros que dispara en `initState` -leer la direccion guardada, leer la
/// cola-, asi que no hay nada concreto que esperar. Se espera entonces por el
/// resultado: se le dan vueltas al bucle real hasta que aparece lo que tiene
/// que aparecer. Un `Future.delayed` fijo tambien alcanza casi siempre, y
/// "casi siempre" en una prueba es una prueba intermitente.
Future<void> _abrir(WidgetTester tester, Widget pantalla) async {
  await tester.runAsync(() async {
    await tester.pumpWidget(MaterialApp(home: pantalla));
    await Future<void>.delayed(const Duration(milliseconds: 50));
  });
  await tester.pump();
}

/// Da vueltas al bucle de eventos real hasta que el buscado aparece, o hasta
/// que se acaba el limite. Si se acaba, la prueba sigue y el `expect` de
/// afuera es el que informa el fallo.
///
/// El limite es de quince segundos y no cuesta nada, porque el bucle sale
/// apenas aparece lo buscado: no son quince segundos de prueba, son quince
/// segundos de paciencia para una maquina cargada. Pero que quede claro lo que
/// este numero NO es: no es el arreglo de nada. Ver `_esperarDireccion`.
Future<void> _esperar(
  WidgetTester tester,
  Finder buscado, {
  Duration limite = const Duration(seconds: 15),
}) async {
  final fin = DateTime.now().add(limite);
  while (DateTime.now().isBefore(fin)) {
    await tester.pump();
    if (buscado.evaluate().isNotEmpty) return;
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
  }
  await tester.pump();
}

/// Lo mismo, pero mirando el archivo en vez de la pantalla: la escritura que
/// dispara `onChanged` tampoco vuelve a manos de la prueba.
///
/// AVISO, y es lo importante de este archivo: «tipear rapido deja guardada la
/// direccion completa, no un prefijo» **parpadea en Windows**, y subir este
/// limite NO lo arregla. Lo unico que consigue es que cada fallo tarde quince
/// segundos en vez de cinco. Cuando falla, el bucle consume el limite entero y
/// lo guardado se queda fijo en un prefijo -«...:808» sin el ultimo
/// caracter- para siempre; no es lentitud, es que el valor correcto no va a
/// llegar nunca.
///
/// La causa esta en `almacen_registros.dart`: `_escribirArchivo` escribe en un
/// `.tmp` y hace `temporal.rename(destino)`, y en **Windows** ese rename falla
/// si alguien tiene el destino abierto para leer. Este poller lee cada 20 ms
/// mientras todavia quedan escrituras encadenadas, asi que la ultima revienta;
/// `_guardarDireccion` se traga el error en un SnackBar y el prefijo queda.
///
/// Se midio aislado -sin widgets, contra el codigo de `ef268b1`-: 40 intentos
/// de escribir la direccion letra por letra con un lector concurrente dejaron
/// **26 en un prefijo** y **466 escrituras con error**.
///
/// Dos cosas que hacen que esto sea para saber y no para arreglar de apuro:
/// es **preexistente** -no lo introdujo el trabajo del modelo en el aparato- y
/// **no afecta al producto**, porque en Android el `rename` de POSIX renombra
/// sobre un archivo abierto sin chistar. Es un defecto del anfitrion Windows.
/// Se decidio convivir con el: volver a meter mano en la primitiva de
/// escritura -que ya llevaba dos rondas de defectos- la noche antes de una
/// defensa tiene peor pronostico que una prueba que parpadea en el escritorio.
Future<String?> _esperarDireccion(
  WidgetTester tester,
  AlmacenRegistros almacen,
  String esperada, {
  Duration limite = const Duration(seconds: 15),
}) async {
  String? leida;
  final fin = DateTime.now().add(limite);
  while (DateTime.now().isBefore(fin)) {
    leida = await tester.runAsync<String?>(() => almacen.leerDireccionBackend());
    if (leida == esperada) return leida;
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
  }
  return leida;
}

void main() {
  late Directory carpeta;

  setUp(() => carpeta = Directory.systemTemp.createTempSync('forja-pantalla'));
  tearDown(() => carpeta.deleteSync(recursive: true));

  Diagrama unaClase() => Diagrama(
        id: 'd1',
        nombre: 'Clinica',
        version: 1,
        clases: [Clase(id: 'c1', nombre: 'Paciente')],
      );

  testWidgets('lista una entrada por clase del diagrama', (tester) async {
    final diagrama = Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente'),
      Clase(id: 'c2', nombre: 'Consulta'),
    ]);

    await _abrir(
      tester,
      PantallaEntidades(
        diagrama: diagrama,
        repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
      ),
    );

    expect(find.text('Paciente'), findsOneWidget);
    expect(find.text('Consulta'), findsOneWidget);
    // La ruta se muestra: es lo que hace evidente que esto habla con el
    // backend generado y no con FORJA.
    expect(find.text('/api/pacientes'), findsOneWidget);
  });

  testWidgets('la pantalla abre sin direccion guardada y sin backend', (tester) async {
    // El requisito central del proyecto: ninguna pantalla puede necesitar red
    // para abrirse. Aca el repositorio va sin `api`, o sea que no hay backend
    // al que llamar ni por error.
    await _abrir(
      tester,
      PantallaEntidades(
        diagrama: unaClase(),
        repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
      ),
    );

    expect(find.text('Paciente'), findsOneWidget);
    expect(find.text('Sin operaciones pendientes'), findsOneWidget);
  });

  testWidgets('el contador refleja lo que hay en la cola', (tester) async {
    final almacen = AlmacenRegistros(carpeta);
    // Dos operaciones ya encoladas antes de abrir: es lo que queda despues de
    // cargar datos sin senal.
    await tester.runAsync(() async {
      await almacen.encolar(const OperacionPendiente(
          id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'}));
      await almacen.encolar(const OperacionPendiente(
          id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'}));
    });

    await _abrir(
      tester,
      PantallaEntidades(diagrama: unaClase(), repositorio: Repositorio(almacen: almacen)),
    );
    await _esperar(tester, find.text('2 operacion(es) esperando'));

    expect(find.text('2 operacion(es) esperando'), findsOneWidget);
  });

  testWidgets('la direccion escrita se guarda y aparece sola al reabrir', (tester) async {
    final almacen = AlmacenRegistros(carpeta);

    await _abrir(
      tester,
      PantallaEntidades(diagrama: unaClase(), repositorio: Repositorio(almacen: almacen)),
    );

    await tester.runAsync(() => tester.enterText(
          find.byType(TextField),
          'http://192.168.43.7:8080',
        ));
    expect(
      await _esperarDireccion(tester, almacen, 'http://192.168.43.7:8080'),
      'http://192.168.43.7:8080',
    );

    // Se abre de nuevo -como al volver a entrar- con un repositorio fresco
    // sobre el mismo almacen: la direccion tiene que aparecer sola, sin que
    // haya que escribirla otra vez.
    await _abrir(
      tester,
      PantallaEntidades(diagrama: unaClase(), repositorio: Repositorio(almacen: almacen)),
    );
    await _esperar(tester, find.text('http://192.168.43.7:8080'));

    expect(find.text('http://192.168.43.7:8080'), findsOneWidget);
  });

  testWidgets('tipear rapido deja guardada la direccion completa, no un prefijo',
      (tester) async {
    // `onChanged` guarda en cada tecla sin esperar al guardado anterior. Si las
    // escrituras no pasaran todas por una sola cadena, esto reventaria al
    // renombrar el temporal -o peor: terminaria guardando un prefijo, porque el
    // orden en que terminan los renombres no seria el orden en que se pidieron.
    const direccion = 'http://192.168.43.15:8080';
    final almacen = AlmacenRegistros(carpeta);

    await _abrir(
      tester,
      PantallaEntidades(diagrama: unaClase(), repositorio: Repositorio(almacen: almacen)),
    );

    await tester.runAsync(() async {
      for (var i = 1; i <= direccion.length; i++) {
        await tester.enterText(find.byType(TextField), direccion.substring(0, i));
      }
    });

    expect(await _esperarDireccion(tester, almacen, direccion), direccion);
  });

  testWidgets('sincronizar sin direccion cargada lo dice en vez de callarse', (tester) async {
    final almacen = AlmacenRegistros(carpeta);
    await tester.runAsync(() => almacen.encolar(const OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'})));

    await _abrir(
      tester,
      PantallaEntidades(diagrama: unaClase(), repositorio: Repositorio(almacen: almacen)),
    );
    await _esperar(tester, find.text('1 operacion(es) esperando'));

    await tester.tap(find.text('Sincronizar'));
    await tester.pump();

    expect(find.textContaining('Carga la direccion del backend generado'), findsOneWidget);
    // Y la cola sigue intacta: tocar el boton no perdio nada.
    expect(find.text('1 operacion(es) esperando'), findsOneWidget);
  });

  testWidgets('si no se llega al backend la pantalla lo dice', (tester) async {
    // `Repositorio.sincronizar()` no lanza cuando el backend no contesta:
    // devuelve 0. Sin aviso, ese 0 se lee como "no habia nada que hacer" y el
    // usuario se queda esperando un envio que nunca paso.
    final almacen = AlmacenRegistros(carpeta);
    await tester.runAsync(() => almacen.encolar(const OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'})));

    await _abrir(
      tester,
      PantallaEntidades(
        diagrama: unaClase(),
        repositorio: Repositorio(
          almacen: almacen,
          api: ApiGenerada(
            base: 'http://192.168.43.1:8080',
            cliente: MockClient((_) async => throw const SocketException('sin ruta')),
          ),
        ),
      ),
    );
    await _esperar(tester, find.text('1 operacion(es) esperando'));

    await tester.tap(find.text('Sincronizar'));
    await _esperar(tester, find.textContaining('No se pudo llegar al backend generado'));

    expect(find.textContaining('No se pudo llegar al backend generado'), findsOneWidget);
    // La operacion no se perdio: sigue encolada esperando otra oportunidad.
    expect(find.text('1 operacion(es) esperando'), findsOneWidget);
  });

  testWidgets('al volver de cargar registros el contador ya dice la verdad',
      (tester) async {
    // El guion de la demostracion: modo avion, entrar a una entidad, cargar dos
    // registros, volver. El contador solo se leia en initState y despues de
    // sincronizar, asi que al volver decia «Sin operaciones pendientes» con dos
    // operaciones en la cola: la pantalla desmentia justo el paso que hay que
    // demostrar, y se corregia recien al tocar Sincronizar, que es el siguiente.
    final almacen = AlmacenRegistros(carpeta);
    // Sin `api`: esto es el modo avion. Cargar encola y nada sale del aparato.
    final repositorio = Repositorio(almacen: almacen);

    await _abrir(
      tester,
      PantallaEntidades(
        diagrama: Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
          Clase(id: 'c1', nombre: 'Paciente', atributos: const [
            Atributo(id: 'a1', nombre: 'nombre', tipo: 'String'),
          ]),
        ]),
        repositorio: repositorio,
      ),
    );
    expect(find.text('Sin operaciones pendientes'), findsOneWidget);

    // Nada de `pumpAndSettle` aca: la pantalla de registros deja futuros de
    // archivo pedidos desde el reloj falso, que ahi adentro no se completan
    // nunca, asi que esperar a que "todo se aquiete" no termina jamas. Lo que
    // hay que dejar correr es la transicion de ruta, y eso dura un segundo.
    await tester.tap(find.text('Paciente'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Agregar'), findsOneWidget);

    // Dos registros cargados sin senal, escritos derecho en el archivo de la
    // cola. Seria mas fiel llamar a `repositorio.crear`, que es lo que hace el
    // boton «Agregar», pero con la pantalla de registros montada `runAsync` se
    // cuelga: la primera operacion de archivo pasa y la segunda no vuelve mas.
    // La cola es un archivo JSON, asi que escribirla es igual de real y no
    // depende de que el bucle de eventos coopere.
    File('${carpeta.path}${Platform.pathSeparator}cola-registros.json')
        .writeAsStringSync(jsonEncode([
      const OperacionPendiente(
              id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'})
          .aJson(),
      const OperacionPendiente(
              id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'})
          .aJson(),
    ]));

    await tester.pageBack();
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    await _esperar(tester, find.text('2 operacion(es) esperando'));

    expect(find.text('2 operacion(es) esperando'), findsOneWidget);
    expect(find.text('Sin operaciones pendientes'), findsNothing);
  });

  testWidgets('un rechazo del backend se cuenta como rechazo, no como falta de red',
      (tester) async {
    // El otro lado del mismo problema: `Repositorio.sincronizar()` tenia un
    // `catch` pelado, asi que un 400 se anunciaba como "No se pudo llegar al
    // backend generado" y la operacion quedaba trabando la cola. Ahora se
    // descarta y se nombra: lo que se perdio tiene que decirse, porque es
    // trabajo que alguien cargo y que no va a entrar nunca.
    final almacen = AlmacenRegistros(carpeta);
    await tester.runAsync(() => almacen.encolar(const OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'})));

    await _abrir(
      tester,
      PantallaEntidades(
        diagrama: unaClase(),
        repositorio: Repositorio(
          almacen: almacen,
          api: ApiGenerada(
            base: 'http://192.168.43.1:8080',
            cliente: MockClient((peticion) async => peticion.method == 'GET'
                ? http.Response('[]', 200,
                    headers: {'content-type': 'application/json; charset=utf-8'})
                : http.Response('falta la fecha de nacimiento', 400)),
          ),
        ),
      ),
    );
    await _esperar(tester, find.text('1 operacion(es) esperando'));

    await tester.tap(find.text('Sincronizar'));
    await _esperar(tester, find.textContaining('rechazo y se descarto'));

    // Se nombra lo descartado: quien lo cargo tiene que saber cual volver a
    // cargar a mano.
    expect(find.textContaining('Ana'), findsOneWidget);
    // Y no se le echa la culpa a la red, que anduvo perfecto.
    expect(find.textContaining('No se pudo llegar'), findsNothing);
    // La cola quedo libre en vez de muerta detras de algo imposible.
    await _esperar(tester, find.text('Sin operaciones pendientes'));
    expect(find.text('Sin operaciones pendientes'), findsOneWidget);
  });
}
