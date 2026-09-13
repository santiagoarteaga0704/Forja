package bo.forja.backend.generador;

import org.springframework.stereotype.Component;

/**
 * Las tres capas que se derivan mecanicamente de la entidad: repositorio,
 * servicio y controlador.
 * <p>
 * A diferencia de la entidad, aqui no hay decisiones que tomar: la forma de
 * cada archivo es la misma para todas las clases y solo cambian los
 * nombres. Estan juntas justamente por eso; separarlas en tres componentes
 * repetiria la misma estructura tres veces sin que ninguna aportara una
 * regla propia.
 * <p>
 * El servicio recibe y devuelve la entidad en lugar de objetos de
 * transferencia. Es una simplificacion consciente: el proyecto generado es
 * el punto de partida de un CRUD, y agregar una capa de traduccion que solo
 * copia campos uno a uno daria mas archivos sin agregar comportamiento.
 * Queda anotado en el README del proyecto generado.
 */
@Component
public class EscritorCapas {

    // ---------- Repositorio -------------------------------------------------

    public String repositorio(Plan.Proyecto proyecto, Plan.Clase clase) {
        Plan.Campo identificador = proyecto.identificadorDe(clase);
        String tipoId = TipoJava.simple(identificador.tipoJava());

        Fuente fuente = new Fuente(proyecto.paqueteBase() + ".repositorio");
        fuente.importar(proyecto.paqueteBase() + ".dominio." + clase.nombreJava());
        fuente.importar("org.springframework.data.jpa.repository.JpaRepository");
        fuente.importar(identificador.tipoJava());

        fuente.linea("/** Acceso a datos de " + clase.nombreJava() + ". Generado por FORJA. */");
        fuente.linea("public interface " + clase.nombreJava() + "Repositorio");
        fuente.linea("        extends JpaRepository<" + clase.nombreJava() + ", " + tipoId + "> {");
        fuente.linea("}");
        return fuente.texto();
    }

    // ---------- Servicio ----------------------------------------------------

    public String servicio(Plan.Proyecto proyecto, Plan.Clase clase) {
        Plan.Campo identificador = proyecto.identificadorDe(clase);
        String tipoId = TipoJava.simple(identificador.tipoJava());
        String entidad = clase.nombreJava();
        String variable = Nombres.campo(entidad);

        Fuente fuente = new Fuente(proyecto.paqueteBase() + ".servicio");
        fuente.importar(proyecto.paqueteBase() + ".dominio." + entidad);
        fuente.importar(proyecto.paqueteBase() + ".repositorio." + entidad + "Repositorio");
        fuente.importar("org.springframework.stereotype.Service",
                "org.springframework.transaction.annotation.Transactional",
                "java.util.List", "java.util.NoSuchElementException");
        fuente.importar(identificador.tipoJava());

        fuente.linea("/** Reglas de negocio de " + entidad + ". Generado por FORJA. */");
        fuente.linea("@Service");
        fuente.linea("public class " + entidad + "Servicio {");
        fuente.linea();
        fuente.linea("    private final " + entidad + "Repositorio repositorio;");
        fuente.linea();
        fuente.linea("    public " + entidad + "Servicio(" + entidad + "Repositorio repositorio) {");
        fuente.linea("        this.repositorio = repositorio;");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @Transactional(readOnly = true)");
        fuente.linea("    public List<" + entidad + "> listar() {");
        fuente.linea("        return repositorio.findAll();");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @Transactional(readOnly = true)");
        fuente.linea("    public " + entidad + " obtener(" + tipoId + " id) {");
        fuente.linea("        return repositorio.findById(id)");
        fuente.linea("                .orElseThrow(() -> new NoSuchElementException(");
        fuente.linea("                        \"No existe " + entidad + " con identificador \" + id));");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @Transactional");
        fuente.linea("    public " + entidad + " crear(" + entidad + " " + variable + ") {");
        fuente.linea("        return repositorio.save(" + variable + ");");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    /**");
        fuente.linea("     * Reemplaza el registro completo. Se comprueba que exista antes de");
        fuente.linea("     * guardar para que una actualizacion sobre un identificador ajeno");
        fuente.linea("     * falle en lugar de crear una fila nueva por la puerta de atras.");
        fuente.linea("     */");
        fuente.linea("    @Transactional");
        fuente.linea("    public " + entidad + " actualizar(" + tipoId + " id, " + entidad
                + " " + variable + ") {");
        fuente.linea("        obtener(id);");
        fuente.linea("        " + variable + ".set"
                + Character.toUpperCase(identificador.nombre().charAt(0))
                + identificador.nombre().substring(1) + "(id);");
        fuente.linea("        return repositorio.save(" + variable + ");");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @Transactional");
        fuente.linea("    public void eliminar(" + tipoId + " id) {");
        fuente.linea("        repositorio.delete(obtener(id));");
        fuente.linea("    }");

        fuente.linea("}");
        return fuente.texto();
    }

    // ---------- Controlador -------------------------------------------------

    public String controlador(Plan.Proyecto proyecto, Plan.Clase clase) {
        Plan.Campo identificador = proyecto.identificadorDe(clase);
        String tipoId = TipoJava.simple(identificador.tipoJava());
        String entidad = clase.nombreJava();
        String variable = Nombres.campo(entidad);

        Fuente fuente = new Fuente(proyecto.paqueteBase() + ".web");
        fuente.importar(proyecto.paqueteBase() + ".dominio." + entidad);
        fuente.importar(proyecto.paqueteBase() + ".servicio." + entidad + "Servicio");
        fuente.importar("org.springframework.http.HttpStatus",
                "org.springframework.http.ResponseEntity",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PathVariable",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.RequestBody",
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.RestController",
                "java.util.List");
        fuente.importar(identificador.tipoJava());

        fuente.linea("/** API REST de " + entidad + ". Generado por FORJA. */");
        fuente.linea("@RestController");
        fuente.linea("@RequestMapping(\"/api/" + clase.ruta() + "\")");
        fuente.linea("public class " + entidad + "Controlador {");
        fuente.linea();
        fuente.linea("    private final " + entidad + "Servicio servicio;");
        fuente.linea();
        fuente.linea("    public " + entidad + "Controlador(" + entidad + "Servicio servicio) {");
        fuente.linea("        this.servicio = servicio;");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @GetMapping");
        fuente.linea("    public List<" + entidad + "> listar() {");
        fuente.linea("        return servicio.listar();");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @GetMapping(\"/{id}\")");
        fuente.linea("    public " + entidad + " obtener(@PathVariable " + tipoId + " id) {");
        fuente.linea("        return servicio.obtener(id);");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @PostMapping");
        fuente.linea("    public ResponseEntity<" + entidad + "> crear(@RequestBody " + entidad
                + " " + variable + ") {");
        fuente.linea("        return ResponseEntity.status(HttpStatus.CREATED)");
        fuente.linea("                .body(servicio.crear(" + variable + "));");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @PutMapping(\"/{id}\")");
        fuente.linea("    public " + entidad + " actualizar(@PathVariable " + tipoId + " id,");
        fuente.linea("            @RequestBody " + entidad + " " + variable + ") {");
        fuente.linea("        return servicio.actualizar(id, " + variable + ");");
        fuente.linea("    }");

        fuente.linea();
        fuente.linea("    @DeleteMapping(\"/{id}\")");
        fuente.linea("    public ResponseEntity<Void> eliminar(@PathVariable " + tipoId + " id) {");
        fuente.linea("        servicio.eliminar(id);");
        fuente.linea("        return ResponseEntity.noContent().build();");
        fuente.linea("    }");

        fuente.linea("}");
        return fuente.texto();
    }
}
