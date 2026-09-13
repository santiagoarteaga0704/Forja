import 'package:flutter/material.dart';

import 'almacen.dart';
import 'api.dart';
import 'pantallas/diagramas.dart';
import 'pantallas/entrar.dart';
import 'sincronizador.dart';
import 'tipos.dart';

void main() {
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
  static const ambar = Color(0xFFE8913C);
  static const ambarClaro = Color(0xFFF6B26B);
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

/// Rombo de la marca, el mismo signo que en la web.
class Yunque extends StatelessWidget {
  const Yunque({super.key, this.lado = 10});

  final double lado;

  @override
  Widget build(BuildContext context) => Transform.rotate(
        angle: 0.785,
        child: Container(width: lado, height: lado, color: Colores.ambar),
      );
}
