package bo.forja.backend.servicio;

import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.MetodoUmlRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lectura del modelo UML completo de un diagrama.
 * <p>
 * Existe porque el modelo no puede traerse de una sola consulta. Los
 * atributos y los metodos de una clase son dos colecciones ordenadas, y
 * JPA prohibe combinarlas en un mismo fetch: el producto cartesiano entre
 * ambas haria imposible saber a que clase pertenece cada fila. La salida
 * no es renunciar al modelo completo sino armarlo en varias consultas dentro
 * de una misma transaccion, de modo que cada una complete las mismas
 * instancias que devolvio la primera.
 * <p>
 * Es la entrada de todo lo que necesita leer el modelo entero y no un
 * elemento suelto: el generador de codigo Spring Boot, la exportacion a
 * XMI y la carga inicial del lienzo.
 */
@Service
public class ServicioModelo {

    private final DiagramaRepositorio diagramas;
    private final ClaseUmlRepositorio clases;
    private final MetodoUmlRepositorio metodos;
    private final RelacionUmlRepositorio relaciones;

    public ServicioModelo(DiagramaRepositorio diagramas,
                          ClaseUmlRepositorio clases,
                          MetodoUmlRepositorio metodos,
                          RelacionUmlRepositorio relaciones) {
        this.diagramas = diagramas;
        this.clases = clases;
        this.metodos = metodos;
        this.relaciones = relaciones;
    }

    /**
     * Clases del diagrama con sus atributos y metodos ya cargados, en orden
     * alfabetico.
     * <p>
     * Las consultas devuelven las mismas instancias porque comparten el
     * contexto de persistencia de esta transaccion: las siguientes no
     * duplican las clases, solo inicializan las colecciones que faltaban. De
     * ahi que el metodo sea transaccional y no una simple suma de llamadas.
     */
    @Transactional(readOnly = true)
    public List<ClaseUml> clasesCompletas(UUID diagramaId) {
        exigirDiagrama(diagramaId);
        List<ClaseUml> conAtributos = clases.buscarConAtributos(diagramaId);
        clases.buscarConMetodos(diagramaId);
        // Tercera consulta: los parametros de los metodos. Sin ella el modelo
        // solo parece completo, y el fallo aparece lejos de aqui, cuando quien
        // lo recibe recorre la firma de una operacion ya fuera de la sesion.
        metodos.buscarConParametros(diagramaId);
        return conAtributos;
    }

    @Transactional(readOnly = true)
    public List<RelacionUml> relacionesDe(UUID diagramaId) {
        exigirDiagrama(diagramaId);
        return relaciones.findByDiagramaId(diagramaId);
    }

    private void exigirDiagrama(UUID diagramaId) {
        if (!diagramas.existsById(diagramaId)) {
            throw new RecursoNoEncontrado("No existe el diagrama " + diagramaId);
        }
    }
}
