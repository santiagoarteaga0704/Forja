import '../comandos.dart';

/// Lo que el parser entendio de una frase. Espejo de Interpretacion.java.
///
/// Cuando no entiende no devuelve un error sino SUGERENCIAS. La diferencia
/// importa para algo que se usa hablando: "no te entendi" deja a la persona
/// probando al azar, mientras que "proba: agrega el atributo X de tipo texto a
/// Paciente" le ensena la forma que si funciona.

class Paso {
  const Paso({required this.tipo, required this.comando});

  /// El mismo texto que TipoOperacion en el servidor: 'CLASE_CREAR',
  /// 'ATRIBUTO_AGREGAR', y los demas.
  final String tipo;

  /// La carga, con los mismos nombres de campo que el registro de Java. Va como
  /// mapa y no como clase por la misma razon por la que Comando lo hace: es
  /// exactamente la forma en que viaja por la red y en que el servidor la
  /// guarda en la bitacora.
  final Map<String, dynamic> comando;
}

class Interpretacion {
  const Interpretacion({
    required this.frase,
    required this.entendida,
    required this.pasos,
    required this.explicacion,
    required this.sugerencias,
  });

  factory Interpretacion.entendida(
          String frase, String explicacion, List<Paso> pasos) =>
      Interpretacion(
        frase: frase,
        entendida: true,
        pasos: pasos,
        explicacion: explicacion,
        sugerencias: const [],
      );

  factory Interpretacion.noEntendida(String frase, List<String> sugerencias) =>
      Interpretacion(
        frase: frase,
        entendida: false,
        pasos: const [],
        explicacion: 'No reconoci esa instruccion',
        sugerencias: sugerencias,
      );

  final String frase;
  final bool entendida;

  /// Los comandos a aplicar, en orden. Una frase puede producir varios.
  final List<Paso> pasos;

  /// Que se hizo, en palabras, para devolverselo a quien dicto.
  final String explicacion;
  final List<String> sugerencias;
}

/// Convierte los pasos en comandos listos para aplicar y encolar.
///
/// El token lo pone quien llama y no el parser, por la misma razon por la que
/// el parser no sabe de la red: el token existe para que reenviar una operacion
/// tras un corte no la duplique, y eso es asunto de la cola. Cada paso se lleva
/// el suyo, porque cada uno viaja como una operacion aparte.
List<Comando> comandosDe(Interpretacion interpretacion, String Function() token) =>
    interpretacion.pasos
        .map((paso) => Comando(
              tipo: paso.tipo,
              carga: paso.comando,
              tokenCliente: token(),
              origen: 'VOZ',
            ))
        .toList();
