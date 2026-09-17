/// Las clases que ya estan en el diagrama, para resolver las referencias que
/// llegan dictadas.
///
/// Es el espejo de ContextoDelDiagrama.java. Sin esto, "Paciente tiene muchas
/// Consultas" y "Paciente tiene muchas deudas" son indistinguibles: la primera
/// relaciona dos clases y la segunda describe un atributo, y lo unico que las
/// separa es si el segundo nombre corresponde a una clase que existe.
///
/// La unica diferencia con el servidor es que aca el identificador es texto: en
/// el telefono los ids viajan dentro de la carga del comando, que es un mapa
/// JSON.

library;

/// Acentos que hay que sacar.
///
/// Dart no trae la normalizacion NFD que usa el servidor, asi que la tabla va
/// explicita. Es corta porque el idioma es uno solo, y equivocarse se nota
/// enseguida: el corpus compartido tiene un caso con "creá la clase Médico."
const _acentos = {
  'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ü': 'u',
  'Á': 'A', 'É': 'E', 'Í': 'I', 'Ó': 'O', 'Ú': 'U', 'Ü': 'U',
  'à': 'a', 'è': 'e', 'ì': 'i', 'ò': 'o', 'ù': 'u',
  'À': 'A', 'È': 'E', 'Ì': 'I', 'Ò': 'O', 'Ù': 'U',
  'ñ': 'n', 'Ñ': 'N',
};

String sinAcentos(String texto) {
  final salida = StringBuffer();
  for (final letra in texto.split('')) {
    salida.write(_acentos[letra] ?? letra);
  }
  return salida.toString();
}

/// Forma comparable de un nombre: sin acentos, sin nada que no sea letra o
/// digito, en minuscula. Quien dicta no pronuncia mayusculas, el reconocedor
/// acentua como le parece y la foto de una pizarra confunde la caja.
String clave(String texto) => sinAcentos(texto)
    .replaceAll(RegExp(r'[^\p{L}\p{N}]', unicode: true), '')
    .toLowerCase();

class ClaseConocida {
  const ClaseConocida({required this.id, required this.nombre});

  final String id;
  final String nombre;
}

class ContextoDelDiagrama {
  ContextoDelDiagrama._(this._porNombre);

  factory ContextoDelDiagrama.de(List<ClaseConocida> clases) {
    final indice = <String, ClaseConocida>{};
    for (final clase in clases) {
      indice[clave(clase.nombre)] = clase;
    }
    return ContextoDelDiagrama._(indice);
  }

  factory ContextoDelDiagrama.vacio() => ContextoDelDiagrama._({});

  /// En orden de carga: las sugerencias del parser toman el primer nombre como
  /// ejemplo y el segundo como el otro extremo de una relacion.
  final Map<String, ClaseConocida> _porNombre;

  /// Primero exacto. Si no, parcial, pero SOLO cuando es unica: dictando es
  /// facil que "historia" quiera decir "HistoriaClinica" o que "Consultas" sea
  /// el plural de Consulta, pero con dos clases que empiezan igual, elegir una
  /// seria adivinar.
  ClaseConocida? resolver(String? texto) {
    if (texto == null || texto.trim().isEmpty) return null;
    final buscada = clave(texto);

    final exacta = _porNombre[buscada];
    if (exacta != null) return exacta;

    final parciales = _porNombre.entries
        .where((entrada) =>
            entrada.key.startsWith(buscada) || buscada.startsWith(entrada.key))
        .map((entrada) => entrada.value)
        .toList();

    return parciales.length == 1 ? parciales.first : null;
  }

  List<String> nombres() => _porNombre.values.map((clase) => clase.nombre).toList();
}
