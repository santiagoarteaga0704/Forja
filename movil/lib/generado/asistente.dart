import 'package:flutter/foundation.dart';

/// El escalon que entra cuando la gramatica no entendio.
///
/// El modelo NO emite operaciones: propone una frase canonica que el parser
/// determinista vuelve a interpretar, y el usuario confirma antes de que se
/// aplique. Es la decision de diseno del 16 de septiembre, y tiene una razon
/// vivida: en la version web un respaldo por IA aplicaba sin revision y creaba
/// clases fantasma en silencio. El modelo propone; las reglas disponen.
///
/// Recibe la funcion que habla con el modelo en lugar de crearla: asi se prueba
/// sin cargar 529 MB, y asi la app sigue en pie cuando el modelo no esta.
class Asistente {
  const Asistente({
    required this.preguntarAlModelo,
    this.limite = const Duration(seconds: 30),
  });

  final Future<String> Function(String) preguntarAlModelo;

  /// Cuanto se le aguanta al modelo antes de darlo por perdido.
  ///
  /// Sin esto, un `generateChatResponse()` que no vuelve nunca deja el dictado
  /// colgado para siempre y el boton del microfono mudo. Treinta segundos son
  /// muchisimo para una frase de diez palabras: si tarda mas, algo se trabo.
  final Duration limite;

  /// La frase que el parser tiene que volver a interpretar, o null si el modelo
  /// no propuso nada util. Nunca lanza: quedarse sin este escalon no puede
  /// tumbar un dictado.
  ///
  /// [entidades] son los nombres de las clases del diagrama. Van al prompt
  /// porque sin ellos el modelo traduce a ciegas: propondria «agrega un cliente
  /// llamado Juan» sobre un diagrama que tiene `Paciente`, el parser lo
  /// rechazaria con razon, y el escalon entero se veria como que el modelo no
  /// acierta nunca.
  Future<String?> fraseCanonica(String pedido, {List<String> entidades = const []}) async {
    try {
      return _limpiar(await preguntarAlModelo(_prompt(pedido, entidades)).timeout(limite));
    } catch (_) {
      // Incluye el TimeoutException: para el que llama, un modelo que no
      // contesta y un modelo que contesta cualquier cosa son lo mismo.
      return null;
    }
  }

  // El prefijo que el prompt deja colgando: estos modelos suelen repetir la
  // ultima etiqueta antes de contestar.
  static final _prefijo = RegExp(r'^orden\s*:\s*', caseSensitive: false);
  static final _abreComillas = RegExp(r'^["“«]+');
  static final _cierraComillas = RegExp(r'["”»]+$');

  /// Un modelo de mil millones de parametros agrega explicaciones aunque se le
  /// pida que no. Se recorta aca y no en el parser: la gramatica es el espejo
  /// del lado Java y no se ensucia con las manias de un modelo.
  static String? _limpiar(String propuesta) {
    final primera = propuesta
        .split('\n')
        .map((linea) => linea.trim())
        .where((linea) => linea.isNotEmpty)
        .firstOrNull;
    if (primera == null) return null;

    // Dos pasadas porque las dos basuras vienen en cualquier orden:
    // «Orden: "agrega..."» y «"Orden: agrega..."» son los dos habituales.
    final limpia = _pelar(_pelar(primera));
    return limpia.isEmpty ? null : limpia;
  }

  static String _pelar(String texto) => texto
      .replaceFirst(_prefijo, '')
      .replaceFirst(_abreComillas, '')
      .replaceFirst(_cierraComillas, '')
      .trim();

  /// Se le ensenan las DOS formas que el parser entiende, no una.
  ///
  /// `voz_registros.dart` interpreta tanto `llamado <valor>` como
  /// `con <campo> <valor>`; ensenar solo la primera desperdicia la mitad de lo
  /// que la gramatica ya sabe hacer.
  String _prompt(String pedido, List<String> entidades) => '''
Converti el pedido en UNA sola orden, con una de estas dos formas exactas:
"agrega un <entidad> llamado <valor>"
"agrega un <entidad> con <campo> <valor>"
${_lasEntidades(entidades)}No expliques nada. No agregues nada. Si no podes, responde vacio.

Pedido: $pedido
Orden:''';

  static String _lasEntidades(List<String> entidades) => entidades.isEmpty
      ? ''
      : 'La <entidad> tiene que ser una de estas, escrita igual: '
          '${entidades.join(', ')}.\n';
}

/// El asistente que nunca llega.
///
/// Ahorra tener que tratar aparte, en cada pantalla, el caso de que no haya
/// nada que escuchar. Esta vacio para siempre y por eso se comparte: no hay
/// nada que liberar.
final ValueListenable<Asistente?> sinAsistente = ValueNotifier<Asistente?>(null);
