/// Tipos tal como los dice una persona, llevados a un nombre unico.
///
/// Espejo de TiposDeclarados.java. Del otro lado lo comparten el dictado y la
/// lectura de una fotografia, y esa union no es comodidad: si cada canal
/// normalizara por su cuenta, el mismo modelo terminaria con "texto" en un
/// atributo y "String" en otro, el generador emitiria dos tipos distintos para
/// lo mismo y la exportacion XMI declararia dos tipos de datos donde hay uno.
/// Por eso el telefono tiene que decir exactamente lo mismo.
///
/// Un tipo que no esta en la tabla se respeta tal cual: puede ser otra clase
/// del modelo, o un enumerado que la persona va a crear despues. Inventar una
/// equivalencia seria peor que no tener ninguna.

const _equivalencias = <String, String>{
  // Texto
  'texto': 'String', 'cadena': 'String', 'string': 'String',
  'caracteres': 'String', 'char': 'String', 'varchar': 'String',
  'text': 'String', 'str': 'String',
  // Enteros
  'entero': 'Integer', 'numero': 'Integer', 'int': 'Integer',
  'integer': 'Integer', 'number': 'Integer',
  'largo': 'Long', 'long': 'Long', 'bigint': 'Long',
  // Decimales
  'decimal': 'Decimal', 'importe': 'Decimal', 'precio': 'Decimal',
  'monto': 'Decimal', 'real': 'Decimal', 'double': 'Decimal',
  'float': 'Decimal', 'money': 'Decimal',
  // Booleanos
  'booleano': 'Boolean', 'logico': 'Boolean', 'bool': 'Boolean',
  'boolean': 'Boolean',
  // Fechas
  'fecha': 'Date', 'date': 'Date',
  'fechayhora': 'DateTime', 'fechahora': 'DateTime', 'datetime': 'DateTime',
  'marcadetiempo': 'DateTime', 'timestamp': 'DateTime',
  'hora': 'Time', 'time': 'Time',
  // Identificadores
  'uuid': 'UUID', 'guid': 'UUID',
};

String normalizarTipo(String? escrito) {
  if (escrito == null || escrito.trim().isEmpty) return 'String';

  final limpio = escrito.trim();
  final clave = limpio
      .toLowerCase()
      .replaceAll(RegExp(r'[^\p{L}\p{N}]', unicode: true), '');

  final conocido = _equivalencias[clave];
  if (conocido != null) return conocido;

  return limpio[0].toUpperCase() + limpio.substring(1);
}
