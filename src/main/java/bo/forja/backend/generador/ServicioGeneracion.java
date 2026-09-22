package bo.forja.backend.generador;

import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.Diagrama;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.servicio.ServicioModelo;
import bo.forja.backend.servicio.ServicioProyectos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera un proyecto Spring Boot completo a partir de un diagrama.
 * <p>
 * Es el nucleo del desarrollo basado en componentes que el proyecto se
 * propone demostrar: el diagrama deja de ser documentacion que acompana al
 * codigo y pasa a ser la fuente desde la que el codigo se produce.
 * <p>
 * La generacion se expone de dos maneras. {@link #archivos} devuelve el
 * proyecto como un mapa de ruta a contenido, que es lo que permite
 * comprobar en una prueba que lo generado <b>compila</b>; {@link #comprimir}
 * lo empaqueta para descargarlo. La primera existe por la segunda: sin
 * poder inspeccionar los archivos, la unica forma de verificar el generador
 * seria abrir el zip a mano.
 */
@Service
public class ServicioGeneracion {

    private static final Logger log = LoggerFactory.getLogger(ServicioGeneracion.class);

    private final ServicioProyectos proyectos;
    private final ServicioModelo modelo;
    private final PlanificadorGeneracion planificador;
    private final EscritorEntidad entidades;
    private final EscritorCapas capas;
    private final EscritorAndamiaje andamiaje;

    public ServicioGeneracion(ServicioProyectos proyectos,
                              ServicioModelo modelo,
                              PlanificadorGeneracion planificador,
                              EscritorEntidad entidades,
                              EscritorCapas capas,
                              EscritorAndamiaje andamiaje) {
        this.proyectos = proyectos;
        this.modelo = modelo;
        this.planificador = planificador;
        this.entidades = entidades;
        this.capas = capas;
        this.andamiaje = andamiaje;
    }

    /**
     * Proyecto generado como mapa de ruta relativa a contenido.
     *
     * @param paqueteBase paquete raiz; si viene vacio se deduce del diagrama
     */
    @Transactional(readOnly = true)
    public Map<String, String> archivos(UUID diagramaId, UUID usuarioId, String paqueteBase) {
        Diagrama diagrama = proyectos.diagramaAccesible(diagramaId, usuarioId);

        List<ClaseUml> clases = modelo.clasesCompletas(diagramaId);
        List<RelacionUml> relaciones = modelo.relacionesDe(diagramaId);

        String artefacto = artefactoDe(diagrama.getNombre());
        String paquete = paqueteBase == null || paqueteBase.isBlank()
                ? "com.ejemplo." + artefacto.replace("-", "")
                : paqueteBase.trim();

        Plan.Proyecto plan = planificador.planificar(
                paquete, artefacto, diagrama.getNombre(), clases, relaciones);

        Map<String, String> salida = new LinkedHashMap<>();
        String raizJava = "src/main/java/" + paquete.replace('.', '/');

        salida.put("pom.xml", andamiaje.pom(plan));
        salida.put("README.md", andamiaje.readme(plan));
        salida.put("compose.yaml", andamiaje.compose(plan));
        salida.put("src/main/resources/application.yml", andamiaje.configuracion(plan));
        salida.put(raizJava + "/web/ConfiguracionCors.java", andamiaje.cors(plan));
        // El wrapper viaja tal cual, sin plantilla: son los mismos archivos que
        // usa FORJA. Sin ellos el README miente -manda a correr ./mvnw- y el
        // proyecto solo arranca si quien lo bajo tiene Maven instalado.
        salida.putAll(wrapper());
        salida.put(raizJava + "/" + andamiaje.nombreClaseAplicacion(plan) + ".java",
                andamiaje.aplicacion(plan));

        // Las interfaces y las clases abstractas se emiten como tipos del
        // dominio, pero no reciben repositorio, servicio ni controlador: no
        // hay filas que consultar para un tipo que no se instancia.
        for (Plan.Clase clase : plan.clases()) {
            salida.put(raizJava + "/dominio/" + clase.nombreJava() + ".java",
                    entidades.escribir(plan, clase));
        }
        for (Plan.Clase clase : plan.generables()) {
            salida.put(raizJava + "/repositorio/" + clase.nombreJava() + "Repositorio.java",
                    capas.repositorio(plan, clase));
            salida.put(raizJava + "/servicio/" + clase.nombreJava() + "Servicio.java",
                    capas.servicio(plan, clase));
            salida.put(raizJava + "/web/" + clase.nombreJava() + "Controlador.java",
                    capas.controlador(plan, clase));
        }

        log.info("Diagrama {}: generados {} archivos para {} entidades",
                diagramaId, salida.size(), plan.generables().size());
        return salida;
    }

    /** El mismo proyecto, empaquetado para descargar. */
    /**
     * El wrapper de Maven, copiado de los recursos de la aplicacion.
     * <p>
     * Se empaqueta con FORJA en lugar de escribirse aqui porque son casi
     * quinientas lineas de guion de arranque que no tiene sentido mantener
     * dentro de una plantilla, y porque asi el proyecto generado usa
     * exactamente el mismo wrapper que esta probado en este repositorio.
     */
    private Map<String, String> wrapper() {
        Map<String, String> salida = new LinkedHashMap<>();
        salida.put("mvnw", recurso("andamiaje/mvnw"));
        salida.put("mvnw.cmd", recurso("andamiaje/mvnw.cmd"));
        salida.put(".mvn/wrapper/maven-wrapper.properties",
                recurso("andamiaje/wrapper/maven-wrapper.properties"));
        return salida;
    }

    private String recurso(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falta el recurso " + ruta + " en el empaquetado", e);
        }
    }

    public byte[] comprimir(Map<String, String> archivos, String carpetaRaiz) {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(destino, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> archivo : archivos.entrySet()) {
                zip.putNextEntry(new ZipEntry(carpetaRaiz + "/" + archivo.getKey()));
                zip.write(archivo.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            // Se escribe en memoria: un fallo de entrada y salida aqui no es
            // una condicion que el cliente pueda resolver reintentando.
            throw new UncheckedIOException("No se pudo empaquetar el proyecto generado", e);
        }
        return destino.toByteArray();
    }

    /** Nombre de artefacto Maven a partir del nombre del diagrama. */
    public static String artefactoDe(String nombreDelDiagrama) {
        String limpio = Nombres.slug(nombreDelDiagrama);
        return limpio.isBlank() ? "proyecto-generado" : limpio;
    }
}
