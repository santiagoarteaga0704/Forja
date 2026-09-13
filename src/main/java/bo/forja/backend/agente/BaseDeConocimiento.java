package bo.forja.backend.agente;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.OrigenOperacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Las reglas que el agente guia sabe.
 * <p>
 * Estan agrupadas en dos familias porque responden a dos preguntas distintas.
 * Las de <b>descubrimiento</b> miran lo que la persona todavia no probo y le
 * ensenan la herramienta; su condicion se apoya en la bitacora, que registra por
 * que via entro cada cambio. Las de <b>modelo</b> y <b>diseno</b> miran el
 * diagrama y senalan lo que va a afectar al codigo generado, o lo que el propio
 * modelo ya deja ver que conviene reorganizar.
 * <p>
 * La prioridad esta pensada para que lo urgente no quede tapado: un diagrama
 * vacio no necesita consejos de diseno, y una interfaz con atributos -que rompe
 * el codigo generado- va antes que una convencion de nombres.
 * <p>
 * Ninguna regla afirma que algo esta mal sin decir que consecuencia tiene. Un
 * agente que solo senala defectos se ignora a la segunda vez.
 */
@Component
public class BaseDeConocimiento {

    /**
     * Terminaciones en s que en castellano son singulares: analisis, crisis,
     * tesis, campus, virus. Son las unicas fiables. Se descarto incluir "as",
     * "os" y "es" porque son precisamente las marcas de plural mas frecuentes, y
     * excluirlas dejaba la regla sin efecto.
     * <p>
     * Queda un margen de error conocido -"estres" termina en es y es singular- y
     * por eso el consejo esta redactado como condicion: "si es plural, la
     * convencion es el singular". Con esa forma, un falso positivo no afirma
     * nada incorrecto.
     */
    private static final Set<String> TERMINACIONES_SINGULARES = Set.of("is", "us");

    public List<Regla> reglas() {
        return List.of(
                // ---------- Descubrimiento: ensenar la herramienta ----------
                regla("diagrama-vacio", this::diagramaVacio),
                regla("sin-relaciones", this::sinRelaciones),
                regla("nunca-dicto", this::nuncaDicto),
                regla("listo-para-generar", this::listoParaGenerar),
                regla("proyecto-de-uno", this::proyectoDeUnoSolo),
                regla("puede-intercambiar", this::puedeIntercambiar),

                // ---------- Modelo: lo que afecta a lo que se genera ----------
                regla("clase-sin-atributos", this::claseSinAtributos),
                regla("clase-aislada", this::claseAislada),
                regla("sin-clave-propia", this::sinClavePropia),
                regla("interfaz-con-atributos", this::interfazConAtributos),

                // ---------- Diseno: lo que el modelo deja ver ----------
                regla("raiz-sin-abstracta", this::raizSinAbstracta),
                regla("atributos-repetidos", this::atributosRepetidos),
                regla("atributo-ya-heredado", this::atributoYaHeredado),
                regla("nombre-en-plural", this::nombreEnPlural));
    }

    // ---------- Descubrimiento ----------------------------------------------

    private List<Consejo> diagramaVacio(Observacion o) {
        if (!o.clases().isEmpty()) {
            return List.of();
        }
        return List.of(Consejo.de("diagrama-vacio", Consejo.Categoria.DESCUBRIMIENTO, 100,
                "El diagrama todavia no tiene ninguna clase",
                "Sin clases no hay nada que generar ni que exportar: todo lo demas parte de aqui",
                "Usa el boton + Clase, o dicta: crea la clase Paciente"));
    }

    private List<Consejo> sinRelaciones(Observacion o) {
        if (o.clases().size() < 2 || !o.relaciones().isEmpty()) {
            return List.of();
        }
        String primera = o.clases().get(0).getNombre();
        String segunda = o.clases().get(1).getNombre();
        return List.of(Consejo.de("sin-relaciones", Consejo.Categoria.DESCUBRIMIENTO, 90,
                "Hay " + o.clases().size() + " clases pero ninguna relacion entre ellas",
                "Las relaciones son las que producen las claves ajenas y las colecciones del "
                        + "codigo generado; sin ellas salen tablas sueltas",
                "Dicta: un " + primera + " tiene muchas " + segunda));
    }

    private List<Consejo> nuncaDicto(Observacion o) {
        // Se espera a que haya trabajado un poco: sugerirlo en el primer cambio
        // seria ruido antes de que la persona sepa que esta haciendo.
        if (o.totalDeOperaciones() < 3 || o.operacionesConOrigen(OrigenOperacion.VOZ) > 0) {
            return List.of();
        }
        return List.of(Consejo.de("nunca-dicto", Consejo.Categoria.DESCUBRIMIENTO, 60,
                "Todos los cambios se hicieron con el raton",
                "El diagrama se puede dictar, y para cargar atributos suele ser mas rapido que "
                        + "abrir el panel de cada clase",
                "Abri Dictar y proba: a " + o.clases().get(0).getNombre()
                        + " agregale el atributo nombre de tipo texto obligatorio"));
    }

    private List<Consejo> listoParaGenerar(Observacion o) {
        if (o.clases().size() < 2 || o.cantidadDeAtributos() < 2) {
            return List.of();
        }
        return List.of(Consejo.de("listo-para-generar", Consejo.Categoria.DESCUBRIMIENTO, 50,
                "Este diagrama ya alcanza para generar un backend completo",
                "Vas a obtener la entidad, el repositorio, el servicio y el controlador REST de "
                        + "cada clase, y el proyecto arranca sin configurar nada",
                "Usa Generar backend: podes leer el codigo antes de descargarlo"));
    }

    private List<Consejo> proyectoDeUnoSolo(Observacion o) {
        if (o.cantidadDeMiembros() > 1 || o.clases().size() < 2) {
            return List.of();
        }
        return List.of(Consejo.de("proyecto-de-uno", Consejo.Categoria.DESCUBRIMIENTO, 40,
                "Sos el unico miembro del proyecto",
                "El lienzo es colaborativo: dos personas pueden modelar a la vez y cada una ve "
                        + "lo que la otra esta editando",
                "Invita a alguien desde la pantalla de proyectos, por su correo"));
    }

    private List<Consejo> puedeIntercambiar(Observacion o) {
        if (o.clases().size() < 3) {
            return List.of();
        }
        return List.of(Consejo.de("puede-intercambiar", Consejo.Categoria.DESCUBRIMIENTO, 30,
                "El modelo ya tiene tamano para intercambiarlo con otra herramienta",
                "Se exporta en XMI 2.5.1, que es lo que lee Enterprise Architect, y lo que salga "
                        + "de alla se puede volver a importar aqui",
                "Usa Exportar XMI"));
    }

    // ---------- Modelo -------------------------------------------------------

    private List<Consejo> claseSinAtributos(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            // Una interfaz sin atributos es correcta, y una clase abstracta puede
            // existir solo para agrupar comportamiento.
            if (o.esInterfaz(clase) || clase.isEsAbstracta() || !clase.getAtributos().isEmpty()) {
                continue;
            }
            consejos.add(Consejo.sobre("clase-sin-atributos:" + clase.getId(),
                    Consejo.Categoria.MODELO, 80, clase.getId(),
                    clase.getNombre() + " no tiene ningun atributo",
                    "Su tabla va a salir con una sola columna, la clave que se genera "
                            + "automaticamente, y su API no va a poder guardar ningun dato",
                    "Dicta: a " + clase.getNombre() + " agregale el atributo nombre de tipo texto"));
        }
        return consejos;
    }

    private List<Consejo> claseAislada(Observacion o) {
        if (o.clases().size() < 2) {
            return List.of();
        }
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            if (!o.relacionesDe(clase).isEmpty()) {
                continue;
            }
            consejos.add(Consejo.sobre("clase-aislada:" + clase.getId(),
                    Consejo.Categoria.MODELO, 70, clase.getId(),
                    clase.getNombre() + " no se relaciona con ninguna otra clase",
                    "Una clase aislada genera una tabla que nada referencia; si de verdad no se "
                            + "relaciona con nada, quiza pertenezca a otro diagrama",
                    "Trazale una relacion, o dicta: un " + clase.getNombre()
                            + " tiene muchas " + otraQueNoSea(o, clase)));
        }
        return consejos;
    }

    private List<Consejo> sinClavePropia(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            boolean tieneClave = clase.getAtributos().stream().anyMatch(AtributoUml::isEsIdentificador);
            // Si hereda, la clave vive en la raiz de la jerarquia y esta bien que
            // no declare una propia.
            if (o.esInterfaz(clase) || clase.getAtributos().isEmpty() || tieneClave
                    || o.padreDe(clase).isPresent()) {
                continue;
            }
            consejos.add(Consejo.sobre("sin-clave-propia:" + clase.getId(),
                    Consejo.Categoria.MODELO, 35, clase.getId(),
                    clase.getNombre() + " no declara cual de sus atributos es la clave",
                    "El generador le va a agregar un id numerico automatico. Funciona, pero si el "
                            + "modelo ya tiene un identificador propio -un codigo, una matricula- "
                            + "conviene marcarlo",
                    "Marca el atributo como clave en el panel de la clase"));
        }
        return consejos;
    }

    private List<Consejo> interfazConAtributos(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            if (!o.esInterfaz(clase) || clase.getAtributos().isEmpty()) {
                continue;
            }
            consejos.add(Consejo.sobre("interfaz-con-atributos:" + clase.getId(),
                    Consejo.Categoria.MODELO, 95, clase.getId(),
                    clase.getNombre() + " esta marcada como interfaz pero declara atributos",
                    "Una interfaz de Java no puede tener atributos de instancia: al generar, esos "
                            + "atributos se pierden. Si el tipo necesita guardar datos, es una clase "
                            + "abstracta y no una interfaz",
                    "Quitale el estereotipo interface y marcala como abstracta, o saca los atributos"));
        }
        return consejos;
    }

    // ---------- Diseno -------------------------------------------------------

    private List<Consejo> raizSinAbstracta(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            List<ClaseUml> hijos = o.hijosDe(clase);
            if (hijos.size() < 2 || clase.isEsAbstracta() || o.esInterfaz(clase)) {
                continue;
            }
            consejos.add(Consejo.sobre("raiz-sin-abstracta:" + clase.getId(),
                    Consejo.Categoria.DISENO, 45, clase.getId(),
                    clase.getNombre() + " tiene " + hijos.size()
                            + " subclases pero no esta marcada como abstracta",
                    "Si no existe ninguna " + clase.getNombre()
                            + " que no sea una de sus subclases, marcarla abstracta impide crear "
                            + "instancias que no representan nada",
                    "Dicta: marca " + clase.getNombre() + " como abstracta"));
        }
        return consejos;
    }

    /**
     * Deteccion de una superclase que falta.
     * <p>
     * Es la unica regla que infiere algo que no esta dibujado: si dos clases
     * comparten dos o mas atributos con el mismo nombre, es probable que haya un
     * concepto comun sin extraer. Exige dos coincidencias y no una porque un solo
     * "nombre" repetido no dice nada, y se calla si ya comparten jerarquia.
     */
    private List<Consejo> atributosRepetidos(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        List<ClaseUml> clases = o.clases();

        for (int i = 0; i < clases.size(); i++) {
            for (int j = i + 1; j < clases.size(); j++) {
                ClaseUml una = clases.get(i);
                ClaseUml otra = clases.get(j);
                if (yaComparteJerarquia(o, una, otra)) {
                    continue;
                }

                Set<String> comunes = new LinkedHashSet<>(normalizados(o, una));
                comunes.retainAll(normalizados(o, otra));
                if (comunes.size() < 2) {
                    continue;
                }

                consejos.add(Consejo.sobre("atributos-repetidos:" + una.getId() + ":" + otra.getId(),
                        Consejo.Categoria.DISENO, 55, una.getId(),
                        una.getNombre() + " y " + otra.getNombre() + " repiten "
                                + comunes.size() + " atributos: " + String.join(", ", comunes),
                        "Cuando dos clases comparten varios atributos suele haber un concepto comun "
                                + "sin nombrar. Extraerlo a una superclase evita mantener los mismos "
                                + "campos en dos lugares",
                        "Crea la clase comun y dicta: " + una.getNombre() + " hereda de "
                                + nombreSugeridoDePadre(una, otra)));
            }
        }
        return consejos;
    }

    private List<Consejo> atributoYaHeredado(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            ClaseUml padre = o.padreDe(clase).orElse(null);
            if (padre == null) {
                continue;
            }
            Set<String> delPadre = new LinkedHashSet<>(normalizados(o, padre));
            List<String> repetidos = normalizados(o, clase).stream()
                    .filter(delPadre::contains)
                    .toList();
            if (repetidos.isEmpty()) {
                continue;
            }
            consejos.add(Consejo.sobre("atributo-ya-heredado:" + clase.getId(),
                    Consejo.Categoria.DISENO, 65, clase.getId(),
                    clase.getNombre() + " vuelve a declarar atributos que ya hereda de "
                            + padre.getNombre() + ": " + String.join(", ", repetidos),
                    "En el codigo generado van a existir dos veces, una en cada tabla de la "
                            + "jerarquia, y nada garantiza que tengan el mismo valor",
                    "Quitalos de " + clase.getNombre() + ": ya los tiene por herencia"));
        }
        return consejos;
    }

    private List<Consejo> nombreEnPlural(Observacion o) {
        List<Consejo> consejos = new ArrayList<>();
        for (ClaseUml clase : o.clases()) {
            if (!pareceEnPlural(clase.getNombre())) {
                continue;
            }
            consejos.add(Consejo.sobre("nombre-en-plural:" + clase.getId(),
                    Consejo.Categoria.DISENO, 10, clase.getId(),
                    "Si " + clase.getNombre() + " es plural, la convencion es nombrar en singular",
                    "Una clase describe un objeto y una tabla guarda muchos: el generador ya "
                            + "pluraliza el nombre de la tabla, de modo que en plural saldria "
                            + "duplicado",
                    "Renombrala en singular desde el panel de la clase"));
        }
        return consejos;
    }

    // ---------- Auxiliares ---------------------------------------------------

    private Regla regla(String nombre, java.util.function.Function<Observacion, List<Consejo>> cuerpo) {
        return new Regla() {
            @Override
            public String nombre() {
                return nombre;
            }

            @Override
            public List<Consejo> evaluar(Observacion observacion) {
                return cuerpo.apply(observacion);
            }
        };
    }

    private List<String> normalizados(Observacion o, ClaseUml clase) {
        return o.nombresDeAtributos(clase).stream()
                .map(nombre -> nombre.toLowerCase(Locale.ROOT))
                .toList();
    }

    private boolean yaComparteJerarquia(Observacion o, ClaseUml una, ClaseUml otra) {
        boolean unaHeredaDeOtra = o.padreDe(una).map(p -> p.getId().equals(otra.getId())).orElse(false);
        boolean otraHeredaDeUna = o.padreDe(otra).map(p -> p.getId().equals(una.getId())).orElse(false);
        boolean mismoPadre = o.padreDe(una).isPresent() && o.padreDe(otra).isPresent()
                && o.padreDe(una).get().getId().equals(o.padreDe(otra).get().getId());
        return unaHeredaDeOtra || otraHeredaDeUna || mismoPadre;
    }

    /** Nombre de relleno para la superclase sugerida; la persona lo va a cambiar. */
    private String nombreSugeridoDePadre(ClaseUml una, ClaseUml otra) {
        String prefijo = prefijoComun(una.getNombre(), otra.getNombre());
        return prefijo.length() >= 4 ? prefijo : "Base";
    }

    private String prefijoComun(String uno, String otro) {
        int limite = Math.min(uno.length(), otro.length());
        int i = 0;
        while (i < limite && Character.toLowerCase(uno.charAt(i)) == Character.toLowerCase(otro.charAt(i))) {
            i++;
        }
        return uno.substring(0, i);
    }

    /**
     * Heuristica conservadora de plural. Se descartan las terminaciones que en
     * castellano suelen ser singulares -analisis, campus, ves- porque un consejo
     * equivocado sobre el nombre de una clase resta credito a todos los demas.
     */
    private boolean pareceEnPlural(String nombre) {
        if (nombre == null || nombre.length() < 5 || !nombre.toLowerCase(Locale.ROOT).endsWith("s")) {
            return false;
        }
        String enMinuscula = nombre.toLowerCase(Locale.ROOT);
        String ultimasDos = enMinuscula.substring(enMinuscula.length() - 2);
        return !TERMINACIONES_SINGULARES.contains(ultimasDos);
    }

    private String otraQueNoSea(Observacion o, ClaseUml clase) {
        return o.clases().stream()
                .filter(c -> !c.getId().equals(clase.getId()))
                .map(ClaseUml::getNombre)
                .findFirst()
                .orElse("Consulta");
    }
}
