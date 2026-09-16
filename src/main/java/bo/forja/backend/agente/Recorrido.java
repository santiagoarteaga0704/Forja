package bo.forja.backend.agente;

import bo.forja.backend.dominio.Herramienta;
import bo.forja.backend.dominio.OrigenOperacion;

import java.util.List;
import java.util.Optional;

/**
 * El camino completo por FORJA, con lo que ya esta hecho marcado.
 * <p>
 * Los consejos sueltos ensenan de a una funcion; esto muestra el recorrido
 * entero, que es otra cosa: quien recien entra necesita saber cuanto falta y a
 * donde lleva todo esto, no solo cual es el proximo boton.
 * <p>
 * <b>Ningun paso se marca porque la persona haya visto una pantalla.</b> Se
 * marcan mirando la evidencia real -la bitacora y el registro de uso-, asi que
 * el recorrido no se puede completar haciendo clic en "siguiente": hay que haber
 * hecho la cosa. Esa misma evidencia es la que la evaluacion pide para demostrar
 * que cada funcion existe y se usa.
 * <p>
 * Los pasos siguen el orden del enunciado: modelar por las tres vias, generar el
 * backend, intercambiar con Enterprise Architect y trabajar entre varios.
 */
public record Recorrido(List<Paso> pasos) {

    /**
     * @param hecho si ya ocurrio, segun la bitacora o el registro de uso
     * @param donde pantalla en la que se hace, para que el cliente pueda llevar
     *              a la persona hasta ahi
     */
    public record Paso(String id, String titulo, String comoSeHace, boolean hecho, String donde) {
    }

    public static Recorrido de(Panorama p) {
        return new Recorrido(List.of(
                new Paso("proyecto",
                        "Crear un proyecto",
                        "Agrupa los diagramas de un mismo sistema y a quienes pueden editarlos",
                        p.proyectos() > 0,
                        "PROYECTOS"),
                new Paso("diagrama",
                        "Crear un diagrama",
                        "Es la hoja donde se modela, y de donde sale todo lo demás",
                        p.diagramas() > 0,
                        "PROYECTOS"),
                new Paso("modelar",
                        "Dibujar el modelo",
                        "Clases con sus atributos y operaciones, y relaciones entre ellas",
                        p.totalDeOperaciones() > 0,
                        "LIENZO"),
                new Paso("dictar",
                        "Dictarle una frase",
                        "«un Paciente tiene muchas Consultas»: la segunda vía de entrada",
                        p.operacionesConOrigen(OrigenOperacion.VOZ) > 0,
                        "LIENZO"),
                new Paso("pizarra",
                        "Leer la foto de una pizarra",
                        "La tercera vía: el reconocimiento ocurre en tu navegador",
                        p.operacionesConOrigen(OrigenOperacion.FOTO) > 0,
                        "LIENZO"),
                new Paso("generar",
                        "Generar el backend",
                        "Cuatro capas por clase, en un proyecto Spring Boot que compila",
                        p.yaUso(Herramienta.BACKEND_GENERADO),
                        "LIENZO"),
                new Paso("intercambiar",
                        "Llevarlo a Enterprise Architect",
                        "Exportar el XMI 2.5.1, o importar un modelo hecho en otra herramienta",
                        p.yaUso(Herramienta.XMI_EXPORTADO)
                                || p.operacionesConOrigen(OrigenOperacion.IMPORTACION) > 0,
                        "LIENZO"),
                new Paso("invitar",
                        "Invitar a alguien",
                        "Dos personas modelando a la vez sobre el mismo diagrama",
                        p.invitados() > 0,
                        "PROYECTOS")));
    }

    public long hechos() {
        return pasos.stream().filter(Paso::hecho).count();
    }

    public int total() {
        return pasos.size();
    }

    public boolean completo() {
        return hechos() == total();
    }

    /**
     * El primer paso pendiente.
     * <p>
     * Se devuelve el primero que falta y no "el que sigue al ultimo hecho": se
     * puede probar el dictado antes de dibujar nada, y en ese caso lo que hay que
     * senalar es el hueco, no el paso siguiente al ultimo marcado.
     */
    public Optional<Paso> loQueSigue() {
        return pasos.stream().filter(paso -> !paso.hecho()).findFirst();
    }
}
