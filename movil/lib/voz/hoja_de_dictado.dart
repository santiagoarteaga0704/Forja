import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../main.dart';

/// Hoja de dictado: escucha y devuelve el texto reconocido.
///
/// El reconocimiento lo hace Android, que puede funcionar sin conexion **si el
/// paquete de idioma esta descargado en el telefono**. Es una condicion del
/// sistema, no de la aplicacion, y conviene comprobarla antes de la defensa.
///
/// Vive aca, fuera de lienzo.dart, porque la pantalla de registros dicta con
/// el mismo cuadro: era privado y solo entendia el diagrama, asi que se
/// extrajo tal cual para que las dos pantallas compartan un solo lugar donde
/// arreglar el dictado, en vez de tener dos copias que se van separando.
class HojaDeDictado extends StatefulWidget {
  const HojaDeDictado({super.key, required this.voz, required this.idioma});

  final SpeechToText voz;

  /// Nulo significa "no fijes ninguno": el aparato usa el suyo.
  final String? idioma;

  @override
  State<HojaDeDictado> createState() => _HojaDeDictadoState();
}

class _HojaDeDictadoState extends State<HojaDeDictado> {
  String _reconocido = '';
  bool _escuchando = false;

  @override
  void initState() {
    super.initState();
    _escuchar();
  }

  Future<void> _escuchar() async {
    setState(() => _escuchando = true);
    await widget.voz.listen(
      onResult: (resultado) => setState(() => _reconocido = resultado.recognizedWords),
      listenOptions: SpeechListenOptions(
        // El castellano de la region: el reconocedor de Android acierta bastante
        // mas con la variante correcta que con el castellano de Espana. Cual es
        // lo decide idiomaDelDictado() mirando lo que el aparato tiene
        // instalado; nulo quiere decir que use el del sistema.
        localeId: widget.idioma,
        partialResults: true,
        cancelOnError: true,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.only(
          left: 18,
          right: 18,
          top: 18,
          bottom: 18 + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(Icons.mic, size: 34, color: _escuchando ? Colores.ambar : Colores.textoDebil),
          const SizedBox(height: 10),
          Text(
            _reconocido.isEmpty
                ? (_escuchando ? 'Escuchando...' : 'Toca para dictar')
                : _reconocido,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 15),
          ),
          const SizedBox(height: 6),
          const Text('Por ejemplo: "un Paciente tiene muchas Consultas"',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colores.textoDebil, fontSize: 11.5)),
          const SizedBox(height: 16),
          Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancelar'),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: FilledButton(
                onPressed: _reconocido.trim().isEmpty
                    ? null
                    : () => Navigator.pop(context, _reconocido),
                child: const Text('Aplicar'),
              ),
            ),
          ]),
        ]),
      );
}
