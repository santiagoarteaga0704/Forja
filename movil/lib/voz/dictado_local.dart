import '../comandos.dart';
import 'contexto.dart';
import 'interpretacion.dart';
import 'parser_voz.dart';

/// El dictado, interpretado en el aparato.
///
/// Esto es lo que hace que dictar funcione en modo avion: la frase no sale del
/// telefono. El reconocimiento de voz lo hace Android -y tambien sin red, si el
/// paquete de espanol esta descargado- y de ahi en adelante todo es la gramatica
/// que vive en parser_voz.dart.
///
/// Del servidor se replica una segunda regla ademas de la gramatica: la
/// cuadricula. El parser no pone coordenadas porque dictando no se dicen
/// coordenadas, asi que alguien tiene que ubicarlas o todas las clases quedan
/// apiladas en el origen, invisibles salvo la ultima.

/// Espejo de Cuadricula.java. Los tres numeros tienen que ser los mismos que
/// alli: si el telefono y el servidor distribuyeran distinto, un modelo armado
/// en parte en cada uno tendria dos disposiciones superpuestas.
///
/// El paso de 380 deja sitio para el conector y no solo para la caja. Una clase
/// mide 220, asi que un paso de 260 dejaba 40 de hueco: entraba la linea y nada
/// mas, el rombo de una composicion ocupa 14 y cada multiplicidad se dibuja a 26
/// de su extremo, de modo que los dos rotulos se montaban uno sobre otro.
const _pasoX = 380.0;
const _pasoY = 260.0;
const _porFila = 4;

/// Devuelve la carga con coordenadas si es un alta de clase; cualquier otro
/// comando pasa sin cambios.
Map<String, dynamic> ubicarEnCuadricula(Map<String, dynamic> carga, int casilla) => {
      ...carga,
      'posX': (casilla % _porFila) * _pasoX,
      'posY': (casilla ~/ _porFila) * _pasoY,
    };

/// Lo que sale de dictar: lo que se entendio, para mostrarlo, y los comandos,
/// para aplicarlos.
class DictadoLocal {
  const DictadoLocal({required this.interpretacion, required this.comandos});

  final Interpretacion interpretacion;
  final List<Comando> comandos;
}

final _parser = ParserVoz();

/// Interpreta una frase dictada. No toca la red: el telefono entiende solo,
/// tenga o no conexion.
///
/// La casilla de la que parte la cuadricula son las clases que ya existen, que
/// es exactamente lo que cuenta el servidor.
DictadoLocal interpretarDictado(
    String frase, ContextoDelDiagrama contexto, String Function() token) {
  final interpretacion = _parser.interpretar(frase, contexto);

  var casilla = contexto.nombres().length;
  final ubicados = <Paso>[];
  for (final paso in interpretacion.pasos) {
    if (paso.tipo == 'CLASE_CREAR') {
      ubicados.add(Paso(tipo: paso.tipo, comando: ubicarEnCuadricula(paso.comando, casilla)));
      casilla++;
    } else {
      ubicados.add(paso);
    }
  }

  // La interpretacion que se devuelve es la ya ubicada: es la que de verdad se
  // aplico, no la que salio del parser antes de tener coordenadas.
  final ubicada = Interpretacion(
    frase: interpretacion.frase,
    entendida: interpretacion.entendida,
    pasos: ubicados,
    explicacion: interpretacion.explicacion,
    sugerencias: interpretacion.sugerencias,
  );

  return DictadoLocal(
      interpretacion: ubicada, comandos: comandosDe(ubicada, token));
}
