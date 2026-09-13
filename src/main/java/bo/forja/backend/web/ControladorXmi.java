package bo.forja.backend.web;

import bo.forja.backend.seguridad.UsuarioActual;
import bo.forja.backend.xmi.ServicioXmi;
import bo.forja.backend.xmi.XmiInvalido;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Intercambio del modelo en XMI 2.5.1.
 * <p>
 * La importacion admite dos formas de enviar el documento porque los dos
 * clientes lo obtienen de maneras distintas: el navegador, de un selector de
 * archivos que naturalmente produce un envio multiparte; la aplicacion
 * movil, de un texto que ya tiene en memoria. Obligar a una sola forma
 * complicaria a uno de los dos sin beneficio.
 */
@RestController
@RequestMapping("/api/diagramas/{diagramaId}/xmi")
public class ControladorXmi {

    private final ServicioXmi xmi;

    public ControladorXmi(ServicioXmi xmi) {
        this.xmi = xmi;
    }

    /** Exporta el diagrama para abrirlo en Enterprise Architect. */
    @GetMapping
    public ResponseEntity<String> exportar(@AuthenticationPrincipal Jwt token,
                                           @PathVariable UUID diagramaId) {

        String documento = xmi.exportar(diagramaId, UsuarioActual.id(token));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("modelo-" + diagramaId + ".xmi").build().toString())
                .contentType(MediaType.APPLICATION_XML)
                .body(documento);
    }

    /** Importa el documento enviado como cuerpo de la peticion. */
    @PostMapping(consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE})
    public ServicioXmi.ResumenImportacion importar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable UUID diagramaId,
            @RequestParam String sesionId,
            @RequestParam(required = false) String tokenImportacion,
            @RequestBody String documento) {

        return xmi.importar(diagramaId, UsuarioActual.id(token), sesionId,
                documento, tokenImportacion);
    }

    /** Importa el documento enviado como archivo, desde un formulario. */
    @PostMapping(path = "/archivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServicioXmi.ResumenImportacion importarArchivo(
            @AuthenticationPrincipal Jwt token,
            @PathVariable UUID diagramaId,
            @RequestParam String sesionId,
            @RequestParam(required = false) String tokenImportacion,
            @RequestParam MultipartFile archivo) {

        if (archivo.isEmpty()) {
            throw new XmiInvalido("El archivo llego vacio");
        }
        try {
            String documento = new String(archivo.getBytes(), StandardCharsets.UTF_8);
            return xmi.importar(diagramaId, UsuarioActual.id(token), sesionId,
                    documento, tokenImportacion);
        } catch (IOException e) {
            throw new XmiInvalido("No se pudo leer el archivo enviado", e);
        }
    }
}
