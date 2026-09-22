import 'package:flutter/material.dart';

import '../generado/api_generada.dart';
import '../generado/nombres.dart';
import '../generado/repositorio.dart';
import '../tipos.dart';
import 'registros.dart';

/// Las entidades del backend generado, sacadas del diagrama.
///
/// No hay pantalla escrita por entidad ni codigo generado en el telefono: la
/// lista sale del diagrama en tiempo de ejecucion, y por eso la misma app sirve
/// para cualquier modelo sin recompilar.
///
/// Es StatefulWidget y no StatelessWidget -a diferencia del primer borrador de
/// esta pantalla- porque tiene dos cosas que recordar mientras esta abierta: la
/// direccion del backend que se esta editando y cuantas operaciones quedan sin
/// enviar.
class PantallaEntidades extends StatefulWidget {
  const PantallaEntidades({super.key, required this.diagrama, required this.repositorio});

  final Diagrama diagrama;
  final Repositorio repositorio;

  @override
  State<PantallaEntidades> createState() => _PantallaEntidadesState();
}

class _PantallaEntidadesState extends State<PantallaEntidades> {
  late Repositorio _repositorio;
  final _direccionControlador = TextEditingController();
  int _pendientes = 0;
  bool _sincronizando = false;

  @override
  void initState() {
    super.initState();
    _repositorio = widget.repositorio;
    _cargarDireccion();
    _actualizarPendientes();
  }

  @override
  void dispose() {
    _direccionControlador.dispose();
    super.dispose();
  }

  /// La direccion guardada de una corrida anterior, si la hay. Sin ella el
  /// repositorio se queda con `api: null` y la pantalla anda igual: la
  /// restriccion central es que ninguna pantalla necesite red para abrirse.
  Future<void> _cargarDireccion() async {
    final guardada = await _repositorio.almacen.leerDireccionBackend();
    if (!mounted || guardada == null || guardada.isEmpty) return;
    _direccionControlador.text = guardada;
    setState(() {
      _repositorio = Repositorio(
        almacen: _repositorio.almacen,
        api: ApiGenerada(base: guardada),
      );
    });
  }

  Future<void> _actualizarPendientes() async {
    final cola = await _repositorio.almacen.leerCola();
    if (!mounted) return;
    setState(() => _pendientes = cola.length);
  }

  /// Se persiste y se reconstruye el repositorio en cada cambio del campo: es
  /// la forma mas simple de que "escribir la direccion" y "usarla" sean el
  /// mismo gesto, sin un boton aparte que haya que acordarse de tocar.
  Future<void> _guardarDireccion(String texto) async {
    final limpia = texto.trim();
    try {
      await _repositorio.almacen.guardarDireccionBackend(limpia.isEmpty ? null : limpia);
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('No se pudo guardar la direccion del backend.'),
      ));
      return;
    }
    if (!mounted) return;
    setState(() {
      _repositorio = Repositorio(
        almacen: _repositorio.almacen,
        api: limpia.isEmpty ? null : ApiGenerada(base: limpia),
      );
    });
  }

  Future<void> _sincronizar() async {
    if (_repositorio.api == null) {
      // Sin direccion no hay adonde mandar la cola: decirlo evita que parezca
      // que el boton no hizo nada.
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Carga la direccion del backend generado: sin ella no hay adonde sincronizar.'),
      ));
      return;
    }

    final pendientesAntes = _pendientes;
    setState(() => _sincronizando = true);

    var vaciadas = 0;
    String? error;
    try {
      vaciadas = await _repositorio.sincronizar();
    } catch (_) {
      error = 'No se pudo sincronizar: revisa la conexion y la direccion del backend.';
    }

    await _actualizarPendientes();
    if (!mounted) return;
    setState(() => _sincronizando = false);

    if (error != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error)));
    } else if (vaciadas == 0 && pendientesAntes > 0) {
      // Repositorio.sincronizar() no lanza por un backend inalcanzable: solo
      // devuelve 0. Sin este aviso, ese 0 se leeria como que no habia nada
      // que hacer en vez de que no se pudo llegar.
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('No se pudo llegar al backend generado: la cola sigue esperando.'),
      ));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.diagrama.nombre)),
        body: ListView(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
              child: TextField(
                controller: _direccionControlador,
                decoration: const InputDecoration(
                  labelText: 'Direccion del backend generado',
                  hintText: 'http://192.168.43.1:8080',
                  border: OutlineInputBorder(),
                ),
                keyboardType: TextInputType.url,
                onChanged: _guardarDireccion,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      // Sin esto no hay forma de ver el momento en que lo
                      // cargado sin senal llega al backend.
                      _pendientes == 0
                          ? 'Sin operaciones pendientes'
                          : '$_pendientes operacion(es) esperando',
                    ),
                  ),
                  TextButton.icon(
                    onPressed: _sincronizando ? null : _sincronizar,
                    icon: _sincronizando
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.sync),
                    label: const Text('Sincronizar'),
                  ),
                ],
              ),
            ),
            const Divider(height: 1),
            for (final clase in widget.diagrama.clases)
              ListTile(
                title: Text(clase.nombre),
                subtitle: Text('/api/${rutaDe(clase.nombre)}'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => PantallaRegistros(clase: clase, repositorio: _repositorio),
                )),
              ),
          ],
        ),
      );
}
