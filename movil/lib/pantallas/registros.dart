import 'package:flutter/material.dart';

import '../generado/repositorio.dart';
import '../generado/tipos_de_campo.dart';
import '../tipos.dart';

/// Las filas de una entidad y el formulario para agregar una.
///
/// Los campos salen de los atributos de la clase: el tipo escrito en el
/// diagrama decide el teclado, que es lo unico que hace falta para que cargar
/// un numero no obligue a buscar el simbolo.
class PantallaRegistros extends StatefulWidget {
  const PantallaRegistros({super.key, required this.clase, required this.repositorio});

  final Clase clase;
  final Repositorio repositorio;

  @override
  State<PantallaRegistros> createState() => _PantallaRegistrosState();
}

class _PantallaRegistrosState extends State<PantallaRegistros> {
  late Future<List<Map<String, dynamic>>> _filas;
  final _valores = <String, String>{};

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

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.clase.nombre)),
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
