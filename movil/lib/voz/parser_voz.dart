import '../identificadores.dart';
import 'contexto.dart';
import 'gramatica.dart';
import 'interpretacion.dart';

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
  /// los completa la parte de la gramatica que sabe leer un detalle; por ahora
  /// queda la clase sola.
  Interpretacion _crearClaseConSusAtributos(
      String frase, String nombre, String listaDeAtributos) {
    final carga = _crearClaseCarga(nombre);
    return Interpretacion.entendida(
        frase, 'Cree la clase $nombre', [Paso(tipo: 'CLASE_CREAR', comando: carga)]);
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
