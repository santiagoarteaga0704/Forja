package bo.forja.backend.web;

import bo.forja.backend.dominio.RolMiembro;
import bo.forja.backend.dominio.TipoDiagrama;
import bo.forja.backend.seguridad.UsuarioActual;
import bo.forja.backend.servicio.ServicioProyectos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Proyectos colaborativos y los diagramas que contienen. */
@RestController
@RequestMapping("/api/proyectos")
public class ControladorProyectos {

    private final ServicioProyectos proyectos;

    public ControladorProyectos(ServicioProyectos proyectos) {
        this.proyectos = proyectos;
    }

    @GetMapping
    public List<Vistas.ProyectoVista> mios(@AuthenticationPrincipal Jwt token) {
        return proyectos.deParticipante(UsuarioActual.id(token)).stream()
                .map(Vistas::de)
                .toList();
    }

    @PostMapping
    public ResponseEntity<Vistas.ProyectoVista> crear(@AuthenticationPrincipal Jwt token,
                                                      @Valid @RequestBody NuevoProyecto cuerpo) {
        Vistas.ProyectoVista creado = Vistas.de(proyectos.crear(
                UsuarioActual.id(token), cuerpo.nombre(), cuerpo.descripcion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/{proyectoId}/miembros")
    public ResponseEntity<Void> invitar(@AuthenticationPrincipal Jwt token,
                                        @PathVariable UUID proyectoId,
                                        @Valid @RequestBody NuevoMiembro cuerpo) {
        proyectos.invitar(proyectoId, UsuarioActual.id(token), cuerpo.email(),
                cuerpo.rol() != null ? cuerpo.rol() : RolMiembro.EDITOR);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{proyectoId}/diagramas")
    public List<Vistas.DiagramaResumen> diagramas(@AuthenticationPrincipal Jwt token,
                                                  @PathVariable UUID proyectoId) {
        return proyectos.diagramasDe(proyectoId, UsuarioActual.id(token)).stream()
                .map(Vistas::resumen)
                .toList();
    }

    @PostMapping("/{proyectoId}/diagramas")
    public ResponseEntity<Vistas.DiagramaResumen> crearDiagrama(
            @AuthenticationPrincipal Jwt token,
            @PathVariable UUID proyectoId,
            @Valid @RequestBody NuevoDiagrama cuerpo) {

        Vistas.DiagramaResumen creado = Vistas.resumen(proyectos.crearDiagrama(
                proyectoId, UsuarioActual.id(token), cuerpo.nombre(), cuerpo.tipo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    public record NuevoProyecto(
            @NotBlank @Size(max = 150) String nombre,
            String descripcion) {
    }

    public record NuevoDiagrama(
            @NotBlank @Size(max = 150) String nombre,
            TipoDiagrama tipo) {
    }

    public record NuevoMiembro(
            @NotBlank @Email String email,
            RolMiembro rol) {
    }
}
