import 'dart:math';

import 'package:flutter/material.dart';

import '../main.dart';
import '../tipos.dart';

/// Dibuja el diagrama.
///
/// Se pinta sobre un lienzo y no se arman widgets por clase: un diagrama de
/// treinta clases con sus miembros serian cientos de widgets que Flutter tendria
/// que medir y ubicar en cada cuadro del desplazamiento. Pintando, el costo de
/// mover la vista es una transformacion y nada mas.
///
/// Los adornos de las relaciones no son decorativos: el rombo lleno o hueco y el
/// triangulo son lo unico que distingue una composicion de una agregacion y una
/// herencia de una realizacion. Sin ellos el diagrama no se puede leer.
class PintorDeDiagrama extends CustomPainter {
  PintorDeDiagrama({
    required this.diagrama,
    required this.desplazamiento,
    required this.escala,
    required this.usuarioId,
    this.seleccionada,
    this.senalada,
    this.revision = 0,
  });

  final Diagrama diagrama;
  final Offset desplazamiento;
  final double escala;
  final String usuarioId;
  final String? seleccionada;

  /// Primer extremo elegido mientras se traza una relacion.
  final String? senalada;

  /// Contador que la pantalla incrementa con cada cambio local.
  ///
  /// Hace falta porque el modelo se muta en lugar de copiarse: arrastrar una
  /// clase cambia sus coordenadas sin alterar ninguna longitud ni la version, asi
  /// que sin esto el lienzo no se repintaria durante el arrastre.
  final int revision;

  static const double _altoDeFila = 16;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.translate(desplazamiento.dx, desplazamiento.dy);
    canvas.scale(escala);

    for (final relacion in diagrama.relaciones) {
      final origen = diagrama.clasePorId(relacion.origenId);
      final destino = diagrama.clasePorId(relacion.destinoId);
      if (origen != null && destino != null) {
        _pintarRelacion(canvas, relacion, origen, destino);
      }
    }
    for (final clase in diagrama.clases) {
      _pintarClase(canvas, clase);
    }

    canvas.restore();
  }

  // ---------- Clases -------------------------------------------------------

  void _pintarClase(Canvas canvas, Clase clase) {
    final bloqueo = diagrama.bloqueoDe(clase.id);
    final ajeno = bloqueo != null && bloqueo.poseedorId != usuarioId;
    final destacada = clase.id == seleccionada || clase.id == senalada;

    final caja = Rect.fromLTWH(clase.posX, clase.posY, Clase.ancho, clase.alto);
    final redondeada = RRect.fromRectAndRadius(caja, const Radius.circular(4));
    final cabecera = clase.estereotipo != null ? 40.0 : 28.0;

    canvas.drawRRect(redondeada, Paint()..color = Colores.superficie);
    // La cabecera lleva un fondo apenas distinto: separa el nombre del contenido
    // sin gastar una linea mas.
    canvas.save();
    canvas.clipRRect(redondeada);
    canvas.drawRect(
      Rect.fromLTWH(caja.left, caja.top, caja.width, cabecera),
      Paint()..color = Colores.superficieAlta,
    );
    canvas.restore();

    final color = ajeno
        ? Colores.ajeno
        : destacada
            ? Colores.ambar
            : Colores.borde;
    canvas.drawRRect(
      redondeada,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = destacada || ajeno ? 2 : 1,
    );
    canvas.drawLine(
      Offset(caja.left, caja.top + cabecera),
      Offset(caja.right, caja.top + cabecera),
      Paint()..color = color,
    );

    if (clase.estereotipo != null) {
      _texto(canvas, '«${clase.estereotipo}»',
          Offset(caja.center.dx, caja.top + 6),
          tamano: 10, color: Colores.ambarClaro, centrado: true);
    }
    _texto(canvas, clase.nombre,
        Offset(caja.center.dx, caja.top + (clase.estereotipo != null ? 21 : 7)),
        tamano: 13,
        color: Colores.texto,
        negrita: true,
        cursiva: clase.esAbstracta,
        centrado: true);

    var y = caja.top + cabecera + 5;
    for (final atributo in clase.atributos) {
      _texto(canvas, atributo.comoSeLee, Offset(caja.left + 8, y),
          tamano: 11, color: Colores.textoMedio, monoespaciada: true);
      y += _altoDeFila;
    }
    if (clase.metodos.isNotEmpty) {
      y += 3;
      canvas.drawLine(Offset(caja.left, y), Offset(caja.right, y),
          Paint()..color = Colores.borde);
      y += 3;
      for (final metodo in clase.metodos) {
        _texto(canvas, metodo.comoSeLee, Offset(caja.left + 8, y),
            tamano: 11, color: Colores.textoMedio, monoespaciada: true,
            cursiva: metodo.esAbstracto);
        y += _altoDeFila;
      }
    }

    // Quien la esta editando, arriba de la caja para no tapar el contenido.
    if (ajeno) {
      final etiqueta = '${bloqueo.poseedorNombre} esta editando';
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(caja.left, caja.top - 19, min(Clase.ancho, etiqueta.length * 6.0 + 10), 16),
          const Radius.circular(3),
        ),
        Paint()..color = Colores.ajeno,
      );
      _texto(canvas, etiqueta, Offset(caja.left + 5, caja.top - 17),
          tamano: 9.5, color: Colors.white);
    }
  }

  // ---------- Relaciones ---------------------------------------------------

  void _pintarRelacion(Canvas canvas, Relacion relacion, Clase origen, Clase destino) {
    if (origen.id == destino.id) {
      // Autoasociacion: un lazo sobre el borde superior, que es la convencion.
      final desde = Offset(origen.posX + Clase.ancho - 40, origen.posY);
      final ruta = Path()
        ..moveTo(desde.dx, desde.dy)
        ..cubicTo(desde.dx + 20, desde.dy - 44, desde.dx + 70, desde.dy - 18,
            origen.posX + Clase.ancho, origen.posY + 22);
      canvas.drawPath(
        ruta,
        Paint()
          ..color = Colores.textoDebil
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.4,
      );
      return;
    }

    final centroOrigen = _centro(origen);
    final centroDestino = _centro(destino);
    final desde = _borde(origen, centroOrigen, centroDestino);
    final hasta = _borde(destino, centroDestino, centroOrigen);

    final punteada = relacion.tipo == 'REALIZACION' || relacion.tipo == 'DEPENDENCIA';
    final rombo = relacion.tipo == 'AGREGACION' || relacion.tipo == 'COMPOSICION';
    final triangulo = relacion.tipo == 'HERENCIA' || relacion.tipo == 'REALIZACION';

    final trazo = Paint()
      ..color = Colores.textoDebil
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;

    final inicio = rombo ? _avanzar(desde, hasta, 14) : desde;
    final fin = triangulo ? _avanzar(hasta, desde, 12) : hasta;

    if (punteada) {
      _lineaPunteada(canvas, inicio, fin, trazo);
    } else {
      canvas.drawLine(inicio, fin, trazo);
    }

    if (rombo) {
      final relleno = Paint()
        ..color = relacion.tipo == 'COMPOSICION' ? Colores.textoDebil : Colores.lienzo;
      canvas.drawPath(_rombo(desde, hasta), relleno);
      canvas.drawPath(_rombo(desde, hasta), trazo);
    }
    if (triangulo) {
      canvas.drawPath(_triangulo(hasta, desde), Paint()..color = Colores.lienzo);
      canvas.drawPath(_triangulo(hasta, desde), trazo);
    }
    if (relacion.tipo == 'DEPENDENCIA') {
      canvas.drawPath(_flechaAbierta(hasta, desde), trazo);
    }

    if (!triangulo) {
      _texto(canvas, relacion.multiplicidadOrigen, _avanzar(desde, hasta, 18),
          tamano: 10, color: Colores.textoMedio, monoespaciada: true, centrado: true);
      _texto(canvas, relacion.multiplicidadDestino, _avanzar(hasta, desde, 18),
          tamano: 10, color: Colores.textoMedio, monoespaciada: true, centrado: true);
    }
  }

  void _lineaPunteada(Canvas canvas, Offset desde, Offset hasta, Paint pintura) {
    const trozo = 6.0;
    const hueco = 4.0;
    final largo = (hasta - desde).distance;
    final direccion = (hasta - desde) / (largo == 0 ? 1 : largo);
    var recorrido = 0.0;
    while (recorrido < largo) {
      final fin = min(recorrido + trozo, largo);
      canvas.drawLine(desde + direccion * recorrido, desde + direccion * fin, pintura);
      recorrido = fin + hueco;
    }
  }

  Offset _centro(Clase clase) =>
      Offset(clase.posX + Clase.ancho / 2, clase.posY + clase.alto / 2);

  /// Punto en que la linea entre dos centros cruza el borde de la caja. Se
  /// resuelve escalando el vector hasta el primero de los dos bordes que alcanza.
  Offset _borde(Clase clase, Offset propio, Offset ajeno) {
    final d = ajeno - propio;
    if (d.dx == 0 && d.dy == 0) return propio;

    final medioAncho = Clase.ancho / 2;
    final medioAlto = clase.alto / 2;
    final escalaX = d.dx == 0 ? double.infinity : medioAncho / d.dx.abs();
    final escalaY = d.dy == 0 ? double.infinity : medioAlto / d.dy.abs();
    return propio + d * min(escalaX, escalaY);
  }

  Offset _avanzar(Offset desde, Offset hacia, double distancia) {
    final largo = (hacia - desde).distance;
    if (largo == 0) return desde;
    return desde + (hacia - desde) / largo * distancia;
  }

  Offset _perpendicular(Offset desde, Offset hacia) {
    final d = hacia - desde;
    final largo = d.distance == 0 ? 1 : d.distance;
    return Offset(-d.dy / largo, d.dx / largo);
  }

  Path _rombo(Offset punta, Offset hacia) {
    final eje = _avanzar(punta, hacia, 14);
    final medio = _avanzar(punta, hacia, 7);
    final n = _perpendicular(punta, hacia);
    return Path()
      ..moveTo(punta.dx, punta.dy)
      ..lineTo(medio.dx + n.dx * 4.5, medio.dy + n.dy * 4.5)
      ..lineTo(eje.dx, eje.dy)
      ..lineTo(medio.dx - n.dx * 4.5, medio.dy - n.dy * 4.5)
      ..close();
  }

  Path _triangulo(Offset punta, Offset hacia) {
    final base = _avanzar(punta, hacia, 12);
    final n = _perpendicular(punta, hacia);
    return Path()
      ..moveTo(punta.dx, punta.dy)
      ..lineTo(base.dx + n.dx * 5.5, base.dy + n.dy * 5.5)
      ..lineTo(base.dx - n.dx * 5.5, base.dy - n.dy * 5.5)
      ..close();
  }

  Path _flechaAbierta(Offset punta, Offset hacia) {
    final base = _avanzar(punta, hacia, 11);
    final n = _perpendicular(punta, hacia);
    return Path()
      ..moveTo(base.dx + n.dx * 5, base.dy + n.dy * 5)
      ..lineTo(punta.dx, punta.dy)
      ..lineTo(base.dx - n.dx * 5, base.dy - n.dy * 5);
  }

  void _texto(
    Canvas canvas,
    String texto,
    Offset donde, {
    required double tamano,
    required Color color,
    bool negrita = false,
    bool cursiva = false,
    bool monoespaciada = false,
    bool centrado = false,
  }) {
    final pintor = TextPainter(
      text: TextSpan(
        text: texto,
        style: TextStyle(
          fontSize: tamano,
          color: color,
          fontWeight: negrita ? FontWeight.w600 : FontWeight.normal,
          fontStyle: cursiva ? FontStyle.italic : FontStyle.normal,
          fontFamily: monoespaciada ? 'monospace' : null,
        ),
      ),
      textDirection: TextDirection.ltr,
      maxLines: 1,
      ellipsis: '…',
    )..layout(maxWidth: Clase.ancho - 14);

    pintor.paint(canvas, centrado ? Offset(donde.dx - pintor.width / 2, donde.dy) : donde);
  }

  @override
  bool shouldRepaint(PintorDeDiagrama anterior) =>
      anterior.desplazamiento != desplazamiento ||
      anterior.escala != escala ||
      anterior.seleccionada != seleccionada ||
      anterior.senalada != senalada ||
      anterior.revision != revision ||
      anterior.diagrama.version != diagrama.version ||
      anterior.diagrama.clases.length != diagrama.clases.length ||
      anterior.diagrama.relaciones.length != diagrama.relaciones.length ||
      anterior.diagrama.bloqueos.length != diagrama.bloqueos.length;
}
