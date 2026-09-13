package bo.forja.backend.generador;

import java.util.Set;
import java.util.TreeSet;

/**
 * Acumulador de un archivo fuente de Java.
 * <p>
 * Resuelve un detalle que ensucia cualquier generador escrito a mano: las
 * importaciones no se conocen hasta haber escrito el cuerpo, pero van
 * arriba. Aqui se declaran a medida que se necesitan y se emiten ordenadas
 * al renderizar, de modo que el codigo generado no lleve importaciones con
 * comodin ni importaciones que no se usan.
 */
final class Fuente {

    private final String paquete;
    private final Set<String> importaciones = new TreeSet<>();
    private final StringBuilder cuerpo = new StringBuilder();

    Fuente(String paquete) {
        this.paquete = paquete;
    }

    /**
     * Declara una importacion. Se ignoran los tipos sin paquete y los de
     * {@code java.lang}, que no la necesitan.
     */
    Fuente importar(String tipoCompleto) {
        if (tipoCompleto != null && tipoCompleto.contains(".")
                && !tipoCompleto.startsWith("java.lang.")) {
            importaciones.add(tipoCompleto);
        }
        return this;
    }

    Fuente importar(String... tipos) {
        for (String tipo : tipos) {
            importar(tipo);
        }
        return this;
    }

    Fuente linea(String texto) {
        cuerpo.append(texto).append('\n');
        return this;
    }

    Fuente linea() {
        cuerpo.append('\n');
        return this;
    }

    String texto() {
        StringBuilder salida = new StringBuilder();
        salida.append("package ").append(paquete).append(";\n\n");

        // Las de jakarta y org van antes que las de java, como es costumbre en
        // los proyectos Spring; dentro de cada grupo, en orden alfabetico.
        String grupoAnterior = null;
        for (String importacion : importaciones) {
            String grupo = importacion.startsWith("java") ? "java" : "otros";
            if (grupoAnterior != null && !grupo.equals(grupoAnterior)) {
                salida.append('\n');
            }
            salida.append("import ").append(importacion).append(";\n");
            grupoAnterior = grupo;
        }
        if (!importaciones.isEmpty()) {
            salida.append('\n');
        }
        salida.append(cuerpo);
        return salida.toString();
    }
}
