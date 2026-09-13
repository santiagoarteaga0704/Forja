import 'comandos.dart';
import 'tipos.dart';

/// Aplica un comando sobre la copia local del diagrama.
///
/// Es el tercer aplicador del sistema -hay uno en el servidor y otro en la web-
/// y aqui la duplicacion no es solo por fluidez: es lo que permite que el
/// telefono siga funcionando **sin conexion**. Sin esta pieza, trabajar offline
/// seria imposible, no incomodo.
///
/// Lo que no se duplica son las validaciones. Aqui no se comprueba nada: el
/// servidor es la autoridad, y cuando la cola se vacia puede rechazar algo que
/// localmente parecio bien. Ante cualquier divergencia la salida es volver a
/// pedir el diagrama entero, no discutir con el servidor.
///
/// Muta el diagrama en lugar de copiarlo. Es deliberado: reproducir una cola de
/// doscientas operaciones creando doscientas copias del modelo en un telefono es
/// gasto sin beneficio, y aqui no hay un marco de interfaz que necesite detectar
/// el cambio por identidad.
void aplicar(Diagrama diagrama, Comando comando, {int? secuencia}) {
  final carga = comando.carga;

  switch (comando.tipo) {
    case 'CLASE_CREAR':
      final id = carga['claseId'] as String;
      if (diagrama.clasePorId(id) != null) return;
      diagrama.clases.add(Clase(
        id: id,
        nombre: carga['nombre'] as String,
        estereotipo: carga['estereotipo'] as String?,
        esAbstracta: carga['esAbstracta'] as bool? ?? false,
        posX: (carga['posX'] as num?)?.toDouble() ?? 0,
        posY: (carga['posY'] as num?)?.toDouble() ?? 0,
      ));

    case 'CLASE_RENOMBRAR':
      diagrama.clasePorId(carga['claseId'] as String)?.nombre = carga['nombre'] as String;

    case 'CLASE_MARCAR':
      final clase = diagrama.clasePorId(carga['claseId'] as String);
      if (clase != null) {
        final estereotipo = carga['estereotipo'] as String?;
        clase.estereotipo = (estereotipo == null || estereotipo.isEmpty) ? null : estereotipo;
        clase.esAbstracta = carga['esAbstracta'] as bool? ?? false;
      }

    case 'CLASE_MOVER':
      final clase = diagrama.clasePorId(carga['claseId'] as String);
      if (clase != null) {
        clase.posX = (carga['posX'] as num).toDouble();
        clase.posY = (carga['posY'] as num).toDouble();
      }

    case 'CLASE_ELIMINAR':
      final id = carga['claseId'] as String;
      diagrama.clases.removeWhere((c) => c.id == id);
      // Las relaciones que la tocaban se van con ella, igual que en el servidor.
      diagrama.relaciones.removeWhere((r) => r.origenId == id || r.destinoId == id);

    case 'ATRIBUTO_AGREGAR':
      final clase = diagrama.clasePorId(carga['claseId'] as String);
      if (clase != null) {
        clase.atributos.add(Atributo(
          id: carga['atributoId'] as String,
          nombre: carga['nombre'] as String,
          tipo: carga['tipo'] as String? ?? 'String',
          visibilidad: visibilidadDe(carga['visibilidad'] as String?),
          esIdentificador: carga['esIdentificador'] as bool? ?? false,
          esRequerido: carga['esRequerido'] as bool? ?? false,
          esUnico: carga['esUnico'] as bool? ?? false,
          longitud: (carga['longitud'] as num?)?.toInt(),
        ));
      }

    case 'ATRIBUTO_ELIMINAR':
      diagrama
          .clasePorId(carga['claseId'] as String)
          ?.atributos
          .removeWhere((a) => a.id == carga['atributoId']);

    case 'METODO_AGREGAR':
      final clase = diagrama.clasePorId(carga['claseId'] as String);
      if (clase != null) {
        clase.metodos.add(Metodo(
          id: carga['metodoId'] as String,
          nombre: carga['nombre'] as String,
          tipoRetorno: carga['tipoRetorno'] as String? ?? 'void',
          visibilidad: visibilidadDe(carga['visibilidad'] as String?),
          esAbstracto: carga['esAbstracto'] as bool? ?? false,
        ));
      }

    case 'METODO_ELIMINAR':
      diagrama
          .clasePorId(carga['claseId'] as String)
          ?.metodos
          .removeWhere((m) => m.id == carga['metodoId']);

    case 'RELACION_CREAR':
      final id = carga['relacionId'] as String;
      if (diagrama.relaciones.any((r) => r.id == id)) return;
      diagrama.relaciones.add(Relacion(
        id: id,
        origenId: carga['origenId'] as String,
        destinoId: carga['destinoId'] as String,
        tipo: carga['tipo'] as String,
        multiplicidadOrigen: carga['multiplicidadOrigen'] as String? ?? '1',
        multiplicidadDestino: carga['multiplicidadDestino'] as String? ?? '1',
      ));

    case 'RELACION_ELIMINAR':
      diagrama.relaciones.removeWhere((r) => r.id == carga['relacionId']);
  }

  if (secuencia != null && secuencia > diagrama.version) {
    diagrama.version = secuencia;
  }
}

/// Reproduce en orden una lista de cambios. Es lo que ocurre al volver la
/// conexion: se piden las operaciones posteriores a la version conocida y se
/// aplican una tras otra sobre la copia local.
void reproducir(Diagrama diagrama, List<OperacionRemota> operaciones) {
  for (final operacion in operaciones) {
    aplicar(
      diagrama,
      Comando(
        tipo: operacion.tipo,
        carga: operacion.carga,
        tokenCliente: 'remota',
        origen: operacion.origen,
      ),
      secuencia: operacion.secuencia,
    );
  }
}

/// Una operacion que el servidor ya acepto, tal como llega en el delta.
class OperacionRemota {
  const OperacionRemota({
    required this.secuencia,
    required this.tipo,
    required this.carga,
    required this.origen,
    required this.autorId,
  });

  final int secuencia;
  final String tipo;
  final Map<String, dynamic> carga;
  final String origen;
  final String autorId;

  factory OperacionRemota.desdeJson(Map<String, dynamic> json) => OperacionRemota(
        secuencia: (json['secuencia'] as num).toInt(),
        tipo: json['tipo'] as String,
        carga: Map<String, dynamic>.from(json['comando'] as Map),
        origen: json['origen'] as String? ?? 'LIENZO',
        autorId: json['autorId'] as String? ?? '',
      );
}
