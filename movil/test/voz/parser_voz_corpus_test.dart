import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/parser_voz.dart';

import 'corpus_de_voz.dart';

/// La gramatica del telefono contra el MISMO corpus que la del servidor.
///
/// Esta es la prueba que sostiene la promesa de que las dos copias dicen lo
/// mismo. Si se pone roja y ParserVozCorpusTest sigue verde, el telefono se
/// separo; si se ponen rojas las dos, cambio el corpus a proposito.
void main() {
  for (final caso in cargarCorpus()) {
    for (final frase in caso.frases) {
      test('${caso.id}: "$frase"', () {
        verificarCaso(caso, frase, ParserVoz().interpretar(frase, caso.contexto));
      });
    }
  }
}
