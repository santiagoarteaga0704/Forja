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
