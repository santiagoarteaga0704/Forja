import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../generado/asistente.dart';
import '../generado/dictado_de_campos.dart';
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
    this.asistente,
  });

  final Clase clase;
  final Repositorio repositorio;

  // El diagrama entero, no solo la clase: interpretarPedidoDeRegistro necesita
  // buscar en todas las clases y sus campos, porque lo dictado puede nombrar
  // cualquier entidad, no solo la que esta abierta.
  final Diagrama diagrama;

  /// El modelo en el aparato, si esta y cuando este.
  ///
  /// Es un ValueListenable y no un Asistente pelado por una razon concreta:
  /// MaterialPageRoute construye su pagina UNA sola vez y la cachea, asi que un
  /// setState de un ancestro del Navigator no la vuelve a construir. Como el
  /// modelo son 529 MB y tarda mas en cargar de lo que tarda alguien en entrar
  /// a una entidad, pasando el valor esta pantalla se quedaria en null para
  /// siempre, sin ninguna senal y sin mas rodeo que salir y volver a entrar.
  /// Escuchando, el asistente llega aunque la pantalla ya este abierta.
  ///
  /// Sigue siendo opcional: la pantalla entera funciona sin modelo, solo que un
  /// pedido que la gramatica no entiende se queda sin segunda oportunidad.
  final ValueListenable<Asistente?>? asistente;

  @override
  State<PantallaRegistros> createState() => _PantallaRegistrosState();
}

class _PantallaRegistrosState extends State<PantallaRegistros> {
  late Future<List<Map<String, dynamic>>> _filas;

  /// Una caja de texto por campo, y la caja es la unica fuente de verdad.
  ///
  /// Antes lo dictado se guardaba en un mapa aparte y se creaba el registro de
  /// una: el usuario nunca veia lo que se habia entendido. Ahora el dictado
  /// escribe ACA, en la caja, que es lo mismo que hace el teclado: lo dictado
  /// se lee, se corrige y recien despues se da de alta.
  final _controladores = <String, TextEditingController>{};

  final SpeechToText _voz = SpeechToText();

  /// Un dictado por vez. Sin esto, dos toques al microfono abren dos chats
  /// sobre el mismo modelo en paralelo, que es la forma mas rapida de quedarse
  /// sin memoria en un telefono que ya tiene medio giga de pesos cargados.
  bool _dictando = false;

  /// Solo mientras se consulta al modelo. El microfono se convierte en una
  /// rueda: son varios segundos de pantalla quieta y sin esto parece colgada.
  bool _pensando = false;

  /// Lo que se escucha cuando no hay nada que escuchar.
  late final ValueListenable<Asistente?> _asistente = widget.asistente ?? sinAsistente;

  @override
  void initState() {
    super.initState();
    _filas = widget.repositorio.filas(widget.clase.nombre);
    for (final atributo in _editables) {
      _controladores[atributo.nombre] = TextEditingController();
    }
  }

  @override
  void dispose() {
    for (final controlador in _controladores.values) {
      controlador.dispose();
    }
    super.dispose();
  }

  /// Los identificadores no se piden: los pone el backend generado.
  List<Atributo> get _editables =>
      widget.clase.atributos.where((a) => !a.esIdentificador).toList();

  /// Lo que hay escrito ahora en la ficha, sin espacios de sobra.
  Map<String, String> get _valores => {
        for (final atributo in _editables)
          atributo.nombre: (_controladores[atributo.nombre]?.text ?? '').trim(),
      };

  TextInputType _tecladoDe(Atributo atributo) => switch (claseDeCampoDe(atributo.tipo)) {
        ClaseDeCampo.entero => TextInputType.number,
        ClaseDeCampo.decimal => const TextInputType.numberWithOptions(decimal: true),
        ClaseDeCampo.fecha || ClaseDeCampo.fechaHora => TextInputType.datetime,
        _ => TextInputType.text,
      };

  /// Crea el registro, pero solo si esta entero.
  ///
  /// El backend generado emite las columnas con `nullable = false`, asi que un
  /// campo que falta no es un registro incompleto: es un 500. Y un 500 se
  /// trata como error temporal, o sea que la operacion se queda en la cola y
  /// se reintenta para siempre. Un solo alta a medio llenar deja la cola
  /// trabada sin salida. Por eso la validacion vive aca, antes de encolar
  /// nada, y no se confia en que el servidor avise.
  ///
  /// Se exigen TODOS los campos editables y no solo los marcados `esRequerido`
  /// en el diagrama: se probo contra el backend generado y rechaza cualquier
  /// campo faltante, tenga o no la marca. El diagrama miente por omision y lo
  /// que manda es lo que el servidor hace.
  Future<void> _guardar() async {
    if (_sinAtributos) return;

    final valores = _valores;
    final faltan = _editables
        .map((a) => a.nombre)
        .where((nombre) => (valores[nombre] ?? '').isEmpty)
        .toList();

    if (faltan.isNotEmpty) {
      _avisar(faltan.length == 1
          ? 'Falta ${faltan.single}: el backend generado no acepta el registro sin ese campo.'
          : 'Faltan ${faltan.join(', ')}: el backend generado no acepta el registro con campos vacíos.');
      return;
    }

    await widget.repositorio.crear(widget.clase.nombre, valores);
    for (final controlador in _controladores.values) {
      controlador.clear();
    }
    if (!mounted) return;
    setState(() => _filas = widget.repositorio.filas(widget.clase.nombre));
  }

  // ---------- Dictado --------------------------------------------------

  /// Dicta UN campo de la ficha, no un registro entero.
  ///
  /// Asi lo dicto el usuario en el telefono y asi es como funciona: un toque
  /// de microfono, un valor, y el valor aparece en la caja. «agregar» es lo
  /// que crea. El camino anterior -una frase, un registro creado de una- no
  /// podia funcionar: la gramatica llena un solo campo y Paciente tiene tres
  /// obligatorios, asi que cada dictado creaba un registro que el backend
  /// rechazaba con 500.
  ///
  /// Usa el mismo cuadro de dictado que el lienzo -HojaDeDictado, en
  /// voz/hoja_de_dictado.dart- para que haya un solo lugar donde arreglar como
  /// se escucha, no dos que se van separando con el tiempo.
  Future<void> _dictar() async {
    if (_dictando) return;
    setState(() => _dictando = true);
    try {
      await _dictarUnaVez();
    } finally {
      if (mounted) setState(() => _dictando = false);
    }
  }

  Future<void> _dictarUnaVez() async {
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
      builder: (_) => HojaDeDictado(
        voz: _voz,
        idioma: idioma,
        ejemplo: _ejemploDeDictado,
      ),
    );
    await _voz.stop();
    if (frase == null || frase.trim().isEmpty) return;

    // Se ubica aca, en el aparato: sin esto dictar necesitaria red, y la
    // restriccion del proyecto es que ninguna pantalla la necesite para
    // abrirse ni para operar sin conexion.
    var dictado = ubicarDictado(
      frase: frase,
      campos: _editables,
      valores: _valores,
    );

    // El modelo es el segundo escalon y solo el segundo: entra cuando las
    // reglas no supieron donde poner lo dictado. Propone una frase canonica
    // que vuelve a pasar por las mismas reglas, asi que no puede nombrar un
    // campo que no este en el diagrama ni escribir en la base por su cuenta.
    if (dictado.vacio) dictado = await _ubicarConElModelo(frase);

    if (!mounted) return;
    if (dictado.esAlta) {
      await _guardar();
      return;
    }
    if (dictado.campos.isEmpty) {
      _avisar('No entendí dónde poner "$frase". Nombrá el campo, o escribilo con el teclado.');
      return;
    }

    // Se escribe en la ficha y nada mas: lo dictado se ve, se puede corregir
    // con el teclado, y no sale del telefono hasta que alguien toque
    // «Agregar». Es el freno que faltaba.
    setState(() {
      for (final campo in dictado.campos) {
        _controladores[campo.campo]?.text = campo.valor;
      }
    });
    _avisar('Escrito en ${dictado.campos.map((c) => c.campo).join(', ')}. '
        'Revisá y toca Agregar.');
  }

  Future<DictadoDeFormulario> _ubicarConElModelo(String frase) async {
    // El valor se lee ACA y no se guarda en initState: si el modelo termino de
    // cargar despues de abrir esta pantalla, este es el momento en que ya esta.
    final ayuda = _asistente.value;
    if (ayuda == null) return const DictadoDeFormulario.nada();

    String? propuesta;
    if (mounted) setState(() => _pensando = true);
    try {
      propuesta = await ayuda.fraseCanonica(
        frase,
        entidades: [widget.clase.nombre],
      );
    } finally {
      // En el finally para que un modelo que explota tampoco deje el aviso
      // prendido para siempre.
      if (mounted) setState(() => _pensando = false);
    }

    if (propuesta == null) return const DictadoDeFormulario.nada();
    return ubicarDictado(frase: propuesta, campos: _editables, valores: _valores);
  }

  /// El ejemplo se arma con los campos de ESTA clase: un ejemplo generico
  /// -«agrega un paciente llamado Juan»- ensena a dictar lo que ya no se puede
  /// dictar, y es lo que llevo a las frases enteras metidas en un solo campo.
  String get _ejemploDeDictado {
    final campos = _editables;
    if (campos.isEmpty) return 'Toca el microfono y dicta.';
    return 'Un dato por vez: "${campos.first.nombre} ..." o el valor solo. '
        'Despues deci "agregar".';
  }

  void _avisar(String texto) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(texto), duration: const Duration(seconds: 4)),
    );
  }

  /// Sin campos que cargar, «Agregar» solo puede crear filas vacias: es el
  /// boton que el usuario toco dos veces sin entender. En vez de eso se pide
  /// arreglar el origen -el diagrama- y se saca el boton de en medio.
  bool get _sinAtributos => _editables.isEmpty;

  /// Pluralizacion casera, solo para la invitacion de la lista vacia: alcanza
  /// con los sustantivos comunes de un diagrama de clases (Paciente, Cliente,
  /// Factura) y no pretende cubrir el idioma entero.
  static String _plural(String nombre) {
    final minuscula = nombre.toLowerCase();
    if (minuscula.endsWith('z')) return '${minuscula.substring(0, minuscula.length - 1)}ces';
    if (RegExp(r'[aeiouáéíóú]$').hasMatch(minuscula)) return '${minuscula}s';
    return '${minuscula}es';
  }

  static String _resumen(int total, int pendientes) {
    final registros = total == 1 ? '1 registro' : '$total registros';
    return pendientes > 0 ? '$registros, $pendientes sin enviar' : registros;
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text(widget.clase.nombre),
          actions: [
            // Escucha al asistente para que el boton diga la verdad: sin este
            // ValueListenableBuilder, un modelo que termina de cargar con la
            // pantalla ya abierta no se notaria por ningun lado.
            ValueListenableBuilder<Asistente?>(
              valueListenable: _asistente,
              builder: (_, asistente, _) => IconButton(
                tooltip: _pensando
                    ? 'Pensando...'
                    : asistente == null
                        ? 'Dictar un registro'
                        : 'Dictar un registro (con ayuda del modelo)',
                onPressed: _dictando ? null : _dictar,
                icon: _pensando
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(asistente == null ? Icons.mic : Icons.mic_none),
              ),
            ),
          ],
        ),
        body: FutureBuilder<List<Map<String, dynamic>>>(
          future: _filas,
          builder: (_, resultado) {
            final filas = resultado.data ?? const <Map<String, dynamic>>[];
            final pendientes = filas.where((f) => f.containsKey('_pendiente')).length;
            return Column(children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    _resumen(filas.length, pendientes),
                    style: const TextStyle(color: Colores.textoMedio, fontSize: 13),
                  ),
                ),
              ),
              if (_sinAtributos) _avisoSinAtributos() else _fichaDeAlta(),
              Expanded(
                child: filas.isEmpty
                    ? (_sinAtributos ? const SizedBox.shrink() : _sinRegistros())
                    : ListView(
                        padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                        children: [for (final fila in filas) _tarjetaDeRegistro(fila)],
                      ),
              ),
            ]);
          },
        ),
      );

  /// La ficha de alta: nombre de campo en monoespaciada, columna fija a la
  /// izquierda, rimando a proposito con como el lienzo dibuja los atributos de
  /// la clase -ver pintor.dart-. Quien dibujo la clase reconoce sus propios
  /// atributos en el formulario.
  Widget _fichaDeAlta() => Container(
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colores.superficie,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colores.borde),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Nuevo',
              style: TextStyle(color: Colores.textoDebil, fontSize: 13, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            for (final atributo in _editables) _campoDeAlta(atributo),
            const SizedBox(height: 8),
            FilledButton(onPressed: _guardar, child: const Text('Agregar')),
          ],
        ),
      );

  Widget _campoDeAlta(Atributo atributo) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            SizedBox(
              width: 92,
              child: Text(
                atributo.nombre,
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 13,
                  color: Colores.textoMedio,
                ),
              ),
            ),
            Expanded(
              child: TextField(
                controller: _controladores[atributo.nombre],
                decoration: const InputDecoration(isDense: true),
                keyboardType: _tecladoDe(atributo),
              ),
            ),
          ],
        ),
      );

  /// Una clase del diagrama sin atributos propios (mas alla del identificador
  /// que pone el backend). Antes esto mostraba un «Agregar» solo, que el
  /// usuario tocaba sin saber que iba a crear una fila vacia.
  Widget _avisoSinAtributos() => Container(
        margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colores.superficie,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: Colores.borde),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${widget.clase.nombre} no tiene atributos en el diagrama.',
              style: const TextStyle(color: Colores.texto, fontSize: 14, fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 6),
            const Text(
              'Agregalos en FORJA y volve a bajar el diagrama.',
              style: TextStyle(color: Colores.textoMedio, fontSize: 14),
            ),
          ],
        ),
      );

  Widget _sinRegistros() => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            'Todavia no hay ${_plural(widget.clase.nombre)}. Carga el primero aca arriba.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colores.textoMedio, fontSize: 14),
          ),
        ),
      );

  /// Un registro, campo por campo: se lee como una tabla, no como el parrafo
  /// unido con «·» que mostraba antes.
  Widget _tarjetaDeRegistro(Map<String, dynamic> fila) {
    final pendiente = fila.containsKey('_pendiente');
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colores.superficie,
        borderRadius: BorderRadius.circular(8),
        // Lo que todavia no llego al backend se marca con el borde: sin esto,
        // sin senal no se distingue lo guardado de lo enviado.
        border: Border.all(color: pendiente ? Colores.ambar : Colores.borde),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (final atributo in _editables)
                  if ((fila[atributo.nombre]?.toString() ?? '').isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          SizedBox(
                            width: 92,
                            child: Text(
                              atributo.nombre,
                              style: const TextStyle(
                                fontFamily: 'monospace',
                                fontSize: 13,
                                color: Colores.textoDebil,
                              ),
                            ),
                          ),
                          Expanded(
                            child: Text(
                              fila[atributo.nombre].toString(),
                              style: const TextStyle(fontSize: 14, color: Colores.texto),
                            ),
                          ),
                        ],
                      ),
                    ),
              ],
            ),
          ),
          if (pendiente)
            const Padding(
              padding: EdgeInsets.only(left: 8, top: 2),
              child: Icon(Icons.schedule, size: 18, color: Colores.ambar),
            ),
        ],
      ),
    );
  }
}

/// Le muestra al usuario lo que el modelo propuso y espera un si.
///
/// Es el freno de mano del dictado de un REGISTRO ENTERO -el camino que arma
/// `resolverPedidoDictado`, en generado/dictado_de_registros.dart-: el modelo
/// propone una frase, la gramatica la interpreta, y recien despues de este
/// cartel se crea algo.
///
/// La ficha de alta de esta pantalla ya no pasa por aca, y el motivo es que
/// ahora tiene un freno mejor: lo dictado se escribe en las cajas de texto, a
/// la vista y editable, y no sale del telefono hasta que alguien toque
/// «Agregar». Un cartel que se confirma de memoria frena menos que un
/// formulario que se lee.
Future<bool> confirmarPedidoDictado(
  BuildContext context,
  String fraseCanonica,
  PedidoDeRegistro pedido,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (dialogo) => AlertDialog(
        title: const Text('Se entendio asi'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('"$fraseCanonica"'),
            const SizedBox(height: 12),
            // Se muestra el pedido ya interpretado y no solo la frase: lo que
            // se va a guardar es esto, y es lo unico que el usuario puede
            // revisar de verdad.
            Text('Agregar a ${pedido.clase}:'),
            for (final dato in pedido.datos.entries)
              Text('${dato.key}: ${dato.value}'),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogo).pop(false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogo).pop(true),
            child: const Text('Agregar'),
          ),
        ],
      ),
    ) ??
    // Cerrar el cartel tocando afuera es no confirmar.
    false;
