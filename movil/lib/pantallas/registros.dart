import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../generado/repositorio.dart';
import '../generado/tipos_de_campo.dart';
import '../generado/voz_registros.dart';
import '../main.dart';
import '../tipos.dart';
import '../voz/hoja_de_dictado.dart';
import '../voz/idioma_del_dictado.dart';

/// Las filas de una entidad y el formulario para agregar una.
///
/// Los campos salen de los atributos de la clase: el tipo escrito en el
/// diagrama decide el teclado, que es lo unico que hace falta para que cargar
/// un numero no obligue a buscar el simbolo.
class PantallaRegistros extends StatefulWidget {
  const PantallaRegistros({
    super.key,
    required this.clase,
    required this.repositorio,
    required this.diagrama,
  });

  final Clase clase;
  final Repositorio repositorio;

  // El diagrama entero, no solo la clase: interpretarPedidoDeRegistro necesita
  // buscar en todas las clases y sus campos, porque lo dictado puede nombrar
  // cualquier entidad, no solo la que esta abierta.
  final Diagrama diagrama;

  @override
  State<PantallaRegistros> createState() => _PantallaRegistrosState();
}

class _PantallaRegistrosState extends State<PantallaRegistros> {
  late Future<List<Map<String, dynamic>>> _filas;
  final _valores = <String, String>{};
  final SpeechToText _voz = SpeechToText();

  @override
  void initState() {
    super.initState();
    _filas = widget.repositorio.filas(widget.clase.nombre);
  }

  /// Los identificadores no se piden: los pone el backend generado.
  Iterable<Atributo> get _editables =>
      widget.clase.atributos.where((a) => !a.esIdentificador);

  TextInputType _tecladoDe(Atributo atributo) => switch (claseDeCampoDe(atributo.tipo)) {
        ClaseDeCampo.entero => TextInputType.number,
        ClaseDeCampo.decimal => const TextInputType.numberWithOptions(decimal: true),
        ClaseDeCampo.fecha || ClaseDeCampo.fechaHora => TextInputType.datetime,
        _ => TextInputType.text,
      };

  Future<void> _guardar() async {
    await widget.repositorio.crear(widget.clase.nombre, Map.of(_valores));
    _valores.clear();
    setState(() => _filas = widget.repositorio.filas(widget.clase.nombre));
  }

  // ---------- Dictado --------------------------------------------------

  /// Dicta un alta: "agrega un paciente llamado Juan". Usa el mismo cuadro de
  /// dictado que el lienzo -HojaDeDictado, en voz/hoja_de_dictado.dart- para
  /// que haya un solo lugar donde arreglar como se escucha, no dos que se van
  /// separando con el tiempo.
  Future<void> _dictar() async {
    final disponible = await _voz.initialize(
      onError: (error) => _avisar('No se pudo escuchar: ${error.errorMsg}'),
    );
    if (!disponible) {
      _avisar('Este telefono no tiene reconocimiento de voz disponible');
      return;
    }

    final idioma = idiomaDelDictado(
      (await _voz.locales()).map((disponible) => disponible.localeId).toList(),
    );

    if (!mounted) return;
    final frase = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colores.superficie,
      builder: (_) => HojaDeDictado(voz: _voz, idioma: idioma),
    );
    await _voz.stop();
    if (frase == null || frase.trim().isEmpty) return;

    // Se interpreta aca, en el aparato: sin esto dictar un registro
    // necesitaria red, y la restriccion del proyecto es que ninguna pantalla
    // la necesite para abrirse ni para operar sin conexion.
    final pedido = interpretarPedidoDeRegistro(frase, widget.diagrama);
    if (pedido == null) {
      // No se inventa nada: si la entidad o el campo no estan en el diagrama,
      // se muestra lo que se entendio y no se crea nada.
      _avisar('No entendi: "$frase"');
      return;
    }

    await widget.repositorio.crear(pedido.clase, pedido.datos);
    if (!mounted) return;
    setState(() => _filas = widget.repositorio.filas(widget.clase.nombre));
    _avisar('Agregado a ${pedido.clase}.');
  }

  void _avisar(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(texto), duration: const Duration(seconds: 4)),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(widget.clase.nombre),
          actions: [
            IconButton(
              tooltip: 'Dictar un registro',
              onPressed: _dictar,
              icon: const Icon(Icons.mic),
            ),
          ],
        ),
        body: Column(children: [
          for (final atributo in _editables)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: TextField(
                decoration: InputDecoration(labelText: atributo.nombre),
                keyboardType: _tecladoDe(atributo),
                onChanged: (texto) => _valores[atributo.nombre] = texto,
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton(onPressed: _guardar, child: const Text('Agregar')),
          ),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: _filas,
              builder: (_, resultado) => ListView(children: [
                for (final fila in resultado.data ?? const <Map<String, dynamic>>[])
                  ListTile(
                    title: Text(_editables
                        .map((a) => fila[a.nombre]?.toString() ?? '')
                        .where((t) => t.isNotEmpty)
                        .join(' · ')),
                    // Lo que todavia no llego al backend se marca: sin esto,
                    // sin senal no se distingue lo guardado de lo enviado.
                    trailing: fila.containsKey('_pendiente')
                        ? const Icon(Icons.schedule, size: 18)
                        : null,
                  ),
              ]),
            ),
          ),
        ]),
      );
}
