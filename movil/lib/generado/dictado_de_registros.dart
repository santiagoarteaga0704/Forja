import '../tipos.dart';
import 'asistente.dart';
import 'voz_registros.dart';

/// Como termino un dictado.
///
/// Hace falta distinguir «no entendi» de «el usuario dijo que no»: son dos
/// cosas distintas y la pantalla tiene que decir cada una con sus palabras. Con
/// un `PedidoDeRegistro?` pelado las dos serian el mismo null.
class Dictado {
  const Dictado._(this.pedido, this.cancelado);

  const Dictado.entendido(PedidoDeRegistro pedido) : this._(pedido, false);
  const Dictado.noEntendido() : this._(null, false);
  const Dictado.cancelado() : this._(null, true);

  final PedidoDeRegistro? pedido;
  final bool cancelado;
}

/// Los tres escalones de un dictado, en este orden y no en otro.
///
/// 1. La gramatica determinista. Si entiende, se acabo: el modelo ni se
///    consulta, porque es mas lento, mas caro y menos confiable que una
///    expresion regular que ya acerto.
/// 2. Si no entendio y hay modelo en el aparato, el modelo PROPONE una frase
///    canonica, y esa frase vuelve a pasar por la misma gramatica. El modelo no
///    emite la operacion: no puede nombrar una entidad que no este en el
///    diagrama, porque quien decide sigue siendo el parser.
/// 3. El usuario confirma. Sin este paso una traduccion desafortunada crearia
///    un registro en silencio, que es exactamente lo que paso en la version web
///    con el respaldo por IA que creaba clases fantasma.
///
/// [confirmar] entra como parametro para que esto se pruebe sin pantalla, y
/// [asistente] es opcional porque el modelo es prescindible: sin el se pierde
/// un escalon, no la app.
///
/// [alPensar] se llama con true justo antes de consultar al modelo y con false
/// al volver. Existe porque el segundo escalon son varios segundos de pantalla
/// quieta y alguien tiene que poder avisar que se esta pensando; queda aca y no
/// en la pantalla porque este es el unico lugar que sabe si el modelo se va a
/// consultar o no.
Future<Dictado> resolverPedidoDictado({
  required String frase,
  required Diagrama diagrama,
  required Future<bool> Function(String fraseCanonica, PedidoDeRegistro pedido) confirmar,
  Asistente? asistente,
  void Function(bool pensando)? alPensar,
}) async {
  final directo = interpretarPedidoDeRegistro(frase, diagrama);
  if (directo != null) return Dictado.entendido(directo);

  final ayuda = asistente;
  if (ayuda == null) return const Dictado.noEntendido();

  String? propuesta;
  alPensar?.call(true);
  try {
    // Las entidades del diagrama viajan al prompt: el modelo tiene que proponer
    // nombres que el parser despues vaya a reconocer, no inventar los suyos.
    propuesta = await ayuda.fraseCanonica(
      frase,
      entidades: diagrama.clases.map((c) => c.nombre).toList(),
    );
  } finally {
    // En el finally para que un modelo que explota tampoco deje el aviso
    // prendido para siempre.
    alPensar?.call(false);
  }

  if (propuesta == null) return const Dictado.noEntendido();

  final pedido = interpretarPedidoDeRegistro(propuesta, diagrama);
  if (pedido == null) return const Dictado.noEntendido();

  return await confirmar(propuesta, pedido)
      ? Dictado.entendido(pedido)
      : const Dictado.cancelado();
}
