import 'dart:io';

import 'package:flutter_gemma/flutter_gemma.dart';
import 'package:flutter_gemma_mediapipe/flutter_gemma_mediapipe.dart';
import 'package:path_provider/path_provider.dart';

/// El archivo que hay que empujar al telefono con `adb push`. El nombre esta
/// aca y no repartido por la app para que la guia de instalacion y el codigo no
/// se separen.
const nombreDelArchivoDelModelo = 'gemma3-1b-it-int4.task';

/// Donde se busca el modelo, en orden.
///
/// Primero la carpeta externa de la app -en Android,
/// `/sdcard/Android/data/<applicationId>/files`-, porque es la unica en la que
/// `adb push` escribe sin `run-as` ni root: poner medio giga en el telefono
/// tiene que ser un comando y no un procedimiento. Despues la de documentos,
/// que es adonde llegaria el archivo si algun dia lo copiara la propia app.
Future<File> archivoDelModelo() async {
  final lugares = <Directory?>[
    if (Platform.isAndroid) await getExternalStorageDirectory(),
    await getApplicationDocumentsDirectory(),
  ];

  for (final carpeta in lugares) {
    if (carpeta == null) continue;
    final archivo = File('${carpeta.path}${Platform.pathSeparator}$nombreDelArchivoDelModelo');
    if (await archivo.exists()) return archivo;
  }

  throw StateError(
    'El modelo no esta: se busco $nombreDelArchivoDelModelo en '
    '${lugares.nonNulls.map((c) => c.path).join(', ')}',
  );
}

/// Arranque del modelo en el aparato.
///
/// Devuelve la funcion que [Asistente] necesita, o lanza si el modelo no esta.
/// Quien lo llama decide que hacer con eso: la app tiene que seguir en pie sin
/// modelo, asi que la excepcion no puede escaparse a la interfaz.
///
/// El modelo no se descarga: son 529 MB y bajarlos desde la app obligaria a
/// tener red, que es justo lo que este proyecto no quiere. Se copia una vez con
/// `adb push` y se lee del disco.
Future<Future<String> Function(String)> arrancarGemma() async {
  final archivo = await archivoDelModelo();

  // Los motores son opt-in desde la version 1.0 del plugin: sin registrar
  // MediaPipe aca, el primer createModel lanza pidiendo el paquete del motor.
  await FlutterGemma.initialize(inferenceEngines: const [MediaPipeEngine()]);
  await FlutterGemma.installModel(
    modelType: ModelType.gemmaIt,
    fileType: ModelFileType.task,
  ).fromFile(archivo.path).install();

  // 512 alcanza de sobra: el prompt son cuatro renglones y la respuesta que se
  // espera es una sola frase. Pedir mas contexto solo gastaria memoria en un
  // telefono que ya esta cargando medio giga de pesos.
  final modelo = await FlutterGemma.getActiveModel(maxTokens: 512);

  return (String prompt) async {
    // Una conversacion por pedido: el asistente no conversa, traduce una frase.
    // Arrastrar historial solo gastaria contexto y haria que un pedido anterior
    // contaminara el siguiente.
    final chat = await modelo.createChat(temperature: 0.1, topK: 1);
    try {
      await chat.addQueryChunk(Message.text(text: prompt, isUser: true));
      final respuesta = await chat.generateChatResponse();
      // generateChatResponse devuelve un ModelResponse sellado, no un String:
      // cambio entre versiones del plugin. Lo unico que interesa aca es el
      // texto; una llamada a herramienta o un bloque de razonamiento no son
      // una frase canonica y se descartan.
      return respuesta is TextResponse ? respuesta.token : '';
    } finally {
      await chat.close();
    }
  };
}
