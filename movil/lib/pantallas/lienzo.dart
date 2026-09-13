import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../almacen.dart';
import '../api.dart';
import '../comandos.dart';
import '../lienzo/pintor.dart';
import '../main.dart';
import '../sincronizador.dart';
import '../tipos.dart';

/// El lienzo en el telefono.
///
/// Todo cambio pasa por el sincronizador: se aplica en el momento sobre la copia
/// local y se encola. La pantalla nunca espera al servidor, que es lo que permite
/// modelar en un aula sin senal.
///
/// A diferencia de la web, aqui no se pide el bloqueo antes de arrastrar. No es
/// una omision: sin conexion no hay a quien pedirselo, y el servidor ya toma y
/// suelta el bloqueo por su cuenta al registrar el cambio. Lo que se pierde es
/// que los demas vean "esta editando" durante el arrastre; lo que se gana es que
/// arrastrar funcione con el telefono en modo avion.
class PantallaLienzo extends StatefulWidget {
  const PantallaLienzo({
    super.key,
    required this.api,
    required this.almacen,
    required this.credencial,
    required this.sesionId,
    required this.resumen,
  });

  final Api api;
  final Almacen almacen;
  final Credencial credencial;
  final String sesionId;
  final ResumenDiagrama resumen;

  @override
  State<PantallaLienzo> createState() => _PantallaLienzoState();
}

class _PantallaLienzoState extends State<PantallaLienzo> {
  late final Sincronizador _sincronizador;
  final SpeechToText _voz = SpeechToText();

  Offset _desplazamiento = const Offset(20, 20);
  double _escala = 1;
  int _revision = 0;

  String? _seleccionada;
  String? _primerExtremo;
  String? _tipoDeRelacion;

  /// Clase que se esta arrastrando, con el desfase del dedo respecto de su
  /// esquina: sin el, la caja salta para que su esquina quede bajo el dedo.
  String? _arrastrando;
  Offset _desfase = Offset.zero;

  @override
  void initState() {
    super.initState();
    _sincronizador = Sincronizador(
      api: widget.api,
      almacen: widget.almacen,
      sesionId: widget.sesionId,
    )..addListener(_alCambiar);
    _sincronizador.abrir(widget.resumen.id);
  }

  @override
  void dispose() {
    _sincronizador.removeListener(_alCambiar);
    _sincronizador.dispose();
    super.dispose();
  }

  void _alCambiar() {
    if (mounted) setState(() => _revision++);
  }

  Diagrama? get _diagrama => _sincronizador.diagrama;

  Future<void> _ejecutar(Comando comando) async {
    await _sincronizador.ejecutar(comando);
    if (mounted) setState(() => _revision++);
  }

  // ---------- Coordenadas y toque ------------------------------------------

  Offset _aCoordenadasDelModelo(Offset enPantalla) =>
      (enPantalla - _desplazamiento) / _escala;

  Clase? _claseEn(Offset enPantalla) {
    final diagrama = _diagrama;
    if (diagrama == null) return null;
    final punto = _aCoordenadasDelModelo(enPantalla);
    // Se recorre al revés: la ultima dibujada es la que esta arriba.
    for (final clase in diagrama.clases.reversed) {
      final caja = Rect.fromLTWH(clase.posX, clase.posY, Clase.ancho, clase.alto);
      if (caja.contains(punto)) return clase;
    }
    return null;
  }

  void _tocar(Offset enPantalla) {
    final clase = _claseEn(enPantalla);

    if (_tipoDeRelacion != null) {
      if (clase == null) return;
      if (_primerExtremo == null) {
        setState(() => _primerExtremo = clase.id);
        return;
      }
      _crearRelacion(_primerExtremo!, clase.id);
      return;
    }

    setState(() => _seleccionada = clase?.id);
    if (clase != null) _abrirHojaDeClase(clase);
  }

  // ---------- Acciones del modelo -----------------------------------------

  Future<void> _nuevaClase() async {
    final nombre = await _pedirTexto('Nueva clase', 'Nombre', 'Paciente');
    if (nombre == null || nombre.trim().isEmpty) return;

    final cuantas = _diagrama?.clases.length ?? 0;
    await _ejecutar(Comando.crearClase(
      claseId: Sincronizador.nuevoToken(),
      nombre: nombre.trim(),
      posX: (cuantas % 3) * 230,
      posY: (cuantas ~/ 3) * 190,
      token: Sincronizador.nuevoToken(),
    ));
  }

  Future<void> _crearRelacion(String origenId, String destinoId) async {
    final tipo = _tipoDeRelacion!;
    setState(() {
      _tipoDeRelacion = null;
      _primerExtremo = null;
    });

    final esJerarquia = tipo == 'HERENCIA' || tipo == 'REALIZACION';
    await _ejecutar(Comando.crearRelacion(
      relacionId: Sincronizador.nuevoToken(),
      origenId: origenId,
      destinoId: destinoId,
      tipoDeRelacion: tipo,
      multiplicidadOrigen: '1',
      multiplicidadDestino: esJerarquia ? '1' : '0..*',
      token: Sincronizador.nuevoToken(),
    ));
  }

  // ---------- Dictado ------------------------------------------------------

  Future<void> _dictar() async {
    final disponible = await _voz.initialize(
      onError: (error) => _avisar('No se pudo escuchar: ${error.errorMsg}'),
    );
    if (!disponible) {
      _avisar('Este telefono no tiene reconocimiento de voz disponible');
      return;
    }

    if (!mounted) return;
    final frase = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colores.superficie,
      builder: (_) => _HojaDeDictado(voz: _voz),
    );
    await _voz.stop();
    if (frase == null || frase.trim().isEmpty) return;

    // El dictado lo interpreta el servidor: la gramatica vive alli para que el
    // telefono y la web entiendan exactamente lo mismo. Sin conexion todavia no
    // funciona, y se dice en lugar de fallar en silencio.
    try {
      final resultado = await widget.api.dictar(widget.resumen.id, frase, widget.sesionId);
      if (resultado['entendida'] == true) {
        _avisar(resultado['explicacion'] as String? ?? 'Aplicado');
        await _sincronizador.sincronizar();
      } else {
        final sugerencias = (resultado['sugerencias'] as List<dynamic>? ?? []).take(2).join(' · ');
        _avisar('No entendi. Proba: $sugerencias');
      }
    } on SinConexion {
      _avisar('El dictado necesita conexion por ahora. Lo que ya hiciste se '
          'guarda igual y se envia cuando vuelva la red.');
    } on ErrorApi catch (e) {
      _avisar(e.mensaje);
    }
  }

  // ---------- Interfaz ------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final diagrama = _diagrama;

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.resumen.nombre, style: const TextStyle(fontSize: 16)),
        actions: [
          _IndicadorDeSincronizacion(sincronizador: _sincronizador),
          IconButton(
            tooltip: 'Sincronizar',
            onPressed: () => _sincronizador.sincronizar(),
            icon: const Icon(Icons.sync),
          ),
        ],
      ),
      body: diagrama == null
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(28),
                child: Text(
                  'Este diagrama no esta descargado y no hay conexion.\n'
                  'Conectate una vez para poder abrirlo sin senal.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colores.textoDebil),
                ),
              ),
            )
          : Column(children: [
              if (_sincronizador.rechazados.isNotEmpty) _franjaDeRechazos(),
              if (_sincronizador.aviso != null)
                _Franja(
                  texto: _sincronizador.aviso!,
                  color: Colores.ambar,
                  icono: Icons.info_outline,
                ),
              if (_tipoDeRelacion != null)
                _Franja(
                  texto: _primerExtremo == null
                      ? 'Toca la primera clase de la ${_tipoDeRelacion!.toLowerCase()}'
                      : 'Ahora toca la segunda clase',
                  color: Colores.ambar,
                  icono: Icons.timeline,
                ),
              Expanded(child: _lienzo(diagrama)),
            ]),
      bottomNavigationBar: diagrama == null ? null : _barraDeAcciones(),
    );
  }

  Widget _lienzo(Diagrama diagrama) {
    return ClipRect(
      child: Container(
        color: Colores.lienzo,
        child: GestureDetector(
          onTapUp: (detalle) => _tocar(detalle.localPosition),
          onScaleStart: (detalle) {
            final clase = _claseEn(detalle.localFocalPoint);
            // Con un dedo sobre una clase se arrastra la clase; en cualquier otro
            // caso se mueve la vista. Con dos dedos siempre es la vista.
            if (detalle.pointerCount == 1 && clase != null && _tipoDeRelacion == null) {
              _arrastrando = clase.id;
              _desfase = _aCoordenadasDelModelo(detalle.localFocalPoint) -
                  Offset(clase.posX, clase.posY);
            } else {
              _arrastrando = null;
            }
          },
          onScaleUpdate: (detalle) {
            setState(() {
              final arrastrando = _arrastrando;
              if (arrastrando != null && detalle.pointerCount == 1) {
                final clase = diagrama.clasePorId(arrastrando);
                if (clase != null) {
                  final punto = _aCoordenadasDelModelo(detalle.localFocalPoint) - _desfase;
                  clase.posX = punto.dx;
                  clase.posY = punto.dy;
                  _revision++;
                }
                return;
              }
              if (detalle.scale != 1.0) {
                _escala = (_escala * detalle.scale).clamp(0.3, 2.5);
              }
              _desplazamiento += detalle.focalPointDelta;
            });
          },
          onScaleEnd: (_) {
            final arrastrando = _arrastrando;
            _arrastrando = null;
            if (arrastrando == null) return;

            final clase = diagrama.clasePorId(arrastrando);
            if (clase == null) return;
            // Se manda una sola operacion al soltar, no una por cada movimiento:
            // de un arrastre interesa donde quedo la clase, no el recorrido.
            _ejecutar(Comando.moverClase(
              claseId: clase.id,
              posX: clase.posX.roundToDouble(),
              posY: clase.posY.roundToDouble(),
              token: Sincronizador.nuevoToken(),
            ));
          },
          child: CustomPaint(
            size: Size.infinite,
            painter: PintorDeDiagrama(
              diagrama: diagrama,
              desplazamiento: _desplazamiento,
              escala: _escala,
              usuarioId: widget.credencial.usuarioId,
              seleccionada: _seleccionada,
              senalada: _primerExtremo,
              revision: _revision,
            ),
          ),
        ),
      ),
    );
  }

  Widget _barraDeAcciones() => BottomAppBar(
        color: Colores.superficie,
        height: 62,
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
          _Accion(icono: Icons.add_box_outlined, etiqueta: 'Clase', alTocar: _nuevaClase),
          _Accion(icono: Icons.mic_none, etiqueta: 'Dictar', alTocar: _dictar),
          _Accion(
            icono: Icons.timeline,
            etiqueta: 'Relacion',
            activo: _tipoDeRelacion != null,
            alTocar: _elegirTipoDeRelacion,
          ),
          _Accion(
            icono: Icons.center_focus_weak,
            etiqueta: 'Centrar',
            alTocar: () => setState(() {
              _desplazamiento = const Offset(20, 20);
              _escala = 1;
            }),
          ),
        ]),
      );

  Widget _franjaDeRechazos() => Container(
        width: double.infinity,
        color: Colores.peligro.withValues(alpha: 0.14),
        padding: const EdgeInsets.all(10),
        child: Row(children: [
          const Icon(Icons.warning_amber, size: 17, color: Colores.peligro),
          const SizedBox(width: 8),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('El servidor rechazo cambios que habias hecho:',
                  style: TextStyle(color: Colores.peligro, fontSize: 12.5)),
              for (final rechazo in _sincronizador.rechazados)
                Text('· $rechazo',
                    style: const TextStyle(color: Colores.peligro, fontSize: 11.5)),
            ]),
          ),
          IconButton(
            onPressed: _sincronizador.olvidarRechazados,
            icon: const Icon(Icons.close, size: 17, color: Colores.peligro),
          ),
        ]),
      );

  // ---------- Hojas --------------------------------------------------------

  Future<void> _elegirTipoDeRelacion() async {
    const tipos = ['ASOCIACION', 'AGREGACION', 'COMPOSICION', 'HERENCIA', 'REALIZACION',
      'DEPENDENCIA'];
    final elegido = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colores.superficie,
      builder: (_) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Padding(
            padding: EdgeInsets.all(14),
            child: Text('Que relacion vas a trazar',
                style: TextStyle(fontWeight: FontWeight.w600)),
          ),
          for (final tipo in tipos)
            ListTile(
              dense: true,
              title: Text(tipo[0] + tipo.substring(1).toLowerCase()),
              onTap: () => Navigator.pop(context, tipo),
            ),
        ]),
      ),
    );
    if (elegido != null) {
      setState(() {
        _tipoDeRelacion = elegido;
        _primerExtremo = null;
      });
    }
  }

  Future<void> _abrirHojaDeClase(Clase clase) async {
    final bloqueo = _diagrama?.bloqueoDe(clase.id);
    final ajeno = bloqueo != null && bloqueo.poseedorId != widget.credencial.usuarioId;

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: Colores.superficie,
      isScrollControlled: true,
      builder: (contexto) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Expanded(
                  child: Text(clase.nombre,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                ),
                if (clase.esAbstracta) const _Insignia(texto: 'abstracta'),
                if (clase.estereotipo != null) _Insignia(texto: '«${clase.estereotipo}»'),
              ]),
              const SizedBox(height: 4),
              Text('${clase.atributos.length} atributos · ${clase.metodos.length} operaciones',
                  style: const TextStyle(color: Colores.textoDebil, fontSize: 12)),

              if (ajeno) ...[
                const SizedBox(height: 12),
                _Franja(
                  texto: '${bloqueo.poseedorNombre} la esta editando. Tus cambios pueden '
                      'ser rechazados.',
                  color: Colores.ajeno,
                  icono: Icons.lock_outline,
                ),
              ],

              const SizedBox(height: 14),
              Wrap(spacing: 8, runSpacing: 8, children: [
                OutlinedButton.icon(
                  icon: const Icon(Icons.edit, size: 16),
                  label: const Text('Renombrar'),
                  onPressed: () async {
                    Navigator.pop(contexto);
                    final nombre = await _pedirTexto('Renombrar', 'Nombre', clase.nombre);
                    if (nombre != null && nombre.trim().isNotEmpty) {
                      await _ejecutar(Comando.renombrarClase(
                          claseId: clase.id,
                          nombre: nombre.trim(),
                          token: Sincronizador.nuevoToken()));
                    }
                  },
                ),
                OutlinedButton.icon(
                  icon: Icon(clase.esAbstracta ? Icons.check_box : Icons.check_box_outline_blank,
                      size: 16),
                  label: const Text('Abstracta'),
                  onPressed: () async {
                    Navigator.pop(contexto);
                    await _ejecutar(Comando.marcarClase(
                        claseId: clase.id,
                        estereotipo: clase.estereotipo,
                        esAbstracta: !clase.esAbstracta,
                        token: Sincronizador.nuevoToken()));
                  },
                ),
                OutlinedButton.icon(
                  icon: const Icon(Icons.playlist_add, size: 16),
                  label: const Text('Atributo'),
                  onPressed: () async {
                    Navigator.pop(contexto);
                    await _agregarAtributo(clase);
                  },
                ),
                OutlinedButton.icon(
                  icon: const Icon(Icons.functions, size: 16),
                  label: const Text('Operacion'),
                  onPressed: () async {
                    Navigator.pop(contexto);
                    final nombre = await _pedirTexto('Nueva operacion', 'Nombre', 'calcularEdad');
                    if (nombre != null && nombre.trim().isNotEmpty) {
                      await _ejecutar(Comando.agregarMetodo(
                          claseId: clase.id,
                          metodoId: Sincronizador.nuevoToken(),
                          nombre: nombre.trim(),
                          token: Sincronizador.nuevoToken()));
                    }
                  },
                ),
                OutlinedButton.icon(
                  icon: const Icon(Icons.delete_outline, size: 16, color: Colores.peligro),
                  label: const Text('Eliminar', style: TextStyle(color: Colores.peligro)),
                  onPressed: () async {
                    Navigator.pop(contexto);
                    await _ejecutar(Comando.eliminarClase(
                        claseId: clase.id, token: Sincronizador.nuevoToken()));
                    setState(() => _seleccionada = null);
                  },
                ),
              ]),

              if (clase.atributos.isNotEmpty) ...[
                const SizedBox(height: 16),
                const Text('ATRIBUTOS',
                    style: TextStyle(color: Colores.textoDebil, fontSize: 11, letterSpacing: 1)),
                for (final atributo in clase.atributos)
                  ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(atributo.comoSeLee,
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                    trailing: IconButton(
                      icon: const Icon(Icons.close, size: 16, color: Colores.textoDebil),
                      onPressed: () async {
                        Navigator.pop(contexto);
                        await _ejecutar(Comando.eliminarAtributo(
                            claseId: clase.id,
                            atributoId: atributo.id,
                            token: Sincronizador.nuevoToken()));
                      },
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _agregarAtributo(Clase clase) async {
    final nombre = TextEditingController();
    final tipo = TextEditingController(text: 'String');
    var esClave = false;

    final confirmado = await showDialog<bool>(
      context: context,
      builder: (contexto) => StatefulBuilder(
        builder: (contexto, refrescar) => AlertDialog(
          backgroundColor: Colores.superficie,
          title: const Text('Nuevo atributo', style: TextStyle(fontSize: 17)),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(
              controller: nombre,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Nombre'),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: tipo,
              decoration: const InputDecoration(labelText: 'Tipo'),
            ),
            CheckboxListTile(
              dense: true,
              contentPadding: EdgeInsets.zero,
              value: esClave,
              onChanged: (valor) => refrescar(() => esClave = valor ?? false),
              title: const Text('Es la clave', style: TextStyle(fontSize: 13)),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(contexto, false), child: const Text('Cancelar')),
            FilledButton(onPressed: () => Navigator.pop(contexto, true), child: const Text('Agregar')),
          ],
        ),
      ),
    );

    if (confirmado == true && nombre.text.trim().isNotEmpty) {
      await _ejecutar(Comando.agregarAtributo(
        claseId: clase.id,
        atributoId: Sincronizador.nuevoToken(),
        nombre: nombre.text.trim(),
        tipo: tipo.text.trim().isEmpty ? 'String' : tipo.text.trim(),
        esIdentificador: esClave,
        esRequerido: esClave,
        esUnico: esClave,
        token: Sincronizador.nuevoToken(),
      ));
    }
    nombre.dispose();
    tipo.dispose();
  }

  Future<String?> _pedirTexto(String titulo, String etiqueta, String ejemplo) async {
    final control = TextEditingController();
    final respuesta = await showDialog<String>(
      context: context,
      builder: (contexto) => AlertDialog(
        backgroundColor: Colores.superficie,
        title: Text(titulo, style: const TextStyle(fontSize: 17)),
        content: TextField(
          controller: control,
          autofocus: true,
          decoration: InputDecoration(labelText: etiqueta, hintText: ejemplo),
          onSubmitted: (valor) => Navigator.pop(contexto, valor),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(contexto), child: const Text('Cancelar')),
          FilledButton(
            onPressed: () => Navigator.pop(contexto, control.text),
            child: const Text('Listo'),
          ),
        ],
      ),
    );
    control.dispose();
    return respuesta;
  }

  void _avisar(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(texto), duration: const Duration(seconds: 4)),
    );
  }
}

// ---------- Piezas de la interfaz -----------------------------------------

class _IndicadorDeSincronizacion extends StatelessWidget {
  const _IndicadorDeSincronizacion({required this.sincronizador});

  final Sincronizador sincronizador;

  @override
  Widget build(BuildContext context) {
    final (icono, color, detalle) = switch (sincronizador.estado) {
      EstadoDeSincronizacion.alDia => (Icons.cloud_done, Colores.exito, 'Al dia'),
      EstadoDeSincronizacion.enviando => (Icons.cloud_upload, Colores.ambar, 'Enviando'),
      EstadoDeSincronizacion.pendiente =>
        (Icons.cloud_queue, Colores.ambar, '${sincronizador.cuantosPendientes} sin enviar'),
      EstadoDeSincronizacion.sinConexion =>
        (Icons.cloud_off, Colores.textoDebil, sincronizador.cuantosPendientes > 0
            ? '${sincronizador.cuantosPendientes} sin enviar'
            : 'Sin conexion'),
    };

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Row(children: [
        Icon(icono, size: 17, color: color),
        const SizedBox(width: 5),
        Text(detalle, style: TextStyle(color: color, fontSize: 11.5)),
      ]),
    );
  }
}

class _Accion extends StatelessWidget {
  const _Accion({
    required this.icono,
    required this.etiqueta,
    required this.alTocar,
    this.activo = false,
  });

  final IconData icono;
  final String etiqueta;
  final VoidCallback alTocar;
  final bool activo;

  @override
  Widget build(BuildContext context) {
    final color = activo ? Colores.ambar : Colores.textoMedio;
    return InkWell(
      onTap: alTocar,
      borderRadius: BorderRadius.circular(6),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(icono, size: 21, color: color),
          const SizedBox(height: 2),
          Text(etiqueta, style: TextStyle(fontSize: 10.5, color: color)),
        ]),
      ),
    );
  }
}

class _Franja extends StatelessWidget {
  const _Franja({required this.texto, required this.color, required this.icono});

  final String texto;
  final Color color;
  final IconData icono;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        color: color.withValues(alpha: 0.12),
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
        child: Row(children: [
          Icon(icono, size: 16, color: color),
          const SizedBox(width: 8),
          Expanded(child: Text(texto, style: TextStyle(color: color, fontSize: 12))),
        ]),
      );
}

class _Insignia extends StatelessWidget {
  const _Insignia({required this.texto});

  final String texto;

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(left: 6),
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(
          color: Colores.ambar.withValues(alpha: 0.14),
          border: Border.all(color: Colores.ambar.withValues(alpha: 0.4)),
          borderRadius: BorderRadius.circular(3),
        ),
        child: Text(texto, style: const TextStyle(fontSize: 10, color: Colores.ambarClaro)),
      );
}

/// Hoja de dictado: escucha y devuelve el texto reconocido.
///
/// El reconocimiento lo hace Android, que puede funcionar sin conexion **si el
/// paquete de idioma esta descargado en el telefono**. Es una condicion del
/// sistema, no de la aplicacion, y conviene comprobarla antes de la defensa.
class _HojaDeDictado extends StatefulWidget {
  const _HojaDeDictado({required this.voz});

  final SpeechToText voz;

  @override
  State<_HojaDeDictado> createState() => _HojaDeDictadoState();
}

class _HojaDeDictadoState extends State<_HojaDeDictado> {
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
        // mas con la variante correcta que con el castellano de Espana.
        localeId: 'es_419',
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
