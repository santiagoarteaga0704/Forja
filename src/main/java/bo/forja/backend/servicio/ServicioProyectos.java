package bo.forja.backend.servicio;

import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.Proyecto;
import bo.forja.backend.dominio.ProyectoMiembro;
import bo.forja.backend.dominio.ProyectoMiembroId;
import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.TipoDiagrama;
import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.repositorio.DiagramaRepositorio;
import bo.forja.backend.repositorio.ProyectoMiembroRepositorio;
import bo.forja.backend.repositorio.ProyectoRepositorio;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Proyectos colaborativos y sus diagramas.
 * <p>
 * Un proyecto es la unidad de colaboracion: quien pertenece a el ve y
 * edita todos sus diagramas, y quien no pertenece no ve ninguno. Por eso
 * la pertenencia se comprueba aqui y en {@link ServicioOperaciones}, que
 * son los dos lugares por donde se entra al modelo.
 */
@Service
public class ServicioProyectos {

    private final ProyectoRepositorio proyectos;
    private final ProyectoMiembroRepositorio miembros;
    private final DiagramaRepositorio diagramas;
    private final UsuarioRepositorio usuarios;

    public ServicioProyectos(ProyectoRepositorio proyectos,
                             ProyectoMiembroRepositorio miembros,
                             DiagramaRepositorio diagramas,
                             UsuarioRepositorio usuarios) {
        this.proyectos = proyectos;
        this.miembros = miembros;
        this.diagramas = diagramas;
        this.usuarios = usuarios;
    }

    /**
     * Crea el proyecto y deja a su autor inscrito como propietario.
     * <p>
     * La membresia se crea explicitamente en lugar de deducirse de la
     * columna {@code propietario_id}: asi todas las comprobaciones de
     * permiso consultan una sola tabla, sin casos especiales para el dueno.
     */
    @Transactional
    public Proyecto crear(UUID autorId, String nombre, String descripcion) {
        Usuario autor = usuarios.findById(autorId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el usuario " + autorId));

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre.trim());
        proyecto.setDescripcion(descripcion == null || descripcion.isBlank()
                ? null : descripcion.trim());
        proyecto.setPropietario(autor);
        proyectos.save(proyecto);

        inscribir(proyecto, autor, RolMiembro.PROPIETARIO);
        return proyecto;
    }

    @Transactional(readOnly = true)
    public List<Proyecto> deParticipante(UUID usuarioId) {
        return proyectos.buscarPorParticipante(usuarioId);
    }

    /** Invita a otra persona al proyecto; solo el propietario puede hacerlo. */
    @Transactional
    public ProyectoMiembro invitar(UUID proyectoId, UUID solicitanteId, String email, RolMiembro rol) {
        Proyecto proyecto = exigirProyecto(proyectoId);
        ProyectoMiembro solicitante = exigirMembresia(proyectoId, solicitanteId);
        if (solicitante.getRol() != RolMiembro.PROPIETARIO) {
            throw new AccesoDenegado("Solo el propietario puede invitar miembros al proyecto");
        }

        Usuario invitado = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new RecursoNoEncontrado(
                        "No hay ninguna cuenta registrada con el correo " + email));

        return miembros.findByProyectoIdAndUsuarioId(proyectoId, invitado.getId())
                .map(existente -> {
                    existente.setRol(rol);
                    return existente;
                })
                .orElseGet(() -> inscribir(proyecto, invitado, rol));
    }

    @Transactional
    public Diagrama crearDiagrama(UUID proyectoId, UUID autorId, String nombre, TipoDiagrama tipo) {
        Proyecto proyecto = exigirProyecto(proyectoId);
        ProyectoMiembro miembro = exigirMembresia(proyectoId, autorId);
        if (!miembro.getRol().puedeEditar()) {
            throw new AccesoDenegado("El rol " + miembro.getRol() + " no permite crear diagramas");
        }

        Diagrama diagrama = new Diagrama();
        diagrama.setProyecto(proyecto);
        diagrama.setNombre(nombre.trim());
        diagrama.setTipo(tipo != null ? tipo : TipoDiagrama.CLASES);
        return diagramas.save(diagrama);
    }

    @Transactional(readOnly = true)
    public List<Diagrama> diagramasDe(UUID proyectoId, UUID usuarioId) {
        exigirProyecto(proyectoId);
        exigirMembresia(proyectoId, usuarioId);
        return diagramas.findByProyectoIdOrderByNombre(proyectoId);
    }

    /**
     * Diagrama al que el usuario tiene acceso, con su proyecto comprobado.
     * Lo usan los controladores que reciben un identificador de diagrama
     * suelto, sin el proyecto en la ruta.
     */
    @Transactional(readOnly = true)
    public Diagrama diagramaAccesible(UUID diagramaId, UUID usuarioId) {
        Diagrama diagrama = diagramas.findById(diagramaId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el diagrama " + diagramaId));
        exigirMembresia(diagrama.getProyecto().getId(), usuarioId);
        return diagrama;
    }

    @Transactional(readOnly = true)
    public RolMiembro rolEnDiagrama(UUID diagramaId, UUID usuarioId) {
        Diagrama diagrama = diagramas.findById(diagramaId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el diagrama " + diagramaId));
        return exigirMembresia(diagrama.getProyecto().getId(), usuarioId).getRol();
    }

    // ---------- Auxiliares -------------------------------------------------

    private ProyectoMiembro inscribir(Proyecto proyecto, Usuario usuario, RolMiembro rol) {
        ProyectoMiembro miembro = new ProyectoMiembro();
        // La clave compuesta se fija a mano: al derivarse de las asociaciones
        // con @MapsId queda vacia hasta el volcado, y Spring Data no puede
        // decidir si la fila es nueva o existente.
        miembro.setId(new ProyectoMiembroId(proyecto.getId(), usuario.getId()));
        miembro.setProyecto(proyecto);
        miembro.setUsuario(usuario);
        miembro.setRol(rol);
        return miembros.save(miembro);
    }

    private Proyecto exigirProyecto(UUID proyectoId) {
        return proyectos.findById(proyectoId)
                .orElseThrow(() -> new RecursoNoEncontrado("No existe el proyecto " + proyectoId));
    }

    private ProyectoMiembro exigirMembresia(UUID proyectoId, UUID usuarioId) {
        return miembros.findByProyectoIdAndUsuarioId(proyectoId, usuarioId)
                .orElseThrow(() -> new AccesoDenegado("El usuario no es miembro del proyecto"));
    }
}
