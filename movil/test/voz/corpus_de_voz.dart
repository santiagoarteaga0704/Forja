import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/voz/contexto.dart';
import 'package:forja_movil/voz/interpretacion.dart';

/// Lee el mismo compartido/corpus-voz.json que la suite de Java.
///
/// Es el gemelo de CorpusDeVoz.java, y tiene que decidir igual que el: mismas
/// convenciones de identificador, misma regla de comparar solo los campos que
/// el caso nombra, misma forma de entrar en listas y objetos anidados.
///
/// flutter test corre con el directorio de trabajo en movil/, de ahi el ../.

class CasoDelCorpus {
  CasoDelCorpus({
    required this.id,
    required this.frases,
    required this.entendida,
    required this.pasos,
    required this.sugerencias,
    required this.clases,
  });

  final String id;
  final List<String> frases;
  final bool entendida;
  final List<dynamic> pasos;
  final List<String> sugerencias;
  final Map<String, String> clases;

  ContextoDelDiagrama get contexto => ContextoDelDiagrama.de(clases.entries
      .map((entrada) => ClaseConocida(id: entrada.value, nombre: entrada.key))
      .toList());
}

List<CasoDelCorpus> cargarCorpus() {
  final archivo = File('../compartido/corpus-voz.json');
  if (!archivo.existsSync()) {
    throw StateError('No encontre ${archivo.absolute.path}');
  }
  final raiz = jsonDecode(archivo.readAsStringSync()) as Map<String, dynamic>;

  final contextos = <String, Map<String, String>>{};
  (raiz['contextos'] as Map<String, dynamic>).forEach((nombre, cuerpo) {
    final clases = <String, String>{};
    for (final clase in (cuerpo as Map<String, dynamic>)['clases'] as List) {
      clases[clase as String] = 'id-${clase.toLowerCase()}';
    }
    contextos[nombre] = clases;
  });

  return [
    for (final crudo in raiz['casos'] as List)
      if (crudo is Map<String, dynamic>)
        CasoDelCorpus(
          id: crudo['id'] as String,
          frases: ((crudo['frases'] as List?) ?? const []).cast<String>(),
          entendida: crudo['entendida'] as bool,
          pasos: (crudo['pasos'] as List?) ?? const [],
          sugerencias: ((crudo['sugerencias'] as List?) ?? const []).cast<String>(),
          clases: contextos[crudo['contexto'] as String]!,
        ),
  ];
}

void verificarCaso(CasoDelCorpus caso, String frase, Interpretacion obtenida) {
  final donde = '${caso.id} / "$frase"';

  expect(obtenida.entendida, caso.entendida, reason: '$donde: entendida');

  if (!caso.entendida) {
    if (caso.sugerencias.isNotEmpty) {
      expect(obtenida.sugerencias, caso.sugerencias, reason: '$donde: sugerencias');
    }
    return;
  }

  expect(obtenida.pasos, hasLength(caso.pasos.length),
      reason: '$donde: cantidad de pasos');

  final ligados = <String, String>{};
  for (var i = 0; i < caso.pasos.length; i++) {
    final esperado = caso.pasos[i] as Map<String, dynamic>;
    final paso = obtenida.pasos[i];
    final enElPaso = '$donde / paso $i';

    expect(paso.tipo, esperado['tipo'], reason: '$enElPaso: tipo');

    (esperado['comando'] as Map<String, dynamic>).forEach((campo, valorEsperado) {
      _compararCampo(enElPaso, campo, valorEsperado, paso.comando[campo], caso, ligados);
    });
  }
}

void _compararCampo(String donde, String campo, Object? esperado, Object? salio,
    CasoDelCorpus caso, Map<String, String> ligados) {
  final etiqueta = '$donde / $campo';

  if (esperado is String && esperado.startsWith('@')) {
    final nombre = esperado.substring(1);
    final id = caso.clases[nombre];
    expect(id, isNotNull, reason: '$etiqueta: el contexto no tiene la clase $nombre');
    expect(salio, id, reason: '$etiqueta: id de $nombre');
    return;
  }

  if (esperado is String && esperado.startsWith(r'$')) {
    expect(salio, isNotNull, reason: '$etiqueta: $esperado tiene que ser un id');
    final yaLigado = ligados[esperado];
    if (yaLigado == null) {
      ligados[esperado] = salio as String;
    } else {
      expect(salio, yaLigado, reason: '$etiqueta: $esperado tiene que repetirse');
    }
    return;
  }

  if (esperado is List) {
    expect(salio, isA<List>(), reason: '$etiqueta: se esperaba una lista');
    final salieron = salio as List;
    expect(salieron, hasLength(esperado.length), reason: '$etiqueta: cantidad');
    for (var i = 0; i < esperado.length; i++) {
      _compararCampo(etiqueta, '[$i]', esperado[i], salieron[i], caso, ligados);
    }
    return;
  }

  if (esperado is Map) {
    expect(salio, isA<Map>(), reason: '$etiqueta: se esperaba un objeto');
    final salieron = salio as Map;
    esperado.forEach((subcampo, subesperado) {
      _compararCampo(
          etiqueta, '$subcampo', subesperado, salieron[subcampo], caso, ligados);
    });
    return;
  }

  if (esperado is num) {
    expect(salio, isA<num>(), reason: '$etiqueta: el campo no vino');
    expect((salio! as num).toDouble(), esperado.toDouble(), reason: etiqueta);
    return;
  }

  expect(salio, esperado, reason: etiqueta);
}
