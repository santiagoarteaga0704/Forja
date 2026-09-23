import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../generado/api_generada.dart';
import '../generado/asistente.dart';
import '../generado/nombres.dart';
import '../generado/almacen_registros.dart';
import '../generado/repositorio.dart';
import '../main.dart';
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
  const PantallaEntidades({
    super.key,
    required this.diagrama,
    required this.repositorio,
    this.asistente,
  });

  final Diagrama diagrama;
  final Repositorio repositorio;

  /// Va de paso hacia la pantalla de registros, que es donde se dicta.
  ///
  /// Viaja como ValueListenable y no como valor porque el MaterialPageRoute que
  /// abre la pantalla de registros construye su pagina una sola vez: con el
  /// valor, un modelo que termina de cargar despues no llegaria nunca.
  final ValueListenable<Asistente?>? asistente;

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

    setState(() => _sincronizando = true);

    ResultadoDeSincronizacion? resultado;
    String? error;
    try {
      resultado = await _repositorio.sincronizar();
    } catch (_) {
      error = 'No se pudo sincronizar: revisa la conexion y la direccion del backend.';
    }

    await _actualizarPendientes();
    if (!mounted) return;
    setState(() => _sincronizando = false);

    // Repositorio.sincronizar() no lanza: cuenta lo que paso. Sin estos avisos,
    // una pasada que no movio nada se leeria como que no habia nada que hacer.
    final avisos = <String>[
      ?error,
      // Lo descartado va primero porque es lo unico irrecuperable: son datos
      // que alguien cargo y que el backend no va a aceptar nunca. Se nombran
      // uno por uno para que se sepa cual hay que volver a cargar a mano.
      if (resultado != null && resultado.rechazadas.isNotEmpty)
        'El backend generado rechazo y se descarto: ${resultado.rechazadas.join('; ')}.',
      ?resultado?.corte,
    ];

    if (avisos.isNotEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(avisos.join(' '))));
    }
  }

  /// La salida de emergencia de la cola.
  ///
  /// Sin esto, una sola operacion que el backend generado conteste con 500 -un
  /// campo obligatorio vacio, por ejemplo- deja la app trabada para siempre: el
  /// 500 se trata como error temporal, asi que la operacion se conserva, la
  /// pasada se corta ahi, y todo lo que viene detras no sale nunca. No habia
  /// ninguna forma de sacarla desde el telefono.
  ///
  /// Se confirma antes de borrar porque esto es perdida de datos de verdad, y
  /// se dice cuantas son y cuales: tirar a ciegas es peor que quedarse trabado.
  Future<void> _descartarPendientes() async {
    final cola = await _repositorio.almacen.leerCola();
    if (!mounted) return;
    if (cola.isEmpty) {
      await _actualizarPendientes();
      return;
    }

    final confirmado = await showDialog<bool>(
          context: context,
          builder: (dialogo) => AlertDialog(
            title: Text(cola.length == 1
                ? 'Descartar 1 operación'
                : 'Descartar ${cola.length} operaciones'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Se borran del teléfono y no llegan nunca al backend generado. '
                  'No se puede deshacer.',
                ),
                const SizedBox(height: 12),
                for (final operacion in cola.take(_aLoSumo))
                  Text('• ${_describir(operacion)}',
                      style: const TextStyle(color: Colores.textoMedio, fontSize: 13)),
                if (cola.length > _aLoSumo)
                  Text('y ${cola.length - _aLoSumo} más.',
                      style: const TextStyle(color: Colores.textoMedio, fontSize: 13)),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogo).pop(false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(dialogo).pop(true),
                child: const Text('Descartar'),
              ),
            ],
          ),
        ) ??
        // Cerrar el cartel tocando afuera es no confirmar.
        false;
    if (!confirmado) return;

    // Las filas locales de esas operaciones se van con ellas: quedaron
    // guardadas con la marca '_pendiente' y sin la operacion detras no las
    // puede confirmar nadie. Dejarlas seria mostrar en ambar, para siempre,
    // filas que ya nadie va a enviar.
    //
    // Primero las filas y despues la cola: si algo falla a mitad de camino,
    // la cola intacta sigue siendo la verdad y se puede volver a intentar.
    final descartadas = cola.map((o) => o.id).toSet();
    for (final clase in cola.map((o) => o.clase).toSet()) {
      final filas = await _repositorio.almacen.leerFilas(clase);
      final quedan = filas.where((f) => !descartadas.contains(f['_pendiente'])).toList();
      if (quedan.length != filas.length) {
        await _repositorio.almacen.guardarFilas(clase, quedan);
      }
    }
    await _repositorio.almacen.reemplazarCola([]);

    await _actualizarPendientes();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(cola.length == 1
          ? 'Se descartó 1 operación pendiente.'
          : 'Se descartaron ${cola.length} operaciones pendientes.'),
    ));
  }

  /// Cuantas operaciones se nombran en el cartel antes de resumir el resto.
  static const _aLoSumo = 5;

  /// Como se nombra una operacion para que se entienda cual es. Mismo criterio
  /// que `Repositorio._describir`: el verbo, la clase y, si lo hay, el nombre,
  /// que es el campo por el que una persona reconoce su fila.
  static String _describir(OperacionPendiente operacion) {
    final nombre = operacion.datos['nombre'];
    return nombre is String && nombre.isNotEmpty
        ? '${operacion.verbo} ${operacion.clase} ($nombre)'
        : '${operacion.verbo} ${operacion.clase}';
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
                  hintText: 'http://192.168.43.1:8081',
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
                  // Solo aparece si hay algo que descartar: es una salida de
                  // emergencia, no un boton de todos los dias.
                  if (_pendientes > 0)
                    IconButton(
                      tooltip: 'Descartar lo pendiente',
                      onPressed: _sincronizando ? null : _descartarPendientes,
                      icon: const Icon(Icons.delete_outline, color: Colores.peligro),
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
                // Se espera el retorno y se recuenta: cargar registros sin
                // senal encola operaciones, y el contador solo se leia en
                // initState y despues de sincronizar. Al volver decia "Sin
                // operaciones pendientes" con la cola llena, y se corregia
                // recien al tocar Sincronizar, que es justo el paso siguiente
                // al que hay que demostrar.
                onTap: () async {
                  await Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => PantallaRegistros(
                      clase: clase,
                      repositorio: _repositorio,
                      diagrama: widget.diagrama,
                      asistente: widget.asistente,
                    ),
                  ));
                  await _actualizarPendientes();
                },
              ),
          ],
        ),
      );
}
