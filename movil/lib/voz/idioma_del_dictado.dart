/// Con que idioma pedirle al aparato que escuche.
///
/// El reconocedor -el del navegador y el de Android- solo acepta variantes del
/// castellano CON PAIS. Aca se pedia `es_419`, el tag de macro-region de
/// Latinoamerica, y el dictado no funcionaba: el servicio rechaza el idioma y
/// lo informa como `network`, un error que hace buscar el problema en la red,
/// en los permisos del microfono o en el navegador, que es donde no esta.
///
/// Medido el 21 de septiembre de 2026 contra el reconocedor real: `es-419`,
/// `es` y no fijar nada fallan los tres; `es-BO`, `es-PE`, `es-CL`, `es-MX`,
/// `es-AR`, `es-ES` y `es-US` andan. De ahi que NO alcance con quitar la linea
/// y dejar que el aparato elija.
///
/// En el navegador se pudo fijar `es-BO` y comprobarlo con los oidos. En el
/// telefono no: cada aparato trae su propia lista de idiomas instalados y este
/// codigo nunca corrio en uno. Por eso no se fija un tag a ciegas -seria
/// cambiar una suposicion por otra- sino que se elige de lo que el aparato
/// declara tener, y si no hay ningun castellano servible se devuelve nulo para
/// que el plugin use el idioma del sistema.
library;

/// El pais que se prefiere si esta disponible.
const String paisPreferido = 'BO';

/// Un castellano utilizable: `es` seguido de un separador y DOS LETRAS de pais.
/// Deja afuera `es_419` -el pais son digitos-, `es` a secas -no hay pais- y
/// `est_EE`, que es estonio y no castellano.
final RegExp _castellanoConPais = RegExp(r'^es[-_]([A-Za-z]{2})$');

/// Elige el idioma del dictado entre los que el aparato dice tener.
///
/// Devuelve `null` cuando ninguno sirve, que el llamador tiene que entender
/// como "no fijes ninguno" y no como un error.
String? idiomaDelDictado(List<String> disponibles) {
  final servibles = disponibles.where((tag) => _castellanoConPais.hasMatch(tag)).toList();
  if (servibles.isEmpty) return null;

  for (final tag in servibles) {
    if (_pais(tag) == paisPreferido) return tag;
  }
  // Antes que el de Espana va cualquier otro de America: el reconocedor acierta
  // bastante mas con la variante de por aca, que es la razon por la que esto
  // alguna vez quiso decir `es_419`.
  for (final tag in servibles) {
    if (_pais(tag) != 'ES') return tag;
  }
  return servibles.first;
}

String _pais(String tag) => tag.substring(3).toUpperCase();
