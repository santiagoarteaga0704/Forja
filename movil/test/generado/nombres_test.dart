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
