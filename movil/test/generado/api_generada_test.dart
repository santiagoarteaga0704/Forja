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
