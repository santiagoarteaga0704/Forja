import { useCallback, useEffect, useRef, useState } from 'react'
import { BASE, ErrorApi, api } from '../api'
import { abrirCanal, type Canal } from '../canal'
import Dictado from './Dictado'
import Foto from './Foto'
import CajaClase from '../lienzo/CajaClase'
import LineaRelacion from '../lienzo/LineaRelacion'
import { ANCHO_CLASE, aplicar, bloqueoDe, conBloqueoLiberado, conBloqueoTomado } from '../modelo'
import { SESION_ID } from '../sesion'
import PanelClase from './PanelClase'
import PanelGuia from './PanelGuia'
import type {
  Comando,
  Consejo,
  Credencial,
  DiagramaCompleto,
  DiagramaResumen,
  ProyectoVista,
  TipoOperacion,
  TipoRelacion,
} from '../tipos'

interface Props {
  credencial: Credencial
  proyecto: ProyectoVista
  diagrama: DiagramaResumen
  alVolver: () => void
}

type Seleccion = { tipo: 'CLASE' | 'RELACION'; id: string } | null
type Aviso = { clase: 'error' | 'bien' | 'informacion'; texto: string } | null

const TIPOS_DE_RELACION: TipoRelacion[] = [
  'ASOCIACION',
  'AGREGACION',
  'COMPOSICION',
  'HERENCIA',
  'REALIZACION',
  'DEPENDENCIA',
]

/**
 * El lienzo colaborativo.
 *
 * Reune las tres piezas que el resto del cliente prepara: la fotografia inicial
 * por HTTP, los cambios propios por HTTP tambien -para que el reenvio sea
 * idempotente- y los ajenos por el canal.
 *
 * Sobre el arrastre: la posicion se actualiza localmente en cada movimiento del
 * raton, pero al servidor se le manda una sola operacion al soltar. Registrar
 * cada pixel intermedio llenaria la bitacora de ruido sin aportar nada: lo que
 * interesa del movimiento es donde quedo la clase, no el recorrido.
 */
export default function Lienzo({ credencial, proyecto, diagrama, alVolver }: Props) {
  const [modelo, setModelo] = useState<DiagramaCompleto | null>(null)
  const [vista, setVista] = useState({ x: 40, y: 40, k: 1 })
  const [seleccion, setSeleccion] = useState<Seleccion>(null)
  const [conectado, setConectado] = useState(false)
  const [aviso, setAviso] = useState<Aviso>(null)
  const [relacionando, setRelacionando] = useState<TipoRelacion | null>(null)
  const [primerExtremo, setPrimerExtremo] = useState<string | null>(null)
  const [mostrandoGeneracion, setMostrandoGeneracion] = useState(false)
  const [leyendoPizarra, setLeyendoPizarra] = useState(false)
  const [dictando, setDictando] = useState(false)
  const [consejos, setConsejos] = useState<Consejo[]>([])
  // En lugar de marcar "cargando" antes de la llamada, se marca que ya hubo una
  // respuesta cuando llega: el aviso de espera solo importa la primera vez, y
  // asi el efecto no provoca un render extra en cada consulta.
  const [elAgenteYaRespondio, setElAgenteYaRespondio] = useState(false)
  const [mostrandoGuia, setMostrandoGuia] = useState(false)
  // Los avisos cerrados se recuerdan por diagrama y en el navegador: es una
  // preferencia de quien mira, no un dato del modelo.
  const [descartados, setDescartados] = useState<string[]>(() => leerDescartados(diagrama.id))

  const svgRef = useRef<SVGSVGElement>(null)
  const canalRef = useRef<Canal | null>(null)
  const arrastre = useRef<{
    claseId: string
    inicioRaton: { x: number; y: number }
    inicioClase: { x: number; y: number }
    bloqueoTomado: boolean
    movido: boolean
  } | null>(null)
  const desplazando = useRef<{ x: number; y: number; vista: { x: number; y: number } } | null>(null)

  const avisar = useCallback((clase: 'error' | 'bien' | 'informacion', texto: string) => {
    setAviso({ clase, texto })
    window.setTimeout(() => setAviso(null), 5000)
  }, [])

  const fallar = useCallback(
    (e: unknown) =>
      avisar('error', e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor'),
    [avisar],
  )

  const recargar = useCallback(() => {
    api.diagrama(diagrama.id).then(setModelo).catch(fallar)
  }, [diagrama.id, fallar])

  useEffect(recargar, [recargar])

  // ---------- Canal de avisos ----------------------------------------------

  useEffect(() => {
    const canal = abrirCanal({
      diagramaId: diagrama.id,
      sesionId: SESION_ID,
      token: credencial.token,
      alCambiarEstado: setConectado,
      alRecibir: (evento) => {
        // Se usa la forma funcional de setModelo en lugar de leer el modelo del
        // ambito: el canal vive mas que cualquier render y una captura del
        // estado quedaria vieja al primer cambio.
        switch (evento.evento) {
          case 'OPERACION':
            setModelo((actual) =>
              actual
                ? aplicar(actual, evento.contenido.tipo, evento.contenido.comando, evento.contenido.secuencia)
                : actual,
            )
            break
          case 'BLOQUEO_TOMADO':
            setModelo((actual) =>
              actual
                ? conBloqueoTomado(actual, {
                    elementoTipo: evento.contenido.elementoTipo,
                    elementoId: evento.contenido.elementoId,
                    poseedorId: evento.contenido.poseedorId,
                    poseedorNombre: evento.contenido.poseedorNombre,
                    expiraEn: '',
                  })
                : actual,
            )
            break
          case 'BLOQUEO_LIBERADO':
            setModelo((actual) =>
              actual ? conBloqueoLiberado(actual, evento.contenido.elementoId) : actual,
            )
            break
          case 'SESION_CERRADA':
            // Solto varios bloqueos de golpe: en lugar de reconstruir cual era
            // cual, se vuelve a pedir la fotografia, que es una sola consulta.
            recargar()
            break
        }
      },
    })
    canalRef.current = canal
    return () => canal.cerrar()
  }, [diagrama.id, credencial.token, recargar])

  // ---------- Agente guia ---------------------------------------------------

  // Se vuelve a consultar cuando avanza la version del diagrama: el agente
  // observa el modelo, asi que sus conclusiones cambian con cada operacion
  // aceptada, propia o ajena.
  const version = modelo?.version
  useEffect(() => {
    if (version === undefined) return
    api
      .consejos(diagrama.id, descartados)
      .then(setConsejos)
      .catch(() => setConsejos([]))
      .finally(() => setElAgenteYaRespondio(true))
  }, [diagrama.id, version, descartados])

  // Un diagrama vacio abre la guia solo: es el momento en que la persona no
  // tiene nada mas que mirar en el panel y si necesita saber como empezar.
  const yaSeAbrioLaGuia = useRef(false)
  useEffect(() => {
    if (!yaSeAbrioLaGuia.current && modelo && modelo.clases.length === 0) {
      yaSeAbrioLaGuia.current = true
      setMostrandoGuia(true)
    }
  }, [modelo])

  const descartar = (id: string) => {
    const nuevos = [...descartados, id]
    setDescartados(nuevos)
    guardarDescartados(diagrama.id, nuevos)
  }

  const senalar = (elementoId: string) => {
    const esClase = modelo?.clases.some((c) => c.id === elementoId)
    setSeleccion({ tipo: esClase ? 'CLASE' : 'RELACION', id: elementoId })
    setMostrandoGuia(false)
  }

  // ---------- Envio de cambios ---------------------------------------------

  const enviar = useCallback(
    async (tipo: TipoOperacion, comando: Comando, descripcion: string) => {
      try {
        const resultado = await api.operacion(diagrama.id, tipo, comando, SESION_ID)

        if (resultado.estado === 'RECHAZADA_POR_BLOQUEO') {
          const quien = resultado.bloqueo?.poseedorNombre ?? 'otro usuario'
          avisar('informacion', `${quien} tiene tomado ese elemento. Intenta en un momento.`)
          // El cambio no se aplico en el servidor: se vuelve a pedir el estado
          // para no quedarse con una vista que no corresponde.
          recargar()
          return false
        }

        if (resultado.estado === 'APLICADA') {
          setModelo((actual) =>
            actual ? aplicar(actual, tipo, comando as Record<string, any>, resultado.secuencia) : actual,
          )
        }
        return true
      } catch (e) {
        // Se antepone que se estaba intentando: "no se pudo agregar atributo"
        // dice mucho mas que el mensaje del servidor a secas.
        avisar(
          'error',
          `No se pudo ${descripcion}: ` +
            (e instanceof ErrorApi ? e.message : 'no responde el servidor'),
        )
        if (e instanceof ErrorApi && e.estado === 422) recargar()
        return false
      }
    },
    [diagrama.id, avisar, recargar],
  )

  // ---------- Coordenadas ---------------------------------------------------

  const aCoordenadasDelModelo = useCallback(
    (clienteX: number, clienteY: number) => {
      const caja = svgRef.current?.getBoundingClientRect()
      if (!caja) return { x: 0, y: 0 }
      return {
        x: (clienteX - caja.left - vista.x) / vista.k,
        y: (clienteY - caja.top - vista.y) / vista.k,
      }
    },
    [vista],
  )

  // ---------- Arrastre de clases -------------------------------------------

  const presionarClase = (claseId: string) => (evento: React.MouseEvent) => {
    evento.stopPropagation()
    if (!modelo) return

    if (relacionando) {
      elegirExtremo(claseId)
      return
    }

    const bloqueo = bloqueoDe(modelo, claseId)
    if (bloqueo && bloqueo.poseedorId !== credencial.usuarioId) {
      avisar('informacion', `${bloqueo.poseedorNombre} esta editando esa clase`)
      return
    }

    const clase = modelo.clases.find((c) => c.id === claseId)
    if (!clase) return

    setSeleccion({ tipo: 'CLASE', id: claseId })
    arrastre.current = {
      claseId,
      inicioRaton: { x: evento.clientX, y: evento.clientY },
      inicioClase: { x: clase.posX, y: clase.posY },
      bloqueoTomado: false,
      movido: false,
    }
  }

  useEffect(() => {
    const mover = (evento: MouseEvent) => {
      if (desplazando.current) {
        setVista((v) => ({
          ...v,
          x: desplazando.current!.vista.x + (evento.clientX - desplazando.current!.x),
          y: desplazando.current!.vista.y + (evento.clientY - desplazando.current!.y),
        }))
        return
      }

      const actual = arrastre.current
      if (!actual) return

      const dx = evento.clientX - actual.inicioRaton.x
      const dy = evento.clientY - actual.inicioRaton.y
      if (!actual.movido && Math.hypot(dx, dy) < 3) return

      // El bloqueo se pide cuando el arrastre empieza de verdad, no al hacer
      // clic: seleccionar una clase no es editarla, y pedir el turno por cada
      // clic llenaria el canal de avisos sin sentido.
      if (!actual.movido) {
        actual.movido = true
        api
          .bloquear(diagrama.id, 'CLASE', actual.claseId, SESION_ID)
          .then((resultado) => {
            if (resultado.estado === 'RECHAZADO') {
              avisar('informacion', `${resultado.poseedorNombre} tomo la clase primero`)
              arrastre.current = null
              recargar()
            } else if (arrastre.current) {
              arrastre.current.bloqueoTomado = true
            }
          })
          .catch(fallar)
      }

      setModelo((m) =>
        m
          ? {
              ...m,
              clases: m.clases.map((c) =>
                c.id === actual.claseId
                  ? {
                      ...c,
                      posX: actual.inicioClase.x + dx / vista.k,
                      posY: actual.inicioClase.y + dy / vista.k,
                    }
                  : c,
              ),
            }
          : m,
      )
    }

    const soltar = () => {
      desplazando.current = null
      const actual = arrastre.current
      arrastre.current = null
      if (!actual || !actual.movido) return

      setModelo((m) => {
        const clase = m?.clases.find((c) => c.id === actual.claseId)
        if (clase) {
          enviar(
            'CLASE_MOVER',
            { claseId: clase.id, posX: Math.round(clase.posX), posY: Math.round(clase.posY) },
            'mover',
          ).finally(() => {
            if (actual.bloqueoTomado) {
              api.liberar(diagrama.id, 'CLASE', actual.claseId, SESION_ID).catch(() => {})
            }
          })
        }
        return m
      })
    }

    window.addEventListener('mousemove', mover)
    window.addEventListener('mouseup', soltar)
    return () => {
      window.removeEventListener('mousemove', mover)
      window.removeEventListener('mouseup', soltar)
    }
  }, [vista.k, diagrama.id, enviar, avisar, fallar, recargar])

  // ---------- Desplazamiento y acercamiento --------------------------------

  const presionarFondo = (evento: React.MouseEvent) => {
    if (relacionando) {
      setRelacionando(null)
      setPrimerExtremo(null)
      return
    }
    setSeleccion(null)
    desplazando.current = { x: evento.clientX, y: evento.clientY, vista: { x: vista.x, y: vista.y } }
  }

  const rodar = (evento: React.WheelEvent) => {
    evento.preventDefault()
    const caja = svgRef.current?.getBoundingClientRect()
    if (!caja) return

    const factor = evento.deltaY < 0 ? 1.12 : 1 / 1.12
    const nuevaK = Math.min(2.5, Math.max(0.25, vista.k * factor))

    // Se acerca hacia el puntero y no hacia el centro: es lo que hace que
    // apuntar a una clase y rodar la agrande en su lugar.
    const raton = { x: evento.clientX - caja.left, y: evento.clientY - caja.top }
    setVista({
      k: nuevaK,
      x: raton.x - ((raton.x - vista.x) * nuevaK) / vista.k,
      y: raton.y - ((raton.y - vista.y) * nuevaK) / vista.k,
    })
  }

  // ---------- Acciones del modelo ------------------------------------------

  /**
   * Busca un hueco cerca del punto pedido para que la clase nueva no quede
   * tapada por otra.
   *
   * Todas las altas parten del centro de lo que se esta mirando, asi que sin
   * esto cada clase nueva caia exactamente encima de la anterior: el lienzo
   * mostraba una sola caja y las demas quedaban invisibles debajo, aunque el
   * modelo las tuviera. Se recorre en cuadricula desde el punto deseado hasta
   * dar con un sitio despejado.
   */
  const sitioLibre = (x: number, y: number) => {
    const PASO_X = 260
    const PASO_Y = 200
    const ocupado = (px: number, py: number) =>
      (modelo?.clases ?? []).some(
        (c) => Math.abs(c.posX - px) < PASO_X * 0.9 && Math.abs(c.posY - py) < PASO_Y * 0.6,
      )

    // Anillos concentricos alrededor del punto pedido: el primer hueco libre
    // es el mas cercano a donde el usuario esta mirando.
    for (let anillo = 0; anillo < 12; anillo++) {
      for (let dy = -anillo; dy <= anillo; dy++) {
        for (let dx = -anillo; dx <= anillo; dx++) {
          if (anillo > 0 && Math.abs(dx) !== anillo && Math.abs(dy) !== anillo) continue
          const px = x + dx * PASO_X
          const py = y + dy * PASO_Y
          if (!ocupado(px, py)) return { x: px, y: py }
        }
      }
    }
    return { x, y }
  }

  const crearClase = async () => {
    const nombre = window.prompt('Nombre de la clase nueva')
    if (!nombre?.trim()) return

    const caja = svgRef.current?.getBoundingClientRect()
    const centro = aCoordenadasDelModelo(
      (caja?.left ?? 0) + (caja?.width ?? 600) / 2,
      (caja?.top ?? 0) + (caja?.height ?? 400) / 3,
    )
    const sitio = sitioLibre(Math.round(centro.x - ANCHO_CLASE / 2), Math.round(centro.y))

    await enviar(
      'CLASE_CREAR',
      {
        claseId: crypto.randomUUID(),
        nombre: nombre.trim(),
        esAbstracta: false,
        posX: sitio.x,
        posY: sitio.y,
      },
      'crear clase',
    )
  }

  const elegirExtremo = async (claseId: string) => {
    if (!primerExtremo) {
      setPrimerExtremo(claseId)
      return
    }
    const tipo = relacionando!
    const origen = primerExtremo
    setPrimerExtremo(null)
    setRelacionando(null)

    const esJerarquia = tipo === 'HERENCIA' || tipo === 'REALIZACION'
    const multiplicidadOrigen = esJerarquia
      ? '1'
      : window.prompt('Multiplicidad del primer extremo', '1') ?? '1'
    const multiplicidadDestino = esJerarquia
      ? '1'
      : window.prompt('Multiplicidad del segundo extremo', '0..*') ?? '1'

    await enviar(
      'RELACION_CREAR',
      {
        relacionId: crypto.randomUUID(),
        origenId: origen,
        destinoId: claseId,
        tipo,
        multiplicidadOrigen,
        multiplicidadDestino,
      },
      'crear relacion',
    )
  }

  const eliminarSeleccion = async () => {
    if (!seleccion) return
    if (seleccion.tipo === 'CLASE') {
      const clase = modelo?.clases.find((c) => c.id === seleccion.id)
      if (!window.confirm(`Eliminar la clase ${clase?.nombre} y sus relaciones?`)) return
      if (await enviar('CLASE_ELIMINAR', { claseId: seleccion.id }, 'eliminar clase')) {
        setSeleccion(null)
      }
    } else {
      if (await enviar('RELACION_ELIMINAR', { relacionId: seleccion.id }, 'eliminar relacion')) {
        setSeleccion(null)
      }
    }
  }

  // ---------- Intercambio --------------------------------------------------

  const exportarXmi = async () => {
    try {
      await api.descargar(
        `/api/diagramas/${diagrama.id}/xmi`,
        `${diagrama.nombre.replace(/\s+/g, '-').toLowerCase()}.xmi`,
      )
      avisar('bien', 'XMI exportado. Abrilo en Enterprise Architect para comprobarlo.')
    } catch (e) {
      fallar(e)
    }
  }

  const importarXmi = async (archivo: File) => {
    try {
      const resumen = await api.importarXmi(diagrama.id, await archivo.text(), SESION_ID)
      recargar()
      if (resumen.problemas.length) {
        avisar(
          'informacion',
          `Importadas ${resumen.aplicadas} de ${resumen.comandosLeidos}. ` +
            `Rechazadas: ${resumen.problemas[0]}`,
        )
      } else {
        avisar('bien', `Importado: ${resumen.aplicadas} cambios aplicados`)
      }
    } catch (e) {
      fallar(e)
    }
  }

  const descargarProyecto = async () => {
    try {
      await api.descargar(`/api/diagramas/${diagrama.id}/generacion/zip`, 'proyecto-generado.zip')
      avisar('bien', 'Proyecto Spring Boot descargado')
    } catch (e) {
      fallar(e)
    }
  }

  // ---------- Dibujo -------------------------------------------------------

  const claseSeleccionada =
    seleccion?.tipo === 'CLASE' ? modelo?.clases.find((c) => c.id === seleccion.id) : undefined

  return (
    <div className="aplicacion">
      <header className="barra">
        <button onClick={alVolver}>← Proyectos</button>
        <div className="separador" />
        <span className="titulo">{diagrama.nombre}</span>
        <span className="sutil">
          {proyecto.nombre} · version {modelo?.version ?? '—'}
        </span>
        <div className="crece" />

        <button onClick={crearClase}>+ Clase</button>
        <select
          value={relacionando ?? ''}
          onChange={(e) => {
            setRelacionando((e.target.value || null) as TipoRelacion | null)
            setPrimerExtremo(null)
          }}
          style={{ width: 150 }}
        >
          <option value="">Relacionar...</option>
          {TIPOS_DE_RELACION.map((tipo) => (
            <option key={tipo} value={tipo}>
              {tipo.charAt(0) + tipo.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
        <button onClick={eliminarSeleccion} className="peligro" disabled={!seleccion}>
          Eliminar
        </button>

        <div className="separador" />
        <button className={dictando ? 'activo' : ''} onClick={() => setDictando(!dictando)}>
          Dictar
        </button>
        <button onClick={() => setLeyendoPizarra(true)}>Leer pizarra</button>
        <button
          className={mostrandoGuia ? 'activo' : ''}
          onClick={() => setMostrandoGuia(!mostrandoGuia)}
        >
          Guia{consejos.length > 0 && <span className="contador">{consejos.length}</span>}
        </button>

        <div className="separador" />
        <button onClick={() => setMostrandoGeneracion(true)}>Generar backend</button>
        <button onClick={exportarXmi}>Exportar XMI</button>
        <label
          style={{
            margin: 0,
            textTransform: 'none',
            letterSpacing: 0,
            fontSize: 13,
            color: 'var(--texto)',
          }}
        >
          <span
            style={{
              border: '1px solid var(--borde)',
              background: 'var(--superficie-alta)',
              borderRadius: 4,
              padding: '6px 11px',
              cursor: 'pointer',
              display: 'inline-block',
            }}
          >
            Importar XMI
          </span>
          <input
            type="file"
            accept=".xmi,.xml"
            style={{ display: 'none' }}
            onChange={(e) => {
              const archivo = e.target.files?.[0]
              if (archivo) importarXmi(archivo)
              e.target.value = ''
            }}
          />
        </label>

        <div className="separador" />
        <span className={`senal${conectado ? ' viva' : ''}`}>
          <span className="punto" />
          {conectado ? 'En vivo' : 'Sin canal'}
        </span>
      </header>

      {aviso && (
        <div style={{ padding: '8px 14px 0' }}>
          <div className={`mensaje ${aviso.clase}`} style={{ marginBottom: 0 }}>
            {aviso.texto}
          </div>
        </div>
      )}

      <div className="area">
        <div className={`lienzo${relacionando ? ' relacionando' : ''}`}>
          <svg ref={svgRef} onMouseDown={presionarFondo} onWheel={rodar}>
            <defs>
              <pattern id="cuadricula" width={26} height={26} patternUnits="userSpaceOnUse">
                <circle cx={1} cy={1} r={1} fill="#1d2532" />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#cuadricula)" />

            <g transform={`translate(${vista.x}, ${vista.y}) scale(${vista.k})`}>
              {modelo?.relaciones.map((relacion) => {
                const origen = modelo.clases.find((c) => c.id === relacion.origenId)
                const destino = modelo.clases.find((c) => c.id === relacion.destinoId)
                if (!origen || !destino) return null
                return (
                  <LineaRelacion
                    key={relacion.id}
                    relacion={relacion}
                    origen={origen}
                    destino={destino}
                    seleccionada={seleccion?.tipo === 'RELACION' && seleccion.id === relacion.id}
                    alElegir={(evento) => {
                      evento.stopPropagation()
                      setSeleccion({ tipo: 'RELACION', id: relacion.id })
                    }}
                  />
                )
              })}

              {modelo?.clases.map((clase) => {
                const bloqueo = bloqueoDe(modelo, clase.id)
                return (
                  <CajaClase
                    key={clase.id}
                    clase={clase}
                    seleccionada={seleccion?.tipo === 'CLASE' && seleccion.id === clase.id}
                    bloqueo={bloqueo}
                    propio={bloqueo?.poseedorId === credencial.usuarioId}
                    senalada={primerExtremo === clase.id}
                    alPresionar={presionarClase(clase.id)}
                  />
                )
              })}
            </g>
          </svg>

          {dictando && (
            <Dictado
              diagramaId={diagrama.id}
              alAplicar={recargar}
              alCerrar={() => setDictando(false)}
            />
          )}

          {relacionando && !dictando && (
            <div className="pista">
              {primerExtremo
                ? 'Ahora hace clic en la segunda clase'
                : `Hace clic en la primera clase de la ${relacionando.toLowerCase()}`}
            </div>
          )}
          {modelo && modelo.clases.length === 0 && !relacionando && !dictando && (
            <div className="pista">Empeza creando una clase con el boton de arriba</div>
          )}
        </div>

        <aside className="panel">
          {mostrandoGuia ? (
            <PanelGuia
              consejos={consejos}
              cargando={!elAgenteYaRespondio}
              alDescartar={descartar}
              alSenalar={senalar}
            />
          ) : (
          <PanelClase
            clase={claseSeleccionada}
            relacion={
              seleccion?.tipo === 'RELACION'
                ? modelo?.relaciones.find((r) => r.id === seleccion.id)
                : undefined
            }
            clases={modelo?.clases ?? []}
            bloqueo={claseSeleccionada ? bloqueoDe(modelo!, claseSeleccionada.id) : undefined}
            usuarioId={credencial.usuarioId}
            alEnviar={enviar}
          />
          )}
        </aside>
      </div>

      {leyendoPizarra && (
        <Foto
          diagramaId={diagrama.id}
          alAplicar={recargar}
          alCerrar={() => setLeyendoPizarra(false)}
        />
      )}

      {mostrandoGeneracion && (
        <DialogoGeneracion
          diagramaId={diagrama.id}
          alCerrar={() => setMostrandoGeneracion(false)}
          alDescargar={descargarProyecto}
          alFallar={fallar}
        />
      )}
    </div>
  )
}

// ---------- Avisos cerrados -------------------------------------------------

const CLAVE_DESCARTADOS = 'forja.guia.descartados.'

function leerDescartados(diagramaId: string): string[] {
  try {
    const guardado = localStorage.getItem(CLAVE_DESCARTADOS + diagramaId)
    return guardado ? (JSON.parse(guardado) as string[]) : []
  } catch {
    return []
  }
}

function guardarDescartados(diagramaId: string, ids: string[]) {
  try {
    localStorage.setItem(CLAVE_DESCARTADOS + diagramaId, JSON.stringify(ids))
  } catch {
    // Si el navegador no deja guardar, los avisos vuelven a aparecer al
    // recargar. Es una molestia menor y no vale interrumpir por eso.
  }
}

// ---------- Generacion ------------------------------------------------------

/**
 * Muestra lo que produjo el generador antes de descargarlo.
 *
 * Poder leer el codigo en pantalla es lo que convierte la generacion en algo
 * demostrable: quien evalua el diagrama ve que salio de el sin descomprimir
 * nada.
 */
function DialogoGeneracion({
  diagramaId,
  alCerrar,
  alDescargar,
  alFallar,
}: {
  diagramaId: string
  alCerrar: () => void
  alDescargar: () => void
  alFallar: (e: unknown) => void
}) {
  const [rutas, setRutas] = useState<string[]>([])
  const [elegida, setElegida] = useState<string | null>(null)
  const [contenido, setContenido] = useState('')
  const [paquete, setPaquete] = useState('')

  const cargar = useCallback(() => {
    api
      .generacion(diagramaId, paquete || undefined)
      .then((resumen) => {
        setRutas(resumen.rutas)
        const primera = resumen.rutas.find((r) => r.includes('/dominio/')) ?? resumen.rutas[0]
        if (primera) elegir(primera)
      })
      .catch(alFallar)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [diagramaId, paquete])

  const elegir = (ruta: string) => {
    setElegida(ruta)
    api
      .archivoGenerado(diagramaId, ruta, paquete || undefined)
      .then(setContenido)
      .catch(alFallar)
  }

  useEffect(cargar, [cargar])

  return (
    <div className="telon" onClick={alCerrar}>
      <div className="dialogo" style={{ maxWidth: 760 }} onClick={(e) => e.stopPropagation()}>
        <h2>Backend generado desde el diagrama</h2>

        <div className="formulario-linea">
          <input
            placeholder="Paquete base (por omision com.ejemplo.<diagrama>)"
            value={paquete}
            onChange={(e) => setPaquete(e.target.value)}
          />
          <button onClick={cargar}>Regenerar</button>
        </div>

        <p className="sutil" style={{ marginTop: 0 }}>
          {rutas.length} archivos: entidad JPA, repositorio, servicio y controlador REST por cada
          clase concreta.
        </p>

        <div className="arbol-archivos">
          {rutas.map((ruta) => (
            <button
              key={ruta}
              className={elegida === ruta ? 'elegido' : ''}
              onClick={() => elegir(ruta)}
            >
              {ruta.replace(/^src\/main\/java\//, '')}
            </button>
          ))}
        </div>

        {contenido && <pre className="codigo">{contenido}</pre>}

        <div className="botonera">
          <button onClick={alCerrar}>Cerrar</button>
          <button className="principal" onClick={alDescargar}>
            Descargar el proyecto
          </button>
        </div>
      </div>
    </div>
  )
}

export { BASE }
