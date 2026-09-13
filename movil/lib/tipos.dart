// Contrato con el backend. Los nombres coinciden con los de los registros de
// Java: mantenerlos iguales cuesta menos que traducirlos y hace evidente de
// donde viene cada campo cuando algo no cuadra.

enum Visibilidad { publico, privado, protegido, paquete }

Visibilidad visibilidadDe(String? texto) => switch (texto) {
      'PUBLICO' => Visibilidad.publico,
      'PROTEGIDO' => Visibilidad.protegido,
      'PAQUETE' => Visibilidad.paquete,
      _ => Visibilidad.privado,
    };

String nombreDeVisibilidad(Visibilidad v) => switch (v) {
      Visibilidad.publico => 'PUBLICO',
      Visibilidad.privado => 'PRIVADO',
      Visibilidad.protegido => 'PROTEGIDO',
      Visibilidad.paquete => 'PAQUETE',
    };

String simboloDe(Visibilidad v) => switch (v) {
      Visibilidad.publico => '+',
      Visibilidad.privado => '-',
      Visibilidad.protegido => '#',
      Visibilidad.paquete => '~',
    };

class Credencial {
  const Credencial({
    required this.token,
    required this.usuarioId,
    required this.nombre,
    required this.email,
    this.expiraEn,
  });

  final String token;
  final String usuarioId;
  final String nombre;
  final String email;
  final DateTime? expiraEn;

  factory Credencial.desdeJson(Map<String, dynamic> json) => Credencial(
        token: json['token'] as String,
        usuarioId: json['usuarioId'] as String,
        nombre: json['nombre'] as String? ?? '',
        email: json['email'] as String? ?? '',
        expiraEn: DateTime.tryParse(json['expiraEn'] as String? ?? ''),
      );

  Map<String, dynamic> aJson() => {
        'token': token,
        'usuarioId': usuarioId,
        'nombre': nombre,
        'email': email,
        'expiraEn': expiraEn?.toIso8601String(),
      };

  bool get vencida => expiraEn != null && expiraEn!.isBefore(DateTime.now());
}

class Proyecto {
  const Proyecto({required this.id, required this.nombre, required this.propietarioId});

  final String id;
  final String nombre;
  final String propietarioId;

  factory Proyecto.desdeJson(Map<String, dynamic> json) => Proyecto(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        propietarioId: json['propietarioId'] as String? ?? '',
      );
}

class ResumenDiagrama {
  const ResumenDiagrama({required this.id, required this.nombre, required this.version});

  final String id;
  final String nombre;
  final int version;

  factory ResumenDiagrama.desdeJson(Map<String, dynamic> json) => ResumenDiagrama(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        version: (json['version'] as num).toInt(),
      );

  Map<String, dynamic> aJson() => {'id': id, 'nombre': nombre, 'version': version};
}

class Atributo {
  const Atributo({
    required this.id,
    required this.nombre,
    required this.tipo,
    this.visibilidad = Visibilidad.privado,
    this.esIdentificador = false,
    this.esRequerido = false,
    this.esUnico = false,
    this.longitud,
  });

  final String id;
  final String nombre;
  final String tipo;
  final Visibilidad visibilidad;
  final bool esIdentificador;
  final bool esRequerido;
  final bool esUnico;
  final int? longitud;

  factory Atributo.desdeJson(Map<String, dynamic> json) => Atributo(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        tipo: json['tipo'] as String? ?? 'String',
        visibilidad: visibilidadDe(json['visibilidad'] as String?),
        esIdentificador: json['esIdentificador'] as bool? ?? false,
        esRequerido: json['esRequerido'] as bool? ?? false,
        esUnico: json['esUnico'] as bool? ?? false,
        longitud: (json['longitud'] as num?)?.toInt(),
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'nombre': nombre,
        'tipo': tipo,
        'visibilidad': nombreDeVisibilidad(visibilidad),
        'esIdentificador': esIdentificador,
        'esRequerido': esRequerido,
        'esUnico': esUnico,
        'longitud': longitud,
      };

  String get comoSeLee {
    final marcas = [
      if (esIdentificador) 'PK',
      if (esUnico && !esIdentificador) 'U',
      if (esRequerido) '*',
    ].join(' ');
    final largo = longitud != null ? '($longitud)' : '';
    return '${simboloDe(visibilidad)} $nombre: $tipo$largo${marcas.isEmpty ? '' : ' $marcas'}';
  }
}

class Metodo {
  const Metodo({
    required this.id,
    required this.nombre,
    required this.tipoRetorno,
    this.visibilidad = Visibilidad.publico,
    this.esAbstracto = false,
  });

  final String id;
  final String nombre;
  final String tipoRetorno;
  final Visibilidad visibilidad;
  final bool esAbstracto;

  factory Metodo.desdeJson(Map<String, dynamic> json) => Metodo(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        tipoRetorno: json['tipoRetorno'] as String? ?? 'void',
        visibilidad: visibilidadDe(json['visibilidad'] as String?),
        esAbstracto: json['esAbstracto'] as bool? ?? false,
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'nombre': nombre,
        'tipoRetorno': tipoRetorno,
        'visibilidad': nombreDeVisibilidad(visibilidad),
        'esAbstracto': esAbstracto,
      };

  String get comoSeLee => '${simboloDe(visibilidad)} $nombre(): $tipoRetorno';
}

class Clase {
  Clase({
    required this.id,
    required this.nombre,
    this.estereotipo,
    this.esAbstracta = false,
    this.posX = 0,
    this.posY = 0,
    List<Atributo>? atributos,
    List<Metodo>? metodos,
  })  : atributos = atributos ?? [],
        metodos = metodos ?? [];

  final String id;
  String nombre;
  String? estereotipo;
  bool esAbstracta;
  double posX;
  double posY;
  final List<Atributo> atributos;
  final List<Metodo> metodos;

  factory Clase.desdeJson(Map<String, dynamic> json) => Clase(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        estereotipo: json['estereotipo'] as String?,
        esAbstracta: json['esAbstracta'] as bool? ?? false,
        posX: (json['posX'] as num?)?.toDouble() ?? 0,
        posY: (json['posY'] as num?)?.toDouble() ?? 0,
        atributos: (json['atributos'] as List<dynamic>? ?? [])
            .map((a) => Atributo.desdeJson(a as Map<String, dynamic>))
            .toList(),
        metodos: (json['metodos'] as List<dynamic>? ?? [])
            .map((m) => Metodo.desdeJson(m as Map<String, dynamic>))
            .toList(),
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'nombre': nombre,
        'estereotipo': estereotipo,
        'esAbstracta': esAbstracta,
        'posX': posX,
        'posY': posY,
        'atributos': atributos.map((a) => a.aJson()).toList(),
        'metodos': metodos.map((m) => m.aJson()).toList(),
      };

  /// Alto que ocupa en el lienzo. Se calcula y no se guarda porque depende de
  /// cuantos miembros tenga en cada momento.
  double get alto {
    final cabecera = estereotipo != null ? 40.0 : 28.0;
    return cabecera + 8 + (atributos.length + metodos.length) * 16 +
        (metodos.isNotEmpty ? 6 : 0) + 8;
  }

  static const double ancho = 200;
}

class Relacion {
  const Relacion({
    required this.id,
    required this.origenId,
    required this.destinoId,
    required this.tipo,
    this.multiplicidadOrigen = '1',
    this.multiplicidadDestino = '1',
  });

  final String id;
  final String origenId;
  final String destinoId;
  final String tipo;
  final String multiplicidadOrigen;
  final String multiplicidadDestino;

  factory Relacion.desdeJson(Map<String, dynamic> json) => Relacion(
        id: json['id'] as String,
        origenId: json['origenId'] as String,
        destinoId: json['destinoId'] as String,
        tipo: json['tipo'] as String,
        multiplicidadOrigen: json['multiplicidadOrigen'] as String? ?? '1',
        multiplicidadDestino: json['multiplicidadDestino'] as String? ?? '1',
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'origenId': origenId,
        'destinoId': destinoId,
        'tipo': tipo,
        'multiplicidadOrigen': multiplicidadOrigen,
        'multiplicidadDestino': multiplicidadDestino,
      };
}

class Bloqueo {
  const Bloqueo({
    required this.elementoId,
    required this.poseedorId,
    required this.poseedorNombre,
  });

  final String elementoId;
  final String poseedorId;
  final String poseedorNombre;

  factory Bloqueo.desdeJson(Map<String, dynamic> json) => Bloqueo(
        elementoId: json['elementoId'] as String,
        poseedorId: json['poseedorId'] as String? ?? '',
        poseedorNombre: json['poseedorNombre'] as String? ?? 'alguien',
      );

  Map<String, dynamic> aJson() => {
        'elementoId': elementoId,
        'poseedorId': poseedorId,
        'poseedorNombre': poseedorNombre,
      };
}

/// El diagrama completo tal como lo devuelve el servidor y como se guarda en el
/// telefono para poder abrirlo sin conexion.
class Diagrama {
  Diagrama({
    required this.id,
    required this.nombre,
    required this.version,
    List<Clase>? clases,
    List<Relacion>? relaciones,
    List<Bloqueo>? bloqueos,
  })  : clases = clases ?? [],
        relaciones = relaciones ?? [],
        bloqueos = bloqueos ?? [];

  final String id;
  String nombre;
  int version;
  final List<Clase> clases;
  final List<Relacion> relaciones;
  final List<Bloqueo> bloqueos;

  factory Diagrama.desdeJson(Map<String, dynamic> json) => Diagrama(
        id: json['id'] as String,
        nombre: json['nombre'] as String,
        version: (json['version'] as num?)?.toInt() ?? 0,
        clases: (json['clases'] as List<dynamic>? ?? [])
            .map((c) => Clase.desdeJson(c as Map<String, dynamic>))
            .toList(),
        relaciones: (json['relaciones'] as List<dynamic>? ?? [])
            .map((r) => Relacion.desdeJson(r as Map<String, dynamic>))
            .toList(),
        bloqueos: (json['bloqueos'] as List<dynamic>? ?? [])
            .map((b) => Bloqueo.desdeJson(b as Map<String, dynamic>))
            .toList(),
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'nombre': nombre,
        'version': version,
        'clases': clases.map((c) => c.aJson()).toList(),
        'relaciones': relaciones.map((r) => r.aJson()).toList(),
        'bloqueos': bloqueos.map((b) => b.aJson()).toList(),
      };

  Clase? clasePorId(String id) => clases.where((c) => c.id == id).firstOrNull;

  Clase? clasePorNombre(String nombre) {
    final buscado = claveDeNombre(nombre);
    return clases.where((c) => claveDeNombre(c.nombre) == buscado).firstOrNull;
  }

  Bloqueo? bloqueoDe(String elementoId) =>
      bloqueos.where((b) => b.elementoId == elementoId).firstOrNull;
}

/// Forma comparable de un nombre: sin acentos, sin espacios y en minuscula. Es
/// la misma regla que aplica el servidor, y tiene que seguir siendolo: si
/// divergieran, el telefono creeria que una clase no existe y la duplicaria.
String claveDeNombre(String texto) {
  const conAcento = 'áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ';
  const sinAcento = 'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC';

  final salida = StringBuffer();
  for (final rune in texto.runes) {
    final caracter = String.fromCharCode(rune);
    final donde = conAcento.indexOf(caracter);
    final limpio = donde >= 0 ? sinAcento[donde] : caracter;
    if (RegExp(r'[A-Za-z0-9]').hasMatch(limpio)) {
      salida.write(limpio.toLowerCase());
    }
  }
  return salida.toString();
}
