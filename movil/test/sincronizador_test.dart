import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/almacen.dart';
import 'package:forja_movil/api.dart';
import 'package:forja_movil/comandos.dart';
import 'package:forja_movil/modelo_local.dart';
import 'package:forja_movil/sincronizador.dart';
import 'package:forja_movil/tipos.dart';

/// Api de mentira: permite decidir en cada prueba que contesta el servidor y
/// mirar despues en que orden se le pidieron las cosas.
///
/// Se prefiere escribirla a mano antes que generar un simulacro: son tres
/// metodos, y hacerla explicita deja a la vista que se esta simulando, que es
/// justamente lo que cada prueba necesita controlar.
class ApiDeMentira extends Api {
  ApiDeMentira() : super(base: 'http://pruebas');

  bool hayRed = true;

  /// Respuesta a devolver por cada envio, en orden. Si se acaba, se responde
  /// APLICADA.
  final List<Object> respuestas = [];

  /// Tokens de los comandos que llegaron, en orden de llegada.
  final List<String> recibidos = [];

  /// Lo que devuelve el delta.
  List<OperacionRemota> aDevolverEnDelta = [];

  /// Cuantas veces se pidio el diagrama completo.
  int vecesQueSePidioTodo = 0;
  Diagrama? diagramaDelServidor;

  int _siguiente = 0;

  @override
  Future<ResultadoOperacion> enviar(String diagramaId, Comando comando, String sesionId) async {
    if (!hayRed) throw SinConexion();
    recibidos.add(comando.tokenCliente);

    if (_siguiente < respuestas.length) {
      final respuesta = respuestas[_siguiente++];
      if (respuesta is Exception) throw respuesta;
      return respuesta as ResultadoOperacion;
    }
    return const ResultadoOperacion(estado: 'APLICADA', secuencia: 1);
  }

  @override
  Future<List<OperacionRemota>> delta(String diagramaId, int desde) async {
    if (!hayRed) throw SinConexion();
    return aDevolverEnDelta;
  }

  @override
  Future<Diagrama> diagrama(String diagramaId) async {
    if (!hayRed) throw SinConexion();
    vecesQueSePidioTodo++;
    return diagramaDelServidor ?? Diagrama(id: diagramaId, nombre: 'Del servidor', version: 0);
  }
}

void main() {
  late Directory carpeta;
  late Almacen almacen;
  late ApiDeMentira api;
  late Sincronizador sincronizador;

  const idDiagrama = 'd-1';

  setUp(() async {
    carpeta = await Directory.systemTemp.createTemp('forja-pruebas-');
    almacen = Almacen(carpeta);
    api = ApiDeMentira();
    sincronizador = Sincronizador(api: api, almacen: almacen, sesionId: 'sesion-prueba');
  });

  tearDown(() async {
    if (await carpeta.exists()) await carpeta.delete(recursive: true);
  });

  /// Deja un diagrama con una clase, como si ya se hubiera abierto antes.
  Future<void> conUnDiagramaGuardado() async {
    final diagrama = Diagrama(id: idDiagrama, nombre: 'Clinica', version: 3, clases: [
      Clase(id: 'c-1', nombre: 'Paciente'),
    ]);
    await almacen.guardarDiagrama(diagrama);
  }

  Comando nuevoAtributo(String nombre) => Comando.agregarAtributo(
        claseId: 'c-1',
        atributoId: 'a-$nombre',
        nombre: nombre,
        tipo: 'String',
        token: 'tk-$nombre',
      );

  group('Trabajar sin conexion', () {
    test('un cambio se aplica y se guarda aunque no haya red', () async {
      await conUnDiagramaGuardado();
      api.hayRed = false;
      await sincronizador.abrir(idDiagrama);

      await sincronizador.ejecutar(nuevoAtributo('nombre'));

      // La pantalla ya lo muestra: no se espero al servidor.
      expect(sincronizador.diagrama!.clasePorId('c-1')!.atributos, hasLength(1));
      expect(sincronizador.estado, EstadoDeSincronizacion.sinConexion);
      expect(sincronizador.cuantosPendientes, 1);

      // Y sobrevive a que la aplicacion se cierre.
      final guardado = await almacen.leerDiagrama(idDiagrama);
      expect(guardado!.clasePorId('c-1')!.atributos, hasLength(1));
      expect(await almacen.leerCola(idDiagrama), hasLength(1));
    });

    test('al reabrir sin red, lo pendiente sigue ahi y no se aplica dos veces', () async {
      await conUnDiagramaGuardado();
      api.hayRed = false;
      await sincronizador.abrir(idDiagrama);
      await sincronizador.ejecutar(nuevoAtributo('nombre'));

      // Otra instancia: es lo que pasa al volver a abrir la aplicacion.
      final otro = Sincronizador(api: api, almacen: almacen, sesionId: 'sesion-prueba');
      await otro.abrir(idDiagrama);

      expect(otro.cuantosPendientes, 1, reason: 'la cola tiene que sobrevivir');
      expect(otro.diagrama!.clasePorId('c-1')!.atributos, hasLength(1),
          reason: 'el cambio ya estaba en la copia guardada; reproducirlo lo duplicaria');
    });

    test('al volver la conexion se envia en orden y despues se pide el delta', () async {
      await conUnDiagramaGuardado();
      api.hayRed = false;
      await sincronizador.abrir(idDiagrama);
      await sincronizador.ejecutar(nuevoAtributo('uno'));
      await sincronizador.ejecutar(nuevoAtributo('dos'));
      await sincronizador.ejecutar(nuevoAtributo('tres'));

      api.hayRed = true;
      api.aDevolverEnDelta = [
        // Mientras el telefono estuvo sin red, otro creo una clase.
        const OperacionRemota(
          secuencia: 7,
          tipo: 'CLASE_CREAR',
          carga: {'claseId': 'c-2', 'nombre': 'Consulta', 'esAbstracta': false},
          origen: 'LIENZO',
          autorId: 'otro',
        ),
      ];
      await sincronizador.sincronizar();

      expect(api.recibidos, ['tk-uno', 'tk-dos', 'tk-tres'],
          reason: 'el orden importa: un atributo no puede llegar antes que su clase');
      expect(sincronizador.cuantosPendientes, 0);
      expect(sincronizador.estado, EstadoDeSincronizacion.alDia);
      // Y ademas entro lo que hicieron los demas.
      expect(sincronizador.diagrama!.clasePorNombre('Consulta'), isNotNull);
      expect(sincronizador.diagrama!.version, 7);
    });

    test('si la red se corta a mitad de la cola, lo que falta se conserva', () async {
      await conUnDiagramaGuardado();
      await sincronizador.abrir(idDiagrama);
      api.hayRed = false;
      await sincronizador.ejecutar(nuevoAtributo('uno'));
      await sincronizador.ejecutar(nuevoAtributo('dos'));

      // Vuelve la red, pero se corta despues del primer envio.
      api.hayRed = true;
      api.respuestas.addAll([
        const ResultadoOperacion(estado: 'APLICADA', secuencia: 4),
        SinConexion(),
      ]);
      await sincronizador.sincronizar();

      expect(sincronizador.cuantosPendientes, 1);
      expect(sincronizador.estado, EstadoDeSincronizacion.sinConexion);
      expect(await almacen.leerCola(idDiagrama), hasLength(1),
          reason: 'lo que falta enviar tiene que quedar guardado');
    });
  });

  group('Lo que el servidor contesta', () {
    test('un reenvio que el servidor reconoce se considera hecho', () async {
      await conUnDiagramaGuardado();
      await sincronizador.abrir(idDiagrama);
      api.hayRed = false;
      await sincronizador.ejecutar(nuevoAtributo('uno'));

      api.hayRed = true;
      // Es la garantia de idempotencia funcionando, no un error.
      api.respuestas.add(const ResultadoOperacion(estado: 'DUPLICADA', secuencia: 4));
      await sincronizador.sincronizar();

      expect(sincronizador.cuantosPendientes, 0);
      expect(sincronizador.estado, EstadoDeSincronizacion.alDia);
      expect(sincronizador.rechazados, isEmpty);
    });

    test('un rechazo por bloqueo detiene la cola sin perder el orden', () async {
      await conUnDiagramaGuardado();
      await sincronizador.abrir(idDiagrama);
      api.hayRed = false;
      await sincronizador.ejecutar(nuevoAtributo('uno'));
      await sincronizador.ejecutar(nuevoAtributo('dos'));

      api.hayRed = true;
      api.respuestas.add(const ResultadoOperacion(
          estado: 'RECHAZADA_POR_BLOQUEO', secuencia: 0, retenidoPor: 'Bruno'));
      await sincronizador.sincronizar();

      expect(sincronizador.cuantosPendientes, 2, reason: 'no se saltea ninguno');
      expect(api.recibidos, ['tk-uno'], reason: 'se detiene en el primero que no pasa');
      expect(sincronizador.aviso, contains('Bruno'));
      expect(sincronizador.estado, EstadoDeSincronizacion.pendiente);
    });

    test('tras varios rechazos por bloqueo se descarta y se avisa', () async {
      await conUnDiagramaGuardado();
      final conPocosIntentos = Sincronizador(
          api: api, almacen: almacen, sesionId: 'sesion-prueba', maximosIntentos: 2);
      await conPocosIntentos.abrir(idDiagrama);
      api.hayRed = false;
      await conPocosIntentos.ejecutar(nuevoAtributo('uno'));

      api.hayRed = true;
      api.respuestas.addAll([
        const ResultadoOperacion(
            estado: 'RECHAZADA_POR_BLOQUEO', secuencia: 0, retenidoPor: 'Bruno'),
        const ResultadoOperacion(
            estado: 'RECHAZADA_POR_BLOQUEO', secuencia: 0, retenidoPor: 'Bruno'),
      ]);

      await conPocosIntentos.sincronizar();
      expect(conPocosIntentos.cuantosPendientes, 1);
      await conPocosIntentos.sincronizar();

      // Insistir para siempre dejaria el resto de la cola detenido detras.
      expect(conPocosIntentos.cuantosPendientes, 0);
      expect(conPocosIntentos.rechazados, hasLength(1));
      expect(conPocosIntentos.rechazados.first, contains('Bruno'));
    });

    test('lo que el servidor rechaza definitivamente se descarta y se informa', () async {
      await conUnDiagramaGuardado();
      await sincronizador.abrir(idDiagrama);
      api.hayRed = false;
      await sincronizador.ejecutar(nuevoAtributo('repetido'));

      api.hayRed = true;
      api.respuestas.add(ErrorApi(422, 'La clase ya tiene un atributo llamado repetido'));
      api.diagramaDelServidor = Diagrama(
          id: idDiagrama, nombre: 'Clinica', version: 9, clases: [Clase(id: 'c-1', nombre: 'Paciente')]);

      await sincronizador.sincronizar();

      expect(sincronizador.cuantosPendientes, 0);
      expect(sincronizador.rechazados.first, contains('repetido'));
      // La copia local dejo de coincidir con el servidor: se pide entera en
      // lugar de arrastrar un estado que ya no es verdad.
      expect(api.vecesQueSePidioTodo, greaterThanOrEqualTo(1));
      expect(sincronizador.diagrama!.clasePorId('c-1')!.atributos, isEmpty);
    });

    test('un token vencido no descarta el trabajo pendiente', () async {
      await conUnDiagramaGuardado();
      await sincronizador.abrir(idDiagrama);
      api.hayRed = false;
      await sincronizador.ejecutar(nuevoAtributo('uno'));

      api.hayRed = true;
      api.respuestas.add(ErrorApi(401, 'no autorizado'));
      await sincronizador.sincronizar();

      // Volver a entrar recupera la sesion; perder el cambio no se recupera.
      expect(sincronizador.cuantosPendientes, 1);
      expect(sincronizador.aviso, contains('sesion'));
    });
  });

  group('Abrir por primera vez', () {
    test('sin copia local se pide el diagrama entero', () async {
      api.diagramaDelServidor = Diagrama(
          id: idDiagrama, nombre: 'Clinica', version: 2, clases: [Clase(id: 'c-9', nombre: 'Factura')]);

      await sincronizador.abrir(idDiagrama);

      expect(api.vecesQueSePidioTodo, 1);
      expect(sincronizador.diagrama!.clasePorNombre('Factura'), isNotNull);
      expect(await almacen.leerDiagrama(idDiagrama), isNotNull,
          reason: 'tiene que quedar guardado para la proxima vez que no haya red');
    });

    test('sin copia local y sin red, la pantalla queda vacia pero no rota', () async {
      api.hayRed = false;
      await sincronizador.abrir(idDiagrama);

      expect(sincronizador.diagrama, isNull);
      expect(sincronizador.estado, EstadoDeSincronizacion.sinConexion);
    });
  });
}
