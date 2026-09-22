/// Espejo de Nombres.java. Las reglas tienen que ser las mismas: el telefono
/// deduce la ruta del backend generado a partir del nombre de la clase, y si
/// pluralizara distinto llamaria a un endpoint que no existe y el error se
/// leeria como "404", sin pista de que el problema es una letra.

/// Segmento de ruta HTTP: "HistoriaClinica" da "historia-clinicas".
String rutaDe(String nombreDeClase) {
  final partes = _palabras(nombreDeClase);
  if (partes.isEmpty) return '';
  return _plural(partes.join('-').toLowerCase());
}

String _plural(String palabra) {
  if (palabra.isEmpty || palabra.endsWith('s')) return palabra;
  final ultima = palabra[palabra.length - 1];
  if ('aeiou'.contains(ultima)) return '${palabra}s';
  if (ultima == 'z') return '${palabra.substring(0, palabra.length - 1)}ces';
  return '${palabra}es';
}

List<String> _palabras(String nombre) {
  final limpio = _sinAcentos(nombre)
      .replaceAll(RegExp(r'[^A-Za-z0-9]+'), ' ')
      // Corta entre minuscula y mayuscula para respetar el camello que ya
      // traiga el nombre original.
      .replaceAllMapped(RegExp(r'([a-z0-9])([A-Z])'), (m) => '${m[1]} ${m[2]}')
      .trim();
  return limpio.isEmpty ? [] : limpio.split(RegExp(r'\s+'));
}

String _sinAcentos(String texto) {
  const conAcento = 'áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ';
  const sinAcento = 'aaaaaeeeeiiiiooooouuuuncAAAAAEEEEIIIIOOOOOUUUUNC';
  final salida = StringBuffer();
  for (final rune in texto.runes) {
    final caracter = String.fromCharCode(rune);
    final donde = conAcento.indexOf(caracter);
    salida.write(donde >= 0 ? sinAcento[donde] : caracter);
  }
  return salida.toString();
}
