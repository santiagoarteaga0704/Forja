package bo.forja.backend.foto;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.TiposDeclarados;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee un diagrama de clases escrito en una pizarra y lo convierte en comandos.
 * <p>
 * Recibe <b>texto</b>, no una imagen. La separacion es deliberada: el
 * reconocimiento de caracteres es un componente intercambiable -lo hace el
 * navegador, o el telefono sin conexion- y lo que no puede duplicarse es la
 * interpretacion, porque es la que decide que entra al modelo. Asi hay un solo
 * lugar donde se decide que significa lo que estaba escrito, igual que con el
 * dictado y con la importacion XMI.
 * <p>
 * <b>Que gramatica entiende.</b> La notacion habitual de una pizarra, que es la
 * misma que se escribe en texto plano:
 * <pre>
 *   Paciente                  &lt;&lt;interface&gt;&gt;
 *   -----------               Auditable
 *   - historiaClinica: String -----------
 *   + nombre: String {PK}     + registrar(): void
 *   -----------
 *   + calcularEdad(): int
 *
 *   Paciente 1 *-- 0..* Consulta
 *   Paciente --|&gt; Persona
 * </pre>
 * <p>
 * <b>Como decide que es cada linea.</b> Una linea en blanco cierra el bloque de
 * la clase, que es como se separan los recuadros de una pizarra. Dentro de un
 * bloque, una linea con parentesis es una operacion y una con dos puntos es un
 * atributo. Para las lineas sueltas se usa la convencion de UML: si empieza en
 * mayuscula es el nombre de una clase, y si empieza en minuscula es un atributo
 * sin tipo declarado. Es una heuristica, y por eso lo que no encaja se informa
 * con su numero de linea en lugar de descartarse en silencio.
 * <p>
 * <b>Tolerancia al reconocimiento.</b> Se normalizan los guiones tipograficos,
 * las comillas francesas y la confusion entre {@code l}, {@code I} y {@code 1}
 * -pero solo donde se espera una multiplicidad, no en los nombres, donde
 * cambiar una letra por un numero seria peor que dejarla-.
 */
@Component
public class ParserPizarra {

    /** Conector de una relacion, con el tipo que expresa y su direccion. */
    private record Conector(String token, TipoRelacion tipo, boolean invertido) {
    }

    /**
     * Conectores ordenados de mas largo a mas corto: {@code --|>} contiene
     * {@code --}, asi que buscar primero el corto clasificaria una herencia como
     * asociacion.
     */
    private static final List<Conector> CONECTORES = List.of(
            new Conector("<|--", TipoRelacion.HERENCIA, true),
            new Conector("--|>", TipoRelacion.HERENCIA, false),
            new Conector("<|-", TipoRelacion.HERENCIA, true),
            new Conector("-|>", TipoRelacion.HERENCIA, false),
            new Conector("<|..", TipoRelacion.REALIZACION, true),
            new Conector("..|>", TipoRelacion.REALIZACION, false),
            new Conector(".|>", TipoRelacion.REALIZACION, false),
            new Conector("*--", TipoRelacion.COMPOSICION, false),
            new Conector("--*", TipoRelacion.COMPOSICION, true),
            new Conector("*-", TipoRelacion.COMPOSICION, false),
            new Conector("-*", TipoRelacion.COMPOSICION, true),
            new Conector("o--", TipoRelacion.AGREGACION, false),
            new Conector("--o", TipoRelacion.AGREGACION, true),
            new Conector("o-", TipoRelacion.AGREGACION, false),
            new Conector("<..", TipoRelacion.DEPENDENCIA, true),
            new Conector("..>", TipoRelacion.DEPENDENCIA, false),
            new Conector(".>", TipoRelacion.DEPENDENCIA, false),
            new Conector("<--", TipoRelacion.ASOCIACION, true),
            new Conector("-->", TipoRelacion.ASOCIACION, false),
            new Conector("<-", TipoRelacion.ASOCIACION, true),
            new Conector("->", TipoRelacion.ASOCIACION, false),
            new Conector("--", TipoRelacion.ASOCIACION, false));

    /** Formas escritas con palabras, que en una pizarra tambien aparecen. */
    private static final Map<String, Conector> CONECTORES_EN_PALABRAS = Map.of(
            "hereda de", new Conector("hereda de", TipoRelacion.HERENCIA, false),
            "extiende", new Conector("extiende", TipoRelacion.HERENCIA, false),
            "implementa", new Conector("implementa", TipoRelacion.REALIZACION, false),
            "realiza", new Conector("realiza", TipoRelacion.REALIZACION, false),
            "contiene", new Conector("contiene", TipoRelacion.COMPOSICION, false),
            "depende de", new Conector("depende de", TipoRelacion.DEPENDENCIA, false));

    private static final Pattern SEPARADOR = Pattern.compile("^[-=_~\\s]{3,}$");
    private static final Pattern ESTEREOTIPO = Pattern.compile("^<<\\s*([^>]+?)\\s*>>\\s*(.*)$");
    private static final Pattern MULTIPLICIDAD = Pattern.compile("^[0-9*nN]+(\\.\\.[0-9*nN]+)?$");
    private static final Pattern VISIBILIDAD = Pattern.compile("^([+\\-#~])\\s*(.*)$");
    private static final Pattern OPERACION =
            Pattern.compile("^([\\p{L}][\\p{L}\\p{N}_]*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([\\p{L}\\p{N}_<>]+))?\\s*$");
    private static final Pattern ATRIBUTO =
            Pattern.compile("^([\\p{L}][\\p{L}\\p{N}_ ]*?)\\s*:\\s*([\\p{L}\\p{N}_<>]+)\\s*(.*)$");
    private static final Pattern MARCAS_DE_CLAVE =
            Pattern.compile("\\b(pk|id|clave|primary|identificador)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARCAS_DE_UNICO =
            Pattern.compile("\\b(u|unique|unico|unica)\\b", Pattern.CASE_INSENSITIVE);
    /** El asterisco al final de un miembro es la marca usual de obligatorio. */
    private static final Pattern MARCAS_DE_OBLIGATORIO = Pattern.compile(
            "[*!]|\\b(not\\s+null|no\\s+nulo|required|obligatorio|obligatoria)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LONGITUD = Pattern.compile("\\(?\\s*(\\d{1,4})\\s*\\)?");

    // ---------- Interpretacion ----------------------------------------------

    public Lectura interpretar(String texto, ContextoDelDiagrama contexto) {
        List<Linea> lineas = limpiar(texto);
        if (lineas.isEmpty()) {
            return Lectura.vacia(0, List.of("El texto llego vacio"));
        }

        Map<String, EnConstruccion> encontradas = new LinkedHashMap<>();
        List<Linea> posiblesRelaciones = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();

        EnConstruccion actual = null;
        String estereotipoPendiente = null;
        boolean huboLineaEnBlanco = false;

        for (Linea linea : lineas) {
            String texto1 = linea.texto();

            // Una linea en blanco cierra el recuadro: es como se separan las
            // clases en una pizarra. Pero no se cierra aqui, sino al ver que
            // viene despues: el reconocimiento intercala lineas vacias dentro
            // de un mismo recuadro -parte en parrafos lo que estaba junto- y
            // cerrando de inmediato cada atributo quedaba huerfano.
            if (texto1.isBlank()) {
                huboLineaEnBlanco = true;
                continue;
            }
            if (huboLineaEnBlanco) {
                huboLineaEnBlanco = false;
                // Solo lo que se declara miembro sin ambiguedad continua el
                // recuadro. La convencion de que una palabra en minuscula es
                // un atributo no alcanza aqui: un nombre de clase que el
                // reconocimiento dejo en minuscula se tragaria como atributo
                // de la clase anterior, y en silencio. Preferimos que esa
                // linea se informe.
                if (!declaraSerMiembro(texto1)) {
                    actual = null;
                }
            }
            // Los separadores entre compartimientos no aportan informacion: lo
            // que distingue un atributo de una operacion es su propia forma.
            if (SEPARADOR.matcher(texto1).matches()) {
                continue;
            }

            Matcher marca = ESTEREOTIPO.matcher(texto1);
            if (marca.matches()) {
                estereotipoPendiente = marca.group(1).trim();
                String resto = marca.group(2).trim();
                if (resto.isEmpty()) {
                    continue;
                }
                texto1 = resto;
            }

            Optional<Conector> conector = conectorDe(texto1);
            if (conector.isPresent()) {
                posiblesRelaciones.add(linea);
                continue;
            }

            if (actual != null && esMiembro(texto1)) {
                if (!agregarMiembro(actual, texto1)) {
                    ignoradas.add(linea.numero() + ": " + texto1);
                }
                continue;
            }

            Optional<String> nombre = nombreDeClase(texto1);
            if (nombre.isPresent()) {
                actual = nuevaClase(encontradas, nombre.get(), estereotipoPendiente, contexto);
                estereotipoPendiente = null;
                continue;
            }

            ignoradas.add(linea.numero() + ": " + texto1);
        }

        return armar(lineas.size(), encontradas, posiblesRelaciones, contexto, ignoradas);
    }

    // ---------- Clases y miembros -------------------------------------------

    private EnConstruccion nuevaClase(Map<String, EnConstruccion> encontradas, String nombre,
                                      String estereotipo, ContextoDelDiagrama contexto) {
        String clave = ContextoDelDiagrama.clave(nombre);
        EnConstruccion existente = encontradas.get(clave);
        if (existente != null) {
            // La misma clase aparecio dos veces en la foto: se siguen agregando
            // miembros al mismo bloque en lugar de duplicarla.
            return existente;
        }

        EnConstruccion construccion = new EnConstruccion();
        construccion.nombre = nombre;
        construccion.estereotipo = estereotipo;
        construccion.esAbstracta = estereotipo != null
                && (estereotipo.equalsIgnoreCase("abstract") || estereotipo.equalsIgnoreCase("abstracta"));
        if (construccion.esAbstracta) {
            // "abstract" es un modificador, no un estereotipo del modelo.
            construccion.estereotipo = null;
        }
        contexto.resolver(nombre).ifPresent(conocida -> construccion.yaExistente = conocida.id());
        encontradas.put(clave, construccion);
        return construccion;
    }

    /** Una linea es miembro si declara un tipo, una firma o una visibilidad. */
    /**
     * Si la linea se declara miembro por su forma, sin apoyarse en ninguna
     * convencion: lleva marca de visibilidad, declara un tipo o es una
     * operacion. Es la version estricta de {@link #esMiembro(String)} y se usa
     * para decidir si una linea en blanco del reconocimiento separa de verdad.
     */
    private boolean declaraSerMiembro(String texto) {
        return VISIBILIDAD.matcher(texto).matches()
                || texto.contains(":")
                || texto.contains("(");
    }

    private boolean esMiembro(String texto) {
        if (texto.contains("(") || texto.contains(":")) {
            return true;
        }
        if (VISIBILIDAD.matcher(texto).matches()) {
            return true;
        }
        // Convencion de UML: las clases van en mayuscula y los atributos en
        // minuscula. Es lo unico que distingue una linea suelta de la otra.
        return Character.isLowerCase(texto.charAt(0));
    }

    private boolean agregarMiembro(EnConstruccion clase, String texto) {
        Visibilidad visibilidad = Visibilidad.PRIVADO;
        String cuerpo = texto;

        Matcher conVisibilidad = VISIBILIDAD.matcher(texto);
        if (conVisibilidad.matches()) {
            visibilidad = visibilidadDe(conVisibilidad.group(1));
            cuerpo = conVisibilidad.group(2).trim();
        }
        if (cuerpo.isEmpty()) {
            return false;
        }

        Matcher operacion = OPERACION.matcher(cuerpo);
        if (operacion.matches()) {
            clase.operaciones.add(new MiembroLeido(operacion.group(1),
                    operacion.group(3) == null ? "void" : TiposDeclarados.normalizar(operacion.group(3)),
                    // Una operacion sin visibilidad escrita se asume publica: es
                    // lo que se espera de la interfaz de una clase.
                    visibilidad == Visibilidad.PRIVADO ? Visibilidad.PUBLICO : visibilidad,
                    false, false, false, null, parametros(operacion.group(2))));
            return true;
        }

        Matcher atributo = ATRIBUTO.matcher(cuerpo);
        if (atributo.matches()) {
            String marcas = atributo.group(3) == null ? "" : atributo.group(3);
            boolean esClave = MARCAS_DE_CLAVE.matcher(marcas).find();
            clase.atributos.add(new MiembroLeido(
                    limpiarNombreDeMiembro(atributo.group(1)),
                    TiposDeclarados.normalizar(atributo.group(2)),
                    visibilidad,
                    esClave,
                    // Una clave es obligatoria y unica por definicion.
                    esClave || MARCAS_DE_OBLIGATORIO.matcher(marcas).find(),
                    esClave || MARCAS_DE_UNICO.matcher(marcas).find(),
                    longitudDe(marcas),
                    List.of()));
            return true;
        }

        // Atributo sin tipo declarado: se asume texto, que es lo que menos
        // restringe el esquema que se genere despues.
        if (cuerpo.matches("[\\p{L}][\\p{L}\\p{N}_ ]*")) {
            clase.atributos.add(new MiembroLeido(limpiarNombreDeMiembro(cuerpo), "String",
                    visibilidad, false, false, false, null, List.of()));
            return true;
        }
        return false;
    }

    private List<MiembroLeido.Parametro> parametros(String dentroDeParentesis) {
        if (dentroDeParentesis == null || dentroDeParentesis.isBlank()) {
            return List.of();
        }
        List<MiembroLeido.Parametro> parametros = new ArrayList<>();
        for (String trozo : dentroDeParentesis.split(",")) {
            String[] partes = trozo.trim().split("\\s*:\\s*");
            if (partes[0].isBlank()) {
                continue;
            }
            parametros.add(new MiembroLeido.Parametro(limpiarNombreDeMiembro(partes[0]),
                    partes.length > 1 ? TiposDeclarados.normalizar(partes[1]) : "String"));
        }
        return parametros;
    }

    // ---------- Relaciones ---------------------------------------------------

    private Optional<Conector> conectorDe(String texto) {
        String enMinuscula = texto.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Conector> palabra : CONECTORES_EN_PALABRAS.entrySet()) {
            int donde = enMinuscula.indexOf(" " + palabra.getKey() + " ");
            if (donde > 0 && tieneLetrasAAmbosLados(texto, donde, palabra.getKey().length() + 2)) {
                return Optional.of(palabra.getValue());
            }
        }
        for (Conector conector : CONECTORES) {
            int donde = texto.indexOf(conector.token());
            if (donde > 0 && tieneLetrasAAmbosLados(texto, donde, conector.token().length())) {
                return Optional.of(conector);
            }
        }
        return Optional.empty();
    }

    /**
     * Exige nombres a los dos lados del conector. Sin esta comprobacion, un
     * guion perdido por el reconocimiento convertiria cualquier linea en una
     * relacion a medias.
     */
    private boolean tieneLetrasAAmbosLados(String texto, int donde, int largo) {
        String izquierda = texto.substring(0, donde);
        String derecha = texto.substring(Math.min(donde + largo, texto.length()));
        return izquierda.matches(".*\\p{L}.*") && derecha.matches(".*\\p{L}.*");
    }

    private Optional<RelacionEnConstruccion> leerRelacion(String texto) {
        Optional<Conector> encontrado = conectorDe(texto);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }
        Conector conector = encontrado.get();

        String token = conector.token();
        int donde = CONECTORES_EN_PALABRAS.containsValue(conector)
                ? texto.toLowerCase(Locale.ROOT).indexOf(" " + token + " ") + 1
                : texto.indexOf(token);

        String izquierda = texto.substring(0, donde).trim();
        String derecha = texto.substring(donde + token.length()).trim();

        // La multiplicidad va pegada al conector: al final del lado izquierdo y
        // al comienzo del derecho.
        Extremo primero = separarMultiplicidadAlFinal(izquierda);
        Extremo segundo = separarMultiplicidadAlComienzo(derecha);
        if (primero.nombre().isBlank() || segundo.nombre().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(conector.invertido()
                ? new RelacionEnConstruccion(segundo.nombre(), primero.nombre(), conector.tipo(),
                        segundo.multiplicidad(), primero.multiplicidad())
                : new RelacionEnConstruccion(primero.nombre(), segundo.nombre(), conector.tipo(),
                        primero.multiplicidad(), segundo.multiplicidad()));
    }

    private Extremo separarMultiplicidadAlFinal(String lado) {
        String[] partes = lado.trim().split("\\s+");
        if (partes.length > 1 && esMultiplicidad(partes[partes.length - 1])) {
            String nombre = String.join(" ", List.of(partes).subList(0, partes.length - 1));
            return new Extremo(nombre, normalizarMultiplicidad(partes[partes.length - 1]));
        }
        return new Extremo(lado.trim(), "1");
    }

    private Extremo separarMultiplicidadAlComienzo(String lado) {
        String[] partes = lado.trim().split("\\s+");
        if (partes.length > 1 && esMultiplicidad(partes[0])) {
            String nombre = String.join(" ", List.of(partes).subList(1, partes.length));
            return new Extremo(nombre, normalizarMultiplicidad(partes[0]));
        }
        return new Extremo(lado.trim(), "1");
    }

    private boolean esMultiplicidad(String token) {
        return MULTIPLICIDAD.matcher(normalizarMultiplicidad(token)).matches();
    }

    /**
     * Corrige la confusion tipica del reconocimiento de caracteres, pero solo
     * aqui: en una multiplicidad una {@code l} solo puede ser un uno, mientras
     * que en un nombre cambiarla estropearia la palabra.
     */
    private String normalizarMultiplicidad(String token) {
        return token.replace("l", "1").replace("I", "1").replace("i", "1")
                .replace("O", "0").replace("o", "0")
                .replace(",", ".");
    }

    // ---------- Armado del resultado ----------------------------------------

    private Lectura armar(int lineasLeidas, Map<String, EnConstruccion> encontradas,
                          List<Linea> posiblesRelaciones, ContextoDelDiagrama contexto,
                          List<String> ignoradas) {

        List<ComandoOperacion> comandos = new ArrayList<>();
        List<Lectura.ClaseLeida> resumenDeClases = new ArrayList<>();
        Map<String, UUID> idPorClave = new LinkedHashMap<>();

        for (EnConstruccion clase : encontradas.values()) {
            String clave = ContextoDelDiagrama.clave(clase.nombre);

            if (clase.yaExistente != null) {
                // La clase ya estaba: se registra para poder relacionarla, pero no
                // se le tocan los miembros. La foto agrega lo que falta, no
                // reescribe lo que ya esta.
                idPorClave.put(clave, clase.yaExistente);
                resumenDeClases.add(new Lectura.ClaseLeida(clase.nombre, clase.estereotipo,
                        clase.esAbstracta, 0, 0, true));
                continue;
            }

            UUID claseId = UUID.randomUUID();
            idPorClave.put(clave, claseId);
            comandos.add(new ComandoOperacion.CrearClase(claseId, clase.nombre,
                    clase.estereotipo, clase.esAbstracta, 0, 0));

            for (MiembroLeido atributo : clase.atributos) {
                comandos.add(new ComandoOperacion.AgregarAtributo(claseId, UUID.randomUUID(),
                        atributo.nombre(), atributo.tipo(), atributo.visibilidad(),
                        atributo.esClave(), atributo.esRequerido(), atributo.esUnico(),
                        atributo.longitud()));
            }
            for (MiembroLeido operacion : clase.operaciones) {
                comandos.add(new ComandoOperacion.AgregarMetodo(claseId, UUID.randomUUID(),
                        operacion.nombre(), operacion.tipo(), operacion.visibilidad(), false, false,
                        operacion.parametros().stream()
                                .map(p -> new ComandoOperacion.Parametro(UUID.randomUUID(),
                                        p.nombre(), p.tipo()))
                                .toList()));
            }

            resumenDeClases.add(new Lectura.ClaseLeida(clase.nombre, clase.estereotipo,
                    clase.esAbstracta, clase.atributos.size(), clase.operaciones.size(), false));
        }

        List<Lectura.RelacionLeida> resumenDeRelaciones = new ArrayList<>();
        for (Linea linea : posiblesRelaciones) {
            Optional<RelacionEnConstruccion> leida = leerRelacion(linea.texto());
            if (leida.isEmpty()) {
                ignoradas.add(linea.numero() + ": " + linea.texto());
                continue;
            }
            RelacionEnConstruccion relacion = leida.get();

            UUID origen = resolver(relacion.origen(), idPorClave, contexto);
            UUID destino = resolver(relacion.destino(), idPorClave, contexto);
            if (origen == null || destino == null) {
                ignoradas.add(linea.numero() + ": " + linea.texto()
                        + "  (no reconoci una de las dos clases)");
                continue;
            }

            // Leer dos veces la misma pizarra no debe volver a trazar lo mismo:
            // es la contraparte de no recrear las clases que ya existen.
            if (contexto.yaExisteRelacion(origen, destino, relacion.tipo().name())) {
                continue;
            }

            comandos.add(new ComandoOperacion.CrearRelacion(UUID.randomUUID(), origen, destino,
                    relacion.tipo(), relacion.multiplicidadOrigen(), relacion.multiplicidadDestino(),
                    null, null, null));
            resumenDeRelaciones.add(new Lectura.RelacionLeida(relacion.origen(),
                    relacion.tipo().name(), relacion.destino(),
                    relacion.multiplicidadOrigen() + " a " + relacion.multiplicidadDestino()));
        }

        return new Lectura(lineasLeidas, comandos, resumenDeClases, resumenDeRelaciones, ignoradas);
    }

    private UUID resolver(String nombre, Map<String, UUID> idPorClave, ContextoDelDiagrama contexto) {
        UUID enLaFoto = idPorClave.get(ContextoDelDiagrama.clave(nombre));
        if (enLaFoto != null) {
            return enLaFoto;
        }
        return contexto.resolver(nombre).map(ContextoDelDiagrama.ClaseConocida::id).orElse(null);
    }

    // ---------- Limpieza del texto -------------------------------------------

    /**
     * Normaliza el texto conservando las lineas en blanco, porque son la senal
     * que separa un recuadro de otro, y conservando el numero de linea original
     * para poder senalar lo que no se entendio.
     */
    private List<Linea> limpiar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String[] crudas = texto.split("\\r?\\n");
        List<Linea> lineas = new ArrayList<>();

        for (int i = 0; i < crudas.length; i++) {
            String linea = crudas[i]
                    // Guiones tipograficos: el reconocimiento los produce a
                    // menudo y romperian todos los conectores.
                    .replace('—', '-').replace('–', '-').replace('‒', '-')
                    .replace('−', '-').replace('─', '-')
                    .replace("«", "<<").replace("»", ">>")
                    .replace(' ', ' ')
                    // Vinetas que a veces aparecen delante de un miembro.
                    .replaceAll("^\\s*[•·▪●]\\s*", "")
                    .replaceAll("[ \\t]+", " ")
                    .trim();
            lineas.add(new Linea(i + 1, linea));
        }

        // Se recortan las lineas en blanco de los extremos: no separan nada.
        while (!lineas.isEmpty() && lineas.get(0).texto().isBlank()) {
            lineas.remove(0);
        }
        while (!lineas.isEmpty() && lineas.get(lineas.size() - 1).texto().isBlank()) {
            lineas.remove(lineas.size() - 1);
        }
        return lineas;
    }

    private Optional<String> nombreDeClase(String texto) {
        String limpio = texto.replaceAll("[|:;.,]+$", "").trim();
        if (!limpio.matches("[\\p{L}][\\p{L}\\p{N}_ ]*")) {
            return Optional.empty();
        }
        String[] palabras = limpio.split("\\s+");
        StringBuilder nombre = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            nombre.append(i == 0 ? palabras[i]
                    : Character.toUpperCase(palabras[i].charAt(0)) + palabras[i].substring(1));
        }
        return Optional.of(sinAcentos(nombre.toString()));
    }

    private String limpiarNombreDeMiembro(String bruto) {
        String[] palabras = bruto.trim().split("\\s+");
        StringBuilder nombre = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            nombre.append(i == 0 ? palabras[i]
                    : Character.toUpperCase(palabras[i].charAt(0)) + palabras[i].substring(1));
        }
        return sinAcentos(nombre.toString());
    }

    private Integer longitudDe(String marcas) {
        Matcher encontrada = LONGITUD.matcher(marcas);
        return encontrada.find() ? Integer.valueOf(encontrada.group(1)) : null;
    }

    private Visibilidad visibilidadDe(String simbolo) {
        return switch (simbolo) {
            case "+" -> Visibilidad.PUBLICO;
            case "#" -> Visibilidad.PROTEGIDO;
            case "~" -> Visibilidad.PAQUETE;
            default -> Visibilidad.PRIVADO;
        };
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // ---------- Estructuras internas ----------------------------------------

    private record Linea(int numero, String texto) {
    }

    private record Extremo(String nombre, String multiplicidad) {
    }

    private record RelacionEnConstruccion(
            String origen, String destino, TipoRelacion tipo,
            String multiplicidadOrigen, String multiplicidadDestino) {
    }

    private record MiembroLeido(
            String nombre, String tipo, Visibilidad visibilidad,
            boolean esClave, boolean esRequerido, boolean esUnico,
            Integer longitud, List<Parametro> parametros) {

        record Parametro(String nombre, String tipo) {
        }
    }

    /** Clase que se esta armando a medida que se leen sus lineas. */
    private static final class EnConstruccion {
        String nombre;
        String estereotipo;
        boolean esAbstracta;
        /** Identificador si la clase ya existia en el diagrama. */
        UUID yaExistente;
        final List<MiembroLeido> atributos = new ArrayList<>();
        final List<MiembroLeido> operaciones = new ArrayList<>();
    }
}
