import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'main.dart' show Colores;

/// Lo que se ve cuando algo se rompe, en vez de un recuadro gris.
///
/// Flutter atrapa los errores de construccion y muestra lo que diga
/// [ErrorWidget.builder]. Por omision, en depuracion eso es un recuadro rojo
/// con la excepcion y **en una compilacion de release es un recuadro gris que
/// no dice nada**: ni que fallo, ni como salir.
///
/// El cliente web tenia el mismo hueco y se vio de la peor manera el 21 de
/// septiembre de 2026: un error dentro del lienzo dejaba la pantalla
/// COMPLETAMENTE NEGRA. Ese defecto concreto se arreglo, pero lo que importaba
/// era el otro: no habia red. En un telefono, delante de un tribunal, un
/// recuadro gris es peor todavia que una pantalla negra, porque parece que la
/// aplicacion nunca hubiera cargado.
///
/// Se muestra que fallo. Un "algo salio mal" sin detalle obliga a enchufar el
/// telefono a la computadora para leer el log, que es justo lo que no se hace
/// delante de nadie.
void instalarPantallaRota() {
  ErrorWidget.builder = (FlutterErrorDetails detalles) {
    // Queda ademas en el log, con la pila, que es lo que sirve despues.
    if (kDebugMode) {
      debugPrint('Se rompio algo: ${detalles.exception}\n${detalles.stack}');
    }
    return PantallaRota(mensaje: detalles.exception.toString());
  };
}

class PantallaRota extends StatelessWidget {
  const PantallaRota({super.key, required this.mensaje});

  final String mensaje;

  @override
  Widget build(BuildContext context) {
    // Sin Scaffold a proposito: este widget puede aparecer en el lugar de
    // cualquier otro, incluso dentro de uno que ya es un Scaffold, y anidar
    // dos deja la pantalla en blanco.
    return Material(
      color: Colores.fondo,
      child: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(22),
            child: Container(
              constraints: const BoxConstraints(maxWidth: 460),
              // Sin esquinas redondeadas: Flutter no admite borderRadius
              // cuando los bordes no son del mismo color, y la barra ambar de
              // arriba es lo que hace que el cartel se lea como de FORJA.
              decoration: const BoxDecoration(
                color: Colores.superficie,
                border: Border(
                  top: BorderSide(color: Colores.ambar, width: 3),
                  left: BorderSide(color: Colores.borde),
                  right: BorderSide(color: Colores.borde),
                  bottom: BorderSide(color: Colores.borde),
                ),
              ),
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Se rompió algo',
                    style: TextStyle(
                      color: Colores.texto,
                      fontSize: 19,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'La herramienta encontró un error que no supo manejar y cerró esta '
                    'pantalla para no seguir con datos a medias.',
                    style: TextStyle(color: Colores.textoMedio, height: 1.5),
                  ),
                  const SizedBox(height: 10),
                  const Text(
                    'Lo que ya aplicaste está guardado: cada cambio se escribe en el '
                    'momento en que se hace, así que el diagrama está entero.',
                    style: TextStyle(color: Colores.texto, height: 1.5),
                  ),
                  const SizedBox(height: 14),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colores.lienzo,
                      border: Border.all(color: Colores.borde),
                      borderRadius: BorderRadius.circular(3),
                    ),
                    child: Text(
                      mensaje,
                      style: const TextStyle(
                        color: Colores.textoMedio,
                        fontFamily: 'monospace',
                        fontSize: 12,
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'Volvé atrás y entrá de nuevo al diagrama.',
                    style: TextStyle(color: Colores.textoMedio, fontSize: 13),
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
