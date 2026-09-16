package bo.forja.backend.web;

import bo.forja.backend.generador.ServicioGeneracion;
import bo.forja.backend.seguridad.UsuarioActual;
import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.servicio.ServicioUso;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generacion del proyecto Spring Boot a partir del diagrama.
 * <p>
 * Se ofrecen las dos formas por una razon practica: el zip es el producto,
 * pero poder listar los archivos y leer uno permite mostrar el codigo en la
 * pantalla antes de descargarlo. Quien evalua el diagrama quiere ver que
 * salio de el sin tener que descomprimir nada.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/generacion")
public class ControladorGeneracion {

    private final ServicioGeneracion generacion;
    private final ServicioUso uso;

    public ControladorGeneracion(ServicioGeneracion generacion, ServicioUso uso) {
        this.generacion = generacion;
        this.uso = uso;
    }

    /** Rutas de los archivos que produciria la generacion. */
    @GetMapping
    public Resumen resumen(@AuthenticationPrincipal Jwt token,
                           @PathVariable UUID diagramaId,
                           @RequestParam(required = false) String paquete) {

        Map<String, String> archivos =
                generacion.archivos(diagramaId, UsuarioActual.id(token), paquete);
        // Mirar el codigo generado no deja rastro en el modelo; se anota aqui
        // para que el agente sepa que esta funcion ya se descubrio.
        uso.anotar(UsuarioActual.id(token), Herramienta.BACKEND_GENERADO);
        return new Resumen(archivos.size(), List.copyOf(archivos.keySet()));
    }

    /** Contenido de un archivo concreto, para mostrarlo en pantalla. */
    @GetMapping("/archivo")
    public ResponseEntity<String> archivo(@AuthenticationPrincipal Jwt token,
                                          @PathVariable UUID diagramaId,
                                          @RequestParam String ruta,
                                          @RequestParam(required = false) String paquete) {

        Map<String, String> archivos =
                generacion.archivos(diagramaId, UsuarioActual.id(token), paquete);
        String contenido = archivos.get(ruta);
        if (contenido == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(contenido);
    }

    /** El proyecto entero, comprimido. */
    @GetMapping("/zip")
    public ResponseEntity<byte[]> zip(@AuthenticationPrincipal Jwt token,
                                      @PathVariable UUID diagramaId,
                                      @RequestParam(required = false) String paquete) {

        Map<String, String> archivos =
                generacion.archivos(diagramaId, UsuarioActual.id(token), paquete);
        String carpeta = archivos.containsKey("pom.xml")
                ? nombreDeCarpeta(archivos.get("pom.xml"))
                : "proyecto-generado";
        byte[] comprimido = generacion.comprimir(archivos, carpeta);
        uso.anotar(UsuarioActual.id(token), Herramienta.PROYECTO_DESCARGADO);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(carpeta + ".zip").build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(comprimido.length)
                .body(comprimido);
    }

    /**
     * La carpeta raiz del zip toma el nombre del artefacto, leido del pom que
     * acaba de generarse. Asi el nombre del archivo descargado y el del
     * proyecto que hay dentro coinciden.
     */
    private String nombreDeCarpeta(String pom) {
        int inicio = pom.indexOf("<artifactId>", pom.indexOf("</parent>"));
        if (inicio < 0) {
            return "proyecto-generado";
        }
        int desde = inicio + "<artifactId>".length();
        int hasta = pom.indexOf("</artifactId>", desde);
        return hasta < 0 ? "proyecto-generado" : pom.substring(desde, hasta).trim();
    }

    public record Resumen(int cantidadDeArchivos, List<String> rutas) {
    }
}
