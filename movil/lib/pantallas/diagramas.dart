import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';

import '../almacen.dart';
import '../api.dart';
import '../generado/almacen_registros.dart';
import '../generado/asistente.dart';
import '../generado/repositorio.dart';
import '../main.dart';
import '../tipos.dart';
import 'entidades.dart';
import 'lienzo.dart';

/// Eleccion del diagrama.
///
/// La lista se guarda en el telefono ademas de pedirse al servidor. Sin eso, un
/// telefono sin senal no podria ni llegar a la pantalla del diagrama que ya tiene
/// descargado, y toda la funcionalidad sin conexion quedaria detras de una puerta
/// que no abre.
class PantallaDiagramas extends StatefulWidget {
  const PantallaDiagramas({
    super.key,
    required this.api,
    required this.almacen,
    required this.credencial,
    required this.sesionId,
    required this.alSalir,
    this.asistente,
  });

  final Api api;
  final Almacen almacen;
  final Credencial credencial;
  final String sesionId;
  final Future<void> Function() alSalir;

  /// Solo lo lleva de la mano hasta la pantalla de registros. Esta pantalla no
  /// dicta nada.
  ///
  /// ValueListenable y no valor: las rutas que empuja esta pantalla cachean su
  /// pagina, asi que el valor de hoy seria el valor para siempre.
  final ValueListenable<Asistente?>? asistente;

  @override
  State<PantallaDiagramas> createState() => _PantallaDiagramasState();
}

class _PantallaDiagramasState extends State<PantallaDiagramas> {
  List<ResumenDiagrama> _diagramas = [];
  bool _cargando = true;
  bool _sinConexion = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  /// El diagrama entero para abrir la pantalla de entidades: la lista que
  /// tiene esta pantalla es solo el resumen (id, nombre, version), sin clases
  /// ni atributos. Primero lo guardado -si ya se abrio el lienzo una vez, ya
  /// esta- y recien si falta se pide al servidor.
  Future<Diagrama?> _diagramaCompleto(ResumenDiagrama resumen) async {
    final local = await widget.almacen.leerDiagrama(resumen.id);
    if (local != null) return local;
    try {
      final diagrama = await widget.api.diagrama(resumen.id);
      await widget.almacen.guardarDiagrama(diagrama);
      return diagrama;
    } on SinConexion {
      return null;
    } on ErrorApi {
      return null;
    }
  }

  Future<void> _abrirEntidades(ResumenDiagrama resumen) async {
    final diagrama = await _diagramaCompleto(resumen);
    if (!mounted) return;
    if (diagrama == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Este diagrama todavia no se descargo y no hay conexion.'),
      ));
      return;
    }
    final repositorio = Repositorio(
      almacen: AlmacenRegistros(await getApplicationDocumentsDirectory()),
    );
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => PantallaEntidades(
        diagrama: diagrama,
        repositorio: repositorio,
        asistente: widget.asistente,
      ),
    ));
  }

  Future<void> _cargar() async {
    // Primero lo guardado: la pantalla se dibuja de inmediato y despues se
    // actualiza si hay red.
    final guardados = await widget.almacen.leerResumenes();
    if (mounted && guardados.isNotEmpty) {
      setState(() {
        _diagramas = guardados;
        _cargando = false;
      });
    }

    try {
      final proyectos = await widget.api.proyectos();
      final todos = <ResumenDiagrama>[];
      for (final proyecto in proyectos) {
        todos.addAll(await widget.api.diagramas(proyecto.id));
      }
      await widget.almacen.guardarResumenes(todos);
      if (!mounted) return;
      setState(() {
        _diagramas = todos;
        _sinConexion = false;
        _error = null;
        _cargando = false;
      });
    } on SinConexion {
      if (!mounted) return;
      setState(() {
        _sinConexion = true;
        _cargando = false;
      });
    } on ErrorApi catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.mensaje;
        _cargando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Row(children: const [
          Yunque(),
          SizedBox(width: 10),
          Text('FORJA', style: TextStyle(letterSpacing: 3, fontSize: 14, fontWeight: FontWeight.bold)),
        ]),
        actions: [
          IconButton(
            tooltip: 'Actualizar',
            onPressed: _cargar,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: 'Salir',
            onPressed: widget.alSalir,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : ListView(
                padding: const EdgeInsets.all(14),
                children: [
                  Text('Hola, ${widget.credencial.nombre}',
                      style: const TextStyle(color: Colores.textoMedio, fontSize: 13)),
                  const SizedBox(height: 12),

                  if (_sinConexion)
                    _Franja(
                      icono: Icons.cloud_off,
                      color: Colores.textoDebil,
                      texto: _diagramas.isEmpty
                          ? 'Sin conexion y sin diagramas descargados. Conectate una vez '
                              'para poder trabajar despues sin senal.'
                          : 'Sin conexion. Podes abrir los diagramas que ya descargaste y '
                              'seguir modelando: los cambios se envian cuando vuelva la red.',
                    ),
                  if (_error != null)
                    _Franja(icono: Icons.error_outline, color: Colores.peligro, texto: _error!),

                  const SizedBox(height: 6),
                  if (_diagramas.isEmpty && !_sinConexion)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 40),
                      child: Text(
                        'Todavia no hay diagramas.\nCreá uno desde la version web y aparece aqui.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: Colores.textoDebil),
                      ),
                    ),

                  for (final diagrama in _diagramas)
                    Card(
                      margin: const EdgeInsets.only(bottom: 10),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(6),
                        side: const BorderSide(color: Colores.borde),
                      ),
                      child: ListTile(
                        title: Text(diagrama.nombre,
                            style: const TextStyle(fontWeight: FontWeight.w600)),
                        subtitle: Text('version ${diagrama.version}',
                            style: const TextStyle(color: Colores.textoDebil, fontSize: 12)),
                        // La fila entera abre el backend generado: es la
                        // funcionalidad central que hay que demostrar, y el
                        // lienzo -el diagrama- queda como accion secundaria en
                        // el icono. Antes era al reves y la persona que abria
                        // la app en el telefono tocaba la fila, veia el
                        // lienzo y creia que el backend generado no estaba.
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              tooltip: 'Diagrama',
                              onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                                builder: (_) => PantallaLienzo(
                                  api: widget.api,
                                  almacen: widget.almacen,
                                  credencial: widget.credencial,
                                  sesionId: widget.sesionId,
                                  resumen: diagrama,
                                ),
                              )),
                              icon: const Icon(Icons.account_tree_outlined,
                                  color: Colores.textoDebil),
                            ),
                            const Icon(Icons.chevron_right, color: Colores.textoDebil),
                          ],
                        ),
                        onTap: () => _abrirEntidades(diagrama),
                      ),
                    ),
                ],
              ),
      ),
    );
  }
}

class _Franja extends StatelessWidget {
  const _Franja({required this.icono, required this.color, required this.texto});

  final IconData icono;
  final Color color;
  final String texto;

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 10),
        padding: const EdgeInsets.all(11),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.1),
          border: Border.all(color: color.withValues(alpha: 0.6)),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Row(children: [
          Icon(icono, size: 17, color: color),
          const SizedBox(width: 9),
          Expanded(child: Text(texto, style: TextStyle(color: color, fontSize: 12.5))),
        ]),
      );
}
