package bo.forja.backend.operacion;

import bo.forja.backend.dominio.TipoElemento;

import java.util.UUID;

/**
 * Referencia a un elemento concreto del diagrama.
 * <p>
 * Identifica la unidad sobre la que se adquiere la exclusion mutua. El
 * par (tipo, id) es la misma clave que arbitra la restriccion de
 * unicidad de {@code bloqueo_elemento}, de modo que el comando y el
 * bloqueo hablan del mismo objeto sin traducciones intermedias.
 */
public record Elemento(TipoElemento tipo, UUID id) {

    public static Elemento clase(UUID id) {
        return new Elemento(TipoElemento.CLASE, id);
    }

    public static Elemento relacion(UUID id) {
        return new Elemento(TipoElemento.RELACION, id);
    }
}
