import 'contexto.dart';

/// Los ladrillos con los que estan hechos los 19 patrones de la gramatica.
///
/// Es el espejo de ParserVoz.java y de sus auxiliares. Lo unico que cambia es
/// el escapado: donde Java escribe "\\s" dentro de una cadena, aca va una
/// cadena cruda con \s.
///
/// Tres diferencias del lenguaje que hay que tener presentes al portar, y que
/// el corpus compartido esta ahi para atrapar:
///
/// 1. Java compila con CASE_INSENSITIVE | UNICODE_CASE; aca es
///    `caseSensitive: false, unicode: true`. Sin `unicode: true` las clases
///    \p{L} ni siquiera compilan.
/// 2. Java ancla con matches(); aca se usa firstMatch y el anclaje viene del
///    ^...$ que agrega regla().
/// 3. El split de Java descarta los trozos vacios del final y el de Dart no,
///    asi que limpiarNombre y separarEnumeracion los filtran a mano.

/// Nombre de clase o de miembro: letras, digitos y espacios intermedios.
const nom = r'([\p{L}][\p{L}\p{N}_]*(?:\s+[\p{L}][\p{L}\p{N}_]*)*?)';
const nomFin = r'([\p{L}][\p{L}\p{N}_]*(?:\s+[\p{L}][\p{L}\p{N}_]*)*)';

const crear = r'(?:crea|crear|cree|creame|agrega|agregar|agregame|anade|anadir'
    r'|anadi|nueva|nuevo|dibuja|dibujar)';
// "anadi" y "suma" son el imperativo voseante. Ver el comentario en
// ParserVoz.java: faltaban, y la frase terminaba creando una clase repetida.
const agregar = r'(?:agrega|agregale|agregar|anade|anadile|anadir|anadi|pone|ponele'
    r'|poner|sumale|suma)';
const cantidad = r'(muchas|muchos|varias|varios|una|uno|un|cero\s+o\s+mas'
    r'|al\s+menos\s+una?|al\s+menos\s+uno)';

/// Las marcas de almacenamiento que puede llevar un atributo dictado.
const marcas = r'(?:clave|identificador|primaria|unico|unica|obligatorio'
    r'|obligatoria|requerido|requerida|no\s+nulo|longitud\s+\d+)';

/// El articulo, con su espacio DENTRO del grupo. Sin eso, la alternancia parte
/// "una Consulta" en "un" + "a Consulta" y el nombre de la clase queda
/// arruinado.
const articulo = r'(?:(?:el|la|los|las|un|una|unos|unas)\s+)?';

RegExp regla(String expresion) =>
    RegExp('^$expresion\$', caseSensitive: false, unicode: true);

/// Quita acentos, signos de puntuacion y espacios de sobra.
String limpiar(String frase) => sinAcentos(frase)
    // El reconocimiento de voz agrega puntos y comas donde le parece.
    .replaceAll(RegExp('[.,;:!?¿¡"\']'), ' ')
    .replaceAll(RegExp(r'\s+'), ' ')
    .trim();

/// Nombre listo para el modelo: sin espacios y en mayuscula inicial por
/// palabra, salvo la primera, que conserva su caja para no estropear un nombre
/// que el cliente ya escribio bien. Dictando no se puede pronunciar el camello,
/// asi que "historia clinica" tiene que llegar como historiaClinica.
String limpiarNombre(String? bruto) {
  if (bruto == null) return '';

  final palabras =
      bruto.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();

  final salida = StringBuffer();
  for (var i = 0; i < palabras.length; i++) {
    final palabra = palabras[i];
    if (i == 0) {
      salida.write(palabra);
    } else {
      salida.write(palabra[0].toUpperCase());
      salida.write(palabra.substring(1));
    }
  }
  return salida.toString();
}

/// Separa "a, b y c" en sus partes.
List<String> separarEnumeracion(String texto) => texto
    .split(RegExp(r'\s*(?:,|\sy\s|\se\s)\s*'))
    .map((parte) => parte.trim())
    .where((parte) => parte.isNotEmpty)
    .toList();
