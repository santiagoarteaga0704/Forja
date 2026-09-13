import type {
  BloqueoVista,
  ClaseVista,
  DiagramaCompleto,
  TipoOperacion,
} from './tipos'

/**
 * Aplica un comando sobre la copia local del diagrama.
 *
 * Es un espejo del aplicador del servidor y esa duplicacion es deliberada, no
 * un descuido: sin ella cada trazo tendria que esperar la respuesta de la red
 * antes de dibujarse, y arrastrar una caja se sentiria como escribir en un
 * documento remoto. Lo que NO se duplica son las validaciones: aqui no se
 * comprueba nada. El servidor es la autoridad, y si algo llega a divergir la
 * solucion es volver a pedir la fotografia completa, no discutir con el.
 *
 * Todas las funciones devuelven un objeto nuevo: React necesita la referencia
 * distinta para redibujar, y trabajar sin mutar evita el error clasico de
 * cambiar el estado y que la pantalla no se entere.
 */
export function aplicar(
  modelo: DiagramaCompleto,
  tipo: TipoOperacion,
  comando: Record<string, any>,
  secuencia?: number,
): DiagramaCompleto {
  const conVersion = (cambios: Partial<DiagramaCompleto>): DiagramaCompleto => ({
    ...modelo,
    ...cambios,
    version: secuencia ?? modelo.version,
  })

  switch (tipo) {
    case 'CLASE_CREAR': {
      if (modelo.clases.some((c) => c.id === comando.claseId)) return modelo
      const nueva: ClaseVista = {
        id: comando.claseId,
        nombre: comando.nombre,
        estereotipo: comando.estereotipo ?? null,
        esAbstracta: !!comando.esAbstracta,
        posX: comando.posX ?? 0,
        posY: comando.posY ?? 0,
        ancho: 200,
        alto: 120,
        atributos: [],
        metodos: [],
      }
      return conVersion({ clases: [...modelo.clases, nueva] })
    }

    case 'CLASE_RENOMBRAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({ ...c, nombre: comando.nombre })),
      })

    case 'CLASE_MARCAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          estereotipo: comando.estereotipo || null,
          esAbstracta: !!comando.esAbstracta,
        })),
      })

    case 'CLASE_MOVER':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          posX: comando.posX,
          posY: comando.posY,
        })),
      })

    case 'CLASE_ELIMINAR':
      return conVersion({
        clases: modelo.clases.filter((c) => c.id !== comando.claseId),
        // Las relaciones que tocaban la clase desaparecen con ella, igual que
        // hace el servidor.
        relaciones: modelo.relaciones.filter(
          (r) => r.origenId !== comando.claseId && r.destinoId !== comando.claseId,
        ),
      })

    case 'ATRIBUTO_AGREGAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          atributos: [
            ...c.atributos,
            {
              id: comando.atributoId,
              nombre: comando.nombre,
              tipo: comando.tipo,
              visibilidad: comando.visibilidad ?? 'PRIVADO',
              esIdentificador: !!comando.esIdentificador,
              esRequerido: !!comando.esRequerido,
              esUnico: !!comando.esUnico,
              longitud: comando.longitud ?? null,
              orden: c.atributos.length,
            },
          ],
        })),
      })

    case 'ATRIBUTO_ELIMINAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          atributos: c.atributos.filter((a) => a.id !== comando.atributoId),
        })),
      })

    case 'METODO_AGREGAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          metodos: [
            ...c.metodos,
            {
              id: comando.metodoId,
              nombre: comando.nombre,
              tipoRetorno: comando.tipoRetorno || 'void',
              visibilidad: comando.visibilidad ?? 'PUBLICO',
              esAbstracto: !!comando.esAbstracto,
              esEstatico: !!comando.esEstatico,
              orden: c.metodos.length,
            },
          ],
        })),
      })

    case 'METODO_ELIMINAR':
      return conVersion({
        clases: cambiarClase(modelo, comando.claseId, (c) => ({
          ...c,
          metodos: c.metodos.filter((m) => m.id !== comando.metodoId),
        })),
      })

    case 'RELACION_CREAR': {
      if (modelo.relaciones.some((r) => r.id === comando.relacionId)) return modelo
      return conVersion({
        relaciones: [
          ...modelo.relaciones,
          {
            id: comando.relacionId,
            origenId: comando.origenId,
            destinoId: comando.destinoId,
            tipo: comando.tipo,
            multiplicidadOrigen: comando.multiplicidadOrigen || '1',
            multiplicidadDestino: comando.multiplicidadDestino || '1',
            rolOrigen: comando.rolOrigen ?? null,
            rolDestino: comando.rolDestino ?? null,
            etiqueta: comando.etiqueta ?? null,
          },
        ],
      })
    }

    case 'RELACION_ELIMINAR':
      return conVersion({
        relaciones: modelo.relaciones.filter((r) => r.id !== comando.relacionId),
      })

    default:
      return modelo
  }
}

function cambiarClase(
  modelo: DiagramaCompleto,
  claseId: string,
  cambio: (clase: ClaseVista) => ClaseVista,
): ClaseVista[] {
  return modelo.clases.map((c) => (c.id === claseId ? cambio(c) : c))
}

// ---------- Bloqueos --------------------------------------------------------

export function conBloqueoTomado(
  modelo: DiagramaCompleto,
  bloqueo: BloqueoVista,
): DiagramaCompleto {
  const sinElAnterior = modelo.bloqueos.filter(
    (b) => !(b.elementoTipo === bloqueo.elementoTipo && b.elementoId === bloqueo.elementoId),
  )
  return { ...modelo, bloqueos: [...sinElAnterior, bloqueo] }
}

export function conBloqueoLiberado(
  modelo: DiagramaCompleto,
  elementoId: string,
): DiagramaCompleto {
  return { ...modelo, bloqueos: modelo.bloqueos.filter((b) => b.elementoId !== elementoId) }
}

/** Quien retiene el elemento, si alguien lo retiene. */
export function bloqueoDe(modelo: DiagramaCompleto, elementoId: string) {
  return modelo.bloqueos.find((b) => b.elementoId === elementoId)
}

// ---------- Geometria del lienzo -------------------------------------------

/**
 * Alto que ocupa una clase segun lo que contiene. Se calcula y no se guarda
 * porque depende de cuantos atributos y metodos tenga en cada momento.
 */
export function altoDe(clase: ClaseVista) {
  const cabecera = clase.estereotipo ? 44 : 30
  const filas = clase.atributos.length + clase.metodos.length
  const separador = clase.metodos.length > 0 ? 1 : 0
  return cabecera + 10 + filas * 18 + separador * 6 + 10
}

export const ANCHO_CLASE = 220
