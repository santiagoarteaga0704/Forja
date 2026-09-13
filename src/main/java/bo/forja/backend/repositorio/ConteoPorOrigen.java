package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.OrigenOperacion;

/**
 * Cuantos cambios entraron por cada via.
 * <p>
 * Se consulta agrupado y no trayendo la bitacora entera porque lo unico que se
 * necesita es el recuento: un diagrama con miles de operaciones no tiene por que
 * cargarse en memoria para saber si alguien ya probo el dictado.
 */
public record ConteoPorOrigen(OrigenOperacion origen, long cantidad) {
}
