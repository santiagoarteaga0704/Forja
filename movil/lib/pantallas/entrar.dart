import 'package:flutter/material.dart';

import '../api.dart';
import '../main.dart';
import '../tipos.dart';

/// Entrada a la aplicacion.
///
/// Incluye la direccion del servidor como campo editable, y no es un descuido de
/// configuracion: en el emulador el servidor esta en 10.0.2.2, desde un telefono
/// real esta en la IP de la maquina en la red local, y en el despliegue esta en
/// AWS. Pedir una recompilacion para cambiar entre esas tres cosas volveria
/// incomoda cada prueba en el telefono.
class PantallaEntrar extends StatefulWidget {
  const PantallaEntrar({super.key, required this.api, required this.alEntrar});

  final Api api;
  final Future<void> Function(Credencial) alEntrar;

  @override
  State<PantallaEntrar> createState() => _PantallaEntrarState();
}

class _PantallaEntrarState extends State<PantallaEntrar> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _nombre = TextEditingController();
  late final TextEditingController _servidor =
      TextEditingController(text: widget.api.base);

  bool _esAlta = false;
  bool _enviando = false;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _nombre.dispose();
    _servidor.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    setState(() {
      _error = null;
      _enviando = true;
    });

    widget.api.base = _servidor.text.trim();
    try {
      final credencial = _esAlta
          ? await widget.api.registrarse(
              _email.text.trim(), _nombre.text.trim(), _password.text)
          : await widget.api.entrar(_email.text.trim(), _password.text);
      await widget.alEntrar(credencial);
    } on SinConexion {
      setState(() => _error = 'No se pudo llegar al servidor. Revisa la direccion y '
          'que el backend este corriendo.');
    } on ErrorApi catch (e) {
      setState(() => _error = e.mensaje);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(children: const [
                    Yunque(),
                    SizedBox(width: 10),
                    Text('FORJA',
                        style: TextStyle(
                            fontWeight: FontWeight.bold, letterSpacing: 3, fontSize: 14)),
                  ]),
                  const SizedBox(height: 20),
                  Text(_esAlta ? 'Crear una cuenta' : 'Entrar',
                      style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 4),
                  const Text('Herramienta CASE colaborativa',
                      style: TextStyle(color: Colores.textoMedio, fontSize: 13)),
                  const SizedBox(height: 24),

                  if (_error != null) ...[
                    _Aviso(texto: _error!, color: Colores.peligro),
                    const SizedBox(height: 14),
                  ],

                  TextField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(labelText: 'Correo'),
                  ),
                  const SizedBox(height: 12),

                  if (_esAlta) ...[
                    TextField(
                      controller: _nombre,
                      decoration: const InputDecoration(labelText: 'Nombre'),
                    ),
                    const SizedBox(height: 12),
                  ],

                  TextField(
                    controller: _password,
                    obscureText: true,
                    decoration: InputDecoration(
                      labelText: 'Contrasena',
                      helperText: _esAlta ? 'Al menos 8 caracteres' : null,
                      helperStyle: const TextStyle(color: Colores.textoDebil, fontSize: 11),
                    ),
                    onSubmitted: (_) => _enviar(),
                  ),
                  const SizedBox(height: 12),

                  TextField(
                    controller: _servidor,
                    keyboardType: TextInputType.url,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
                    decoration: const InputDecoration(
                      labelText: 'Servidor',
                      helperText: '10.0.2.2 es la PC vista desde el emulador. Desde un '
                          'telefono real, la IP de la PC en la red.',
                      helperMaxLines: 3,
                      helperStyle: TextStyle(color: Colores.textoDebil, fontSize: 11),
                    ),
                  ),
                  const SizedBox(height: 20),

                  FilledButton(
                    onPressed: _enviando ? null : _enviar,
                    child: Text(_enviando
                        ? 'Un momento...'
                        : _esAlta
                            ? 'Crear la cuenta'
                            : 'Entrar'),
                  ),
                  const SizedBox(height: 12),

                  TextButton(
                    onPressed: () => setState(() {
                      _esAlta = !_esAlta;
                      _error = null;
                    }),
                    child: Text(
                      _esAlta ? 'Ya tengo cuenta' : 'Crear una cuenta',
                      style: const TextStyle(color: Colores.ambarClaro, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Aviso extends StatelessWidget {
  const _Aviso({required this.texto, required this.color});

  final String texto;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          border: Border.all(color: color),
          borderRadius: BorderRadius.circular(6),
        ),
        child: Text(texto, style: TextStyle(color: color, fontSize: 12.5)),
      );
}
