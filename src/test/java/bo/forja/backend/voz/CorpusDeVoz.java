package bo.forja.backend.voz;

import bo.forja.backend.operacion.ContextoDelDiagrama;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lee {@code compartido/corpus-voz.json} y compara lo esperado con lo que salio.
 * <p>
 * El archivo se trabaja como mapas y listas comunes, y no como arbol de Jackson,
 * a proposito: lo que se compara son los nombres de campo y los valores de la
 * carga del comando, y con tipos del lenguaje eso se lee mejor y no ata la
 * prueba a la version de la libreria.
 * <p>
 * Se comparan solo los campos que el caso nombra, y se entra en listas y
 * objetos anidados -los parametros de una operacion, por ejemplo- con las
 * mismas reglas.
 * <p>
 * Los identificadores no se comparan literal porque el parser los inventa. Hay
 * dos convenciones, y las dos tienen que valer igual en la suite de Dart:
 * <ul>
 *   <li>{@code "@Paciente"} es el id de una clase que ya estaba en el contexto.
 *   <li>{@code "$nueva1"} es uno recien inventado: se liga a lo que haya salido
 *       la primera vez y despues tiene que repetirse. Eso es lo que verifica
 *       que los atributos cuelguen de la clase que se acaba de crear.
 * </ul>
 */
final class CorpusDeVoz {

    /** Maven corre con el directorio de trabajo en la raiz del repositorio. */
    private static final Path ARCHIVO = Path.of("compartido", "corpus-voz.json");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Caso(String id,
                List<String> frases,
                boolean entendida,
                List<Map<String, Object>> pasos,
                List<String> sugerencias,
                Map<String, UUID> clasesDelContexto) {

        ContextoDelDiagrama contextoResuelto() {
            List<ContextoDelDiagrama.ClaseConocida> clases = new ArrayList<>();
            clasesDelContexto.forEach((nombre, id) ->
                    clases.add(new ContextoDelDiagrama.ClaseConocida(id, nombre)));
            return ContextoDelDiagrama.de(clases);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Caso> cargar() {
        Map<String, Object> raiz;
        try {
            raiz = JSON.readValue(Files.readString(ARCHIVO), Map.class);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No pude leer " + ARCHIVO.toAbsolutePath(), e);
        }

        Map<String, Map<String, UUID>> contextos = new LinkedHashMap<>();
        ((Map<String, Object>) raiz.get("contextos")).forEach((nombre, cuerpo) -> {
            Map<String, UUID> clases = new LinkedHashMap<>();
            for (Object clase : (List<Object>) ((Map<String, Object>) cuerpo).get("clases")) {
                clases.put((String) clase, UUID.randomUUID());
            }
            contextos.put(nombre, clases);
        });

        List<Caso> casos = new ArrayList<>();
        for (Object crudo : (List<Object>) raiz.get("casos")) {
            Map<String, Object> caso = (Map<String, Object>) crudo;
            casos.add(new Caso(
                    (String) caso.get("id"),
                    (List<String>) caso.getOrDefault("frases", List.of()),
                    (Boolean) caso.get("entendida"),
                    (List<Map<String, Object>>) caso.getOrDefault("pasos", List.of()),
                    (List<String>) caso.getOrDefault("sugerencias", List.of()),
                    contextos.get((String) caso.get("contexto"))));
        }
        return casos;
    }

    @SuppressWarnings("unchecked")
    static void verificar(Caso caso, String frase, Interpretacion obtenida) {
        String donde = caso.id() + " / \"" + frase + "\"";

        assertThat(obtenida.entendida()).as(donde + ": entendida").isEqualTo(caso.entendida());

        if (!caso.entendida()) {
            if (!caso.sugerencias().isEmpty()) {
                assertThat(obtenida.sugerencias()).as(donde + ": sugerencias")
                        .containsExactlyElementsOf(caso.sugerencias());
            }
            return;
        }

        assertThat(obtenida.pasos()).as(donde + ": cantidad de pasos")
                .hasSize(caso.pasos().size());

        Map<String, String> ligados = new HashMap<>();
        for (int i = 0; i < caso.pasos().size(); i++) {
            Map<String, Object> esperado = caso.pasos().get(i);
            Interpretacion.Paso paso = obtenida.pasos().get(i);
            String enElPaso = donde + " / paso " + i;

            assertThat(paso.tipo().name()).as(enElPaso + ": tipo")
                    .isEqualTo(esperado.get("tipo"));

            Map<String, Object> salio = JSON.convertValue(paso.comando(), Map.class);
            ((Map<String, Object>) esperado.get("comando")).forEach((campo, valorEsperado) ->
                    compararCampo(enElPaso, campo, valorEsperado, salio.get(campo), caso, ligados));
        }
    }

    private static void compararCampo(String donde, String campo, Object esperado,
                                      Object salio, Caso caso, Map<String, String> ligados) {
        String etiqueta = donde + " / " + campo;

        if (esperado instanceof String texto && texto.startsWith("@")) {
            String nombre = texto.substring(1);
            UUID id = caso.clasesDelContexto().get(nombre);
            assertThat(id).as(etiqueta + ": el contexto no tiene la clase " + nombre).isNotNull();
            assertThat(String.valueOf(salio)).as(etiqueta + ": id de " + nombre)
                    .isEqualTo(id.toString());
            return;
        }

        if (esperado instanceof String marca && marca.startsWith("$")) {
            assertThat(salio).as(etiqueta + ": " + marca + " tiene que ser un id").isNotNull();
            String yaLigado = ligados.get(marca);
            if (yaLigado == null) {
                ligados.put(marca, String.valueOf(salio));
            } else {
                assertThat(String.valueOf(salio)).as(etiqueta + ": " + marca + " tiene que repetirse")
                        .isEqualTo(yaLigado);
            }
            return;
        }

        if (esperado instanceof List<?> lista) {
            assertThat(salio).as(etiqueta + ": se esperaba una lista").isInstanceOf(List.class);
            List<?> salieron = (List<?>) salio;
            assertThat(salieron).as(etiqueta + ": cantidad").hasSize(lista.size());
            for (int i = 0; i < lista.size(); i++) {
                compararCampo(etiqueta, "[" + i + "]", lista.get(i), salieron.get(i), caso, ligados);
            }
            return;
        }

        if (esperado instanceof Map<?, ?> mapa) {
            assertThat(salio).as(etiqueta + ": se esperaba un objeto").isInstanceOf(Map.class);
            Map<?, ?> salieron = (Map<?, ?>) salio;
            mapa.forEach((subcampo, subesperado) -> compararCampo(
                    etiqueta, String.valueOf(subcampo), subesperado,
                    salieron.get(subcampo), caso, ligados));
            return;
        }

        if (esperado instanceof Number numero) {
            assertThat(salio).as(etiqueta + ": el campo no vino").isInstanceOf(Number.class);
            assertThat(((Number) salio).doubleValue()).as(etiqueta)
                    .isEqualTo(numero.doubleValue());
            return;
        }

        assertThat(salio).as(etiqueta).isEqualTo(esperado);
    }

    private CorpusDeVoz() {
    }
}
