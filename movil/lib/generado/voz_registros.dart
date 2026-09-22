import '../tipos.dart';
import 'tipos_de_campo.dart';

/// La voz, en el dominio de los registros.
///
/// Aparte de parser_voz.dart a proposito: aquel es el espejo de ParserVoz.java
/// y lo que lo mantiene fiel es un corpus compartido. Este habla de filas de una
/// tabla, que es un dominio que el lado Java no tiene por que conocer.
///
/// No inventa nada: si la entidad o el campo no estan en el diagrama, devuelve
/// null y el usuario ve que no se entendio. Es preferible a crear una fila en
/// un lugar que no era.
class PedidoDeRegistro {
  const PedidoDeRegistro({required this.clase, required this.datos});

  final String clase;
  final Map<String, dynamic> datos;
}

// El imperativo voseante va con y sin tilde porque el reconocedor de Android
// escribe las dos formas.
const _verbos = r'(?:agrega|agregá|anadi|anadí|añadi|añadí|crea|creá|nuevo|nueva)';
// El \b final es necesario: sin el, "un" -intentado antes que "una" por venir
// primero en la alternativa- hace match como prefijo de "una" y deja una "a"
// suelta pegada al nombre de la clase capturado a continuacion (una consulta
// -> capturaba "a consulta"). El limite de palabra fuerza al motor a
// retroceder y probar "una" entera.
const _articulos = r'(?:un|una|el|la|los|las)?\b';

final _conCampo = RegExp(
    '^$_verbos\\s+$_articulos\\s*(.+?)\\s+con\\s+(\\S+)\\s+(.+)\$',
    caseSensitive: false);

final _conNombre = RegExp(
    '^$_verbos\\s+$_articulos\\s*(.+?)\\s+llamad[oa]\\s+(.+)\$',
    caseSensitive: false);

PedidoDeRegistro? interpretarPedidoDeRegistro(String frase, Diagrama diagrama) {
  final limpia = frase.trim();

  final conCampo = _conCampo.firstMatch(limpia);
  if (conCampo != null) {
    final clase = _claseDicha(conCampo.group(1)!, diagrama);
    if (clase == null) return null;
    final campo = _campoDicho(clase, conCampo.group(2)!);
    if (campo == null) return null;
    return PedidoDeRegistro(clase: clase.nombre, datos: {campo: conCampo.group(3)!.trim()});
  }

  final conNombre = _conNombre.firstMatch(limpia);
  if (conNombre != null) {
    final clase = _claseDicha(conNombre.group(1)!, diagrama);
    if (clase == null) return null;
    final campo = _primerCampoDeTexto(clase);
    if (campo == null) return null;
    return PedidoDeRegistro(clase: clase.nombre, datos: {campo: conNombre.group(2)!.trim()});
  }

  return null;
}

/// claveDeNombre es la misma regla que ya usa el diagrama para decidir si dos
/// nombres son el mismo: sin acentos, sin espacios, en minuscula.
Clase? _claseDicha(String dicho, Diagrama diagrama) {
  final buscado = claveDeNombre(dicho);
  return diagrama.clases.where((c) => claveDeNombre(c.nombre) == buscado).firstOrNull;
}

String? _campoDicho(Clase clase, String dicho) {
  final buscado = claveDeNombre(dicho);
  return clase.atributos
      .where((a) => claveDeNombre(a.nombre) == buscado)
      .map((a) => a.nombre)
      .firstOrNull;
}

/// Donde cae un «llamado X»: el primer texto que no sea la clave. Dictar una
/// clave primaria no tiene sentido, y si cayera ahi el alta chocaria con la
/// siguiente.
String? _primerCampoDeTexto(Clase clase) => clase.atributos
    .where((a) => !a.esIdentificador && claseDeCampoDe(a.tipo) == ClaseDeCampo.texto)
    .map((a) => a.nombre)
    .firstOrNull;
