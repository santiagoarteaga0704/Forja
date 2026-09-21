package bo.forja.backend.generador;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Primera capa: la entidad JPA.
 * <p>
 * Es la unica capa donde el diagrama se traduce campo por campo, y por eso
 * la que mas decisiones del plan consume. Se generan captadores y
 * establecedores en lugar de recurrir a Lombok para que el proyecto
 * resultante compile sin depender de un procesador de anotaciones: quien
 * reciba el codigo generado puede abrirlo y compilarlo sin configurar nada.
 */
@Component
public class EscritorEntidad {

    public String escribir(Plan.Proyecto proyecto, Plan.Clase clase) {
        return clase.esInterfaz() ? interfaz(proyecto, clase) : entidad(proyecto, clase);
    }

    // ---------- Interfaces --------------------------------------------------

    private String interfaz(Plan.Proyecto proyecto, Plan.Clase clase) {
        Fuente fuente = new Fuente(proyecto.paqueteBase() + ".dominio");

        fuente.linea("/** Generado por FORJA a partir de la clase UML " + clase.nombreUml() + ". */");
        fuente.linea("public interface " + clase.nombreJava() + " {");
        for (Plan.Operacion operacion : clase.operaciones()) {
            operacion.parametros().forEach(p -> fuente.importar(p.tipoJava()));
            fuente.importar(operacion.tipoRetorno());
            fuente.linea();
            fuente.linea("    " + TipoJava.simple(operacion.tipoRetorno()) + " "
                    + operacion.nombre() + "(" + parametros(operacion) + ");");
        }
        fuente.linea("}");
        return fuente.texto();
    }

    // ---------- Entidades ---------------------------------------------------

    private String entidad(Plan.Proyecto proyecto, Plan.Clase clase) {
        Fuente fuente = new Fuente(proyecto.paqueteBase() + ".dominio");
        fuente.importar("jakarta.persistence.Entity", "jakarta.persistence.Table");

        fuente.linea("/** Generado por FORJA a partir de la clase UML " + clase.nombreUml() + ". */");
        fuente.linea("@Entity");
        fuente.linea("@Table(name = \"" + clase.tabla() + "\")");

        if (clase.esRaizDeJerarquia()) {
            // JOINED y no SINGLE_TABLE: con tabla unica, las columnas de las
            // subclases deben admitir nulos, y eso perderia las restricciones
            // de obligatoriedad que el diagrama declaro.
            fuente.importar("jakarta.persistence.Inheritance", "jakarta.persistence.InheritanceType");
            fuente.linea("@Inheritance(strategy = InheritanceType.JOINED)");
        }

        fuente.linea("public " + (clase.esAbstracta() ? "abstract " : "") + "class "
                + clase.nombreJava() + declaracionDeHerencia(clase) + " {");

        escribirIdentificador(fuente, clase);
        clase.campos().forEach(campo -> escribirCampo(fuente, campo));
        clase.asociaciones().forEach(asociacion -> escribirAsociacion(fuente, clase, asociacion));
        escribirAccesores(fuente, clase);
        escribirOperaciones(fuente, clase);

        fuente.linea("}");
        return fuente.texto();
    }

    private String declaracionDeHerencia(Plan.Clase clase) {
        StringBuilder declaracion = new StringBuilder();
        if (clase.padre() != null) {
            declaracion.append(" extends ").append(clase.padre());
        }
        if (!clase.interfaces().isEmpty()) {
            declaracion.append(" implements ").append(String.join(", ", clase.interfaces()));
        }
        return declaracion.toString();
    }

    private void escribirIdentificador(Fuente fuente, Plan.Clase clase) {
        if (!clase.tieneIdentificadorPropio()) {
            return;
        }
        Plan.Campo id = clase.identificador();
        fuente.importar("jakarta.persistence.Id", "jakarta.persistence.Column");
        fuente.importar(id.tipoJava());

        fuente.linea();
        fuente.linea("    @Id");
        if (id.generado()) {
            fuente.importar("jakarta.persistence.GeneratedValue",
                    "jakarta.persistence.GenerationType");
            fuente.linea("    @GeneratedValue(strategy = GenerationType.IDENTITY)");
        }
        fuente.linea("    @Column(name = \"" + id.columna() + "\", nullable = false)");
        fuente.linea("    private " + TipoJava.simple(id.tipoJava()) + " " + id.nombre() + ";");
    }

    private void escribirCampo(Fuente fuente, Plan.Campo campo) {
        fuente.importar("jakarta.persistence.Column");
        fuente.importar(campo.tipoJava());

        List<String> atributos = new ArrayList<>();
        atributos.add("name = \"" + campo.columna() + "\"");
        if (campo.requerido()) {
            atributos.add("nullable = false");
        }
        if (campo.unico()) {
            atributos.add("unique = true");
        }
        if (campo.longitud() != null) {
            atributos.add("length = " + campo.longitud());
        }

        fuente.linea();
        fuente.linea("    @Column(" + String.join(", ", atributos) + ")");
        fuente.linea("    private " + TipoJava.simple(campo.tipoJava()) + " " + campo.nombre() + ";");
    }

    private void escribirAsociacion(Fuente fuente, Plan.Clase clase, Plan.Asociacion asociacion) {
        fuente.linea();
        switch (asociacion.cardinalidad()) {
            case MUCHOS_A_UNO -> muchosAUno(fuente, asociacion);
            case UNO_A_MUCHOS -> unoAMuchos(fuente, asociacion);
            case MUCHOS_A_MUCHOS -> muchosAMuchos(fuente, clase, asociacion);
            case UNO_A_UNO -> unoAUno(fuente, asociacion);
        }
    }

    /**
     * Marca el campo para que Jackson no lo escriba.
     * <p>
     * Se usa en las colecciones, que son siempre el lado inverso: el padre
     * lleva a sus hijos y cada hijo lleva a su padre, de modo que serializar
     * las dos puntas no termina nunca.
     */
    private void anotarSinSerializar(Fuente fuente) {
        fuente.importar("com.fasterxml.jackson.annotation.JsonIgnore");
        fuente.linea("    @JsonIgnore");
    }

    private void muchosAUno(Fuente fuente, Plan.Asociacion asociacion) {
        fuente.importar("jakarta.persistence.ManyToOne", "jakarta.persistence.FetchType",
                "jakarta.persistence.JoinColumn");
        // Ansiosa, y NO perezosa como parecia lo correcto. El proyecto generado
        // trae open-in-view en false -que si es lo correcto- y sus
        // controladores devuelven la entidad, asi que cuando Jackson la
        // serializa la transaccion ya cerro y una asociacion perezosa revienta
        // con "no session". Se comprobo arrancando el proyecto: las cuatro
        // rutas de lectura devolvian 500.
        //
        // Ademas es lo que se espera de un CRUD: pedir una consulta y que
        // venga con su paciente y su medico adentro. El costo es el de
        // siempre con EAGER -trae la otra entidad aunque no se use-, y esta
        // dicho en el README que acompana al proyecto.
        fuente.linea("    @ManyToOne(fetch = FetchType.EAGER"
                + (asociacion.requerido() ? ", optional = false" : "") + ")");
        fuente.linea("    @JoinColumn(name = \"" + asociacion.columnaClaveAjena() + "\""
                + (asociacion.requerido() ? ", nullable = false" : "") + ")");
        fuente.linea("    private " + asociacion.tipoDestino() + " "
                + asociacion.nombreCampo() + ";");
    }

    private void unoAMuchos(Fuente fuente, Plan.Asociacion asociacion) {
        fuente.importar("jakarta.persistence.OneToMany", "java.util.List", "java.util.ArrayList");
        String extras = asociacion.cascadaTotal()
                // Composicion: la parte no tiene vida propia, se borra con el todo.
                ? ", cascade = CascadeType.ALL, orphanRemoval = true"
                : "";
        if (asociacion.cascadaTotal()) {
            fuente.importar("jakarta.persistence.CascadeType");
        }
        // La coleccion no se serializa: el padre lleva sus hijos y cada hijo
        // lleva su padre, asi que salir a escribir JSON por los dos lados es
        // una recursion infinita. Quien quiera los hijos pide la ruta del
        // hijo, que es como se navega un CRUD.
        anotarSinSerializar(fuente);
        fuente.linea("    @OneToMany(mappedBy = \"" + asociacion.mapeadoPor() + "\"" + extras + ")");
        fuente.linea("    private List<" + asociacion.tipoDestino() + "> "
                + asociacion.nombreCampo() + " = new ArrayList<>();");
    }

    private void muchosAMuchos(Fuente fuente, Plan.Clase clase, Plan.Asociacion asociacion) {
        fuente.importar("jakarta.persistence.ManyToMany", "java.util.List", "java.util.ArrayList");
        anotarSinSerializar(fuente);
        if (asociacion.propietario()) {
            fuente.importar("jakarta.persistence.JoinTable", "jakarta.persistence.JoinColumn");
            fuente.linea("    @ManyToMany");
            fuente.linea("    @JoinTable(name = \"" + asociacion.tablaUnion() + "\",");
            fuente.linea("            joinColumns = @JoinColumn(name = \""
                    + Nombres.columna(clase.nombreJava()) + "_id\"),");
            fuente.linea("            inverseJoinColumns = @JoinColumn(name = \""
                    + Nombres.columna(asociacion.tipoDestino()) + "_id\"))");
        } else {
            fuente.linea("    @ManyToMany(mappedBy = \"" + asociacion.mapeadoPor() + "\")");
        }
        fuente.linea("    private List<" + asociacion.tipoDestino() + "> "
                + asociacion.nombreCampo() + " = new ArrayList<>();");
    }

    private void unoAUno(Fuente fuente, Plan.Asociacion asociacion) {
        fuente.importar("jakarta.persistence.OneToOne", "jakarta.persistence.FetchType");
        if (asociacion.propietario()) {
            fuente.importar("jakarta.persistence.JoinColumn");
            fuente.linea("    @OneToOne(fetch = FetchType.EAGER"
                    + (asociacion.requerido() ? ", optional = false" : "") + ")");
            fuente.linea("    @JoinColumn(name = \"" + asociacion.columnaClaveAjena()
                    + "\", unique = true"
                    + (asociacion.requerido() ? ", nullable = false" : "") + ")");
        } else {
            fuente.linea("    @OneToOne(mappedBy = \"" + asociacion.mapeadoPor()
                    + "\", fetch = FetchType.EAGER)");
        }
        fuente.linea("    private " + asociacion.tipoDestino() + " "
                + asociacion.nombreCampo() + ";");
    }

    // ---------- Accesores y operaciones -------------------------------------

    private void escribirAccesores(Fuente fuente, Plan.Clase clase) {
        if (clase.tieneIdentificadorPropio()) {
            accesor(fuente, clase.identificador().nombre(),
                    TipoJava.simple(clase.identificador().tipoJava()));
        }
        clase.campos().forEach(campo ->
                accesor(fuente, campo.nombre(), TipoJava.simple(campo.tipoJava())));
        clase.asociaciones().forEach(asociacion -> accesor(fuente, asociacion.nombreCampo(),
                asociacion.coleccion()
                        ? "List<" + asociacion.tipoDestino() + ">"
                        : asociacion.tipoDestino()));
    }

    private void accesor(Fuente fuente, String nombre, String tipo) {
        String enMayuscula = Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
        fuente.linea();
        fuente.linea("    public " + tipo + " get" + enMayuscula + "() {");
        fuente.linea("        return " + nombre + ";");
        fuente.linea("    }");
        fuente.linea();
        fuente.linea("    public void set" + enMayuscula + "(" + tipo + " " + nombre + ") {");
        fuente.linea("        this." + nombre + " = " + nombre + ";");
        fuente.linea("    }");
    }

    /**
     * Las operaciones declaradas en el diagrama se emiten como metodos con el
     * cuerpo sin escribir. Se dejan aqui, y no en el servicio, porque en UML
     * pertenecen a la clase; el cuerpo no se inventa porque el diagrama
     * declara la firma pero no el comportamiento, y rellenarlo con algo
     * plausible seria adivinar.
     */
    private void escribirOperaciones(Fuente fuente, Plan.Clase clase) {
        for (Plan.Operacion operacion : clase.operaciones()) {
            operacion.parametros().forEach(p -> fuente.importar(p.tipoJava()));
            fuente.importar(operacion.tipoRetorno());

            fuente.linea();
            fuente.linea("    /** Declarada en el diagrama. Falta implementar el comportamiento. */");
            fuente.linea("    public " + TipoJava.simple(operacion.tipoRetorno()) + " "
                    + operacion.nombre() + "(" + parametros(operacion) + ") {");
            fuente.linea("        throw new UnsupportedOperationException(\""
                    + operacion.nombre() + " todavia no esta implementada\");");
            fuente.linea("    }");
        }
    }

    private String parametros(Plan.Operacion operacion) {
        return operacion.parametros().stream()
                .map(p -> TipoJava.simple(p.tipoJava()) + " " + p.nombre())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
