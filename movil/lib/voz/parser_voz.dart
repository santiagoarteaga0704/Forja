import '../identificadores.dart';
import 'contexto.dart';
import 'gramatica.dart';
import 'interpretacion.dart';
import 'tipos_declarados.dart';

/// La gramatica de dictado, corriendo en el aparato.
///
/// Espejo de ParserVoz.java. No hay modelo de lenguaje: son expresiones
/// regulares en cascada, de lo mas especifico a lo mas general, y ese orden es
/// parte de la gramatica. "tiene" puede empezar una relacion o un atributo, y
/// lo unico que las separa es si el destino es una clase que ya existe.
///
/// Que esto diga lo mismo que el servidor no se verifica aca sino en
/// compartido/corpus-voz.json, que leen las dos suites de pruebas. Si toca una
/// regla de un lado, la prueba del otro se pone roja.
class ParserVoz {
  // ---------- Clases ------------------------------------------------------

  static final _crearInterfaz = regla('$crear'
      r'\s+'
      '$articulo'
      r'(?:interfaz|interface)\s+(?:llamada\s+)?'
      '$nomFin');

  static final _crearClaseConAtributos = regla('$crear'
      r'\s+'
      '$articulo'
      r'clase\s+(?:llamada\s+)?'
      '$nom'
      r'\s+con\s+(?:los\s+|las\s+)?atributos?\s+(.+)');

  static final _crearClase = regla('$crear'
      r'\s+'
      '$articulo'
      r'clase\s+(?:llamada\s+)?'
      '$nomFin');

  static final _renombrar = regla(
      r'(?:renombra|renombrar|cambia\s+el\s+nombre\s+de|cambiar\s+el\s+nombre\s+de)\s+'
      '(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+(?:a|por|como)\s+'
      '$nomFin');

  static final _eliminar = regla(
      r'(?:elimina|eliminar|borra|borrar|quita|quitar)\s+(?:la\s+)?clase\s+'
      '$nomFin');

  static final _marcarAbstractaImperativo = regla(
      r'(?:marca|marcar|hace|hacer|pone|poner|declara|declarar)\s+'
      r'(?:a\s+|la\s+clase\s+)?'
      '$nom'
      r'\s+como\s+(?:clase\s+)?abstracta');

  static final _marcarAbstractaDeclarativo = regla('(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+es\s+(?:una\s+clase\s+)?abstracta');

  static final _marcarInterfaz = regla('(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+es\s+una\s+(?:interfaz|interface)');

  // ---------- Relaciones ---------------------------------------------------

  static final _herencia = regla('(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+(?:hereda\s+de|extiende|es\s+un\s+tipo\s+de|es\s+una?\s+subclase\s+de)\s+'
      '$nomFin');

  static final _realizacion = regla('$nom'
      r'\s+(?:implementa|realiza|cumple\s+con)\s+'
      '$nomFin');

  static final _composicion = regla('$nom'
      r'\s+(?:se\s+compone\s+de|esta\s+compuesta?\s+(?:de|por)|contiene)\s+'
      '(?:$cantidad' r'\s+)?'
      '$nomFin');

  static final _agregacion = regla('$nom'
      r'\s+(?:agrupa|reune)\s+'
      '(?:$cantidad' r'\s+)?'
      '$nomFin');

  static final _dependencia = regla('$nom'
      r'\s+(?:depende\s+de|usa\s+a)\s+'
      '$nomFin');

  static final _asociacion = regla(r'(?:un[ao]?\s+)?'
      '$nom'
      r'\s+(?:tiene|posee|se\s+relaciona\s+con|se\s+asocia\s+con)\s+'
      '(?:$cantidad' r'\s+)?'
      '$nomFin');

  // ---------- Atributos y metodos -----------------------------------------

  static final _atributoDestinoPrimero = regla(
      r'(?:a|ha|en|para)\s+'
      '(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+' '$agregar' r'\s+'
      '$articulo'
      r'atributo\s+(.+)');

  static final _atributoDestinoUltimo = regla('$agregar'
      r'\s+'
      '$articulo'
      r'atributo\s+(.+?)\s+(?:a|en)\s+'
      '(?:$articulo'
      r'clase\s+)?'
      '$nomFin');

  /// Forma declarativa: "el Paciente tiene un nombre de tipo texto". Exige que
  /// aparezca la palabra "tipo" para no confundirse con la asociacion, que
  /// empieza igual; la relacion se evalua antes y solo gana si sus dos extremos
  /// son clases conocidas.
  ///
  /// Ojo: aca el articulo son SEIS y no ocho. Es asi en el servidor.
  static final _atributoDeclarativo = regla(
      r'(?:(?:el|la|los|las|un|una)\s+)?(?:clase\s+)?'
      '$nom'
      r'\s+(?:tiene|posee|lleva)\s+(?:(?:un|una|el|la)\s+)?(.+\s+tipo\s+.+)');

  static final _metodoDestinoPrimero = regla(
      r'(?:a|ha|en|para)\s+'
      '(?:$articulo'
      r'clase\s+)?'
      '$nom'
      r'\s+' '$agregar' r'\s+'
      '$articulo'
      r'(?:metodo|operacion)\s+(.+)');

  static final _metodoDestinoUltimo = regla('$agregar'
      r'\s+'
      '$articulo'
      r'(?:metodo|operacion)\s+(.+?)\s+(?:a|en)\s+'
      '(?:$articulo'
      r'clase\s+)?'
      '$nomFin');

  /// Detalle de un atributo. Son DOS patrones y no uno por una razon concreta:
  /// con el tipo opcional y un grupo final que acepta cualquier cosa, el nombre
  /// quedaba siempre en su forma mas corta y el resto se lo tragaba ese final.
  /// "fecha de nacimiento de tipo fecha" producia un atributo llamado "fecha"
  /// de tipo texto. Exigiendo el tipo en el primer intento, el nombre se
  /// extiende hasta donde corresponde.
  static final _detalleAtributoConTipo = regla('$nom'
      r'\s+(?:de\s+)?tipo\s+([\p{L}\p{N}_]+)'
      r'((?:\s+(?:de\s+|con\s+|y\s+)?' '$marcas' r')*)');

  /// Sin tipo declarado se asume texto. El nombre llega hasta la primera
  /// palabra de marca, que es lo unico que permite separar "historia clinica
  /// obligatorio" en un nombre de dos palabras y una marca.
  static final _detalleAtributoSinTipo = regla('$nom'
      r'((?:\s+(?:de\s+|con\s+|y\s+)?' '$marcas' r')*)');

  static final _detalleMetodo = regla('$nom'
      r'(?:\s+con\s+(?:los\s+)?parametros?\s+(.+?))?'
      r'(?:\s+que\s+(?:devuelve|retorna)\s+([\p{L}\p{N}_]+))?');

  static final _parametro = regla('$nom'
      r'\s+(?:de\s+)?tipo\s+([\p{L}\p{N}_]+)');

  static final _longitud = RegExp(r'longitud\s+(\d+)', caseSensitive: false);

  // ---------- Interpretacion ----------------------------------------------

  Interpretacion interpretar(String? fraseOriginal, ContextoDelDiagrama contexto) {
    if (fraseOriginal == null || fraseOriginal.trim().isEmpty) {
      return Interpretacion.noEntendida('', _sugerencias(contexto));
    }
    final frase = limpiar(fraseOriginal);

    return _primeraQueEncaje(frase, contexto) ??
        Interpretacion.noEntendida(fraseOriginal, _sugerencias(contexto));
  }

  /// El orden importa: lo mas especifico primero.
  Interpretacion? _primeraQueEncaje(String frase, ContextoDelDiagrama contexto) {
    var m = _crearInterfaz.firstMatch(frase);
    if (m != null) {
      final nombre = limpiarNombre(m.group(1));
      return _uno(frase, 'Cree la interfaz $nombre', 'CLASE_CREAR',
          _crearClaseCarga(nombre, estereotipo: 'interface'));
    }

    m = _crearClaseConAtributos.firstMatch(frase);
    if (m != null) {
      return _crearClaseConSusAtributos(frase, limpiarNombre(m.group(1)), m.group(2)!);
    }

    m = _crearClase.firstMatch(frase);
    if (m != null) {
      final nombre = limpiarNombre(m.group(1));
      return _uno(frase, 'Cree la clase $nombre', 'CLASE_CREAR',
          _crearClaseCarga(nombre));
    }

    m = _renombrar.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      final nuevo = limpiarNombre(m.group(2));
      return _uno(frase, 'Renombre ${clase.nombre} a $nuevo', 'CLASE_RENOMBRAR',
          {'claseId': clase.id, 'nombre': nuevo});
    }

    m = _eliminar.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, 'Elimine la clase ${clase.nombre}', 'CLASE_ELIMINAR',
          {'claseId': clase.id});
    }

    m = _marcarAbstractaImperativo.firstMatch(frase) ??
        _marcarAbstractaDeclarativo.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, 'Marque ${clase.nombre} como abstracta', 'CLASE_MARCAR',
          {'claseId': clase.id, 'estereotipo': null, 'esAbstracta': true});
    }

    m = _marcarInterfaz.firstMatch(frase);
    if (m != null) {
      final clase = contexto.resolver(m.group(1));
      if (clase == null) return _noConozco(frase, m.group(1)!, contexto);
      return _uno(frase, '${clase.nombre} ahora es una interfaz', 'CLASE_MARCAR',
          {'claseId': clase.id, 'estereotipo': 'interface', 'esAbstracta': false});
    }

    // Las relaciones van ANTES que los atributos: "tiene" puede empezar las dos
    // y solo se decide mirando si el destino es una clase conocida. Mover esto
    // de lugar rompe la mitad de la gramatica sin romper la compilacion.
    final relacion = _comoRelacion(frase, contexto);
    if (relacion != null) return relacion;

    m = _atributoDeclarativo.firstMatch(frase);
    if (m != null) {
      final comoAtributo = _comoAtributo(frase, m.group(1)!, m.group(2)!, contexto);
      if (comoAtributo != null) return comoAtributo;
    }

    m = _atributoDestinoPrimero.firstMatch(frase);
    if (m != null) return _comoAtributo(frase, m.group(1)!, m.group(2)!, contexto);

    m = _atributoDestinoUltimo.firstMatch(frase);
    if (m != null) return _comoAtributo(frase, m.group(2)!, m.group(1)!, contexto);

    m = _metodoDestinoPrimero.firstMatch(frase);
    if (m != null) return _comoMetodo(frase, m.group(1)!, m.group(2)!, contexto);

    m = _metodoDestinoUltimo.firstMatch(frase);
    if (m != null) return _comoMetodo(frase, m.group(2)!, m.group(1)!, contexto);

    return null;
  }

  // ---------- Armado de cargas --------------------------------------------

  /// Los nombres de campo son los del registro ComandoOperacion.CrearClase. Van
  /// en un solo lugar para que no se escriban a mano en cada rama.
  Map<String, dynamic> _crearClaseCarga(String nombre, {String? estereotipo}) => {
        'claseId': nuevoIdDeModelo(),
        'nombre': nombre,
        'estereotipo': estereotipo,
        'esAbstracta': false,
        'posX': 0,
        'posY': 0,
      };

  /// Una frase puede crear la clase y sus atributos de una vez. Los atributos
  /// cuelgan de la clase que se acaba de crear, no de una que haya que buscar.
  Interpretacion _crearClaseConSusAtributos(
      String frase, String nombre, String listaDeAtributos) {
    final carga = _crearClaseCarga(nombre);
    final claseId = carga['claseId'] as String;

    final pasos = <Paso>[Paso(tipo: 'CLASE_CREAR', comando: carga)];
    final agregados = <String>[];

    for (final trozo in separarEnumeracion(listaDeAtributos)) {
      final atributo = _atributoDesdeDetalle(claseId, trozo);
      if (atributo != null) {
        pasos.add(Paso(tipo: 'ATRIBUTO_AGREGAR', comando: atributo));
        agregados.add(atributo['nombre'] as String);
      }
    }

    final explicacion = agregados.isEmpty
        ? 'Cree la clase $nombre'
        : 'Cree la clase $nombre con ${agregados.join(', ')}';
    return Interpretacion.entendida(frase, explicacion, pasos);
  }

  // ---------- Relaciones ---------------------------------------------------

  /// Devuelve null -y deja pasar a las reglas de atributo- cuando alguno de los
  /// dos extremos no es una clase conocida. "Paciente tiene muchas Consultas"
  /// relaciona porque Consulta existe; "Paciente tiene muchas deudas" describe
  /// un atributo porque deudas no existe.
  Interpretacion? _comoRelacion(String frase, ContextoDelDiagrama contexto) {
    // Cada forma dice si lleva grupo de cantidad, porque de eso depende en que
    // grupo cae el destino.
    final formas = <(RegExp, String, bool)>[
      (_herencia, 'HERENCIA', false),
      (_realizacion, 'REALIZACION', false),
      (_composicion, 'COMPOSICION', true),
      (_agregacion, 'AGREGACION', true),
      (_dependencia, 'DEPENDENCIA', false),
      (_asociacion, 'ASOCIACION', true),
    ];

    for (final (patron, tipo, conCantidad) in formas) {
      final m = patron.firstMatch(frase);
      if (m == null) continue;

      final cantidadDicha = conCantidad ? m.group(2) : null;
      final nombreDestino = conCantidad ? m.group(3) : m.group(2);

      final origen = contexto.resolver(m.group(1));
      final destino = contexto.resolver(nombreDestino);
      if (origen == null || destino == null) continue;

      return _uno(
          frase,
          'Relacione ${origen.nombre} con ${destino.nombre} (${tipo.toLowerCase()})',
          'RELACION_CREAR',
          {
            'relacionId': nuevoIdDeModelo(),
            'origenId': origen.id,
            'destinoId': destino.id,
            'tipo': tipo,
            'multiplicidadOrigen': '1',
            'multiplicidadDestino': _multiplicidad(cantidadDicha),
            'rolOrigen': null,
            'rolDestino': null,
            'etiqueta': null,
          });
    }
    return null;
  }

  /// "muchas" se traduce a cero o mas y no a uno o mas. Es la lectura
  /// conservadora: si el modelo dice que puede no haber ninguna, el esquema
  /// generado no impone una restriccion que nadie pidio, mientras que al
  /// contrario obligaria a crear filas que quiza no existen.
  String _multiplicidad(String? cantidadDicha) {
    if (cantidadDicha == null) return '1';
    final limpia = cantidadDicha.trim().toLowerCase().replaceAll(RegExp(r'\s+'), ' ');
    return switch (limpia) {
      'muchas' || 'muchos' || 'varias' || 'varios' || 'cero o mas' => '0..*',
      'al menos una' || 'al menos uno' => '1..*',
      _ => '1',
    };
  }

  // ---------- Atributos y metodos -----------------------------------------

  Interpretacion? _comoAtributo(String frase, String nombreDeLaClase, String detalle,
      ContextoDelDiagrama contexto) {
    final clase = contexto.resolver(nombreDeLaClase);
    if (clase == null) return _noConozco(frase, nombreDeLaClase, contexto);

    final carga = _atributoDesdeDetalle(clase.id, detalle);
    if (carga == null) return null;

    return _uno(
        frase,
        'Agregue ${carga['nombre']}: ${carga['tipo']} a ${clase.nombre}',
        'ATRIBUTO_AGREGAR',
        carga);
  }

  /// Los nombres de campo son los del registro ComandoOperacion.AgregarAtributo.
  Map<String, dynamic>? _atributoDesdeDetalle(String claseId, String detalle) {
    final limpio = detalle.trim();

    // Se intenta primero con el tipo declarado; la forma sin tipo es el
    // respaldo, no la primera opcion.
    var m = _detalleAtributoConTipo.firstMatch(limpio);
    final conTipo = m != null;
    if (!conTipo) {
      m = _detalleAtributoSinTipo.firstMatch(limpio);
      if (m == null) return null;
    }

    final nombre = limpiarNombre(m.group(1));
    if (nombre.isEmpty) return null;

    final tipo = conTipo ? normalizarTipo(m.group(2)) : 'String';
    final marcasDichas = (m.group(conTipo ? 3 : 2) ?? '').toLowerCase();

    final esClave = marcasDichas.contains('clave') ||
        marcasDichas.contains('identificador') ||
        marcasDichas.contains('primaria');
    // Una clave es obligatoria y unica por definicion, aunque no se diga.
    final esUnico = esClave ||
        marcasDichas.contains('unico') ||
        marcasDichas.contains('unica');
    final esRequerido = esClave ||
        marcasDichas.contains('obligatorio') ||
        marcasDichas.contains('obligatoria') ||
        marcasDichas.contains('requerido') ||
        marcasDichas.contains('requerida') ||
        marcasDichas.contains('no nulo');

    final mLongitud = _longitud.firstMatch(marcasDichas);

    return {
      'claseId': claseId,
      'atributoId': nuevoIdDeModelo(),
      'nombre': nombre,
      'tipo': tipo,
      'visibilidad': 'PRIVADO',
      'esIdentificador': esClave,
      'esRequerido': esRequerido,
      'esUnico': esUnico,
      'longitud': mLongitud == null ? null : int.parse(mLongitud.group(1)!),
    };
  }

  Interpretacion? _comoMetodo(String frase, String nombreDeLaClase, String detalle,
      ContextoDelDiagrama contexto) {
    final clase = contexto.resolver(nombreDeLaClase);
    if (clase == null) return _noConozco(frase, nombreDeLaClase, contexto);

    final m = _detalleMetodo.firstMatch(detalle.trim());
    if (m == null) return null;

    final nombre = limpiarNombre(m.group(1));
    if (nombre.isEmpty) return null;

    final parametros = <Map<String, dynamic>>[];
    if (m.group(2) != null) {
      for (final trozo in separarEnumeracion(m.group(2)!)) {
        final mParametro = _parametro.firstMatch(trozo.trim());
        if (mParametro != null) {
          parametros.add({
            'parametroId': nuevoIdDeModelo(),
            'nombre': limpiarNombre(mParametro.group(1)),
            'tipo': normalizarTipo(mParametro.group(2)),
          });
        }
      }
    }

    final retorno = m.group(3) == null ? 'void' : normalizarTipo(m.group(3));

    return _uno(frase, 'Agregue la operacion $nombre(): $retorno a ${clase.nombre}',
        'METODO_AGREGAR', {
      'claseId': clase.id,
      'metodoId': nuevoIdDeModelo(),
      'nombre': nombre,
      'tipoRetorno': retorno,
      'visibilidad': 'PUBLICO',
      'esAbstracto': false,
      'esEstatico': false,
      'parametros': parametros,
    });
  }

  // ---------- Auxiliares --------------------------------------------------

  Interpretacion _uno(
          String frase, String explicacion, String tipo, Map<String, dynamic> comando) =>
      Interpretacion.entendida(frase, explicacion, [Paso(tipo: tipo, comando: comando)]);

  /// No se entendio porque se nombro una clase que no existe. Decirlo asi -y no
  /// "no te entendi"- es la diferencia entre que la persona corrija el nombre y
  /// que repita la misma frase mas fuerte.
  Interpretacion _noConozco(
      String frase, String nombre, ContextoDelDiagrama contexto) {
    final pistas = <String>[
      'No hay ninguna clase llamada "${nombre.trim()}" en el diagrama',
    ];
    if (contexto.nombres().isNotEmpty) {
      pistas.add('Las que hay son: ${contexto.nombres().join(', ')}');
    }
    pistas.add('Proba primero: crea la clase ${limpiarNombre(nombre)}');
    return Interpretacion.noEntendida(frase, pistas);
  }

  List<String> _sugerencias(ContextoDelDiagrama contexto) {
    final nombres = contexto.nombres();
    final ejemplo = nombres.isEmpty ? 'Paciente' : nombres[0];
    final otro = nombres.length > 1 ? nombres[1] : 'Consulta';
    return [
      'crea la clase $ejemplo',
      'a $ejemplo agregale el atributo nombre de tipo texto obligatorio',
      'a $ejemplo agregale el atributo codigo de tipo texto clave de longitud 20',
      'un $ejemplo tiene muchas $otro',
      '$ejemplo hereda de Persona',
      'marca Persona como abstracta',
      'a $ejemplo agregale el metodo calcularEdad que devuelve entero',
    ];
  }
}
