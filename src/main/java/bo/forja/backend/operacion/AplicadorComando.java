package bo.forja.backend.operacion;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.MetodoUml;
import bo.forja.backend.dominio.ParametroUml;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.repositorio.ClaseUmlRepositorio;
import bo.forja.backend.repositorio.RelacionUmlRepositorio;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unico punto donde el modelo UML se modifica.
 * <p>
 * Recibe un comando ya autorizado y ya validado en su forma, y lo
 * traduce a cambios sobre las entidades. Concentrar aqui la escritura
 * tiene una consecuencia que vale mas que el orden: la voz, la
 * fotografia, la importacion XMI y el lienzo comparten exactamente estas
 * validaciones. Un dictado no puede producir un modelo que el lienzo
 * habria rechazado.
 * <p>
 * Las comprobaciones se hacen aqui ademas de existir como restricciones
 * en la base de datos. No es redundancia inutil: la restriccion protege
 * la integridad pero aborta la transaccion con un error opaco, mientras
 * que esta capa devuelve al usuario un mensaje que explica que paso.
 */
@Component
public class AplicadorComando {

    private final ClaseUmlRepositorio clases;
    private final RelacionUmlRepositorio relaciones;

    public AplicadorComando(ClaseUmlRepositorio clases, RelacionUmlRepositorio relaciones) {
        this.clases = clases;
        this.relaciones = relaciones;
    }

    /**
     * Aplica el comando sobre el diagrama. El {@code switch} exhaustivo
     * sobre la interfaz sellada garantiza que ningun comando quede sin
     * tratamiento en tiempo de compilacion.
     */
    public void aplicar(Diagrama diagrama, ComandoOperacion comando) {
        switch (comando) {
            case ComandoOperacion.CrearClase c -> crearClase(diagrama, c);
            case ComandoOperacion.RenombrarClase c -> renombrarClase(diagrama, c);
            case ComandoOperacion.MarcarClase c -> marcarClase(diagrama, c);
            case ComandoOperacion.MoverClase c -> moverClase(diagrama, c);
            case ComandoOperacion.EliminarClase c -> eliminarClase(diagrama, c);
            case ComandoOperacion.AgregarAtributo c -> agregarAtributo(diagrama, c);
            case ComandoOperacion.EliminarAtributo c -> eliminarAtributo(diagrama, c);
            case ComandoOperacion.AgregarMetodo c -> agregarMetodo(diagrama, c);
            case ComandoOperacion.EliminarMetodo c -> eliminarMetodo(diagrama, c);
            case ComandoOperacion.CrearRelacion c -> crearRelacion(diagrama, c);
            case ComandoOperacion.EliminarRelacion c -> eliminarRelacion(diagrama, c);
        }
    }

    // ---------- Clases -------------------------------------------------

    private void crearClase(Diagrama diagrama, ComandoOperacion.CrearClase c) {
        if (clases.existsById(c.claseId())) {
            throw new ComandoInvalido("Ya existe una clase con el identificador " + c.claseId());
        }
        exigirNombreLibre(diagrama.getId(), c.nombre(), null);

        ClaseUml clase = new ClaseUml();
        clase.setId(c.claseId());
        clase.setDiagrama(diagrama);
        clase.setNombre(c.nombre().trim());
        clase.setEstereotipo(vacioANulo(c.estereotipo()));
        clase.setEsAbstracta(c.esAbstracta());
        clase.setPosX(c.posX());
        clase.setPosY(c.posY());
        clases.save(clase);
    }

    private void renombrarClase(Diagrama diagrama, ComandoOperacion.RenombrarClase c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        exigirNombreLibre(diagrama.getId(), c.nombre(), c.claseId());
        clase.setNombre(c.nombre().trim());
    }

    private void marcarClase(Diagrama diagrama, ComandoOperacion.MarcarClase c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        clase.setEstereotipo(vacioANulo(c.estereotipo()));
        clase.setEsAbstracta(c.esAbstracta());
    }

    private void moverClase(Diagrama diagrama, ComandoOperacion.MoverClase c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        clase.setPosX(c.posX());
        clase.setPosY(c.posY());
    }

    /**
     * Elimina la clase y, antes, las relaciones que la tocan. La base de
     * datos las borraria en cascada igual, pero hacerlo explicitamente
     * mantiene sincronizado el contexto de persistencia y deja el efecto
     * completo a la vista de quien lea la bitacora.
     */
    private void eliminarClase(Diagrama diagrama, ComandoOperacion.EliminarClase c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        List<RelacionUml> incidentes =
                relaciones.findByOrigenIdOrDestinoId(c.claseId(), c.claseId());
        if (!incidentes.isEmpty()) {
            relaciones.deleteAll(incidentes);
        }
        clases.delete(clase);
    }

    // ---------- Atributos ----------------------------------------------

    private void agregarAtributo(Diagrama diagrama, ComandoOperacion.AgregarAtributo c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        boolean repetido = clase.getAtributos().stream()
                .anyMatch(a -> a.getNombre().equalsIgnoreCase(c.nombre().trim()));
        if (repetido) {
            throw new ComandoInvalido(
                    "La clase " + clase.getNombre() + " ya tiene un atributo llamado " + c.nombre());
        }

        AtributoUml atributo = new AtributoUml();
        atributo.setId(c.atributoId());
        atributo.setClase(clase);
        atributo.setNombre(c.nombre().trim());
        atributo.setTipo(c.tipo().trim());
        atributo.setVisibilidad(c.visibilidad() != null ? c.visibilidad() : Visibilidad.PRIVADO);
        atributo.setEsIdentificador(c.esIdentificador());
        atributo.setEsRequerido(c.esRequerido());
        atributo.setEsUnico(c.esUnico());
        atributo.setLongitud(c.longitud());
        atributo.setOrden(siguienteOrden(clase.getAtributos().stream()
                .map(AtributoUml::getOrden).toList()));
        clase.getAtributos().add(atributo);
    }

    private void eliminarAtributo(Diagrama diagrama, ComandoOperacion.EliminarAtributo c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        boolean quitado = clase.getAtributos()
                .removeIf(a -> a.getId().equals(c.atributoId()));
        if (!quitado) {
            throw new ComandoInvalido("El atributo " + c.atributoId()
                    + " no pertenece a la clase " + clase.getNombre());
        }
    }

    // ---------- Metodos -------------------------------------------------

    private void agregarMetodo(Diagrama diagrama, ComandoOperacion.AgregarMetodo c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());

        MetodoUml metodo = new MetodoUml();
        metodo.setId(c.metodoId());
        metodo.setClase(clase);
        metodo.setNombre(c.nombre().trim());
        metodo.setTipoRetorno(esVacio(c.tipoRetorno()) ? "void" : c.tipoRetorno().trim());
        metodo.setVisibilidad(c.visibilidad() != null ? c.visibilidad() : Visibilidad.PUBLICO);
        metodo.setEsAbstracto(c.esAbstracto());
        metodo.setEsEstatico(c.esEstatico());
        metodo.setOrden(siguienteOrden(clase.getMetodos().stream()
                .map(MetodoUml::getOrden).toList()));

        int orden = 0;
        for (ComandoOperacion.Parametro declarado : c.parametrosOVacio()) {
            ParametroUml parametro = new ParametroUml();
            parametro.setId(declarado.parametroId());
            parametro.setMetodo(metodo);
            parametro.setNombre(declarado.nombre().trim());
            parametro.setTipo(declarado.tipo().trim());
            parametro.setOrden(orden++);
            metodo.getParametros().add(parametro);
        }

        clase.getMetodos().add(metodo);
    }

    private void eliminarMetodo(Diagrama diagrama, ComandoOperacion.EliminarMetodo c) {
        ClaseUml clase = exigirClase(diagrama, c.claseId());
        boolean quitado = clase.getMetodos()
                .removeIf(m -> m.getId().equals(c.metodoId()));
        if (!quitado) {
            throw new ComandoInvalido("El metodo " + c.metodoId()
                    + " no pertenece a la clase " + clase.getNombre());
        }
    }

    // ---------- Relaciones ----------------------------------------------

    private void crearRelacion(Diagrama diagrama, ComandoOperacion.CrearRelacion c) {
        if (relaciones.existsById(c.relacionId())) {
            throw new ComandoInvalido("Ya existe una relacion con el identificador " + c.relacionId());
        }
        ClaseUml origen = exigirClase(diagrama, c.origenId());
        ClaseUml destino = exigirClase(diagrama, c.destinoId());

        // UML admite la autoasociacion, pero una clase no puede heredar de
        // si misma ni realizarse a si misma.
        boolean reflexiva = c.origenId().equals(c.destinoId());
        if (reflexiva && (c.tipo() == TipoRelacion.HERENCIA || c.tipo() == TipoRelacion.REALIZACION)) {
            throw new ComandoInvalido("Una clase no puede tener una relacion de "
                    + c.tipo() + " consigo misma");
        }

        RelacionUml relacion = new RelacionUml();
        relacion.setId(c.relacionId());
        relacion.setDiagrama(diagrama);
        relacion.setOrigen(origen);
        relacion.setDestino(destino);
        relacion.setTipo(c.tipo());
        relacion.setMultiplicidadOrigen(omitirVacio(c.multiplicidadOrigen(), "1"));
        relacion.setMultiplicidadDestino(omitirVacio(c.multiplicidadDestino(), "1"));
        relacion.setRolOrigen(vacioANulo(c.rolOrigen()));
        relacion.setRolDestino(vacioANulo(c.rolDestino()));
        relacion.setEtiqueta(vacioANulo(c.etiqueta()));
        relaciones.save(relacion);
    }

    private void eliminarRelacion(Diagrama diagrama, ComandoOperacion.EliminarRelacion c) {
        RelacionUml relacion = relaciones.findById(c.relacionId())
                .orElseThrow(() -> new ComandoInvalido(
                        "No existe la relacion " + c.relacionId()));
        if (!relacion.getDiagrama().getId().equals(diagrama.getId())) {
            throw new ComandoInvalido("La relacion " + c.relacionId()
                    + " pertenece a otro diagrama");
        }
        relaciones.delete(relacion);
    }

    // ---------- Auxiliares ----------------------------------------------

    /**
     * Recupera la clase exigiendo que pertenezca al diagrama indicado.
     * Comprobarlo evita que un cliente modifique, con un identificador
     * valido de otro diagrama, un modelo sobre el que no esta trabajando.
     */
    private ClaseUml exigirClase(Diagrama diagrama, UUID claseId) {
        ClaseUml clase = clases.findById(claseId)
                .orElseThrow(() -> new ComandoInvalido("No existe la clase " + claseId));
        if (!clase.getDiagrama().getId().equals(diagrama.getId())) {
            throw new ComandoInvalido("La clase " + claseId + " pertenece a otro diagrama");
        }
        return clase;
    }

    /**
     * El nombre de una clase es unico dentro del diagrama. Al renombrar se
     * excluye la propia clase, para que reasignarle su mismo nombre no se
     * interprete como conflicto.
     */
    private void exigirNombreLibre(UUID diagramaId, String nombre, UUID exceptoClaseId) {
        Optional<ClaseUml> homonima =
                clases.findByDiagramaIdAndNombreIgnoreCase(diagramaId, nombre.trim());
        boolean ocupado = homonima
                .filter(c -> exceptoClaseId == null || !c.getId().equals(exceptoClaseId))
                .isPresent();
        if (ocupado) {
            throw new ComandoInvalido(
                    "El diagrama ya tiene una clase llamada " + nombre.trim());
        }
    }

    private static int siguienteOrden(List<Integer> ordenes) {
        return ordenes.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String vacioANulo(String texto) {
        return esVacio(texto) ? null : texto.trim();
    }

    private static String omitirVacio(String texto, String pordefecto) {
        return esVacio(texto) ? pordefecto : texto.trim();
    }
}
