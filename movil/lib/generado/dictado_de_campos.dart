import '../tipos.dart';
import 'tipos_de_campo.dart';

/// El dictado, puesto en los campos de un formulario.
///
/// Aparte del widget a proposito: es logica pura -texto entra, texto sale- y
/// es donde se cuelan los errores que no se ven hasta que el backend contesta
/// 500. Con una pantalla en el medio cada caso costaria un `testWidgets` con
/// microfono simulado; aca cuesta una linea.
///
/// Dos trabajos, y el orden importa:
///
/// 1. **Donde cae** lo dictado. Un valor suelto va al primer campo vacio; si
///    la frase nombra un campo, va a ese campo aunque ya tenga algo.
/// 2. **Como se escribe** en ese campo: una fecha se dicta en castellano
///    -«23 de marzo del 2000»- y la columna espera `2000-03-23`. Convertir es
///    obligatorio, porque lo que el reconocedor entrega no entra en la columna
///    tal cual.
///
/// Lo que NO hace: inventar. Si un valor no se puede llevar al tipo del campo
/// se deja el texto dictado tal como se escucho y el usuario lo corrige con el
/// teclado antes de dar de alta. Una fecha adivinada es peor que un campo mal
/// escrito: el campo mal escrito se ve.

/// Un valor dictado y el campo donde va, ya convertido al tipo del diagrama.
class CampoDictado {
  const CampoDictado(this.campo, this.valor);

  /// El nombre del atributo tal como esta escrito en el diagrama.
  final String campo;

  /// Lo que hay que escribir en la caja de texto.
  final String valor;

  @override
  String toString() => '$campo=$valor';
}

/// Lo que hay que hacerle al formulario despues de un dictado.
class DictadoDeFormulario {
  const DictadoDeFormulario({this.campos = const [], this.esAlta = false});

  const DictadoDeFormulario.alta() : this(esAlta: true);
  const DictadoDeFormulario.nada() : this();

  /// Los campos a llenar, en el orden en que se dijeron.
  final List<CampoDictado> campos;

  /// Se dijo «agregar»: hay que validar y crear, no llenar nada.
  final bool esAlta;

  /// Ni llena ni crea: la frase no se pudo ubicar en ningun campo.
  bool get vacio => campos.isEmpty && !esAlta;
}

/// Las palabras que disparan el alta.
///
/// Se comparan por [claveDeNombre], que es lo que hace que «agrega» y «agregá»
/// -el reconocedor de Android escribe las dos- sean la misma palabra sin tener
/// que listarlas dos veces.
const _ordenesDeAlta = {
  'agregar', 'agrega', 'agregalo', 'agregala',
  'anadir', 'anadi', 'anade',
  'guardar', 'guarda', 'guardalo', 'guardala',
  'crear', 'crea', 'crealo', 'creala',
  'dardealta', 'listo',
};

/// Si la frase entera es una orden de crear el registro.
///
/// Se exige que sea la frase ENTERA: «agregar» crea, pero «agregar sal» en un
/// campo de texto es un valor. Un verbo suelto no puede ser el valor de nada.
bool esOrdenDeAlta(String frase) => _ordenesDeAlta.contains(claveDeNombre(frase));

/// Decide que hacer con lo dictado sobre un formulario a medio llenar.
///
/// [campos] son los atributos editables, en el orden en que se ven en la
/// pantalla; [valores] es lo que cada uno tiene escrito ahora mismo -es lo que
/// decide cual es «el primer campo vacio»-.
///
/// La frase se parte donde aparece el nombre de un campo, asi que una frase
/// corrida como «77 8954 nombre Santiago nacimiento 16 de julio del 2004»
/// -que es exactamente lo que el telefono escucho y metio entero en `ci`-
/// llena los tres campos en vez de ensuciar uno.
DictadoDeFormulario ubicarDictado({
  required String frase,
  required List<Atributo> campos,
  required Map<String, String> valores,
}) {
  final limpia = frase.trim();
  if (limpia.isEmpty) return const DictadoDeFormulario.nada();
  if (esOrdenDeAlta(limpia)) return const DictadoDeFormulario.alta();
  if (campos.isEmpty) return const DictadoDeFormulario.nada();

  final trozos = _partirPorCampos(limpia, campos);
  if (trozos.isEmpty) return const DictadoDeFormulario.nada();

  // Copia local: un trozo suelto ocupa un campo vacio, y el siguiente trozo
  // suelto tiene que ver ese campo como ocupado. Sin esto dos valores sueltos
  // en la misma frase caerian los dos en el mismo lugar.
  final ocupados = {
    for (final campo in campos) campo.nombre: (valores[campo.nombre] ?? '').trim(),
  };

  final puestos = <CampoDictado>[];
  for (final trozo in trozos) {
    final atributo = trozo.campo ??
        campos.where((c) => (ocupados[c.nombre] ?? '').isEmpty).firstOrNull;
    // Sin campo nombrado y sin ningun hueco libre no hay donde ponerlo. Se
    // saltea en vez de pisar algo al azar: pisar sin que nadie lo pida es como
    // se pierde lo que ya estaba bien cargado.
    if (atributo == null) continue;

    final valor = valorParaCampo(trozo.texto, claseDeCampoDe(atributo.tipo));
    if (valor.isEmpty) continue;
    ocupados[atributo.nombre] = valor;
    puestos.add(CampoDictado(atributo.nombre, valor));
  }

  return DictadoDeFormulario(campos: puestos);
}

/// Un pedazo de la frase y, si la frase lo nombro, el campo al que va.
class _Trozo {
  const _Trozo(this.campo, this.texto);

  final Atributo? campo;
  final String texto;
}

/// Cuantas palabras seguidas se prueban como nombre de un campo.
///
/// Tres alcanza para lo que se ve en un diagrama: `fechaNacimiento` dictado se
/// escucha «fecha nacimiento», y `fecha de nacimiento` llega a tres.
const _palabrasDeUnNombre = 3;

List<_Trozo> _partirPorCampos(String frase, List<Atributo> campos) {
  final palabras = frase.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  final trozos = <_Trozo>[];

  Atributo? campoActual;
  var acumulado = <String>[];

  void cerrar() {
    final texto = acumulado.join(' ').trim();
    if (texto.isNotEmpty) trozos.add(_Trozo(campoActual, texto));
    acumulado = <String>[];
  }

  var i = 0;
  while (i < palabras.length) {
    // De mas largo a mas corto: si hubiera un campo `fecha` y otro
    // `fechaNacimiento`, «fecha nacimiento 23 de marzo» tiene que caer en el
    // largo, que es el que el usuario nombro entero.
    Atributo? encontrado;
    var largo = 0;
    for (var n = _palabrasDeUnNombre; n >= 1; n--) {
      if (i + n > palabras.length) continue;
      final clave = claveDeNombre(palabras.sublist(i, i + n).join(' '));
      if (clave.isEmpty) continue;
      final campo =
          campos.where((c) => claveDeNombre(c.nombre) == clave).firstOrNull;
      if (campo != null) {
        encontrado = campo;
        largo = n;
        break;
      }
    }

    if (encontrado != null) {
      cerrar();
      campoActual = encontrado;
      i += largo;
      continue;
    }

    acumulado.add(palabras[i]);
    i++;
  }
  cerrar();

  // Un campo nombrado sin nada detras -«nacimiento» y se acabo- no deja trozo,
  // y esta bien: no hay valor que poner y no se puede adivinar cual seria.
  return trozos;
}

// ---------- Conversion al tipo del campo -------------------------------

/// Lleva lo dictado al tipo que el campo tiene en el diagrama.
///
/// Devuelve el texto dictado tal cual cuando no puede convertir: es el unico
/// comportamiento seguro. El usuario ve lo que dijo, entiende que no se
/// entendio, y lo corrige; una conversion forzada dejaria un dato plausible y
/// falso en la base.
String valorParaCampo(String dicho, ClaseDeCampo clase) {
  final texto = dicho.trim();
  if (texto.isEmpty) return '';
  return switch (clase) {
        ClaseDeCampo.entero => _entero(texto),
        ClaseDeCampo.decimal => _decimal(texto),
        ClaseDeCampo.booleano => _booleano(texto),
        ClaseDeCampo.fecha => _fechaIso(texto),
        ClaseDeCampo.fechaHora => _fechaHoraIso(texto),
        _ => null,
      } ??
      texto;
}

/// «77 8954» -> «778954».
///
/// El reconocedor corta los numeros largos en grupos: un carnet dictado de
/// corrido vuelve con espacios en el medio. Se pegan los grupos, y tambien se
/// sacan los puntos y comas de miles. Lo que quede tiene que ser SOLO digitos:
/// si hay letras no se recorta el numero de adentro -«calle 5» no es 5-, se
/// prueba con numeros en palabras y si tampoco, se deja el texto.
String? _entero(String texto) {
  final pegado = texto.replaceAll(RegExp(r'[\s.,]'), '');
  if (RegExp(r'^[+-]?\d+$').hasMatch(pegado)) {
    return pegado.startsWith('+') ? pegado.substring(1) : pegado;
  }
  return enteroDePalabras(texto)?.toString();
}

/// «12,5» y «doce coma cinco» -> «12.5». La coma es como se dicta y como se
/// escribe aca; el punto es lo unico que el backend parsea.
String? _decimal(String texto) {
  var pegado = texto.replaceAll(RegExp(r'\s'), '');
  if (RegExp(r'^[+-]?\d+$').hasMatch(pegado)) return _sinMas(pegado);

  // Miles y decimales juntos, en las dos convenciones. Se decide por cual de
  // los dos separadores viene ultimo, que es el decimal.
  if (RegExp(r'^[+-]?\d{1,3}(\.\d{3})+,\d+$').hasMatch(pegado)) {
    pegado = pegado.replaceAll('.', '');
  } else if (RegExp(r'^[+-]?\d{1,3}(,\d{3})+\.\d+$').hasMatch(pegado)) {
    pegado = pegado.replaceAll(',', '');
  }
  pegado = pegado.replaceAll(',', '.');
  if (RegExp(r'^[+-]?\d+\.\d+$').hasMatch(pegado)) return _sinMas(pegado);

  final partes = texto.trim().split(RegExp(r'\s+(?:coma|punto)\s+', caseSensitive: false));
  if (partes.length != 2) return null;
  final entera = _numeroDe(partes[0]);
  final decimales = _digitosDe(partes[1]);
  if (entera == null || decimales == null) return null;
  return '$entera.$decimales';
}

String _sinMas(String numero) => numero.startsWith('+') ? numero.substring(1) : numero;

/// La parte de despues de la coma se lee digito por digito cuando se puede
/// -«cero cinco» son dos digitos, 05, y no la suma 5-. Si no son digitos
/// sueltos se toma como numero entero: «veinticinco» -> 25.
String? _digitosDe(String texto) {
  final palabras = texto.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty);
  if (palabras.isEmpty) return null;

  final digitos = StringBuffer();
  var todosSueltos = true;
  for (final palabra in palabras) {
    final clave = claveDeNombre(palabra);
    if (RegExp(r'^\d+$').hasMatch(clave)) {
      digitos.write(clave);
      continue;
    }
    final valor = _unidades[clave];
    if (valor == null || valor > 9) {
      todosSueltos = false;
      break;
    }
    digitos.write(valor);
  }
  if (todosSueltos) return digitos.toString();
  return _numeroDe(texto)?.toString();
}

const _verdaderos = {'si', 'verdadero', 'cierto', 'true', 'afirmativo', 'correcto', 'activo', '1'};
const _falsos = {'no', 'falso', 'false', 'negativo', 'incorrecto', 'inactivo', '0'};

String? _booleano(String texto) {
  final clave = claveDeNombre(texto);
  if (_verdaderos.contains(clave)) return 'true';
  if (_falsos.contains(clave)) return 'false';
  return null;
}

const _meses = <String, int>{
  'enero': 1,
  'febrero': 2,
  'marzo': 3,
  'abril': 4,
  'mayo': 5,
  'junio': 6,
  'julio': 7,
  'agosto': 8,
  'septiembre': 9,
  'setiembre': 9,
  'octubre': 10,
  'noviembre': 11,
  'diciembre': 12,
};

/// Palabras que rodean una fecha dictada y no dicen nada: «el 23 **de** marzo
/// **del** 2004», «**dia** 3 **de** mayo».
const _relleno = {'el', 'la', 'de', 'del', 'dia', 'ano', 'en', 'los', 'anio'};

/// Lo dictado, en `aaaa-mm-dd`.
///
/// Lo que ya viene en ISO pasa tal cual: alguien puede dictar «2000-03-23» y
/// el reconocedor lo escribe asi.
String? _fechaIso(String texto) {
  final limpio = texto.trim();

  final iso = RegExp(r'^(\d{4})-(\d{1,2})-(\d{1,2})$').firstMatch(limpio);
  if (iso != null) {
    return _armarFecha(
      int.parse(iso.group(1)!),
      int.parse(iso.group(2)!),
      int.parse(iso.group(3)!),
    );
  }

  // Como se escribe a mano en toda Latinoamerica: dia primero.
  final barras = RegExp(r'^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$').firstMatch(limpio);
  if (barras != null) {
    return _armarFecha(
      int.parse(barras.group(3)!),
      int.parse(barras.group(2)!),
      int.parse(barras.group(1)!),
    );
  }

  final palabras = limpio.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  var donde = -1;
  int? mes;
  for (var i = 0; i < palabras.length; i++) {
    final encontrado = _meses[claveDeNombre(palabras[i])];
    if (encontrado != null) {
      mes = encontrado;
      donde = i;
      break;
    }
  }
  if (mes == null) return null;

  final antes = _sinRelleno(palabras.sublist(0, donde));
  final despues = _sinRelleno(palabras.sublist(donde + 1));
  final dia = _numeroDe(antes.join(' '));
  final anio = _numeroDe(despues.join(' '));
  // Sin anio no hay fecha, y no se pone el de hoy: inventar un dato que nadie
  // dicto es justo lo que no puede pasar.
  if (dia == null || anio == null) return null;
  return _armarFecha(anio, mes, dia);
}

/// Un `LocalDateTime` con la fecha dictada y la hora en cero.
///
/// El backend generado parsea `aaaa-mm-ddThh:mm:ss`; una fecha pelada no entra
/// en esa columna. La hora en cero no se inventa nada: nadie la dicto y cero
/// es lo que significa «no se dijo».
String? _fechaHoraIso(String texto) {
  final limpio = texto.trim();
  if (RegExp(r'^\d{4}-\d{2}-\d{2}T').hasMatch(limpio)) return limpio;
  final fecha = _fechaIso(limpio);
  return fecha == null ? null : '${fecha}T00:00:00';
}

List<String> _sinRelleno(List<String> palabras) =>
    palabras.where((p) => !_relleno.contains(claveDeNombre(p))).toList();

/// Arma la fecha y comprueba que exista de verdad: `DateTime` acomoda solo un
/// 30 de febrero al 2 de marzo, y guardar en silencio un dia que no es el que
/// se dicto es peor que dejar el texto para que lo arreglen a mano.
String? _armarFecha(int anio, int mes, int dia) {
  if (anio < 1000 || anio > 9999 || mes < 1 || mes > 12 || dia < 1 || dia > 31) return null;
  final fecha = DateTime(anio, mes, dia);
  if (fecha.year != anio || fecha.month != mes || fecha.day != dia) return null;
  return '${anio.toString().padLeft(4, '0')}-'
      '${mes.toString().padLeft(2, '0')}-'
      '${dia.toString().padLeft(2, '0')}';
}

/// Un numero, venga en digitos o en palabras.
int? _numeroDe(String texto) {
  final pegado = texto.replaceAll(RegExp(r'[\s.,]'), '');
  if (RegExp(r'^\d+$').hasMatch(pegado)) return int.tryParse(pegado);
  return enteroDePalabras(texto);
}

const _unidades = <String, int>{
  'cero': 0,
  'un': 1,
  'uno': 1,
  'una': 1,
  'primero': 1,
  'primer': 1,
  'dos': 2,
  'tres': 3,
  'cuatro': 4,
  'cinco': 5,
  'seis': 6,
  'siete': 7,
  'ocho': 8,
  'nueve': 9,
  'diez': 10,
  'once': 11,
  'doce': 12,
  'trece': 13,
  'catorce': 14,
  'quince': 15,
  'dieciseis': 16,
  'diecisiete': 17,
  'dieciocho': 18,
  'diecinueve': 19,
  'veinte': 20,
  'veintiuno': 21,
  'veintiun': 21,
  'veintidos': 22,
  'veintitres': 23,
  'veinticuatro': 24,
  'veinticinco': 25,
  'veintiseis': 26,
  'veintisiete': 27,
  'veintiocho': 28,
  'veintinueve': 29,
  'treinta': 30,
  'cuarenta': 40,
  'cincuenta': 50,
  'sesenta': 60,
  'setenta': 70,
  'ochenta': 80,
  'noventa': 90,
  'cien': 100,
  'ciento': 100,
  'doscientos': 200,
  'trescientos': 300,
  'cuatrocientos': 400,
  'quinientos': 500,
  'seiscientos': 600,
  'setecientos': 700,
  'ochocientos': 800,
  'novecientos': 900,
};

/// «dos mil cuatro» -> 2004, «treinta y cinco» -> 35.
///
/// Llega hasta los millones, que es de sobra para un anio, un dia o un importe
/// dictado. Devuelve null en cuanto aparece una palabra que no es numero: es
/// lo que separa «doce» de «Juan», y sin eso «Juan» terminaria valiendo cero.
int? enteroDePalabras(String texto) {
  final palabras = texto
      .split(RegExp(r'\s+'))
      .map(claveDeNombre)
      .where((p) => p.isNotEmpty && p != 'y')
      .toList();
  if (palabras.isEmpty) return null;

  var total = 0;
  var parcial = 0;
  var hubo = false;

  for (final palabra in palabras) {
    final unidad = _unidades[palabra];
    if (unidad != null) {
      parcial += unidad;
      hubo = true;
      continue;
    }
    if (RegExp(r'^\d+$').hasMatch(palabra)) {
      parcial += int.parse(palabra);
      hubo = true;
      continue;
    }
    if (palabra == 'mil') {
      parcial = (parcial == 0 ? 1 : parcial) * 1000;
      total += parcial;
      parcial = 0;
      hubo = true;
      continue;
    }
    if (palabra == 'millon' || palabra == 'millones') {
      total = (total + parcial == 0 ? 1 : total + parcial) * 1000000;
      parcial = 0;
      hubo = true;
      continue;
    }
    return null;
  }

  return hubo ? total + parcial : null;
}
