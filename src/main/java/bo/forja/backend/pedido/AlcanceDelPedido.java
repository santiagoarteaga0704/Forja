package bo.forja.backend.pedido;

import bo.forja.backend.operacion.ComandoOperacion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cuanto puede hacer la IA de una sola vez: <b>un elemento</b>.
 * <p>
 * El pedido en lenguaje libre nacio devolviendo el diagrama entero -una frase
 * y veinte clases- y eso es justamente lo que no corresponde: la herramienta
 * no puede hacer el trabajo de modelado que se esta evaluando. La IA asiste
 * con una instruccion por vez, igual que el dictado, con la sola diferencia de
 * que tolera como se diga. Quien decide que lleva el diagrama sigue siendo la
 * persona.
 * <p>
 * <b>Por que el limite esta aca y no en el prompt.</b> Porque el prompt no
 * garantiza nada. El 21 de septiembre de 2026 se midio contra Gemma 3 4B que
 * el modelo desobedece sus instrucciones en la mayoria de las corridas
 * -llegaba a copiar literalmente las palabras de relleno de las plantillas-.
 * Una regla que solo vive en el texto que se le manda al modelo es una
 * expresion de deseo; esta se cumple siempre, y tiene pruebas.
 * <p>
 * <b>La regla.</b> De lo que el modelo proponga se conserva:
 * <ul>
 *   <li>la <b>primera clase</b> que se cree, con sus atributos y metodos; o</li>
 *   <li>si no se crea ninguna, los atributos y metodos de la <b>primera clase
 *       mencionada</b>; o</li>
 *   <li>si tampoco hay, la <b>primera relacion</b>.</li>
 * </ul>
 * La clase le gana a la relacion cuando vienen las dos: una relacion cuyo
 * extremo se acaba de crear en el mismo pedido es parte del mismo movimiento,
 * y dejarla entrar seria hacer dos cosas.
 * <p>
 * El recorte ocurre en la <b>lectura</b>, no al aplicar: lo que se muestra
 * para revisar tiene que ser exactamente lo que va a entrar. Ver
 * {@link PropuestasEnRevision}.
 */
public final class AlcanceDelPedido {

    private AlcanceDelPedido() {
    }

    /**
     * Lo que se conserva de una propuesta y cuanto se dejo afuera.
     *
     * @param descartados comandos que no entraron; se le cuentan a la persona
     *                    en vez de desaparecer, para que entienda que la
     *                    herramienta acota a proposito y no que fallo
     */
    public record Recorte(List<ComandoOperacion> admitidos, int descartados) {

        public boolean seRecorto() {
            return descartados > 0;
        }
    }

    public static Recorte recortar(List<ComandoOperacion> propuestos) {
        if (propuestos == null || propuestos.isEmpty()) {
            return new Recorte(List.of(), 0);
        }

        UUID clase = claseElegida(propuestos);
        List<ComandoOperacion> admitidos = new ArrayList<>();

        if (clase != null) {
            for (ComandoOperacion comando : propuestos) {
                if (clase.equals(claseDe(comando))) {
                    admitidos.add(comando);
                }
            }
        } else {
            for (ComandoOperacion comando : propuestos) {
                if (comando instanceof ComandoOperacion.CrearRelacion) {
                    admitidos.add(comando);
                    break;
                }
            }
        }

        return new Recorte(List.copyOf(admitidos), propuestos.size() - admitidos.size());
    }

    /**
     * La clase sobre la que trata el pedido: la primera que se crea, y si no se
     * crea ninguna, la primera que alguien menciona.
     */
    private static UUID claseElegida(List<ComandoOperacion> propuestos) {
        for (ComandoOperacion comando : propuestos) {
            if (comando instanceof ComandoOperacion.CrearClase crear) {
                return crear.claseId();
            }
        }
        for (ComandoOperacion comando : propuestos) {
            UUID clase = claseDe(comando);
            if (clase != null) {
                return clase;
            }
        }
        return null;
    }

    /** Sobre que clase trabaja el comando, o nulo si no es de una sola clase. */
    private static UUID claseDe(ComandoOperacion comando) {
        return switch (comando) {
            case ComandoOperacion.CrearClase c -> c.claseId();
            case ComandoOperacion.RenombrarClase c -> c.claseId();
            case ComandoOperacion.MarcarClase c -> c.claseId();
            case ComandoOperacion.MoverClase c -> c.claseId();
            case ComandoOperacion.EliminarClase c -> c.claseId();
            case ComandoOperacion.AgregarAtributo c -> c.claseId();
            case ComandoOperacion.EliminarAtributo c -> c.claseId();
            case ComandoOperacion.AgregarMetodo c -> c.claseId();
            case ComandoOperacion.EliminarMetodo c -> c.claseId();
            case ComandoOperacion.CrearRelacion c -> null;
            case ComandoOperacion.EliminarRelacion c -> null;
        };
    }
}
