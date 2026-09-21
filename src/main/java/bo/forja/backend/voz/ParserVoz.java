package bo.forja.backend.voz;

import bo.forja.backend.dominio.TipoRelacion;
import bo.forja.backend.dominio.Visibilidad;
import bo.forja.backend.operacion.ComandoOperacion;
import bo.forja.backend.operacion.ContextoDelDiagrama;
import bo.forja.backend.operacion.TiposDeclarados;
import bo.forja.backend.operacion.TipoOperacion;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce una frase dictada en castellano a comandos del modelo.
 * <p>
 * Es <b>determinista</b>: la misma frase produce siempre el mismo comando, y
 * una frase que no encaja en la gramatica no produce ninguno. Esa es la
 * propiedad que lo vuelve util como base de todo lo demas. El modelo de
 * lenguaje del cliente movil no reemplaza a este parser sino que lo
 * antecede: traduce una frase libre a una de las formas que esta gramatica
 * si reconoce, y despues este decide que se aplica. Asi, lo que puede
 * equivocarse -el modelo- nunca es lo que toca el diagrama.
 * <p>
 * El orden de las reglas es significativo y va de lo especifico a lo
 * general. "Paciente tiene muchas Consultas" y "Paciente tiene un nombre de
 * tipo texto" empiezan igual; lo que las separa es si el segundo nombre
 * corresponde a una clase que existe, y por eso la regla de asociacion se
 * evalua antes y exige que el destino se resuelva.
 * <p>
 * No valida nada del modelo: eso es trabajo del aplicador. Aqui solo se
 * decide <i>que quiso decir</i> la persona.
 */
@Component
public class ParserVoz {

    // ---------- Piezas de la gramatica --------------------------------------

    /** Nombre de clase o de miembro: letras, digitos y espacios intermedios. */
    private static final String NOM = "([\\p{L}][\\p{L}\\p{N}_]*(?:\\s+[\\p{L}][\\p{L}\\p{N}_]*)*?)";
    private static final String NOM_FIN = "([\\p{L}][\\p{L}\\p{N}_]*(?:\\s+[\\p{L}][\\p{L}\\p{N}_]*)*)";
    private static final String CREAR = "(?:crea|crear|cree|creame|agrega|agregar|agregame|anade|anadir|anadi|nueva|nuevo|dibuja|dibujar)";
    // "anadi" y "suma" son el imperativo voseante, que es como se habla aca
    // -y como habla la propia interfaz de la herramienta-. Faltaban, y su
    // ausencia no se notaba como "no te entendi": la frase se caia al fondo
    // de la gramatica y terminaba creando una clase repetida en silencio.
    private static final String AGREGAR = "(?:agrega|agregale|agregar|anade|anadile|anadir|anadi|pone|ponele|poner|sumale|suma)";
    private static final String CANTIDAD = "(muchas|muchos|varias|varios|una|uno|un|cero\\s+o\\s+mas|al\\s+menos\\s+una?|al\\s+menos\\s+uno)";

    private static Pattern regla(String expresion) {
        return Pattern.compile("^" + expresion + "$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static final Pattern CREAR_INTERFAZ =
            regla(CREAR + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?(?:interfaz|interface)\\s+(?:llamada\\s+)?" + NOM_FIN);
    private static final Pattern CREAR_CLASE_CON_ATRIBUTOS =
            regla(CREAR + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+(?:llamada\\s+)?" + NOM
                    + "\\s+con\\s+(?:los\\s+|las\\s+)?atributos?\\s+(.+)");
    private static final Pattern CREAR_CLASE =
            regla(CREAR + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+(?:llamada\\s+)?" + NOM_FIN);

    private static final Pattern RENOMBRAR =
            regla("(?:renombra|renombrar|cambia\\s+el\\s+nombre\\s+de|cambiar\\s+el\\s+nombre\\s+de)"
                    + "\\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+(?:a|por|como)\\s+" + NOM_FIN);
    private static final Pattern ELIMINAR =
            regla("(?:elimina|eliminar|borra|borrar|quita|quitar)\\s+(?:la\\s+)?clase\\s+" + NOM_FIN);

    private static final Pattern MARCAR_ABSTRACTA_IMPERATIVO =
            regla("(?:marca|marcar|hace|hacer|pone|poner|declara|declarar)"
                    + "\\s+(?:a\\s+|la\\s+clase\\s+)?" + NOM + "\\s+como\\s+(?:clase\\s+)?abstracta");
    private static final Pattern MARCAR_ABSTRACTA_DECLARATIVO =
            regla("(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+es\\s+(?:una\\s+clase\\s+)?abstracta");
    private static final Pattern MARCAR_INTERFAZ =
            regla("(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+es\\s+una\\s+(?:interfaz|interface)");

    /*
     * La hache de "ha" es a proposito. Dictando, "a Pedido" y "ha pedido"
     * suenan igual, y "ha pedido" es una frase muchisimo mas frecuente, asi
     * que el reconocedor de voz elige esa casi siempre. Como "a Clase
     * agregale..." es la forma mas usada del dictado, la gramatica se la banca
     * en lugar de mandar la frase al respaldo, que es donde se hacia dano.
     */
    private static final Pattern ATRIBUTO_DESTINO_PRIMERO =
            regla("(?:a|ha|en|para)\\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+" + AGREGAR
                    + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?atributo\\s+(.+)");
    private static final Pattern ATRIBUTO_DESTINO_ULTIMO =
            regla(AGREGAR + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?atributo\\s+(.+?)"
                    + "\\s+(?:a|en)\\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM_FIN);

    /**
     * Forma declarativa: "el Paciente tiene un nombre de tipo texto". Exige que
     * aparezca la palabra "tipo" para no confundirse con la asociacion, que
     * empieza igual; la relacion se evalua antes y solo gana si sus dos extremos
     * son clases conocidas.
     */
    private static final Pattern ATRIBUTO_DECLARATIVO =
            // El articulo se lleva su espacio dentro del grupo. Sin eso, la
            // alternancia parte "una Consulta" en "un" + "a Consulta" y el
            // nombre de la clase queda arruinado.
            regla("(?:(?:el|la|los|las|un|una)\\s+)?(?:clase\\s+)?" + NOM
                    + "\\s+(?:tiene|posee|lleva)\\s+(?:(?:un|una|el|la)\\s+)?(.+\\s+tipo\\s+.+)");

    private static final Pattern METODO_DESTINO_PRIMERO =
            regla("(?:a|ha|en|para)\\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+" + AGREGAR
                    + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?(?:metodo|operacion)\\s+(.+)");
    private static final Pattern METODO_DESTINO_ULTIMO =
            regla(AGREGAR + "\\s+(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?(?:metodo|operacion)\\s+(.+?)"
                    + "\\s+(?:a|en)\\s+(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM_FIN);

    private static final Pattern HERENCIA =
            regla("(?:(?:(?:el|la|los|las|un|una|unos|unas)\\s+)?clase\\s+)?" + NOM + "\\s+(?:hereda\\s+de|extiende|es\\s+un\\s+tipo\\s+de"
                    + "|es\\s+una?\\s+subclase\\s+de)\\s+" + NOM_FIN);
    private static final Pattern REALIZACION =
            regla(NOM + "\\s+(?:implementa|realiza|cumple\\s+con)\\s+" + NOM_FIN);
    private static final Pattern COMPOSICION =
            regla(NOM + "\\s+(?:se\\s+compone\\s+de|esta\\s+compuesta?\\s+(?:de|por)|contiene)"
                    + "\\s+(?:" + CANTIDAD + "\\s+)?" + NOM_FIN);
    private static final Pattern AGREGACION =
            regla(NOM + "\\s+(?:agrupa|reune)\\s+(?:" + CANTIDAD + "\\s+)?" + NOM_FIN);
    private static final Pattern DEPENDENCIA =
            regla(NOM + "\\s+(?:depende\\s+de|usa\\s+a)\\s+" + NOM_FIN);
    private static final Pattern ASOCIACION =
            regla("(?:un[ao]?\\s+)?" + NOM + "\\s+(?:tiene|posee|se\\s+relaciona\\s+con"
                    + "|se\\s+asocia\\s+con)\\s+(?:" + CANTIDAD + "\\s+)?" + NOM_FIN);

    /**
     * Detalle de un atributo. Son dos patrones y no uno por una razon concreta:
     * con el tipo opcional y un grupo final que acepta cualquier cosa, el nombre
     * quedaba siempre en su forma mas corta y el resto se lo tragaba ese final.
     * "fecha de nacimiento de tipo fecha" producia un atributo llamado "fecha"
     * de tipo texto. Exigiendo el tipo en el primer intento, el nombre se
     * extiende hasta donde corresponde.
     */
    private static final String MARCAS =
            "(?:clave|identificador|primaria|unico|unica|obligatorio|obligatoria"
                    + "|requerido|requerida|no\\s+nulo|longitud\\s+\\d+)";
    private static final Pattern DETALLE_ATRIBUTO_CON_TIPO =
            regla(NOM + "\\s+(?:de\\s+)?tipo\\s+([\\p{L}\\p{N}_]+)"
                    + "((?:\\s+(?:de\\s+|con\\s+|y\\s+)?" + MARCAS + ")*)");
    /**
     * Sin tipo declarado se asume texto. El nombre llega hasta la primera
     * palabra de marca, que es lo unico que permite separar "historia clinica
     * obligatorio" en un nombre de dos palabras y una marca.
     */
    private static final Pattern DETALLE_ATRIBUTO_SIN_TIPO =
            regla(NOM + "((?:\\s+(?:de\\s+|con\\s+|y\\s+)?" + MARCAS + ")*)");
    private static final Pattern DETALLE_METODO =
            regla(NOM + "(?:\\s+con\\s+(?:los\\s+)?parametros?\\s+(.+?))?"
                    + "(?:\\s+que\\s+(?:devuelve|retorna)\\s+([\\p{L}\\p{N}_]+))?");
    private static final Pattern PARAMETRO =
            regla(NOM + "\\s+(?:de\\s+)?tipo\\s+([\\p{L}\\p{N}_]+)");
    private static final Pattern LONGITUD = Pattern.compile("longitud\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE);

    // ---------- Interpretacion ----------------------------------------------

    public Interpretacion interpretar(String fraseOriginal, ContextoDelDiagrama contexto) {
        if (fraseOriginal == null || fraseOriginal.isBlank()) {
            return Interpretacion.noEntendida("", sugerencias(contexto));
        }
        String frase = limpiar(fraseOriginal);

        // El orden importa: lo mas especifico primero.
        Optional<Interpretacion> resultado = primeraQueEncaje(frase, contexto);
        return resultado.orElseGet(() -> Interpretacion.noEntendida(fraseOriginal, sugerencias(contexto)));
    }

    private Optional<Interpretacion> primeraQueEncaje(String frase, ContextoDelDiagrama contexto) {
        Matcher m;

        if ((m = CREAR_INTERFAZ.matcher(frase)).matches()) {
            UUID id = UUID.randomUUID();
            String nombre = limpiarNombre(m.group(1));
            return uno(frase, "Cree la interfaz " + nombre, TipoOperacion.CLASE_CREAR,
                    new ComandoOperacion.CrearClase(id, nombre, "interface", false, 0, 0));
        }

        if ((m = CREAR_CLASE_CON_ATRIBUTOS.matcher(frase)).matches()) {
            return crearClaseConAtributos(frase, limpiarNombre(m.group(1)), m.group(2));
        }

        if ((m = CREAR_CLASE.matcher(frase)).matches()) {
            String nombre = limpiarNombre(m.group(1));
            return uno(frase, "Cree la clase " + nombre, TipoOperacion.CLASE_CREAR,
                    new ComandoOperacion.CrearClase(UUID.randomUUID(), nombre, null, false, 0, 0));
        }

        if ((m = RENOMBRAR.matcher(frase)).matches()) {
            Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(m.group(1));
            if (clase.isEmpty()) {
                return noConozco(frase, m.group(1), contexto);
            }
            String nuevo = limpiarNombre(m.group(2));
            return uno(frase, "Renombre " + clase.get().nombre() + " a " + nuevo,
                    TipoOperacion.CLASE_RENOMBRAR,
                    new ComandoOperacion.RenombrarClase(clase.get().id(), nuevo));
        }

        if ((m = ELIMINAR.matcher(frase)).matches()) {
            Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(m.group(1));
            if (clase.isEmpty()) {
                return noConozco(frase, m.group(1), contexto);
            }
            return uno(frase, "Elimine la clase " + clase.get().nombre(),
                    TipoOperacion.CLASE_ELIMINAR,
                    new ComandoOperacion.EliminarClase(clase.get().id()));
        }

        if ((m = MARCAR_ABSTRACTA_IMPERATIVO.matcher(frase)).matches()
                || (m = MARCAR_ABSTRACTA_DECLARATIVO.matcher(frase)).matches()) {
            Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(m.group(1));
            if (clase.isEmpty()) {
                return noConozco(frase, m.group(1), contexto);
            }
            return uno(frase, "Marque " + clase.get().nombre() + " como abstracta",
                    TipoOperacion.CLASE_MARCAR,
                    new ComandoOperacion.MarcarClase(clase.get().id(), null, true));
        }

        if ((m = MARCAR_INTERFAZ.matcher(frase)).matches()) {
            Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(m.group(1));
            if (clase.isEmpty()) {
                return noConozco(frase, m.group(1), contexto);
            }
            return uno(frase, clase.get().nombre() + " ahora es una interfaz",
                    TipoOperacion.CLASE_MARCAR,
                    new ComandoOperacion.MarcarClase(clase.get().id(), "interface", false));
        }

        // Las relaciones van antes que los atributos: "tiene" puede empezar las
        // dos y solo se decide mirando si el destino es una clase conocida.
        Optional<Interpretacion> relacion = comoRelacion(frase, contexto);
        if (relacion.isPresent()) {
            return relacion;
        }

        if ((m = ATRIBUTO_DECLARATIVO.matcher(frase)).matches()) {
            Optional<Interpretacion> comoAtributo = comoAtributo(frase, m.group(1), m.group(2), contexto);
            if (comoAtributo.isPresent()) {
                return comoAtributo;
            }
        }
        if ((m = ATRIBUTO_DESTINO_PRIMERO.matcher(frase)).matches()) {
            return comoAtributo(frase, m.group(1), m.group(2), contexto);
        }
        if ((m = ATRIBUTO_DESTINO_ULTIMO.matcher(frase)).matches()) {
            return comoAtributo(frase, m.group(2), m.group(1), contexto);
        }
        if ((m = METODO_DESTINO_PRIMERO.matcher(frase)).matches()) {
            return comoMetodo(frase, m.group(1), m.group(2), contexto);
        }
        if ((m = METODO_DESTINO_ULTIMO.matcher(frase)).matches()) {
            return comoMetodo(frase, m.group(2), m.group(1), contexto);
        }

        return Optional.empty();
    }

    // ---------- Relaciones ---------------------------------------------------

    private Optional<Interpretacion> comoRelacion(String frase, ContextoDelDiagrama contexto) {
        record Forma(Pattern patron, TipoRelacion tipo, boolean conCantidad) {
        }
        List<Forma> formas = List.of(
                new Forma(HERENCIA, TipoRelacion.HERENCIA, false),
                new Forma(REALIZACION, TipoRelacion.REALIZACION, false),
                new Forma(COMPOSICION, TipoRelacion.COMPOSICION, true),
                new Forma(AGREGACION, TipoRelacion.AGREGACION, true),
                new Forma(DEPENDENCIA, TipoRelacion.DEPENDENCIA, false),
                new Forma(ASOCIACION, TipoRelacion.ASOCIACION, true));

        for (Forma forma : formas) {
            Matcher m = forma.patron().matcher(frase);
            if (!m.matches()) {
                continue;
            }
            String cantidad = forma.conCantidad() ? m.group(2) : null;
            String nombreDestino = forma.conCantidad() ? m.group(3) : m.group(2);

            Optional<ContextoDelDiagrama.ClaseConocida> origen = contexto.resolver(m.group(1));
            Optional<ContextoDelDiagrama.ClaseConocida> destino = contexto.resolver(nombreDestino);

            // Si alguno de los dos extremos no es una clase conocida, esto no era
            // una relacion: se deja seguir a las reglas de atributo.
            if (origen.isEmpty() || destino.isEmpty()) {
                continue;
            }

            String multDestino = multiplicidad(cantidad);
            return uno(frase,
                    "Relacione " + origen.get().nombre() + " con " + destino.get().nombre()
                            + " (" + forma.tipo().name().toLowerCase(Locale.ROOT) + ")",
                    TipoOperacion.RELACION_CREAR,
                    new ComandoOperacion.CrearRelacion(UUID.randomUUID(), origen.get().id(),
                            destino.get().id(), forma.tipo(), "1", multDestino, null, null, null));
        }
        return Optional.empty();
    }

    /**
     * "muchas" se traduce a cero o mas y no a uno o mas. Es la lectura
     * conservadora: si el modelo dice que puede no haber ninguna, el esquema
     * generado no impone una restriccion que nadie pidio, mientras que al
     * contrario obligaria a crear filas que quiza no existen.
     */
    private String multiplicidad(String cantidad) {
        if (cantidad == null) {
            return "1";
        }
        String limpia = cantidad.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return switch (limpia) {
            case "muchas", "muchos", "varias", "varios", "cero o mas" -> "0..*";
            case "al menos una", "al menos uno" -> "1..*";
            default -> "1";
        };
    }

    // ---------- Atributos y metodos -----------------------------------------

    private Optional<Interpretacion> crearClaseConAtributos(String frase, String nombre,
                                                            String listaDeAtributos) {
        UUID claseId = UUID.randomUUID();
        List<Interpretacion.Paso> pasos = new ArrayList<>();
        pasos.add(new Interpretacion.Paso(TipoOperacion.CLASE_CREAR,
                new ComandoOperacion.CrearClase(claseId, nombre, null, false, 0, 0)));

        List<String> nombresAgregados = new ArrayList<>();
        for (String trozo : separarEnumeracion(listaDeAtributos)) {
            atributoDesdeDetalle(claseId, trozo).ifPresent(comando -> {
                pasos.add(new Interpretacion.Paso(TipoOperacion.ATRIBUTO_AGREGAR, comando));
                nombresAgregados.add(comando.nombre());
            });
        }

        String explicacion = nombresAgregados.isEmpty()
                ? "Cree la clase " + nombre
                : "Cree la clase " + nombre + " con " + String.join(", ", nombresAgregados);
        return Optional.of(Interpretacion.entendida(frase, explicacion, pasos));
    }

    private Optional<Interpretacion> comoAtributo(String frase, String nombreDeLaClase,
                                                  String detalle, ContextoDelDiagrama contexto) {
        Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(nombreDeLaClase);
        if (clase.isEmpty()) {
            return noConozco(frase, nombreDeLaClase, contexto);
        }
        return atributoDesdeDetalle(clase.get().id(), detalle).map(comando ->
                Interpretacion.entendida(frase,
                        "Agregue " + comando.nombre() + ": " + comando.tipo()
                                + " a " + clase.get().nombre(),
                        List.of(new Interpretacion.Paso(TipoOperacion.ATRIBUTO_AGREGAR, comando))));
    }

    private Optional<ComandoOperacion.AgregarAtributo> atributoDesdeDetalle(UUID claseId,
                                                                            String detalle) {
        String limpio = detalle.trim();

        // Se intenta primero con el tipo declarado; la forma sin tipo es el
        // respaldo, no la primera opcion.
        Matcher m = DETALLE_ATRIBUTO_CON_TIPO.matcher(limpio);
        boolean conTipo = m.matches();
        if (!conTipo) {
            m = DETALLE_ATRIBUTO_SIN_TIPO.matcher(limpio);
            if (!m.matches()) {
                return Optional.empty();
            }
        }

        String nombre = limpiarNombre(m.group(1));
        if (nombre.isBlank()) {
            return Optional.empty();
        }
        String tipo = conTipo ? TiposDeclarados.normalizar(m.group(2)) : "String";
        String grupoDeMarcas = m.group(conTipo ? 3 : 2);
        String marcas = grupoDeMarcas == null ? "" : grupoDeMarcas.toLowerCase(Locale.ROOT);

        boolean esClave = marcas.contains("clave") || marcas.contains("identificador")
                || marcas.contains("primaria");
        boolean esUnico = esClave || marcas.contains("unico") || marcas.contains("unica");
        boolean esRequerido = esClave || marcas.contains("obligatorio") || marcas.contains("obligatoria")
                || marcas.contains("requerido") || marcas.contains("requerida")
                || marcas.contains("no nulo");

        Integer longitud = null;
        Matcher mLongitud = LONGITUD.matcher(marcas);
        if (mLongitud.find()) {
            longitud = Integer.valueOf(mLongitud.group(1));
        }

        return Optional.of(new ComandoOperacion.AgregarAtributo(claseId, UUID.randomUUID(),
                nombre, tipo, Visibilidad.PRIVADO, esClave, esRequerido, esUnico, longitud));
    }

    private Optional<Interpretacion> comoMetodo(String frase, String nombreDeLaClase,
                                                String detalle, ContextoDelDiagrama contexto) {
        Optional<ContextoDelDiagrama.ClaseConocida> clase = contexto.resolver(nombreDeLaClase);
        if (clase.isEmpty()) {
            return noConozco(frase, nombreDeLaClase, contexto);
        }

        Matcher m = DETALLE_METODO.matcher(detalle.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        String nombre = limpiarNombre(m.group(1));
        if (nombre.isBlank()) {
            return Optional.empty();
        }

        List<ComandoOperacion.Parametro> parametros = new ArrayList<>();
        if (m.group(2) != null) {
            for (String trozo : separarEnumeracion(m.group(2))) {
                Matcher mParametro = PARAMETRO.matcher(trozo.trim());
                if (mParametro.matches()) {
                    parametros.add(new ComandoOperacion.Parametro(UUID.randomUUID(),
                            limpiarNombre(mParametro.group(1)), TiposDeclarados.normalizar(mParametro.group(2))));
                }
            }
        }

        String retorno = m.group(3) == null ? "void" : TiposDeclarados.normalizar(m.group(3));
        return uno(frase,
                "Agregue la operacion " + nombre + "(): " + retorno + " a " + clase.get().nombre(),
                TipoOperacion.METODO_AGREGAR,
                new ComandoOperacion.AgregarMetodo(clase.get().id(), UUID.randomUUID(), nombre,
                        retorno, Visibilidad.PUBLICO, false, false, parametros));
    }

    // ---------- Auxiliares --------------------------------------------------

    private Optional<Interpretacion> uno(String frase, String explicacion, TipoOperacion tipo,
                                         ComandoOperacion comando) {
        return Optional.of(Interpretacion.entendida(frase, explicacion,
                List.of(new Interpretacion.Paso(tipo, comando))));
    }

    /**
     * No se entendio porque se nombro una clase que no existe. Decirlo asi -y no
     * "no te entendi"- es la diferencia entre que la persona corrija el nombre y
     * que repita la misma frase mas fuerte.
     */
    private Optional<Interpretacion> noConozco(String frase, String nombre, ContextoDelDiagrama contexto) {
        List<String> pistas = new ArrayList<>();
        pistas.add("No hay ninguna clase llamada \"" + nombre.trim() + "\" en el diagrama");
        if (!contexto.nombres().isEmpty()) {
            pistas.add("Las que hay son: " + String.join(", ", contexto.nombres()));
        }
        pistas.add("Proba primero: crea la clase " + limpiarNombre(nombre));
        return Optional.of(Interpretacion.noEntendida(frase, pistas));
    }

    private List<String> sugerencias(ContextoDelDiagrama contexto) {
        String ejemplo = contexto.nombres().isEmpty() ? "Paciente" : contexto.nombres().get(0);
        String otro = contexto.nombres().size() > 1 ? contexto.nombres().get(1) : "Consulta";
        return List.of(
                "crea la clase " + ejemplo,
                "a " + ejemplo + " agregale el atributo nombre de tipo texto obligatorio",
                "a " + ejemplo + " agregale el atributo codigo de tipo texto clave de longitud 20",
                "un " + ejemplo + " tiene muchas " + otro,
                ejemplo + " hereda de Persona",
                "marca Persona como abstracta",
                "a " + ejemplo + " agregale el metodo calcularEdad que devuelve entero");
    }

    /** Quita acentos, signos de puntuacion y espacios de sobra. */
    private String limpiar(String frase) {
        String sinAcentos = Normalizer.normalize(frase, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos
                // El reconocimiento de voz agrega puntos y comas donde le parece.
                .replaceAll("[.,;:!?¿¡\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Nombre listo para el modelo: sin espacios y en mayuscula inicial por
     * palabra. Dictando no se puede pronunciar el camello, asi que "historia
     * clinica" tiene que llegar como HistoriaClinica.
     */
    private String limpiarNombre(String bruto) {
        if (bruto == null) {
            return "";
        }
        String[] palabras = bruto.trim().split("\\s+");
        StringBuilder salida = new StringBuilder();
        for (int i = 0; i < palabras.length; i++) {
            String palabra = palabras[i];
            if (palabra.isEmpty()) {
                continue;
            }
            // La primera palabra conserva su caja si ya venia en mayuscula, para
            // no estropear un nombre que el cliente escribio bien.
            if (i == 0) {
                salida.append(palabra);
            } else {
                salida.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1));
            }
        }
        return salida.toString();
    }

    /** Separa "a, b y c" en sus partes. */
    private List<String> separarEnumeracion(String texto) {
        return List.of(texto.split("\\s*(?:,|\\sy\\s|\\se\\s)\\s*"));
    }
}
