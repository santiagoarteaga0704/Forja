import 'tipos.dart';

/// Un cambio sobre el diagrama, listo para enviarse y para guardarse.
///
/// Se representa como el tipo mas su carga, que es exactamente la forma en que
/// viaja por la red y en que el servidor lo guarda en la bitacora. La
/// alternativa -una jerarquia de clases selladas como en el backend- daria mas
/// seguridad al escribir el codigo, pero obligaria a escribir la conversion a
/// JSON de cada variante dos veces, y aqui el comando tiene que persistirse tal
/// cual para sobrevivir a que la aplicacion se cierre con la cola sin vaciar.
///
/// La seguridad al construirlo se consigue con los constructores de fabrica: no
/// hay lugar donde se escriba el nombre de un campo a mano.
class Comando {
  Comando({
    required this.tipo,
    required this.carga,
    required this.tokenCliente,
    this.origen = 'LIENZO',
    this.intentos = 0,
  });

  final String tipo;
  final Map<String, dynamic> carga;

  /// Lo genera el telefono antes de encolar. Es lo que hace que reenviar una
  /// operacion tras un corte de red no la duplique.
  final String tokenCliente;
  final String origen;

  /// Cuantas veces se intento enviar. Sirve para no reintentar para siempre algo
  /// que el servidor rechaza por una razon que no va a cambiar.
  int intentos;

  factory Comando.desdeJson(Map<String, dynamic> json) => Comando(
        tipo: json['tipo'] as String,
        carga: Map<String, dynamic>.from(json['comando'] as Map),
        tokenCliente: json['tokenCliente'] as String,
        origen: json['origen'] as String? ?? 'LIENZO',
        intentos: (json['intentos'] as num?)?.toInt() ?? 0,
      );

  /// Forma en que lo espera el endpoint de operaciones.
  Map<String, dynamic> aJson({String? sesionId}) => {
        'tipo': tipo,
        'comando': carga,
        'origen': origen,
        'tokenCliente': tokenCliente,
        'sesionId': ?sesionId,
      };

  Map<String, dynamic> aJsonGuardado() => {...aJson(), 'intentos': intentos};

  // ---------- Constructores de cada comando -------------------------------

  factory Comando.crearClase({
    required String claseId,
    required String nombre,
    String? estereotipo,
    bool esAbstracta = false,
    double posX = 0,
    double posY = 0,
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'CLASE_CREAR',
        tokenCliente: token,
        origen: origen,
        carga: {
          'claseId': claseId,
          'nombre': nombre,
          'estereotipo': estereotipo,
          'esAbstracta': esAbstracta,
          'posX': posX,
          'posY': posY,
        },
      );

  factory Comando.renombrarClase({
    required String claseId,
    required String nombre,
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'CLASE_RENOMBRAR',
        tokenCliente: token,
        origen: origen,
        carga: {'claseId': claseId, 'nombre': nombre},
      );

  factory Comando.marcarClase({
    required String claseId,
    String? estereotipo,
    bool esAbstracta = false,
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'CLASE_MARCAR',
        tokenCliente: token,
        origen: origen,
        carga: {'claseId': claseId, 'estereotipo': estereotipo, 'esAbstracta': esAbstracta},
      );

  factory Comando.moverClase({
    required String claseId,
    required double posX,
    required double posY,
    required String token,
  }) =>
      Comando(
        tipo: 'CLASE_MOVER',
        tokenCliente: token,
        carga: {'claseId': claseId, 'posX': posX, 'posY': posY},
      );

  factory Comando.eliminarClase({required String claseId, required String token}) => Comando(
        tipo: 'CLASE_ELIMINAR',
        tokenCliente: token,
        carga: {'claseId': claseId},
      );

  factory Comando.agregarAtributo({
    required String claseId,
    required String atributoId,
    required String nombre,
    required String tipo,
    Visibilidad visibilidad = Visibilidad.privado,
    bool esIdentificador = false,
    bool esRequerido = false,
    bool esUnico = false,
    int? longitud,
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'ATRIBUTO_AGREGAR',
        tokenCliente: token,
        origen: origen,
        carga: {
          'claseId': claseId,
          'atributoId': atributoId,
          'nombre': nombre,
          'tipo': tipo,
          'visibilidad': nombreDeVisibilidad(visibilidad),
          'esIdentificador': esIdentificador,
          'esRequerido': esRequerido,
          'esUnico': esUnico,
          'longitud': longitud,
        },
      );

  factory Comando.eliminarAtributo({
    required String claseId,
    required String atributoId,
    required String token,
  }) =>
      Comando(
        tipo: 'ATRIBUTO_ELIMINAR',
        tokenCliente: token,
        carga: {'claseId': claseId, 'atributoId': atributoId},
      );

  factory Comando.agregarMetodo({
    required String claseId,
    required String metodoId,
    required String nombre,
    String tipoRetorno = 'void',
    Visibilidad visibilidad = Visibilidad.publico,
    List<Map<String, String>> parametros = const [],
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'METODO_AGREGAR',
        tokenCliente: token,
        origen: origen,
        carga: {
          'claseId': claseId,
          'metodoId': metodoId,
          'nombre': nombre,
          'tipoRetorno': tipoRetorno,
          'visibilidad': nombreDeVisibilidad(visibilidad),
          'esAbstracto': false,
          'esEstatico': false,
          'parametros': parametros,
        },
      );

  factory Comando.eliminarMetodo({
    required String claseId,
    required String metodoId,
    required String token,
  }) =>
      Comando(
        tipo: 'METODO_ELIMINAR',
        tokenCliente: token,
        carga: {'claseId': claseId, 'metodoId': metodoId},
      );

  factory Comando.crearRelacion({
    required String relacionId,
    required String origenId,
    required String destinoId,
    required String tipoDeRelacion,
    String multiplicidadOrigen = '1',
    String multiplicidadDestino = '1',
    String origen = 'LIENZO',
    required String token,
  }) =>
      Comando(
        tipo: 'RELACION_CREAR',
        tokenCliente: token,
        origen: origen,
        carga: {
          'relacionId': relacionId,
          'origenId': origenId,
          'destinoId': destinoId,
          'tipo': tipoDeRelacion,
          'multiplicidadOrigen': multiplicidadOrigen,
          'multiplicidadDestino': multiplicidadDestino,
        },
      );

  factory Comando.eliminarRelacion({required String relacionId, required String token}) => Comando(
        tipo: 'RELACION_ELIMINAR',
        tokenCliente: token,
        carga: {'relacionId': relacionId},
      );
}
