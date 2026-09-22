# El teléfono opera el backend generado — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que la app móvil deje de ser un visor del diagrama de FORJA y pase a ser el frontend del backend generado: pantallas CRUD derivadas del diagrama en tiempo de ejecución, operativas sin conexión, manejables por voz y asistidas por un modelo que corre en el aparato.

**Architecture:** El teléfono ya guarda el diagrama sin conexión. De ese diagrama deduce las rutas y los campos del backend generado, porque el generador emite siempre la misma forma. Las lecturas salen del almacén local y las escrituras van a una cola idempotente que se vacía cuando el backend está al alcance. La voz la interpreta una gramática determinista hermana de la que ya existe, esta vez en el dominio de los registros; el modelo solo propone frases que esa gramática vuelve a interpretar.

**Tech Stack:** Flutter/Dart, `http`, `path_provider`, `speech_to_text`, `flutter_gemma` (se agrega en la tarea 9), Gemma 3 1B int4 `.task`.

**Spec:** `docs/superpowers/specs/2026-09-22-cliente-movil-del-backend-generado-design.md`

## Global Constraints

- **Idioma del código:** nombres de clases, métodos, variables y comentarios en castellano, como todo el repositorio.
- **Sin conexión:** ninguna pantalla puede requerir red para abrirse. La única operación que puede exigirla es la primera bajada del diagrama desde FORJA.
- **Espejos exactos:** `nombres.dart` debe producir la misma salida que `Nombres.java` y `tipos_de_campo.dart` la misma clasificación que `TipoJava.java`. Si divergen, el teléfono llama a rutas que no existen.
- **El modelo nunca escribe:** Gemma propone una frase canónica; la gramática determinista es lo único que produce operaciones.
- **Pruebas:** `cd movil && flutter test`. Toda tarea termina con la suite entera en verde, no solo con su prueba.
- **Dependencias nuevas:** ninguna salvo `flutter_gemma` en la tarea 9.
- **Commits:** uno por tarea, en castellano, describiendo el porqué y no el qué.

**Orden deliberado:** las tareas 1 a 6 son la demostración mínima —pantallas CRUD del backend generado, funcionando sin conexión— y no dependen ni de la voz ni del modelo. La 7 agrega la voz, la 8 la arregla para que de verdad no use red, y la 9 es el modelo. **La 10 no es opcional en ningún escenario:** hasta que corra en el teléfono, nada de esto está verificado.

Si el tiempo se acaba, que se acabe en la 7 u 8: lo hecho hasta ahí sigue siendo una demostración completa y honesta.

---

### Task 1: El espejo de rutas

El backend generado publica `/api/historia-clinicas` para una clase `HistoriaClinica`. El teléfono tiene que deducir ese segmento del nombre de la clase, con las mismas reglas que usó el generador.

**Files:**
- Create: `movil/lib/generado/nombres.dart`
- Test: `movil/test/generado/nombres_test.dart`
- Reference: `src/main/java/bo/forja/backend/generador/Nombres.java:74-97`

**Interfaces:**
- Produces: `String rutaDe(String nombreDeClase)` — devuelve el segmento de ruta, sin barras.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/nombres_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/nombres.dart';

void main() {
  // Este corpus es el contrato con Nombres.java. Si alguna vez cambia alla,
  // estos casos son los que avisan aca.
  group('rutaDe', () {
    test('una palabra terminada en vocal agrega s', () {
      expect(rutaDe('Paciente'), 'pacientes');
      expect(rutaDe('Consulta'), 'consultas');
    });

    test('el camello se parte en palabras unidas por guion', () {
      expect(rutaDe('HistoriaClinica'), 'historia-clinicas');
    });

    test('los acentos se pierden, como en el codigo generado', () {
      expect(rutaDe('Médico'), 'medicos');
    });

    test('una palabra terminada en consonante agrega es', () {
      expect(rutaDe('Profesor'), 'profesores');
    });

    test('la z se vuelve ces', () {
      expect(rutaDe('Voz'), 'voces');
    });

    test('lo que ya termina en s no se pluraliza dos veces', () {
      expect(rutaDe('Mes'), 'mes');
    });

    test('los espacios y los guiones bajos separan igual que el camello', () {
      expect(rutaDe('historia_clinica'), 'historia-clinicas');
      expect(rutaDe('Historia Clinica'), 'historia-clinicas');
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/nombres_test.dart`
Expected: FAIL — `Error: Couldn't resolve the package 'forja_movil'` o `nombres.dart not found`.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/nombres.dart

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/nombres_test.dart`
Expected: PASS, 7 pruebas.

- [ ] **Step 5: Run the whole suite**

Run: `cd movil && flutter test`
Expected: todo en verde.

- [ ] **Step 6: Commit**

```bash
git add movil/lib/generado/nombres.dart movil/test/generado/nombres_test.dart
git commit -m "El telefono deduce las rutas del backend generado"
```

---

### Task 2: El espejo de tipos

Un atributo del diagrama dice «fecha», «texto» o «int». El formulario necesita saber qué teclado mostrar y qué validar; el cliente HTTP, cómo serializar.

**Files:**
- Create: `movil/lib/generado/tipos_de_campo.dart`
- Test: `movil/test/generado/tipos_de_campo_test.dart`
- Reference: `src/main/java/bo/forja/backend/generador/TipoJava.java:31-75`

**Interfaces:**
- Consumes: nada.
- Produces: `enum ClaseDeCampo { texto, entero, decimal, booleano, fecha, fechaHora, hora, identificador, desconocido }` y `ClaseDeCampo claseDeCampoDe(String tipoDelDiagrama)`.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/tipos_de_campo_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/tipos_de_campo.dart';

void main() {
  group('claseDeCampoDe', () {
    test('acepta las formas en ingles y en castellano', () {
      expect(claseDeCampoDe('String'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('texto'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('int'), ClaseDeCampo.entero);
      expect(claseDeCampoDe('entero'), ClaseDeCampo.entero);
      expect(claseDeCampoDe('boolean'), ClaseDeCampo.booleano);
      expect(claseDeCampoDe('logico'), ClaseDeCampo.booleano);
    });

    test('no distingue mayusculas', () {
      expect(claseDeCampoDe('TEXTO'), ClaseDeCampo.texto);
      expect(claseDeCampoDe('Fecha'), ClaseDeCampo.fecha);
    });

    test('separa las tres formas de tiempo, que se escriben distinto', () {
      expect(claseDeCampoDe('fecha'), ClaseDeCampo.fecha);
      expect(claseDeCampoDe('fechahora'), ClaseDeCampo.fechaHora);
      expect(claseDeCampoDe('hora'), ClaseDeCampo.hora);
    });

    test('lo monetario es decimal, no entero', () {
      expect(claseDeCampoDe('precio'), ClaseDeCampo.decimal);
      expect(claseDeCampoDe('importe'), ClaseDeCampo.decimal);
    });

    test('un tipo que no se reconoce no se inventa', () {
      // Puede ser otra clase del modelo o un enumerado que el usuario
      // agregara despues. Tratarlo como texto mentiria sobre su naturaleza.
      expect(claseDeCampoDe('Direccion'), ClaseDeCampo.desconocido);
    });
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/tipos_de_campo_test.dart`
Expected: FAIL — `tipos_de_campo.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/tipos_de_campo.dart

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/tipos_de_campo_test.dart`
Expected: PASS, 5 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/generado/tipos_de_campo.dart movil/test/generado/tipos_de_campo_test.dart
git commit -m "Cada tipo del diagrama sabe que campo le corresponde"
```

---

### Task 3: El cliente del backend generado

**Files:**
- Create: `movil/lib/generado/api_generada.dart`
- Test: `movil/test/generado/api_generada_test.dart`
- Reference: `src/main/java/bo/forja/backend/generador/EscritorCapas.java:140-180`

**Interfaces:**
- Consumes: `rutaDe(String)` de la tarea 1.
- Produces: `class ApiGenerada` con constructor `ApiGenerada({required String base, http.Client? cliente})` y los métodos `Future<List<Map<String, dynamic>>> listar(String clase)`, `Future<Map<String, dynamic>> crear(String clase, Map<String, dynamic> datos)`, `Future<Map<String, dynamic>> actualizar(String clase, String id, Map<String, dynamic> datos)`, `Future<void> borrar(String clase, String id)`.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/api_generada_test.dart
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/api_generada.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('listar pega en la ruta plural de la clase', () async {
    late Uri pedida;
    final api = ApiGenerada(
      base: 'http://192.168.0.7:8080',
      cliente: MockClient((peticion) async {
        pedida = peticion.url;
        return http.Response(jsonEncode([
          {'id': 1, 'nombre': 'Juan'}
        ]), 200, headers: {'content-type': 'application/json; charset=utf-8'});
      }),
    );

    final filas = await api.listar('Paciente');

    expect(pedida.path, '/api/pacientes');
    expect(filas.single['nombre'], 'Juan');
  });

  test('crear manda POST con el cuerpo en JSON', () async {
    late http.Request enviada;
    final api = ApiGenerada(
      base: 'http://192.168.0.7:8080',
      cliente: MockClient((peticion) async {
        enviada = peticion as http.Request;
        return http.Response(jsonEncode({'id': 7}), 201,
            headers: {'content-type': 'application/json; charset=utf-8'});
      }),
    );

    final creado = await api.crear('HistoriaClinica', {'resumen': 'alta'});

    expect(enviada.method, 'POST');
    expect(enviada.url.path, '/api/historia-clinicas');
    expect(jsonDecode(enviada.body), {'resumen': 'alta'});
    expect(creado['id'], 7);
  });

  test('una base con barra al final no produce una doble barra', () async {
    late Uri pedida;
    final api = ApiGenerada(
      base: 'http://192.168.0.7:8080/',
      cliente: MockClient((peticion) async {
        pedida = peticion.url;
        return http.Response('[]', 200,
            headers: {'content-type': 'application/json; charset=utf-8'});
      }),
    );

    await api.listar('Consulta');

    expect(pedida.toString(), 'http://192.168.0.7:8080/api/consultas');
  });

  test('un error del servidor se convierte en una excepcion legible', () async {
    final api = ApiGenerada(
      base: 'http://192.168.0.7:8080',
      cliente: MockClient((_) async => http.Response('nope', 500)),
    );

    expect(() => api.listar('Paciente'), throwsA(isA<ErrorDelBackendGenerado>()));
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/api_generada_test.dart`
Expected: FAIL — `api_generada.dart` no existe. Si además falla por `package:http/testing.dart`, agregar `http` a `dev_dependencies` no hace falta: ya está en `dependencies` y `testing.dart` viene en el mismo paquete.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/api_generada.dart
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'nombres.dart';

/// Cliente del backend que FORJA genera.
///
/// No hay descubrimiento de ningun tipo: el generador emite siempre la misma
/// forma -EscritorCapas.java- asi que la ruta se deduce del nombre de la clase
/// y el resto es CRUD. Tampoco hay autenticacion, porque el backend generado no
/// la tiene: no es un olvido de este cliente.
class ErrorDelBackendGenerado implements Exception {
  ErrorDelBackendGenerado(this.codigo, this.cuerpo);

  final int codigo;
  final String cuerpo;

  @override
  String toString() => 'El backend generado respondio $codigo: $cuerpo';
}

class ApiGenerada {
  ApiGenerada({required String base, http.Client? cliente})
      : _base = base.endsWith('/') ? base.substring(0, base.length - 1) : base,
        _cliente = cliente ?? http.Client();

  final String _base;
  final http.Client _cliente;

  Uri _uri(String clase, [String? id]) =>
      Uri.parse('$_base/api/${rutaDe(clase)}${id == null ? '' : '/$id'}');

  Future<List<Map<String, dynamic>>> listar(String clase) async {
    final respuesta = await _cliente.get(_uri(clase));
    final cuerpo = _leer(respuesta);
    return (cuerpo as List<dynamic>).map((f) => Map<String, dynamic>.from(f as Map)).toList();
  }

  Future<Map<String, dynamic>> crear(String clase, Map<String, dynamic> datos) async {
    final respuesta = await _cliente.post(_uri(clase),
        headers: {'Content-Type': 'application/json'}, body: jsonEncode(datos));
    return Map<String, dynamic>.from(_leer(respuesta) as Map);
  }

  Future<Map<String, dynamic>> actualizar(
      String clase, String id, Map<String, dynamic> datos) async {
    final respuesta = await _cliente.put(_uri(clase, id),
        headers: {'Content-Type': 'application/json'}, body: jsonEncode(datos));
    return Map<String, dynamic>.from(_leer(respuesta) as Map);
  }

  Future<void> borrar(String clase, String id) async {
    final respuesta = await _cliente.delete(_uri(clase, id));
    if (respuesta.statusCode >= 400) {
      throw ErrorDelBackendGenerado(respuesta.statusCode, respuesta.body);
    }
  }

  dynamic _leer(http.Response respuesta) {
    if (respuesta.statusCode >= 400) {
      throw ErrorDelBackendGenerado(respuesta.statusCode, respuesta.body);
    }
    if (respuesta.body.isEmpty) return null;
    return jsonDecode(utf8.decode(respuesta.bodyBytes));
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/api_generada_test.dart`
Expected: PASS, 4 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/generado/api_generada.dart movil/test/generado/api_generada_test.dart
git commit -m "El telefono sabe hablarle al backend generado"
```

---

### Task 4: Los registros sobreviven al corte

Lo que se carga sin señal tiene que estar cuando vuelva. Se replica la decisión que ya tomó `Almacen`: archivos JSON, escritura atómica, y una cola con identificador de operación para poder reenviar sin duplicar.

**Files:**
- Create: `movil/lib/generado/almacen_registros.dart`
- Test: `movil/test/generado/almacen_registros_test.dart`
- Reference: `movil/lib/almacen.dart` (el patrón a seguir), `movil/lib/identificadores.dart`

**Interfaces:**
- Consumes: `Almacen` no; este almacén es independiente y recibe su `Directory` igual que aquel.
- Produces: `class OperacionPendiente` con campos `String id, String clase, String verbo, String? registroId, Map<String, dynamic> datos` y `aJson()` / `OperacionPendiente.desdeJson()`; `class AlmacenRegistros(Directory carpeta)` con `guardarFilas(String clase, List<Map<String,dynamic>>)`, `leerFilas(String clase)`, `encolar(OperacionPendiente)`, `leerCola()`, `reemplazarCola(List<OperacionPendiente>)`.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/almacen_registros_test.dart
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';

void main() {
  late Directory carpeta;
  late AlmacenRegistros almacen;

  setUp(() {
    carpeta = Directory.systemTemp.createTempSync('forja-registros');
    almacen = AlmacenRegistros(carpeta);
  });

  tearDown(() => carpeta.deleteSync(recursive: true));

  test('lo guardado se vuelve a leer igual', () async {
    await almacen.guardarFilas('Paciente', [
      {'id': 1, 'nombre': 'Juan'}
    ]);

    expect(await almacen.leerFilas('Paciente'), [
      {'id': 1, 'nombre': 'Juan'}
    ]);
  });

  test('una clase sin nada guardado devuelve vacio, no revienta', () async {
    expect(await almacen.leerFilas('Consulta'), isEmpty);
  });

  test('la cola conserva el orden en que se encolo', () async {
    await almacen.encolar(OperacionPendiente(
        id: 'op-1', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Ana'}));
    await almacen.encolar(OperacionPendiente(
        id: 'op-2', clase: 'Paciente', verbo: 'crear', datos: {'nombre': 'Beto'}));

    final cola = await almacen.leerCola();

    expect(cola.map((o) => o.id), ['op-1', 'op-2']);
    expect(cola.first.datos['nombre'], 'Ana');
  });

  test('un archivo corrupto se descarta en lugar de arrastrarse', () async {
    File('${carpeta.path}${Platform.pathSeparator}filas-Paciente.json')
        .writeAsStringSync('{ esto no es json');

    expect(await almacen.leerFilas('Paciente'), isEmpty);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/almacen_registros_test.dart`
Expected: FAIL — `almacen_registros.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/almacen_registros.dart
import 'dart:convert';
import 'dart:io';

/// Las filas del backend generado y los cambios que todavia no llegaron.
///
/// Es hermano de Almacen y repite sus dos decisiones a proposito: archivos JSON
/// -lo que se guarda es un documento entero, no algo que se consulte por
/// partes- y escritura atomica, porque morir a mitad de guardar la cola seria
/// perder justo lo que la cola existe para proteger.
class OperacionPendiente {
  const OperacionPendiente({
    required this.id,
    required this.clase,
    required this.verbo,
    this.registroId,
    this.datos = const {},
  });

  /// Identificador generado en el aparato. Es lo que permite reenviar la cola
  /// tras un corte sin duplicar: el servidor reconoce el repetido.
  final String id;
  final String clase;

  /// 'crear', 'actualizar' o 'borrar'.
  final String verbo;
  final String? registroId;
  final Map<String, dynamic> datos;

  factory OperacionPendiente.desdeJson(Map<String, dynamic> json) => OperacionPendiente(
        id: json['id'] as String,
        clase: json['clase'] as String,
        verbo: json['verbo'] as String,
        registroId: json['registroId'] as String?,
        datos: Map<String, dynamic>.from(json['datos'] as Map? ?? {}),
      );

  Map<String, dynamic> aJson() => {
        'id': id,
        'clase': clase,
        'verbo': verbo,
        'registroId': registroId,
        'datos': datos,
      };
}

class AlmacenRegistros {
  AlmacenRegistros(this.carpeta);

  final Directory carpeta;

  File _archivo(String nombre) => File('${carpeta.path}${Platform.pathSeparator}$nombre');

  Future<void> _guardar(String nombre, Object contenido) async {
    final temporal = _archivo('$nombre.tmp');
    await temporal.writeAsString(jsonEncode(contenido), flush: true);
    await temporal.rename(_archivo(nombre).path);
  }

  Future<dynamic> _leer(String nombre) async {
    final archivo = _archivo(nombre);
    if (!await archivo.exists()) return null;
    try {
      return jsonDecode(await archivo.readAsString());
    } catch (_) {
      await archivo.delete();
      return null;
    }
  }

  Future<void> guardarFilas(String clase, List<Map<String, dynamic>> filas) =>
      _guardar('filas-$clase.json', filas);

  Future<List<Map<String, dynamic>>> leerFilas(String clase) async {
    final json = await _leer('filas-$clase.json');
    if (json == null) return [];
    return (json as List<dynamic>).map((f) => Map<String, dynamic>.from(f as Map)).toList();
  }

  Future<void> encolar(OperacionPendiente operacion) async {
    final cola = await leerCola();
    await reemplazarCola([...cola, operacion]);
  }

  Future<List<OperacionPendiente>> leerCola() async {
    final json = await _leer('cola-registros.json');
    if (json == null) return [];
    return (json as List<dynamic>)
        .map((o) => OperacionPendiente.desdeJson(Map<String, dynamic>.from(o as Map)))
        .toList();
  }

  Future<void> reemplazarCola(List<OperacionPendiente> cola) =>
      _guardar('cola-registros.json', cola.map((o) => o.aJson()).toList());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/almacen_registros_test.dart`
Expected: PASS, 4 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/generado/almacen_registros.dart movil/test/generado/almacen_registros_test.dart
git commit -m "Lo que se carga sin senal sobrevive al corte"
```

---

### Task 5: El repositorio, que decide si hay red o no

La pantalla no debe saber si hay conexión. Lee del almacén, escribe en el almacén y encola; alguien más vacía la cola cuando puede.

**Files:**
- Create: `movil/lib/generado/repositorio.dart`
- Test: `movil/test/generado/repositorio_test.dart`

**Interfaces:**
- Consumes: `ApiGenerada` (tarea 3), `AlmacenRegistros` y `OperacionPendiente` (tarea 4), `Sincronizador.nuevoToken()` de `movil/lib/sincronizador.dart`.

**Cuál identificador usar, que el repositorio ya distingue:** `identificadores.dart` tiene `nuevoIdDeModelo()`, que produce un UUID porque los comandos del servidor declaran sus ids como `UUID` de Java. La cola de registros no tiene ese contrato: le alcanza con no repetirse, y para eso está `Sincronizador.nuevoToken()`. Usar ese.
- Produces: `class Repositorio({required AlmacenRegistros almacen, ApiGenerada? api})` con `Future<List<Map<String,dynamic>>> filas(String clase)`, `Future<void> crear(String clase, Map<String,dynamic> datos)`, `Future<int> sincronizar()` (devuelve cuántas operaciones se vaciaron).

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/repositorio_test.dart
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';
import 'package:forja_movil/generado/api_generada.dart';
import 'package:forja_movil/generado/repositorio.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  late Directory carpeta;
  late AlmacenRegistros almacen;

  setUp(() {
    carpeta = Directory.systemTemp.createTempSync('forja-repo');
    almacen = AlmacenRegistros(carpeta);
  });

  tearDown(() => carpeta.deleteSync(recursive: true));

  test('sin backend, crear deja la fila visible igual', () async {
    // Esto es el requisito entero: sin red la app no se degrada a solo lectura.
    final repo = Repositorio(almacen: almacen, api: null);

    await repo.crear('Paciente', {'nombre': 'Ana'});

    final filas = await repo.filas('Paciente');
    expect(filas.single['nombre'], 'Ana');
    expect((await almacen.leerCola()).single.verbo, 'crear');
  });

  test('sincronizar vacia la cola contra el backend', () async {
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((_) async => http.Response('{"id":1}', 201,
            headers: {'content-type': 'application/json; charset=utf-8'})),
      ),
    );
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final vaciadas = await repo.sincronizar();

    expect(vaciadas, 1);
    expect(await almacen.leerCola(), isEmpty);
  });

  test('si el backend no contesta, la cola queda intacta', () async {
    final repo = Repositorio(
      almacen: almacen,
      api: ApiGenerada(
        base: 'http://x',
        cliente: MockClient((_) async => throw const SocketException('sin ruta')),
      ),
    );
    await repo.crear('Paciente', {'nombre': 'Ana'});

    final vaciadas = await repo.sincronizar();

    expect(vaciadas, 0);
    expect((await almacen.leerCola()).length, 1);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/repositorio_test.dart`
Expected: FAIL — `repositorio.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/repositorio.dart
import '../sincronizador.dart';
import 'almacen_registros.dart';
import 'api_generada.dart';

/// Lo unico que sabe si hay red.
///
/// La pantalla pide filas y crea registros sin preguntar por la conexion: si el
/// backend generado esta al alcance se vacia la cola, y si no, no. Que la app
/// funcione sin senal no es un modo aparte, es el comportamiento normal.
class Repositorio {
  Repositorio({required this.almacen, this.api});

  final AlmacenRegistros almacen;
  final ApiGenerada? api;

  Future<List<Map<String, dynamic>>> filas(String clase) => almacen.leerFilas(clase);

  Future<void> crear(String clase, Map<String, dynamic> datos) async {
    final operacion = OperacionPendiente(
      id: Sincronizador.nuevoToken(),
      clase: clase,
      verbo: 'crear',
      datos: datos,
    );
    // Se ve primero y se manda despues: al reves, sin senal la pantalla
    // quedaria vacia y parecería que no se guardo nada.
    final filas = await almacen.leerFilas(clase);
    await almacen.guardarFilas(clase, [...filas, {...datos, '_pendiente': operacion.id}]);
    await almacen.encolar(operacion);
  }

  /// Devuelve cuantas operaciones se vaciaron. Cero es un resultado normal, no
  /// un error: quiere decir que no habia nada o que no se llego al backend.
  Future<int> sincronizar() async {
    final backend = api;
    if (backend == null) return 0;

    final cola = await almacen.leerCola();
    final quedan = <OperacionPendiente>[];
    var vaciadas = 0;

    for (final operacion in cola) {
      try {
        switch (operacion.verbo) {
          case 'crear':
            await backend.crear(operacion.clase, operacion.datos);
          case 'actualizar':
            await backend.actualizar(operacion.clase, operacion.registroId!, operacion.datos);
          case 'borrar':
            await backend.borrar(operacion.clase, operacion.registroId!);
        }
        vaciadas++;
      } catch (_) {
        // Se corta en la primera que falla y se conserva el resto en orden: si
        // se saltearan las siguientes, un alta posterior podria llegar antes
        // que la que la precede.
        quedan.addAll(cola.skip(cola.indexOf(operacion)));
        break;
      }
    }

    await almacen.reemplazarCola(quedan);
    return vaciadas;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/repositorio_test.dart`
Expected: PASS, 3 pruebas.

- [ ] **Step 5: Commit**

```bash
git add movil/lib/generado/repositorio.dart movil/test/generado/repositorio_test.dart
git commit -m "La pantalla no tiene que saber si hay conexion"
```

---

### Task 6: Las pantallas que salen del diagrama

**Files:**
- Create: `movil/lib/pantallas/entidades.dart` (lista de clases del diagrama)
- Create: `movil/lib/pantallas/registros.dart` (filas de una entidad + formulario de alta)
- Modify: `movil/lib/pantallas/diagramas.dart` — agregar en cada diagrama la acción que abre `PantallaEntidades`
- Test: `movil/test/pantallas/entidades_test.dart`

**Interfaces:**
- Consumes: `Diagrama` y `Clase` de `movil/lib/tipos.dart`, `Repositorio` (tarea 5), `claseDeCampoDe` (tarea 2).
- Produces: `class PantallaEntidades extends StatelessWidget` con constructor `PantallaEntidades({required Diagrama diagrama, required Repositorio repositorio})`.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/pantallas/entidades_test.dart
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/almacen_registros.dart';
import 'package:forja_movil/generado/repositorio.dart';
import 'package:forja_movil/pantallas/entidades.dart';
import 'package:forja_movil/tipos.dart';

void main() {
  testWidgets('lista una entrada por clase del diagrama', (tester) async {
    final carpeta = Directory.systemTemp.createTempSync('forja-pantalla');
    addTearDown(() => carpeta.deleteSync(recursive: true));

    final diagrama = Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente'),
      Clase(id: 'c2', nombre: 'Consulta'),
    ]);

    await tester.pumpWidget(MaterialApp(
      home: PantallaEntidades(
        diagrama: diagrama,
        repositorio: Repositorio(almacen: AlmacenRegistros(carpeta)),
      ),
    ));

    expect(find.text('Paciente'), findsOneWidget);
    expect(find.text('Consulta'), findsOneWidget);
    // La ruta se muestra: es lo que hace evidente que esto habla con el
    // backend generado y no con FORJA.
    expect(find.text('/api/pacientes'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/pantallas/entidades_test.dart`
Expected: FAIL — `entidades.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/pantallas/entidades.dart
import 'package:flutter/material.dart';

import '../generado/nombres.dart';
import '../generado/repositorio.dart';
import '../tipos.dart';
import 'registros.dart';

/// Las entidades del backend generado, sacadas del diagrama.
///
/// No hay pantalla escrita por entidad ni codigo generado en el telefono: la
/// lista sale del diagrama en tiempo de ejecucion, y por eso la misma app sirve
/// para cualquier modelo sin recompilar.
class PantallaEntidades extends StatelessWidget {
  const PantallaEntidades({super.key, required this.diagrama, required this.repositorio});

  final Diagrama diagrama;
  final Repositorio repositorio;

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(diagrama.nombre)),
        body: ListView(
          children: [
            for (final clase in diagrama.clases)
              ListTile(
                title: Text(clase.nombre),
                subtitle: Text('/api/${rutaDe(clase.nombre)}'),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => PantallaRegistros(clase: clase, repositorio: repositorio),
                )),
              ),
          ],
        ),
      );
}
```

```dart
// movil/lib/pantallas/registros.dart
import 'package:flutter/material.dart';

import '../generado/repositorio.dart';
import '../generado/tipos_de_campo.dart';
import '../tipos.dart';

/// Las filas de una entidad y el formulario para agregar una.
///
/// Los campos salen de los atributos de la clase: el tipo escrito en el
/// diagrama decide el teclado, que es lo unico que hace falta para que cargar
/// un numero no obligue a buscar el simbolo.
class PantallaRegistros extends StatefulWidget {
  const PantallaRegistros({super.key, required this.clase, required this.repositorio});

  final Clase clase;
  final Repositorio repositorio;

  @override
  State<PantallaRegistros> createState() => _PantallaRegistrosState();
}

class _PantallaRegistrosState extends State<PantallaRegistros> {
  late Future<List<Map<String, dynamic>>> _filas;
  final _valores = <String, String>{};

  @override
  void initState() {
    super.initState();
    _filas = widget.repositorio.filas(widget.clase.nombre);
  }

  /// Los identificadores no se piden: los pone el backend generado.
  Iterable<Atributo> get _editables =>
      widget.clase.atributos.where((a) => !a.esIdentificador);

  TextInputType _tecladoDe(Atributo atributo) => switch (claseDeCampoDe(atributo.tipo)) {
        ClaseDeCampo.entero => TextInputType.number,
        ClaseDeCampo.decimal => const TextInputType.numberWithOptions(decimal: true),
        ClaseDeCampo.fecha || ClaseDeCampo.fechaHora => TextInputType.datetime,
        _ => TextInputType.text,
      };

  Future<void> _guardar() async {
    await widget.repositorio.crear(widget.clase.nombre, Map.of(_valores));
    _valores.clear();
    setState(() => _filas = widget.repositorio.filas(widget.clase.nombre));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(widget.clase.nombre)),
        body: Column(children: [
          for (final atributo in _editables)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: TextField(
                decoration: InputDecoration(labelText: atributo.nombre),
                keyboardType: _tecladoDe(atributo),
                onChanged: (texto) => _valores[atributo.nombre] = texto,
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: FilledButton(onPressed: _guardar, child: const Text('Agregar')),
          ),
          Expanded(
            child: FutureBuilder<List<Map<String, dynamic>>>(
              future: _filas,
              builder: (_, resultado) => ListView(children: [
                for (final fila in resultado.data ?? const <Map<String, dynamic>>[])
                  ListTile(
                    title: Text(_editables
                        .map((a) => fila[a.nombre]?.toString() ?? '')
                        .where((t) => t.isNotEmpty)
                        .join(' · ')),
                    // Lo que todavia no llego al backend se marca: sin esto,
                    // sin senal no se distingue lo guardado de lo enviado.
                    trailing: fila.containsKey('_pendiente')
                        ? const Icon(Icons.schedule, size: 18)
                        : null,
                  ),
              ]),
            ),
          ),
        ]),
      );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/pantallas/entidades_test.dart`
Expected: PASS.

- [ ] **Step 5: Enganchar la entrada desde la lista de diagramas**

En `movil/lib/pantallas/diagramas.dart`, agregar a cada elemento de la lista una acción que abra `PantallaEntidades` con el diagrama ya bajado y un `Repositorio` construido con `AlmacenRegistros(await getApplicationDocumentsDirectory())`. Seguir el estilo de navegación que ya usa ese archivo para abrir el lienzo.

- [ ] **Step 6: Run the whole suite**

Run: `cd movil && flutter test`
Expected: todo en verde.

- [ ] **Step 7: Commit**

```bash
git add movil/lib/pantallas/entidades.dart movil/lib/pantallas/registros.dart movil/lib/pantallas/diagramas.dart movil/test/pantallas/entidades_test.dart
git commit -m "Las pantallas del backend generado salen del diagrama"
```

---

### Task 7: La voz crea registros

Hoy la gramática entiende «creá una clase Paciente». Tiene que entender «agregá un paciente llamado Juan».

**Va en un archivo nuevo y no dentro de `parser_voz.dart`.** Ese archivo es un espejo exacto de `ParserVoz.java`, sostenido por un corpus de casos que corren las dos suites; meterle vocabulario de registros rompería el espejo y obligaría al lado Java a conocer algo que no le incumbe. El dominio es otro: allá se habla de clases y atributos, acá de filas.

**Files:**
- Create: `movil/lib/generado/voz_registros.dart`
- Test: `movil/test/generado/voz_registros_test.dart`

**Interfaces:**
- Consumes: `Diagrama`, `Clase`, `Atributo` y `claveDeNombre()` de `movil/lib/tipos.dart`; `claseDeCampoDe()` de la tarea 2.
- Produces: `class PedidoDeRegistro { final String clase; final Map<String, dynamic> datos; }` y `PedidoDeRegistro? interpretarPedidoDeRegistro(String frase, Diagrama diagrama)`.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/voz_registros_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/voz_registros.dart';
import 'package:forja_movil/tipos.dart';

Diagrama _clinica() => Diagrama(id: 'd1', nombre: 'Clinica', version: 1, clases: [
      Clase(id: 'c1', nombre: 'Paciente', atributos: const [
        Atributo(id: 'a1', nombre: 'ci', tipo: 'texto', esIdentificador: true),
        Atributo(id: 'a2', nombre: 'nombre', tipo: 'texto'),
      ]),
      Clase(id: 'c2', nombre: 'Médico', atributos: const [
        Atributo(id: 'a3', nombre: 'nombre', tipo: 'texto'),
      ]),
      Clase(id: 'c3', nombre: 'Consulta', atributos: const [
        Atributo(id: 'a4', nombre: 'motivo', tipo: 'texto'),
        Atributo(id: 'a5', nombre: 'fecha', tipo: 'fecha'),
      ]),
    ]);

void main() {
  test('«llamado» cae en el primer campo de texto que no sea el identificador', () {
    final pedido = interpretarPedidoDeRegistro('agregá un paciente llamado Juan', _clinica());

    expect(pedido!.clase, 'Paciente');
    // Ni 'ci', que es la clave: pedirla por voz no tendria sentido.
    expect(pedido.datos, {'nombre': 'Juan'});
  });

  test('el reconocedor escribe sin acento y la entidad se encuentra igual', () {
    final pedido = interpretarPedidoDeRegistro('nuevo medico llamado Ana', _clinica());

    expect(pedido!.clase, 'Médico');
  });

  test('«con <campo> <valor>» nombra el campo explicitamente', () {
    final pedido =
        interpretarPedidoDeRegistro('agregá una consulta con motivo control', _clinica());

    expect(pedido!.clase, 'Consulta');
    expect(pedido.datos, {'motivo': 'control'});
  });

  test('una entidad que no esta en el diagrama no se inventa', () {
    expect(interpretarPedidoDeRegistro('agregá una factura llamada 001', _clinica()), isNull);
  });

  test('un campo que la clase no tiene no se inventa', () {
    expect(
        interpretarPedidoDeRegistro('agregá un paciente con domicilio Sucre', _clinica()), isNull);
  });

  test('una frase que no pide un alta no devuelve nada', () {
    expect(interpretarPedidoDeRegistro('hola que tal', _clinica()), isNull);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/voz_registros_test.dart`
Expected: FAIL — `voz_registros.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/voz_registros.dart
import '../tipos.dart';
import 'tipos_de_campo.dart';

/// La voz, en el dominio de los registros.
///
/// Aparte de parser_voz.dart a proposito: aquel es el espejo de ParserVoz.java
/// y lo que lo mantiene fiel es un corpus compartido. Este habla de filas de una
/// tabla, que es un dominio que el lado Java no tiene por que conocer.
///
/// No inventa nada: si la entidad o el campo no estan en el diagrama, devuelve
/// null y el usuario ve que no se entendio. Es preferible a crear una fila en
/// un lugar que no era.
class PedidoDeRegistro {
  const PedidoDeRegistro({required this.clase, required this.datos});

  final String clase;
  final Map<String, dynamic> datos;
}

// El imperativo voseante va con y sin tilde porque el reconocedor de Android
// escribe las dos formas.
const _verbos = r'(?:agrega|agregá|anadi|anadí|añadi|añadí|crea|creá|nuevo|nueva)';
const _articulos = r'(?:un|una|el|la|los|las)?';

final _conCampo = RegExp(
    '^$_verbos\\s+$_articulos\\s*(.+?)\\s+con\\s+(\\S+)\\s+(.+)\$',
    caseSensitive: false);

final _conNombre = RegExp(
    '^$_verbos\\s+$_articulos\\s*(.+?)\\s+llamad[oa]\\s+(.+)\$',
    caseSensitive: false);

PedidoDeRegistro? interpretarPedidoDeRegistro(String frase, Diagrama diagrama) {
  final limpia = frase.trim();

  final conCampo = _conCampo.firstMatch(limpia);
  if (conCampo != null) {
    final clase = _claseDicha(conCampo.group(1)!, diagrama);
    if (clase == null) return null;
    final campo = _campoDicho(clase, conCampo.group(2)!);
    if (campo == null) return null;
    return PedidoDeRegistro(clase: clase.nombre, datos: {campo: conCampo.group(3)!.trim()});
  }

  final conNombre = _conNombre.firstMatch(limpia);
  if (conNombre != null) {
    final clase = _claseDicha(conNombre.group(1)!, diagrama);
    if (clase == null) return null;
    final campo = _primerCampoDeTexto(clase);
    if (campo == null) return null;
    return PedidoDeRegistro(clase: clase.nombre, datos: {campo: conNombre.group(2)!.trim()});
  }

  return null;
}

/// claveDeNombre es la misma regla que ya usa el diagrama para decidir si dos
/// nombres son el mismo: sin acentos, sin espacios, en minuscula.
Clase? _claseDicha(String dicho, Diagrama diagrama) {
  final buscado = claveDeNombre(dicho);
  return diagrama.clases.where((c) => claveDeNombre(c.nombre) == buscado).firstOrNull;
}

String? _campoDicho(Clase clase, String dicho) {
  final buscado = claveDeNombre(dicho);
  return clase.atributos
      .where((a) => claveDeNombre(a.nombre) == buscado)
      .map((a) => a.nombre)
      .firstOrNull;
}

/// Donde cae un «llamado X»: el primer texto que no sea la clave. Dictar una
/// clave primaria no tiene sentido, y si cayera ahi el alta chocaria con la
/// siguiente.
String? _primerCampoDeTexto(Clase clase) => clase.atributos
    .where((a) => !a.esIdentificador && claseDeCampoDe(a.tipo) == ClaseDeCampo.texto)
    .map((a) => a.nombre)
    .firstOrNull;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/voz_registros_test.dart`
Expected: PASS, 6 pruebas.

- [ ] **Step 5: Enganchar el dictado en la pantalla de registros**

En `movil/lib/pantallas/registros.dart`, agregar un botón de micrófono que abra el mismo cuadro de dictado que usa `lienzo.dart`, pase el texto reconocido a `interpretarPedidoDeRegistro` y, si devuelve algo, llame a `repositorio.crear(pedido.clase, pedido.datos)`. Si devuelve `null`, mostrar lo que se entendió y no hacer nada más.

- [ ] **Step 6: Run the whole suite**

Run: `cd movil && flutter test`
Expected: todo en verde.

- [ ] **Step 7: Commit**

```bash
git add movil/lib/generado/voz_registros.dart movil/test/generado/voz_registros_test.dart movil/lib/pantallas/registros.dart
git commit -m "Dictar un registro, no solo una clase"
```

---

### Task 8: El dictado que de verdad no sale a la red

**Files:**
- Modify: `movil/lib/pantallas/lienzo.dart:763-770`
- Test: manual, en el aparato. No hay forma de verificarlo con `flutter test`: depende del reconocedor de Android.

- [ ] **Step 1: Agregar la opción**

```dart
listenOptions: SpeechListenOptions(
  localeId: widget.idioma,
  partialResults: true,
  cancelOnError: true,
  // Sin esto el reconocedor de Android puede salir a la red, y el requisito
  // central es que el dictado funcione sin senal. El comentario de
  // dictado_local.dart ya lo afirmaba; la llamada no lo garantizaba.
  onDevice: true,
),
```

- [ ] **Step 2: Descargar el paquete de español en el teléfono**

En el teléfono: Ajustes → Sistema → Idiomas y entrada → Reconocimiento de voz de Google → Reconocimiento sin conexión → descargar **Español**. Hacerlo **hoy**, con internet.

- [ ] **Step 3: Probar en modo avión**

Poner el teléfono en modo avión, abrir la app, dictar una frase. Si no reconoce nada, el paquete no está instalado o `onDevice` no está soportado en ese aparato: en ese caso, quitar `onDevice: true` y dejar anotado en el documento que el dictado sin red depende del reconocedor del dispositivo. **No inventar una solución en el momento.**

- [ ] **Step 4: Commit**

```bash
git add movil/lib/pantallas/lienzo.dart
git commit -m "El dictado se queda en el aparato, y ahora de verdad"
```

---

### Task 9: El modelo en el aparato

Último por diseño: es la pieza más cara y la única prescindible.

**Files:**
- Modify: `movil/pubspec.yaml` — agregar `flutter_gemma`
- Create: `movil/lib/generado/asistente.dart`
- Test: `movil/test/generado/asistente_test.dart`

**Interfaces:**
- Consumes: el parser de `movil/lib/voz/parser_voz.dart`.
- Produces: `class Asistente({required Future<String> Function(String) preguntarAlModelo})` con `Future<String?> fraseCanonica(String pedido)` — devuelve la frase que el parser debe interpretar, o `null` si el modelo no está disponible.

- [ ] **Step 1: Write the failing test**

```dart
// movil/test/generado/asistente_test.dart
import 'package:flutter_test/flutter_test.dart';
import 'package:forja_movil/generado/asistente.dart';

void main() {
  test('devuelve la frase que propuso el modelo', () async {
    final asistente = Asistente(
      preguntarAlModelo: (_) async => 'agregá un paciente llamado Juan',
    );

    expect(await asistente.fraseCanonica('anotá a Juan como paciente'),
        'agregá un paciente llamado Juan');
  });

  test('si el modelo falla, devuelve null y no rompe la app', () async {
    // El modelo es prescindible: sin el se pierde un escalon, no la app.
    final asistente = Asistente(
      preguntarAlModelo: (_) async => throw Exception('modelo no cargado'),
    );

    expect(await asistente.fraseCanonica('lo que sea'), isNull);
  });

  test('una respuesta vacia se trata como que no hubo propuesta', () async {
    final asistente = Asistente(preguntarAlModelo: (_) async => '   ');

    expect(await asistente.fraseCanonica('lo que sea'), isNull);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd movil && flutter test test/generado/asistente_test.dart`
Expected: FAIL — `asistente.dart` no existe.

- [ ] **Step 3: Write minimal implementation**

```dart
// movil/lib/generado/asistente.dart

/// El escalon que entra cuando la gramatica no entendio.
///
/// El modelo NO emite operaciones: propone una frase canonica que el parser
/// determinista vuelve a interpretar, y el usuario confirma antes de que se
/// aplique. Es la decision del diseno del 16 de septiembre, y tiene una razon
/// vivida: en la web un respaldo por IA aplicaba sin revision y creaba clases
/// fantasma en silencio.
///
/// Recibe la funcion que habla con el modelo en lugar de crearla: asi se prueba
/// sin cargar 529 MB, y asi la app sigue en pie cuando el modelo no esta.
class Asistente {
  const Asistente({required this.preguntarAlModelo});

  final Future<String> Function(String) preguntarAlModelo;

  Future<String?> fraseCanonica(String pedido) async {
    try {
      final propuesta = await preguntarAlModelo(_prompt(pedido));
      final limpia = propuesta.trim();
      return limpia.isEmpty ? null : limpia;
    } catch (_) {
      return null;
    }
  }

  String _prompt(String pedido) => '''
Convertí el pedido en UNA sola orden, con esta forma exacta:
"agregá un <entidad> llamado <valor>"
No expliques nada. No agregues nada. Si no podés, respondé vacío.

Pedido: $pedido
Orden:''';
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd movil && flutter test test/generado/asistente_test.dart`
Expected: PASS, 3 pruebas.

- [ ] **Step 5: Agregar el plugin y escribir el arranque**

```bash
cd movil && flutter pub add flutter_gemma flutter_gemma_mediapipe
```

Crear `movil/lib/generado/motor_gemma.dart`:

```dart
// movil/lib/generado/motor_gemma.dart
import 'dart:io';

import 'package:flutter_gemma/flutter_gemma.dart';
import 'package:flutter_gemma_mediapipe/flutter_gemma_mediapipe.dart';
import 'package:path_provider/path_provider.dart';

/// Arranque del modelo en el aparato.
///
/// Devuelve la funcion que Asistente necesita, o lanza si el modelo no esta.
/// Quien lo llama decide que hacer con eso: la app tiene que seguir en pie sin
/// modelo, asi que la excepcion no puede escaparse a la interfaz.
Future<Future<String> Function(String)> arrancarGemma() async {
  final carpeta = await getApplicationDocumentsDirectory();
  final archivo = File('${carpeta.path}${Platform.pathSeparator}gemma3-1b-it-int4.task');
  if (!await archivo.exists()) {
    throw StateError('El modelo no esta en ${archivo.path}');
  }

  await FlutterGemma.initialize(inferenceEngines: const [MediaPipeEngine()]);
  await FlutterGemma.installModel(
    modelType: ModelType.gemmaIt,
    fileType: ModelFileType.task,
  ).fromFile(archivo.path).install();

  final modelo = await FlutterGemma.getActiveModel(maxTokens: 512);

  return (String prompt) async {
    // Una conversacion por pedido: el asistente no conversa, traduce una frase.
    // Arrastrar historial solo gastaria contexto y haria que un pedido
    // anterior contaminara el siguiente.
    final chat = await modelo.createChat();
    await chat.addQueryChunk(Message.text(text: prompt, isUser: true));
    return chat.generateChatResponse();
  };
}
```

Y en el arranque de la app, envolverlo:

```dart
Asistente? asistente;
try {
  asistente = Asistente(preguntarAlModelo: await arrancarGemma());
} catch (_) {
  // Sin modelo se pierde el tercer escalon y nada mas. No es un error que el
  // usuario tenga que ver.
  asistente = null;
}
```

**Verificar contra la documentación del plugin antes de dar por buena esta API:** los nombres de arriba son los de la versión publicada al 22 de septiembre de 2026, y este plugin cambió de forma entre versiones. Si `flutter pub add` trae una distinta, seguir la de `pub.dev` y ajustar; lo que no cambia es el contrato con `Asistente`, que recibe una `Future<String> Function(String)`.

- [ ] **Step 6: Empujar el modelo al teléfono**

```bash
adb push gemma3-1b-it-int4.task /sdcard/Android/data/<applicationId>/files/
```

El `applicationId` sale de `movil/android/app/build.gradle.kts`. Son 529 MB: tarda.

- [ ] **Step 7: Commit**

```bash
git add movil/pubspec.yaml movil/pubspec.lock movil/lib/generado/asistente.dart movil/test/generado/asistente_test.dart
git commit -m "El modelo propone, la gramatica dispone"
```

---

### Task 10: Que ande en el teléfono, que es donde importa

Nada de lo anterior está verificado hasta que corra en el aparato. Es la tarea que más veces se saltea y la única que decide la demostración.

**Files:** ninguno. Es verificación.

- [ ] **Step 1: Instalar**

```bash
cd movil && flutter build apk --release
adb install -r build/app/outputs/flutter-apk/app-release.apk
```

- [ ] **Step 2: Bajar la definición, con internet**

Entrar con la credencial, abrir el proyecto, bajar el diagrama. Confirmar que quedó guardado: cerrar la app y volver a abrirla.

- [ ] **Step 3: Modo avión**

Sin señal: abrir la app, entrar a una entidad, cargar dos registros, dictar uno. Tienen que aparecer con la marca de pendiente.

- [ ] **Step 4: El hotspot y la sincronización**

Prender el punto de acceso del teléfono, conectar la laptop, levantar el backend generado, apuntar la app a la IP de la laptop y sincronizar. La marca de pendiente tiene que desaparecer y los registros tienen que estar en la base del backend generado.

- [ ] **Step 5: Anotar lo que falle**

Lo que no ande acá es lo único que importa. Anotarlo y arreglarlo antes de tocar cualquier otra cosa.

---

## Lo que queda fuera

- Generar un frontend web desde el diagrama (extra posterior; no aporta al requisito sin conexión).
- Editar y borrar registros desde el teléfono: el plan implementa el alta y la lectura, que es lo que demuestra el circuito completo. `Repositorio.sincronizar()` ya sabe vaciar `actualizar` y `borrar`, así que agregarlos es pantalla y nada más.
- Desplegar el backend generado a la nube.
