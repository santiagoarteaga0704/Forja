package bo.forja.backend.web;

import bo.forja.backend.operacion.ComandoInvalido;
import bo.forja.backend.seguridad.CredencialesInvalidas;
import bo.forja.backend.seguridad.EmailYaRegistrado;
import bo.forja.backend.servicio.AccesoDenegado;
import bo.forja.backend.servicio.RecursoNoEncontrado;
import bo.forja.backend.servicio.SesionSinDueno;
import bo.forja.backend.xmi.XmiInvalido;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones del dominio a respuestas HTTP.
 * <p>
 * Cada situacion tiene un codigo distinto porque el cliente reacciona de
 * manera distinta a cada una, y confundirlas lo llevaria a reintentar lo
 * que nunca va a funcionar o a rendirse ante lo que solo hacia falta
 * repetir. Un comando invalido (422) no debe reintentarse tal cual; un
 * rechazo por bloqueo, en cambio, se resuelve esperando y volviendo a
 * pedir, y por eso no viaja como error sino dentro de la respuesta
 * normal del registro de operaciones.
 * <p>
 * Se responde con {@link ProblemDetail}, el formato de la RFC 7807, en
 * lugar de un objeto propio: los dos clientes leen la misma estructura y
 * no hay que documentar un formato de error inventado.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(CredencialesInvalidas.class)
    ProblemDetail credenciales(CredencialesInvalidas e) {
        return problema(HttpStatus.UNAUTHORIZED, "Credenciales invalidas", e.getMessage());
    }

    @ExceptionHandler(EmailYaRegistrado.class)
    ProblemDetail emailRepetido(EmailYaRegistrado e) {
        return problema(HttpStatus.CONFLICT, "Correo ya registrado", e.getMessage());
    }

    @ExceptionHandler(AccesoDenegado.class)
    ProblemDetail acceso(AccesoDenegado e) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", e.getMessage());
    }

    /**
     * 401 y no 404: lo que falta no es el recurso pedido sino el dueno de la
     * credencial. Es lo que le permite al cliente tirar la sesion y mandar al
     * login, en vez de mostrar un identificador en rojo que no le dice nada a
     * nadie.
     */
    @ExceptionHandler(SesionSinDueno.class)
    ProblemDetail sesionSinDueno(SesionSinDueno e) {
        return problema(HttpStatus.UNAUTHORIZED, "Sesión no válida", e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontrado.class)
    ProblemDetail noEncontrado(RecursoNoEncontrado e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
    }

    /**
     * El comando es sintacticamente correcto pero no puede aplicarse sobre
     * el estado actual del modelo. 422 y no 400: el problema no esta en la
     * forma de la peticion sino en su significado.
     */
    @ExceptionHandler(ComandoInvalido.class)
    ProblemDetail comandoInvalido(ComandoInvalido e) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Comando invalido", e.getMessage());
    }

    /**
     * El documento XMI no se puede interpretar. Es 400 y no 422: el problema
     * esta en el archivo recibido, no en el modelo que describe.
     */
    @ExceptionHandler(XmiInvalido.class)
    ProblemDetail xmiInvalido(XmiInvalido e) {
        return problema(HttpStatus.BAD_REQUEST, "XMI invalido", e.getMessage());
    }

    /** Falla la forma de la peticion: se detalla campo por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail detalle = problema(HttpStatus.BAD_REQUEST, "Peticion invalida",
                "Hay campos que no cumplen las restricciones");
        detalle.setProperty("campos", campos);
        return detalle;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
