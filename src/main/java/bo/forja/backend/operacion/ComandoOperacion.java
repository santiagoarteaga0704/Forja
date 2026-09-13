package bo.forja.backend.operacion;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cambio elemental que puede aplicarse sobre un diagrama de clases.
 * <p>
 * Toda modificacion del modelo, sin importar por que canal entro,
 * termina expresada como uno de estos comandos. El lienzo los emite al
 * arrastrar y escribir; el dictado por voz los obtiene del parser
 * determinista; la fotografia de una pizarra los obtiene del
 * reconocimiento de texto; la importacion XMI los obtiene del documento.
 * Esa convergencia es deliberada: el modelo solo se modifica por una
 * via, y por lo tanto solo hay un lugar donde validar y un unico formato
 * que guardar en la bitacora.
 * <p>
 * La interfaz es sellada para que el aplicador pueda recorrerla con un
 * {@code switch} exhaustivo: agregar un comando nuevo sin contemplar su
 * aplicacion deja de compilar, en lugar de fallar en ejecucion.
 * <p>
 * Los identificadores los asigna el cliente y no la base de datos. Es lo
 * que permite a la aplicacion movil crear clases sin conexion y
 * sincronizarlas despues sin renumerar nada.
 */
public sealed interface ComandoOperacion {

    /**
     * Elemento cuyo bloqueo debe retener el autor para que el comando sea
     * aceptado. Esta vacio en las altas, que no compiten por un elemento
     * preexistente y se controlan por la pertenencia al proyecto.
     */
    Optional<Elemento> elementoAfectado();

    // ---------- Clases -------------------------------------------------

    record CrearClase(
            @NotNull UUID claseId,
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 60) String estereotipo,
            boolean esAbstracta,
            double posX,
            double posY) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.empty();
        }
    }

    record RenombrarClase(
            @NotNull UUID claseId,
            @NotBlank @Size(max = 120) String nombre) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    record MoverClase(
            @NotNull UUID claseId,
            double posX,
            double posY) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    record EliminarClase(@NotNull UUID claseId) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    // ---------- Atributos y metodos ------------------------------------
    // Se bloquea la clase contenedora, no el atributo: en el lienzo el
    // usuario edita la caja completa, y bloquear cada renglon dejaria
    // pasar dos ediciones simultaneas sobre la misma clase.

    record AgregarAtributo(
            @NotNull UUID claseId,
            @NotNull UUID atributoId,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 80) String tipo,
            Visibilidad visibilidad,
            boolean esIdentificador,
            boolean esRequerido,
            boolean esUnico,
            Integer longitud) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    record EliminarAtributo(
            @NotNull UUID claseId,
            @NotNull UUID atributoId) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    record AgregarMetodo(
            @NotNull UUID claseId,
            @NotNull UUID metodoId,
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 80) String tipoRetorno,
            Visibilidad visibilidad,
            boolean esAbstracto,
            boolean esEstatico,
            /* La firma completa, no solo el nombre: sin los parametros, una
             * operacion importada desde XMI perderia la mitad de su
             * definicion y el generador produciria un metodo sin argumentos
             * que no es el que el modelo declaraba. */
            List<Parametro> parametros) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }

        /** Nunca nulo: un metodo sin parametros trae la lista vacia. */
        public List<Parametro> parametrosOVacio() {
            return parametros == null ? List.of() : parametros;
        }
    }

    /** Parametro de una operacion, en el orden en que aparece en la firma. */
    record Parametro(
            @NotNull UUID parametroId,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(max = 80) String tipo) {
    }

    record EliminarMetodo(
            @NotNull UUID claseId,
            @NotNull UUID metodoId) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.clase(claseId));
        }
    }

    // ---------- Relaciones ---------------------------------------------

    record CrearRelacion(
            @NotNull UUID relacionId,
            @NotNull UUID origenId,
            @NotNull UUID destinoId,
            @NotNull TipoRelacion tipo,
            @Size(max = 10) String multiplicidadOrigen,
            @Size(max = 10) String multiplicidadDestino,
            @Size(max = 120) String rolOrigen,
            @Size(max = 120) String rolDestino,
            @Size(max = 150) String etiqueta) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.empty();
        }
    }

    record EliminarRelacion(@NotNull UUID relacionId) implements ComandoOperacion {

        @Override
        public Optional<Elemento> elementoAfectado() {
            return Optional.of(Elemento.relacion(relacionId));
        }
    }
}
