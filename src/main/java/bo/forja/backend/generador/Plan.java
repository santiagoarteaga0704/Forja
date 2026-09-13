package bo.forja.backend.generador;

import java.util.List;

/**
 * Modelo intermedio entre el diagrama y el codigo.
 * <p>
 * Existe para separar dos problemas que conviene no mezclar: interpretar el
 * UML -resolver nombres, decidir claves, deducir cardinalidades, atar la
 * herencia- y escribir texto Java. Con este plan en medio, los escritores
 * no toman ninguna decision: solo redactan lo que el plan ya resolvio. Eso
 * hace que las reglas de traduccion se puedan probar sin comparar cadenas
 * de codigo fuente.
 */
public final class Plan {

    private Plan() {
    }

    /** Cardinalidad efectiva de una asociacion, ya resuelta desde los dos extremos. */
    public enum Cardinalidad {
        UNO_A_UNO,
        MUCHOS_A_UNO,
        UNO_A_MUCHOS,
        MUCHOS_A_MUCHOS
    }

    public record Proyecto(
            String paqueteBase,
            String artefacto,
            String nombreAplicacion,
            List<Clase> clases) {

        public List<Clase> entidades() {
            return clases.stream().filter(c -> !c.esInterfaz()).toList();
        }

        /** Entidades concretas: las unicas que reciben repositorio y API propios. */
        public List<Clase> generables() {
            return entidades().stream().filter(c -> !c.esAbstracta()).toList();
        }

        /**
         * Clave primaria de la clase, buscandola hacia arriba en la jerarquia.
         * <p>
         * Una subclase no declara clave propia, pero su repositorio si necesita
         * saber de que tipo es la que hereda: {@code JpaRepository} lleva el
         * tipo del identificador en su firma.
         */
        public Campo identificadorDe(Clase clase) {
            Clase actual = clase;
            while (actual != null) {
                if (actual.tieneIdentificadorPropio()) {
                    return actual.identificador();
                }
                actual = actual.padre() == null ? null : porNombre(actual.padre());
            }
            return null;
        }

        public Clase porNombre(String nombreJava) {
            return clases.stream()
                    .filter(c -> c.nombreJava().equals(nombreJava))
                    .findFirst()
                    .orElse(null);
        }
    }

    public record Clase(
            String nombreUml,
            String nombreJava,
            String tabla,
            String ruta,
            boolean esAbstracta,
            boolean esInterfaz,
            /** Superclase, si hereda de otra clase del modelo. */
            String padre,
            /** Interfaces que realiza. */
            List<String> interfaces,
            /** Cierto si otra clase la extiende: determina la estrategia JPA. */
            boolean esRaizDeJerarquia,
            /** Nulo cuando la clase hereda: la clave vive en la raiz. */
            Campo identificador,
            List<Campo> campos,
            List<Asociacion> asociaciones,
            List<Operacion> operaciones) {

        public boolean tieneIdentificadorPropio() {
            return identificador != null;
        }
    }

    public record Campo(
            String nombre,
            String tipoJava,
            String columna,
            boolean requerido,
            boolean unico,
            Integer longitud,
            /** Cierto cuando la clave la asigna la base de datos y no el modelo. */
            boolean generado) {
    }

    /**
     * Extremo de una asociacion visto desde una clase.
     *
     * @param propietario cierto en el lado que guarda la clave ajena; el otro
     *                    lado se limita a reflejarla con {@code mappedBy}, de
     *                    modo que la relacion se escriba en un solo lugar
     * @param mapeadoPor  nombre del campo que posee la relacion en la otra
     *                    clase; solo en el lado no propietario
     */
    public record Asociacion(
            Cardinalidad cardinalidad,
            String nombreCampo,
            String tipoDestino,
            boolean coleccion,
            boolean propietario,
            boolean requerido,
            String columnaClaveAjena,
            String tablaUnion,
            String mapeadoPor,
            /** Composicion: la parte no existe sin el todo, se borra con el. */
            boolean cascadaTotal) {
    }

    public record Operacion(
            String nombre,
            String tipoRetorno,
            List<Parametro> parametros) {
    }

    public record Parametro(String nombre, String tipoJava) {
    }
}
