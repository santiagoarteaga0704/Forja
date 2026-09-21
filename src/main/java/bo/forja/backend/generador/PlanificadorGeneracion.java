package bo.forja.backend.generador;

import bo.forja.backend.dominio.AtributoUml;
import bo.forja.backend.dominio.ClaseUml;
import bo.forja.backend.dominio.MetodoUml;
import bo.forja.backend.dominio.ParametroUml;
import bo.forja.backend.dominio.RelacionUml;
import bo.forja.backend.dominio.TipoRelacion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Interpreta el diagrama y decide como se traduce a un proyecto Spring Boot.
 * <p>
 * Aqui viven las reglas de traduccion, que son la parte del generador que
 * puede discutirse: de donde sale la clave primaria, que anotacion JPA le
 * toca a cada asociacion, cual de los dos extremos guarda la clave ajena y
 * que hace la herencia. Todas quedan resueltas en el {@link Plan}, de modo
 * que se pueden comprobar sin leer una sola linea del codigo generado.
 * <p>
 * <b>Convenciones que el generador asume sobre el diagrama:</b>
 * <ul>
 *   <li>En una herencia, el <i>origen</i> es la subclase y el <i>destino</i>
 *       la superclase, siguiendo el sentido de la flecha de generalizacion
 *       de UML, que apunta de lo particular a lo general.</li>
 *   <li>Una clase con estereotipo {@code interface} se genera como interfaz
 *       de Java y no como entidad.</li>
 *   <li>La multiplicidad de cada extremo dice cuantos objetos admite <i>ese</i>
 *       extremo. De la combinacion de ambos sale la cardinalidad.</li>
 *   <li>Una dependencia no produce estructura: expresa un acoplamiento de
 *       uso, no un dato que haya que guardar.</li>
 * </ul>
 */
@Component
public class PlanificadorGeneracion {

    private static final Set<String> ESTEREOTIPOS_DE_INTERFAZ = Set.of("interface", "interfaz");

    public Plan.Proyecto planificar(String paqueteBase,
                                    String artefacto,
                                    String nombreAplicacion,
                                    List<ClaseUml> clases,
                                    List<RelacionUml> relaciones) {

        Map<UUID, ClaseUml> porId = new LinkedHashMap<>();
        clases.forEach(c -> porId.put(c.getId(), c));

        Map<UUID, String> padres = new HashMap<>();
        Map<UUID, List<String>> interfaces = new HashMap<>();
        Set<UUID> conHijos = new HashSet<>();
        Map<UUID, List<Plan.Asociacion>> asociaciones = new HashMap<>();
        clases.forEach(c -> asociaciones.put(c.getId(), new ArrayList<>()));

        for (RelacionUml relacion : relaciones) {
            ClaseUml origen = porId.get(relacion.getOrigen().getId());
            ClaseUml destino = porId.get(relacion.getDestino().getId());
            if (origen == null || destino == null) {
                // Puede pasar si la relacion apunta a una clase de otro
                // diagrama; se omite en lugar de abortar la generacion.
                continue;
            }

            switch (relacion.getTipo()) {
                case HERENCIA -> {
                    padres.put(origen.getId(), Nombres.clase(destino.getNombre()));
                    conHijos.add(destino.getId());
                }
                case REALIZACION -> interfaces
                        .computeIfAbsent(origen.getId(), id -> new ArrayList<>())
                        .add(Nombres.clase(destino.getNombre()));
                case DEPENDENCIA -> {
                    // Sin efecto estructural, por decision explicita.
                }
                default -> repartirAsociacion(relacion, origen, destino, asociaciones);
            }
        }

        // Los nombres de clase del diagrama, para reconocer un atributo que en
        // realidad nombra a otra clase. Ver planificarClase.
        Set<String> nombresDeClase = clases.stream()
                .map(c -> Nombres.clase(c.getNombre()))
                .collect(Collectors.toSet());

        List<Plan.Clase> planificadas = new ArrayList<>();
        for (ClaseUml clase : clases) {
            planificadas.add(planificarClase(clase,
                    padres.get(clase.getId()),
                    interfaces.getOrDefault(clase.getId(), List.of()),
                    conHijos.contains(clase.getId()) && !padres.containsKey(clase.getId()),
                    asociaciones.getOrDefault(clase.getId(), List.of()),
                    nombresDeClase));
        }

        return new Plan.Proyecto(paqueteBase, artefacto, nombreAplicacion,
                conOperacionesDeLasInterfaces(planificadas));
    }

    /**
     * Completa cada clase con las operaciones de las interfaces que realiza.
     * <p>
     * No es un adorno: en Java una clase concreta que implementa una interfaz
     * <b>debe</b> definir sus metodos, asi que sin este paso el codigo
     * generado no compilaria. Y es exactamente el tipo de omision que el
     * diagrama no hace evidente, porque en UML basta con dibujar la flecha de
     * realizacion para que la relacion quede expresada.
     * <p>
     * Se identifican por nombre y cantidad de parametros: si la clase ya
     * declaro una operacion con esa firma, se respeta la suya.
     */
    private List<Plan.Clase> conOperacionesDeLasInterfaces(List<Plan.Clase> clases) {
        Map<String, Plan.Clase> porNombre = new LinkedHashMap<>();
        clases.forEach(c -> porNombre.put(c.nombreJava(), c));

        List<Plan.Clase> resultado = new ArrayList<>();
        for (Plan.Clase clase : clases) {
            if (clase.esInterfaz() || clase.interfaces().isEmpty()) {
                resultado.add(clase);
                continue;
            }

            Set<String> firmasPropias = new HashSet<>();
            clase.operaciones().forEach(o -> firmasPropias.add(firma(o)));

            List<Plan.Operacion> completas = new ArrayList<>(clase.operaciones());
            for (String nombreInterfaz : clase.interfaces()) {
                Plan.Clase interfaz = porNombre.get(nombreInterfaz);
                if (interfaz == null) {
                    continue;
                }
                interfaz.operaciones().stream()
                        .filter(o -> firmasPropias.add(firma(o)))
                        .forEach(completas::add);
            }

            resultado.add(completas.size() == clase.operaciones().size()
                    ? clase
                    : conOperaciones(clase, completas));
        }
        return resultado;
    }

    private String firma(Plan.Operacion operacion) {
        return operacion.nombre() + "/" + operacion.parametros().size();
    }

    private Plan.Clase conOperaciones(Plan.Clase clase, List<Plan.Operacion> operaciones) {
        return new Plan.Clase(clase.nombreUml(), clase.nombreJava(), clase.tabla(), clase.ruta(),
                clase.esAbstracta(), clase.esInterfaz(), clase.padre(), clase.interfaces(),
                clase.esRaizDeJerarquia(), clase.identificador(), clase.campos(),
                clase.asociaciones(), operaciones);
    }

    // ---------- Clases ----------------------------------------------------

    /**
     * @param nombresDeClase clases del diagrama, para distinguir un atributo de
     *                       verdad de uno que nombra a otra clase
     */
    private Plan.Clase planificarClase(ClaseUml clase,
                                       String padre,
                                       List<String> interfaces,
                                       boolean esRaiz,
                                       List<Plan.Asociacion> asociaciones,
                                       Set<String> nombresDeClase) {

        boolean esInterfaz = clase.getEstereotipo() != null
                && ESTEREOTIPOS_DE_INTERFAZ.contains(clase.getEstereotipo().trim().toLowerCase());

        AtributoUml marcado = clase.getAtributos().stream()
                .filter(AtributoUml::isEsIdentificador)
                .findFirst()
                .orElse(null);

        // Un atributo llamado "id" que nadie marco es, igual, la clave. Es como
        // se dibuja una clave la mayor parte de las veces, y antes de tomarlo
        // asi el generador le agregaba ADEMAS la suya, tambien llamada "id":
        // salian dos campos y dos getters con el mismo nombre y el proyecto
        // generado no compilaba.
        AtributoUml comoClave = marcado == null ? atributoLlamadoId(clase) : null;

        // Una clase que hereda no lleva clave propia: la comparte con la raiz
        // de su jerarquia. Declararla de nuevo produciria dos claves para la
        // misma fila.
        Plan.Campo identificador = null;
        if (!esInterfaz && padre == null) {
            if (marcado != null) {
                identificador = campoDe(marcado, false);
            } else if (comoClave != null) {
                // Generada: quien dibujo "id" sin decir mas espera que la
                // asigne la base, no tener que inventar el numero a mano.
                identificador = campoDe(comoClave, true);
            } else {
                identificador = identificadorGenerado();
            }
        }

        // Un atributo cuyo tipo es otra clase del diagrama -"responsable de
        // tipo Medico"- NO es una columna: es una relacion escrita en el unico
        // lugar donde quien modela sabia escribirla. Tratarlo como columna
        // produce @Column sobre una entidad, que compila y despues no arranca
        // porque Hibernate no tiene con que llevar una fila a una columna.
        List<AtributoUml> propios = new ArrayList<>();
        List<Plan.Asociacion> porAtributo = new ArrayList<>();
        for (AtributoUml atributo : clase.getAtributos()) {
            if (atributo == marcado || atributo == comoClave) {
                continue;
            }
            if (esInterfaz || !nombresDeClase.contains(TipoJava.de(atributo.getTipo()))) {
                propios.add(atributo);
            } else {
                porAtributo.add(asociacionDe(atributo));
            }
        }

        List<Plan.Campo> campos = propios.stream()
                .map(a -> campoDe(a, false))
                .toList();

        List<Plan.Asociacion> todasLasAsociaciones = asociaciones;
        if (!porAtributo.isEmpty()) {
            todasLasAsociaciones = new ArrayList<>(asociaciones);
            todasLasAsociaciones.addAll(porAtributo);
        }

        List<Plan.Operacion> operaciones = clase.getMetodos().stream()
                .map(this::operacionDe)
                .toList();

        return new Plan.Clase(
                clase.getNombre(),
                Nombres.clase(clase.getNombre()),
                Nombres.tabla(clase.getNombre()),
                Nombres.ruta(clase.getNombre()),
                clase.isEsAbstracta(),
                esInterfaz,
                padre,
                interfaces,
                esRaiz,
                identificador,
                sinRepetirNombres(identificador, campos, todasLasAsociaciones),
                todasLasAsociaciones,
                operaciones);
    }

    /**
     * Clave primaria cuando el modelo no marco ninguna. Se elige {@code Long}
     * autoincremental y no UUID porque el proyecto generado es un CRUD que se
     * prueba a mano, y una clave corta y legible se escribe sin copiar y pegar.
     */
    /**
     * El atributo que hace de clave sin que nadie lo haya marcado.
     * <p>
     * Solo cuenta si ademas su tipo puede ser una clave: un {@code id} de tipo
     * booleano no lo es, y en ese caso conviene dejar la clave generada y
     * renombrar el atributo, que es lo que hace {@code sinRepetirNombres}.
     */
    private AtributoUml atributoLlamadoId(ClaseUml clase) {
        for (AtributoUml atributo : clase.getAtributos()) {
            if ("id".equalsIgnoreCase(Nombres.campo(atributo.getNombre()))
                    && TipoJava.sirveDeIdentificador(TipoJava.de(atributo.getTipo()))) {
                return atributo;
            }
        }
        return null;
    }

    private Plan.Campo identificadorGenerado() {
        return new Plan.Campo("id", "Long", "id", true, true, null, true);
    }

    /**
     * La relacion que se quiso decir con un atributo de tipo clase.
     * <p>
     * Es muchos a uno y del lado propietario: el atributo apunta a una sola
     * instancia de la otra clase, y la clave ajena vive aca. No se le pone
     * contraparte en la otra clase -la relacion se dibujo en un solo lado- ni
     * cascada, que es una decision que el diagrama no expreso.
     */
    private Plan.Asociacion asociacionDe(AtributoUml atributo) {
        String campo = Nombres.campo(atributo.getNombre());
        return new Plan.Asociacion(
                Plan.Cardinalidad.MUCHOS_A_UNO,
                campo,
                TipoJava.de(atributo.getTipo()),
                false,
                true,
                atributo.isEsRequerido(),
                Nombres.columna(atributo.getNombre()) + "_id",
                null,
                null,
                false);
    }

    private Plan.Campo campoDe(AtributoUml atributo, boolean generado) {
        String tipo = TipoJava.de(atributo.getTipo());
        Integer longitud = TipoJava.esTextual(tipo) ? atributo.getLongitud() : null;
        return new Plan.Campo(
                Nombres.campo(atributo.getNombre()),
                tipo,
                Nombres.columna(atributo.getNombre()),
                atributo.isEsRequerido(),
                atributo.isEsUnico(),
                longitud,
                generado);
    }

    private Plan.Operacion operacionDe(MetodoUml metodo) {
        List<Plan.Parametro> parametros = metodo.getParametros().stream()
                .map(this::parametroDe)
                .toList();
        String retorno = metodo.getTipoRetorno() == null || metodo.getTipoRetorno().isBlank()
                || metodo.getTipoRetorno().equalsIgnoreCase("void")
                ? "void"
                : TipoJava.de(metodo.getTipoRetorno());
        return new Plan.Operacion(Nombres.campo(metodo.getNombre()), retorno, parametros);
    }

    private Plan.Parametro parametroDe(ParametroUml parametro) {
        return new Plan.Parametro(Nombres.campo(parametro.getNombre()),
                TipoJava.de(parametro.getTipo()));
    }

    /**
     * Un atributo y una asociacion pueden aspirar al mismo nombre de campo
     * -por ejemplo un atributo {@code paciente} y una asociacion hacia la
     * clase {@code Paciente}-. Se conserva el del atributo y se desplaza el
     * de la asociacion, porque el atributo lo escribio la persona
     * explicitamente y la asociacion se nombro por deduccion.
     */
    /**
     * @param identificador la clave, que participa del reparto de nombres
     *                      aunque no este en la lista de campos. Sin esto, un
     *                      atributo llamado "id" chocaba con la clave generada
     *                      -tambien "id"- y el proyecto no compilaba. El caso
     *                      normal lo resuelve antes {@code atributoLlamadoId},
     *                      tomando ese atributo COMO la clave; esto queda para
     *                      cuando no puede serlo, por ejemplo un "id" booleano.
     */
    private List<Plan.Campo> sinRepetirNombres(Plan.Campo identificador,
                                               List<Plan.Campo> campos,
                                               List<Plan.Asociacion> asociaciones) {
        Set<String> usados = new HashSet<>();
        if (identificador != null) {
            usados.add(identificador.nombre());
        }
        List<Plan.Campo> resultado = new ArrayList<>();
        for (Plan.Campo campo : campos) {
            String nombre = campo.nombre();
            while (!usados.add(nombre)) {
                nombre = nombre + "_";
            }
            resultado.add(nombre.equals(campo.nombre()) ? campo : renombrar(campo, nombre));
        }

        for (int i = 0; i < asociaciones.size(); i++) {
            Plan.Asociacion asociacion = asociaciones.get(i);
            String nombre = asociacion.nombreCampo();
            while (!usados.add(nombre)) {
                nombre = nombre + "Asociado";
            }
            if (!nombre.equals(asociacion.nombreCampo())) {
                asociaciones.set(i, renombrar(asociacion, nombre));
            }
        }
        return resultado;
    }

    private Plan.Campo renombrar(Plan.Campo campo, String nombre) {
        return new Plan.Campo(nombre, campo.tipoJava(), campo.columna(), campo.requerido(),
                campo.unico(), campo.longitud(), campo.generado());
    }

    private Plan.Asociacion renombrar(Plan.Asociacion a, String nombre) {
        return new Plan.Asociacion(a.cardinalidad(), nombre, a.tipoDestino(), a.coleccion(),
                a.propietario(), a.requerido(), a.columnaClaveAjena(), a.tablaUnion(),
                a.mapeadoPor(), a.cascadaTotal());
    }

    // ---------- Asociaciones ----------------------------------------------

    /**
     * Crea los dos extremos de una asociacion y decide cual guarda la clave
     * ajena.
     * <p>
     * El criterio es el de JPA y no es arbitrario: la clave ajena vive en el
     * lado "muchos", porque es el que puede tener una sola referencia al otro.
     * Cuando ambos lados son "muchos" hace falta una tabla de union, y cuando
     * ninguno lo es la relacion es uno a uno y se elige el origen como
     * propietario por seguir el sentido en que se dibujo la relacion.
     */
    private void repartirAsociacion(RelacionUml relacion,
                                    ClaseUml origen,
                                    ClaseUml destino,
                                    Map<UUID, List<Plan.Asociacion>> destinoDeCadaClase) {

        Multiplicidad enOrigen = Multiplicidad.de(relacion.getMultiplicidadOrigen());
        Multiplicidad enDestino = Multiplicidad.de(relacion.getMultiplicidadDestino());

        String claseOrigen = Nombres.clase(origen.getNombre());
        String claseDestino = Nombres.clase(destino.getNombre());

        // Como se llama, en cada clase, el campo que apunta a la otra. El rol
        // del extremo manda si esta escrito; si no, se deduce del nombre de la
        // clase apuntada.
        String campoHaciaDestino = nombreDeCampo(relacion.getRolDestino(),
                destino.getNombre(), enDestino.muchos());
        String campoHaciaOrigen = nombreDeCampo(relacion.getRolOrigen(),
                origen.getNombre(), enOrigen.muchos());

        boolean composicion = relacion.getTipo() == TipoRelacion.COMPOSICION;

        if (enOrigen.muchos() && enDestino.muchos()) {
            String tablaUnion = Nombres.tabla(origen.getNombre()) + "_" + Nombres.tabla(destino.getNombre());
            agregar(destinoDeCadaClase, origen, new Plan.Asociacion(
                    Plan.Cardinalidad.MUCHOS_A_MUCHOS, campoHaciaDestino, claseDestino,
                    true, true, false, null, tablaUnion, null, false));
            agregar(destinoDeCadaClase, destino, new Plan.Asociacion(
                    Plan.Cardinalidad.MUCHOS_A_MUCHOS, campoHaciaOrigen, claseOrigen,
                    true, false, false, null, null, campoHaciaDestino, false));

        } else if (enDestino.muchos()) {
            // El origen tiene muchos destinos: la clave ajena va en el destino.
            agregar(destinoDeCadaClase, origen, new Plan.Asociacion(
                    Plan.Cardinalidad.UNO_A_MUCHOS, campoHaciaDestino, claseDestino,
                    true, false, false, null, null, campoHaciaOrigen, composicion));
            agregar(destinoDeCadaClase, destino, new Plan.Asociacion(
                    Plan.Cardinalidad.MUCHOS_A_UNO, campoHaciaOrigen, claseOrigen,
                    false, true, enOrigen.obligatorio(),
                    Nombres.columna(campoHaciaOrigen) + "_id", null, null, false));

        } else if (enOrigen.muchos()) {
            agregar(destinoDeCadaClase, destino, new Plan.Asociacion(
                    Plan.Cardinalidad.UNO_A_MUCHOS, campoHaciaOrigen, claseOrigen,
                    true, false, false, null, null, campoHaciaDestino, composicion));
            agregar(destinoDeCadaClase, origen, new Plan.Asociacion(
                    Plan.Cardinalidad.MUCHOS_A_UNO, campoHaciaDestino, claseDestino,
                    false, true, enDestino.obligatorio(),
                    Nombres.columna(campoHaciaDestino) + "_id", null, null, false));

        } else {
            agregar(destinoDeCadaClase, origen, new Plan.Asociacion(
                    Plan.Cardinalidad.UNO_A_UNO, campoHaciaDestino, claseDestino,
                    false, true, enDestino.obligatorio(),
                    Nombres.columna(campoHaciaDestino) + "_id", null, null, composicion));
            agregar(destinoDeCadaClase, destino, new Plan.Asociacion(
                    Plan.Cardinalidad.UNO_A_UNO, campoHaciaOrigen, claseOrigen,
                    false, false, false, null, null, campoHaciaDestino, false));
        }
    }

    private String nombreDeCampo(String rol, String nombreDeLaClase, boolean coleccion) {
        String base = rol != null && !rol.isBlank() ? rol : nombreDeLaClase;
        String campo = Nombres.campo(base);
        return coleccion ? Nombres.plural(campo) : campo;
    }

    private void agregar(Map<UUID, List<Plan.Asociacion>> mapa, ClaseUml clase,
                         Plan.Asociacion asociacion) {
        mapa.computeIfAbsent(clase.getId(), id -> new ArrayList<>()).add(asociacion);
    }
}
