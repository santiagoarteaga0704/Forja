import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';
import 'package:forja_movil/generado/asistente.dart';
import 'package:forja_movil/generado/repositorio.dart';
import 'package:forja_movil/generado/voz_registros.dart';
import 'package:forja_movil/pantallas/registros.dart';
import 'package:forja_movil/tipos.dart';

/// El cartel que frena al modelo.
///
/// Se prueba aparte del dictado entero porque el dictado necesita microfono y
/// esto no: lo que hay que asegurar aca es que lo que se ve sea lo que se va a
/// guardar, y que un «Cancelar» realmente cancele.
void main() {
  const pedido = PedidoDeRegistro(clase: 'Paciente', datos: {'nombre': 'Juan'});

  /// Deja el cartel abierto y devuelve la caja donde va a caer la respuesta:
  /// la respuesta recien existe despues de tocar un boton, asi que no se puede
  /// devolver el valor, hay que devolver donde mirarlo.
  Future<List<bool?>> abrir(WidgetTester tester) async {
    final respondido = <bool?>[];
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (contexto) => Scaffold(
          body: TextButton(
            onPressed: () async => respondido.add(await confirmarPedidoDictado(
              contexto,
              'agrega un paciente llamado Juan',
              pedido,
            )),
            child: const Text('abrir'),
          ),
        ),
      ),
    ));
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();
    return respondido;
  }

  testWidgets('muestra la frase y lo que se va a guardar', (tester) async {
    await abrir(tester);

    expect(find.text('"agrega un paciente llamado Juan"'), findsOneWidget);
    expect(find.text('Agregar a Paciente:'), findsOneWidget);
    expect(find.text('nombre: Juan'), findsOneWidget);
  });

  testWidgets('cancelar devuelve que no', (tester) async {
    final respondido = await abrir(tester);

    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();

    // La confirmacion es el unico freno entre lo que el modelo propuso y una
    // fila creada: si «Cancelar» devolviera true, el freno no existiria.
    expect(respondido, [false]);
  });

  testWidgets('agregar devuelve que si', (tester) async {
    final respondido = await abrir(tester);

    await tester.tap(find.text('Agregar'));
    await tester.pumpAndSettle();

    expect(respondido, [true]);
  });

  testWidgets('cerrar el cartel sin elegir tampoco confirma', (tester) async {
    // showDialog devuelve null si se toca afuera. Sin el `?? false`, ese null
    // se leeria como «no se sabe» y la pantalla tendria que adivinar.
    final respondido = await abrir(tester);

    Navigator.of(tester.element(find.byType(AlertDialog))).pop();
    await tester.pumpAndSettle();

    expect(respondido, [false]);
  });

  group('el modelo que llega tarde', () {
    late Directory carpeta;

    setUp(() => carpeta = Directory.systemTemp.createTempSync('forja-registros'));
    tearDown(() => carpeta.deleteSync(recursive: true));

    /// La pantalla lee filas del disco en initState, y `dart:io` adentro del
    /// tiempo falso de testWidgets no entrega nunca: por eso runAsync.
    Future<void> abrirRegistros(WidgetTester tester, ValueNotifier<Asistente?> asistente) async {
      final clase = Clase(id: 'c1', nombre: 'Paciente', atributos: const [
        Atributo(id: 'a1', nombre: 'nombre', tipo: 'String'),
      ]);
      await tester.runAsync(() async {
        await tester.pumpWidget(MaterialApp(
          home: PantallaRegistros(
            clase: clase,
            repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
            diagrama: Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [clase]),
            asistente: asistente,
          ),
        ));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump();
    }

    testWidgets('una pantalla ya abierta se entera de que el modelo cargo',
        (tester) async {
      // La regresion que esto cuida: MaterialPageRoute construye su pagina UNA
      // sola vez y la cachea. Pasando el valor en vez del listenable, un modelo
      // que termina de cargar -529 MB, siempre despues- no llegaba nunca a una
      // pantalla ya abierta, y no habia ninguna senal de eso: salia «No
      // entendi», igual que sin modelo.
      final asistente = ValueNotifier<Asistente?>(null);
      addTearDown(asistente.dispose);

      await abrirRegistros(tester, asistente);
      expect(find.byTooltip('Dictar un registro'), findsOneWidget);

      asistente.value = Asistente(preguntarAlModelo: (_) async => '');
      await tester.pump();

      expect(find.byTooltip('Dictar un registro (con ayuda del modelo)'), findsOneWidget);
    });

    testWidgets('sin listenable la pantalla abre igual', (tester) async {
      // El modelo es prescindible: pasar null no puede romper nada.
      final clase = Clase(id: 'c1', nombre: 'Paciente');
      await tester.runAsync(() async {
        await tester.pumpWidget(MaterialApp(
          home: PantallaRegistros(
            clase: clase,
            repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
            diagrama: Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [clase]),
          ),
        ));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump();

      expect(find.byTooltip('Dictar un registro'), findsOneWidget);
    });

    testWidgets('una clase sin atributos avisa y no ofrece Agregar', (tester) async {
      // La clase del diagrama no tiene atributos propios -pasa con Cliente o
      // Factura recien dibujadas-. Antes de esto solo quedaba un boton
      // «Agregar» que creaba filas vacias.
      final clase = Clase(id: 'c1', nombre: 'Factura');
      await tester.runAsync(() async {
        await tester.pumpWidget(MaterialApp(
          home: PantallaRegistros(
            clase: clase,
            repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
            diagrama: Diagrama(id: 'd1', nombre: 'Ventas', version: 1, clases: [clase]),
          ),
        ));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump();

      expect(find.text('Factura no tiene atributos en el diagrama.'), findsOneWidget);
      expect(find.text('Agregalos en FORJA y volve a bajar el diagrama.'), findsOneWidget);
      expect(find.text('Agregar'), findsNothing);
    });
  });

  /// De donde sale este grupo: en un telefono real quedaron encolados tres
  /// registros de Paciente incompletos -uno con solo `ci`, uno con la frase
  /// entera en `ci`, y uno vacio-. Las columnas del backend generado son
  /// `nullable = false`, asi que los tres daban 500; y como un 500 se trata
  /// como error temporal, la cola los reintentaba para siempre y no avanzaba
  /// nunca. La app quedaba trabada sin salida.
  group('la ficha de alta no deja crear registros incompletos', () {
    late Directory carpeta;

    setUp(() => carpeta = Directory.systemTemp.createTempSync('forja-alta'));
    tearDown(() => carpeta.deleteSync(recursive: true));

    File cola() => File('${carpeta.path}${Platform.pathSeparator}cola-registros.json');

    /// Da vueltas al bucle de eventos REAL hasta que el archivo aparece. Igual
    /// que en entidades_test.dart y por lo mismo: el `crear` sale de un
    /// manejador que corre en el tiempo falso de testWidgets, y ahi adentro un
    /// futuro de `dart:io` no se entrega solo.
    Future<void> esperarArchivo(WidgetTester tester, File archivo) async {
      final fin = DateTime.now().add(const Duration(seconds: 15));
      while (DateTime.now().isBefore(fin)) {
        await tester.pump();
        if (archivo.existsSync()) return;
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
      }
      await tester.pump();
    }

    Future<void> abrirPaciente(WidgetTester tester, AlmacenRegistros almacen) async {
      final clase = Clase(id: 'c1', nombre: 'Paciente', atributos: const [
        Atributo(id: 'a0', nombre: 'id', tipo: 'Long', esIdentificador: true),
        // Ninguno lleva `esRequerido`, a proposito: el diagrama no los marca y
        // el backend generado los exige igual. Lo que manda es el servidor.
        Atributo(id: 'a1', nombre: 'ci', tipo: 'String'),
        Atributo(id: 'a2', nombre: 'nombre', tipo: 'String'),
        Atributo(id: 'a3', nombre: 'nacimiento', tipo: 'LocalDate'),
      ]);
      await tester.runAsync(() async {
        await tester.pumpWidget(MaterialApp(
          home: PantallaRegistros(
            clase: clase,
            repositorio: Repositorio(almacen: almacen),
            diagrama: Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [clase]),
          ),
        ));
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump();
    }

    testWidgets('con la ficha vacia no encola nada y nombra los tres campos',
        (tester) async {
      await abrirPaciente(tester, AlmacenRegistros(carpeta));

      await tester.tap(find.text('Agregar'));
      await tester.pump();

      expect(find.textContaining('Faltan ci, nombre, nacimiento'), findsOneWidget);
      // Lo importante no es el cartel: es que no se encolo nada. El registro
      // vacio de la cola de verdad entro por aca.
      expect(cola().existsSync(), isFalse);
    });

    testWidgets('con un campo cargado nombra los que faltan, no todos', (tester) async {
      await abrirPaciente(tester, AlmacenRegistros(carpeta));

      await tester.enterText(find.byType(TextField).at(1), 'Juan');
      await tester.pump();
      await tester.tap(find.text('Agregar'));
      await tester.pump();

      expect(find.textContaining('Faltan ci, nacimiento'), findsOneWidget);
      expect(cola().existsSync(), isFalse);
    });

    testWidgets('con la ficha completa si crea, y sin cadenas vacias', (tester) async {
      await abrirPaciente(tester, AlmacenRegistros(carpeta));

      await tester.enterText(find.byType(TextField).at(0), '1234567');
      await tester.enterText(find.byType(TextField).at(1), '  Juan Perez  ');
      await tester.enterText(find.byType(TextField).at(2), '2000-03-23');
      await tester.pump();
      await tester.tap(find.text('Agregar'));
      await esperarArchivo(tester, cola());

      final encolado = jsonDecode(cola().readAsStringSync()) as List<dynamic>;
      expect(encolado, hasLength(1));
      // Recortado: los espacios de sobra viajaban al servidor tal cual.
      expect((encolado.single as Map)['datos'], {
        'ci': '1234567',
        'nombre': 'Juan Perez',
        'nacimiento': '2000-03-23',
      });
    });
  });
}
