import 'package:flutter/material.dart';

import 'almacen.dart';
import 'api.dart';
import 'generado/asistente.dart';
import 'generado/motor_gemma.dart';
import 'pantalla_rota.dart';
import 'pantallas/diagramas.dart';
import 'pantallas/entrar.dart';
import 'sincronizador.dart';
import 'tipos.dart';

void main() {
  // Antes de dibujar nada: si algo se rompe despues, lo que se ve es un cartel
  // que explica y del que se puede salir, y no el recuadro gris sin
  // informacion que Flutter muestra en una compilacion de release.
  instalarPantallaRota();
  runApp(const AplicacionForja());
}

/// Paleta compartida con el cliente web: acero y ambar, porque FORJA es una
/// fragua. Que las dos aplicaciones se vean como la misma cosa no es decoracion:
/// en la defensa se muestran una despues de la otra.
class Colores {
  static const fondo = Color(0xFF0E1116);
  static const lienzo = Color(0xFF12161D);
  static const superficie = Color(0xFF171C24);
  static const superficieAlta = Color(0xFF1F2630);
  static const borde = Color(0xFF2A323D);
  static const texto = Color(0xFFE4E8EE);
  static const textoMedio = Color(0xFF9AA4B2);
  static const textoDebil = Color(0xFF697585);
  // El ambar es el unico color con trabajo en las dos aplicaciones, asi que es
  // el mismo numero: --fuego y --fuego-claro de web/src/estilos.css. Habian
  // quedado corridos un tono entre un cliente y el otro.
  static const ambar = Color(0xFFE8801F);
  static const ambarClaro = Color(0xFFF6A84F);
  static const exito = Color(0xFF4BB37B);
  static const peligro = Color(0xFFE05C5C);
  static const ajeno = Color(0xFF7C6CE0);
}

class AplicacionForja extends StatefulWidget {
  const AplicacionForja({super.key});

  @override
  State<AplicacionForja> createState() => _AplicacionForjaState();
}

class _AplicacionForjaState extends State<AplicacionForja> {
  final Api _api = Api();
  Almacen? _almacen;
  Credencial? _credencial;
  bool _cargando = true;

  /// El modelo en el aparato, cuando llega a cargar. Empieza en null y puede
  /// quedarse en null para siempre: es el unico escalon prescindible de la app.
  ///
  /// Es un ValueNotifier y no un campo con setState porque las pantallas que lo
  /// usan viven detras de rutas que se construyen UNA sola vez: reconstruir
  /// esta no las reconstruye a ellas. Con el notificador, la referencia que
  /// recibieron al abrirse es la misma que avisa cuando el modelo llega.
  final ValueNotifier<Asistente?> _asistente = ValueNotifier(null);

  /// Identifica a esta instalacion durante toda la corrida. Junto con el usuario
  /// determina quien posee un bloqueo, y es lo que el servidor libera cuando el
  /// canal se cierra.
  final String _sesionId = 'movil-${Sincronizador.nuevoToken()}';

  @override
  void initState() {
    super.initState();
    _preparar();
  }

  Future<void> _preparar() async {
    final almacen = await Almacen.enElTelefono();
    final credencial = await almacen.leerCredencial();
    if (credencial != null) _api.fijarToken(credencial.token);

    if (!mounted) return;
    setState(() {
      _almacen = almacen;
      _credencial = credencial;
      _cargando = false;
    });

    // Despues de dibujar y sin esperarlo: son 529 MB de pesos y cargarlos antes
    // de la primera pantalla dejaria la app en blanco vaya a saber cuanto.
    _arrancarAsistente();
  }

  /// Enciende el modelo, y si no se puede no pasa nada.
  ///
  /// Sin modelo se pierde el tercer escalon del dictado -la traduccion de una
  /// frase que la gramatica no entendio- y nada mas. No es un error que el
  /// usuario tenga que ver: la app entera sigue funcionando igual.
  Future<void> _arrancarAsistente() async {
    try {
      final preguntar = await arrancarGemma();
      if (!mounted) return;
      // Sin setState: quien tiene que enterarse escucha el notificador, y
      // reconstruir esto no reconstruiria ninguna pantalla ya abierta.
      _asistente.value = Asistente(preguntarAlModelo: preguntar);
    } catch (_) {
      // A proposito en silencio.
    }
  }

  @override
  void dispose() {
    _asistente.dispose();
    super.dispose();
  }

  Future<void> _entrar(Credencial credencial) async {
    await _almacen!.guardarCredencial(credencial);
    _api.fijarToken(credencial.token);
    setState(() => _credencial = credencial);
  }

  Future<void> _salir() async {
    // Se borra la credencial pero NO los diagramas guardados: si alguien vuelve
    // a entrar sin red, poder seguir viendo su modelo es justamente el punto.
    await _almacen!.guardarCredencial(null);
    _api.fijarToken(null);
    setState(() => _credencial = null);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FORJA',
      debugShowCheckedModeBanner: false,
      theme: _tema(),
      home: _cargando
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : _credencial == null
              ? PantallaEntrar(api: _api, alEntrar: _entrar)
              : PantallaDiagramas(
                  api: _api,
                  almacen: _almacen!,
                  credencial: _credencial!,
                  sesionId: _sesionId,
                  alSalir: _salir,
                  asistente: _asistente,
                ),
    );
  }

  ThemeData _tema() {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: Colores.fondo,
      colorScheme: base.colorScheme.copyWith(
        primary: Colores.ambar,
        onPrimary: const Color(0xFF1A1206),
        surface: Colores.superficie,
        onSurface: Colores.texto,
        error: Colores.peligro,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colores.superficie,
        foregroundColor: Colores.texto,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: const CardThemeData(color: Colores.superficie, elevation: 0),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colores.fondo,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Colores.borde),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Colores.borde),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(6),
          borderSide: const BorderSide(color: Colores.ambar, width: 2),
        ),
        labelStyle: const TextStyle(color: Colores.textoDebil),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: Colores.ambar,
          foregroundColor: const Color(0xFF1A1206),
        ),
      ),
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: Colores.superficieAlta,
        contentTextStyle: TextStyle(color: Colores.texto),
      ),
    );
  }
}

/// La marca: un yunque cortado a 45 grados, el mismo dibujo que en la web.
///
/// Va pintado a mano y no como imagen por la misma razon por la que el cliente
/// web lo dibuja en linea: asi toma los colores de [Colores] y no hay dos
/// ambares parecidos en la aplicacion. Ademas ahorra la dependencia de
/// flutter_svg por una sola figura de lineas rectas.
class Yunque extends StatelessWidget {
  const Yunque({super.key, this.lado = 22});

  /// Nunca por debajo de 20: mas chico, el rombo del talon se cierra.
  final double lado;

  @override
  Widget build(BuildContext context) => SizedBox(
        width: lado,
        height: lado,
        child: CustomPaint(painter: _PintorYunque()),
      );
}

class _PintorYunque extends CustomPainter {
  /// El contorno esta trazado sobre una grilla de 64 y cada corte interno es de
  /// 45 grados, el mismo angulo con el que el editor dibuja las relaciones.
  static const _cuerpo = <Offset>[
    Offset(3, 20), Offset(18, 12), Offset(53, 12), Offset(59, 18),
    Offset(59, 28), Offset(44, 28), Offset(38, 34), Offset(38, 40),
    Offset(45, 47), Offset(46, 47), Offset(46, 53), Offset(17, 53),
    Offset(17, 47), Offset(18, 47), Offset(25, 40), Offset(25, 34),
    Offset(19, 28), Offset(14, 28),
  ];

  /// El rombo del talon: calado, no pintado. Deja pasar el fondo.
  static const _rombo = <Offset>[
    Offset(46, 17.5), Offset(50.5, 22), Offset(46, 26.5), Offset(41.5, 22),
  ];

  /// El bisel de arriba: el metal caliente. Da volumen sin un degradado.
  static const _cara = <Offset>[
    Offset(18, 12), Offset(53, 12), Offset(57, 16), Offset(10.5, 16),
  ];

  static Path _figura(List<Offset> puntos, double escala) {
    final camino = Path()..moveTo(puntos.first.dx * escala, puntos.first.dy * escala);
    for (final punto in puntos.skip(1)) {
      camino.lineTo(punto.dx * escala, punto.dy * escala);
    }
    return camino..close();
  }

  @override
  void paint(Canvas lienzo, Size medida) {
    final escala = medida.width / 64;
    final silueta = _figura(_cuerpo, escala)
      ..addPath(_figura(_rombo, escala), Offset.zero)
      ..fillType = PathFillType.evenOdd;
    lienzo.drawPath(silueta, Paint()..color = Colores.ambar);
    lienzo.drawPath(_figura(_cara, escala), Paint()..color = Colores.ambarClaro);
  }

  @override
  bool shouldRepaint(_PintorYunque otro) => false;
}
