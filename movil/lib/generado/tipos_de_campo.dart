/// Espejo de TipoJava.java, reducido a lo que el formulario necesita saber.
///
/// Alla la traduccion va a tipos de Java porque el destino es codigo; aca va a
/// una clase de campo porque el destino es un teclado y una validacion. Las
/// equivalencias de entrada son las mismas, y tienen que seguir siendolo.
enum ClaseDeCampo { texto, entero, decimal, booleano, fecha, fechaHora, hora, identificador, desconocido }

const _equivalencias = <String, ClaseDeCampo>{
  'string': ClaseDeCampo.texto,
  'str': ClaseDeCampo.texto,
  'texto': ClaseDeCampo.texto,
  'cadena': ClaseDeCampo.texto,
  'char': ClaseDeCampo.texto,
  'varchar': ClaseDeCampo.texto,
  'text': ClaseDeCampo.texto,
  'int': ClaseDeCampo.entero,
  'integer': ClaseDeCampo.entero,
  'entero': ClaseDeCampo.entero,
  'number': ClaseDeCampo.entero,
  'numero': ClaseDeCampo.entero,
  'long': ClaseDeCampo.entero,
  'bigint': ClaseDeCampo.entero,
  'short': ClaseDeCampo.entero,
  'decimal': ClaseDeCampo.decimal,
  'bigdecimal': ClaseDeCampo.decimal,
  'money': ClaseDeCampo.decimal,
  'importe': ClaseDeCampo.decimal,
  'precio': ClaseDeCampo.decimal,
  'double': ClaseDeCampo.decimal,
  'float': ClaseDeCampo.decimal,
  'real': ClaseDeCampo.decimal,
  'boolean': ClaseDeCampo.booleano,
  'bool': ClaseDeCampo.booleano,
  'booleano': ClaseDeCampo.booleano,
  'logico': ClaseDeCampo.booleano,
  'date': ClaseDeCampo.fecha,
  'fecha': ClaseDeCampo.fecha,
  'localdate': ClaseDeCampo.fecha,
  'datetime': ClaseDeCampo.fechaHora,
  'fechahora': ClaseDeCampo.fechaHora,
  'localdatetime': ClaseDeCampo.fechaHora,
  'timestamp': ClaseDeCampo.fechaHora,
  'instant': ClaseDeCampo.fechaHora,
  'time': ClaseDeCampo.hora,
  'hora': ClaseDeCampo.hora,
  'uuid': ClaseDeCampo.identificador,
  'guid': ClaseDeCampo.identificador,
};

ClaseDeCampo claseDeCampoDe(String tipoDelDiagrama) =>
    _equivalencias[tipoDelDiagrama.trim().toLowerCase()] ?? ClaseDeCampo.desconocido;
