import { useCallback, useEffect, useRef, useState } from 'react'
import { BASE, ErrorApi, api } from '../api'
import { abrirCanal, type Canal } from '../canal'
import Dictado from './Dictado'
import Foto from './Foto'
import {
  GlifoRelacion,
  IconoBajar,
  IconoBorrar,
  IconoCamara,
  IconoClase,
  IconoDescargar,
  IconoGenerar,
  IconoGuia,
  IconoIntercambio,
  IconoMicrofono,
  IconoSubir,
  IconoVolver,
} from '../iconos'
import { useGuia } from '../guia'
import { RELACIONES } from '../relaciones'
import CajaClase from '../lienzo/CajaClase'
import LineaRelacion from '../lienzo/LineaRelacion'
import { ANCHO_CLASE, aplicar, bloqueoDe, conBloqueoLiberado, conBloqueoTomado } from '../modelo'
import { SESION_ID } from '../sesion'
import PanelClase from './PanelClase'
import Guia from './Guia'
import type {
  Comando,
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

/** Una relacion a medio trazar: faltan las multiplicidades de sus extremos. */
type RelacionPendiente = { tipo: TipoRelacion; origenId: string; destinoId: string }

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
  const [mostrandoGuia, setMostrandoGuia] = useState(false)

  /*
   * Lo que antes pedian `window.prompt` y `window.confirm`.
   *
   * Los cuadros del navegador no se pueden vestir, aparecen fuera de la
   * ventana y frenan el hilo mientras estan abiertos: no se puede mostrar el
   * diagrama detras ni proponer un valor razonable con contexto. Cada uno pasa
   * a un dialogo propio.
   */
  const [creandoClase, setCreandoClase] = useState(false)
  const [relacionPendiente, setRelacionPendiente] = useState<RelacionPendiente | null>(null)
  const [confirmandoBorrado, setConfirmandoBorrado] = useState<string | null>(null)

  const [menuRelacion, setMenuRelacion] = useState(false)
  const [menuXmi, setMenuXmi] = useState(false)

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
  const { guia, descartar } = useGuia(diagrama.id, version)

  // Un diagrama vacio abre la guia solo: es el momento en que la persona no
  // tiene nada mas que mirar en el panel y si necesita saber como empezar.
  const yaSeAbrioLaGuia = useRef(false)
  useEffect(() => {
    if (!yaSeAbrioLaGuia.current && modelo && modelo.clases.length === 0) {
      yaSeAbrioLaGuia.current = true
      setMostrandoGuia(true)
    }
  }, [modelo])

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
    // El mismo paso que usa `Cuadricula` en el servidor: una clase creada a
    // mano y una dictada tienen que caer en la misma disposicion, o el modelo
    // queda con dos rejillas superpuestas.
    const PASO_X = 380
    const PASO_Y = 260
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

  const crearClase = async (nombre: string) => {
    setCreandoClase(false)
    if (!nombre.trim()) return

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
    const origenId = primerExtremo
    setPrimerExtremo(null)
    setRelacionando(null)

    // La herencia y la realizacion no llevan multiplicidad: preguntarla seria
    // pedir un dato que UML no admite en esos extremos.
    if (tipo === 'HERENCIA' || tipo === 'REALIZACION') {
      await trazarRelacion({ tipo, origenId, destinoId: claseId }, '1', '1')
      return
    }
    setRelacionPendiente({ tipo, origenId, destinoId: claseId })
  }

  const trazarRelacion = async (
    pendiente: RelacionPendiente,
    multiplicidadOrigen: string,
    multiplicidadDestino: string,
  ) => {
    setRelacionPendiente(null)
    await enviar(
      'RELACION_CREAR',
      {
        relacionId: crypto.randomUUID(),
        origenId: pendiente.origenId,
        destinoId: pendiente.destinoId,
        tipo: pendiente.tipo,
        multiplicidadOrigen,
        multiplicidadDestino,
      },
      'crear relacion',
    )
  }

  const pedirEliminar = () => {
    if (!seleccion) return
    // Borrar una relacion se deshace volviendola a trazar; borrar una clase se
    // lleva ademas sus atributos, sus operaciones y todo lo que la tocaba. Solo
    // eso justifica una pregunta.
    if (seleccion.tipo === 'CLASE') {
      setConfirmandoBorrado(seleccion.id)
    } else {
      void eliminarRelacion(seleccion.id)
    }
  }

  const eliminarRelacion = async (relacionId: string) => {
    if (await enviar('RELACION_ELIMINAR', { relacionId }, 'eliminar relacion')) {
      setSeleccion(null)
    }
  }

  const eliminarClase = async (claseId: string) => {
    setConfirmandoBorrado(null)
    if (await enviar('CLASE_ELIMINAR', { claseId }, 'eliminar clase')) {
      setSeleccion(null)
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
      {/*
        El riel de instrumentos.
        Cada grupo responde una pregunta distinta -con que dibujo, por que otra
        via puedo entrar el modelo, quien me ayuda, que sale de aca- y por eso
        estan separados. Diez botones iguales en fila obligan a leerlos todos
        para encontrar uno.
      */}
      <header className="barra">
        <button className="desnudo" onClick={alVolver} title="Volver a los proyectos">
          <IconoVolver />
          Proyectos
        </button>
        <div className="separador" />
        <div className="identidad">
          <span className="titulo">{diagrama.nombre}</span>
          <span className="procedencia">
            {proyecto.nombre}, versión {modelo?.version ?? '—'}
          </span>
        </div>
        <div className="crece" />

        <div className="grupo">
          <button onClick={() => setCreandoClase(true)}>
            <IconoClase />
            Clase
          </button>

          <Desplegable
            abierto={menuRelacion}
            alCambiar={setMenuRelacion}
            disparador={
              <button
                className={relacionando ? 'activo' : ''}
                aria-haspopup="menu"
                aria-expanded={menuRelacion}
              >
                <GlifoRelacion tipo={relacionando ?? 'ASOCIACION'} ancho={30} />
                {relacionando
                  ? RELACIONES.find((r) => r.tipo === relacionando)!.nombre
                  : 'Relación'}
                <IconoBajar tamano={13} />
              </button>
            }
          >
            {(cerrar) => (
              <>
                {RELACIONES.map(({ tipo, nombre, glosa }) => (
                  <button
                    key={tipo}
                    className={`opcion opcion-relacion${relacionando === tipo ? ' elegida' : ''}`}
                    onClick={() => {
                      setRelacionando(relacionando === tipo ? null : tipo)
                      setPrimerExtremo(null)
                      cerrar()
                    }}
                  >
                    <GlifoRelacion tipo={tipo} />
                    <span>
                      {nombre}
                      <span className="glosa">{glosa}</span>
                    </span>
                  </button>
                ))}
              </>
            )}
          </Desplegable>

          <button
            onClick={pedirEliminar}
            className="peligro"
            disabled={!seleccion}
            title="Borrar lo seleccionado"
          >
            <IconoBorrar />
            Borrar
          </button>
        </div>

        {/* Las otras dos vias de entrada, juntas: son alternativas entre si. */}
        <div className="grupo">
          <button
            aria-pressed={dictando}
            onClick={() => setDictando(!dictando)}
            title="Construir el diagrama hablando"
          >
            <IconoMicrofono />
            Dictar
          </button>
          <button onClick={() => setLeyendoPizarra(true)} title="Leer la foto de una pizarra">
            <IconoCamara />
            Pizarra
          </button>
        </div>

        <button
          aria-pressed={mostrandoGuia}
          onClick={() => setMostrandoGuia(!mostrandoGuia)}
          title="Consejos del agente guía sobre este diagrama"
        >
          <IconoGuia />
          Guía
          {(guia?.consejos.length ?? 0) > 0 && (
            <span className="contador">{guia!.consejos.length}</span>
          )}
        </button>

        <div className="separador" />

        <div className="grupo">
          <button onClick={() => setMostrandoGeneracion(true)}>
            <IconoGenerar />
            Generar backend
          </button>

          <Desplegable
            abierto={menuXmi}
            alCambiar={setMenuXmi}
            disparador={
              <button aria-haspopup="menu" aria-expanded={menuXmi} title="Intercambio con XMI 2.5.1">
                <IconoIntercambio />
                XMI
                <IconoBajar tamano={13} />
              </button>
            }
          >
            {(cerrar) => (
              <>
                <button
                  onClick={() => {
                    cerrar()
                    void exportarXmi()
                  }}
                >
                  <IconoDescargar />
                  <span>
                    Exportar
                    <span className="glosa">Se abre en Enterprise Architect</span>
                  </span>
                </button>
                <label className="opcion" style={{ marginBottom: 0, cursor: 'pointer' }}>
                  <IconoSubir />
                  <span>
                    Importar…
                    <span className="glosa">Desde EA o de otra herramienta</span>
                  </span>
                  <input
                    type="file"
                    accept=".xmi,.xml"
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      const archivo = e.target.files?.[0]
                      cerrar()
                      if (archivo) void importarXmi(archivo)
                      e.target.value = ''
                    }}
                  />
                </label>
              </>
            )}
          </Desplegable>
        </div>

        <span className={`senal${conectado ? ' viva' : ''}`} title={
          conectado
            ? 'Lo que cambien los demás aparece solo'
            : 'Sin canal: los cambios ajenos no llegan hasta recargar'
        }>
          <span className="punto" />
          {conectado ? 'En vivo' : 'Sin canal'}
        </span>
      </header>

      {aviso && (
        <div className="cinta-aviso">
          <div className={`mensaje ${aviso.clase}`}>{aviso.texto}</div>
        </div>
      )}

      <div className="area">
        <div className={`lienzo${relacionando ? ' relacionando' : ''}`}>
          <svg ref={svgRef} onMouseDown={presionarFondo} onWheel={rodar}>
            {/*
              La cuadricula del papel.

              Se transforma con la vista -misma traslacion y misma escala que el
              modelo- porque esta SOBRE la hoja: si quedara fija, acercarse
              haria que las clases se despegaran del cuadriculado y el lienzo
              dejaria de leerse como una superficie. Van dos pasos, fino y
              grueso, como en un block de ingenieria: el fino da la sensacion de
              escala y el grueso deja medir distancias de un vistazo.
            */}
            <defs>
              <pattern
                id="reticula"
                width={60}
                height={60}
                patternUnits="userSpaceOnUse"
                patternTransform={`translate(${vista.x}, ${vista.y}) scale(${vista.k})`}
              >
                {/* Los dos pasos van en el MISMO patron. Anidar un patron
                    dentro de otro aplicaria la transformacion de la vista dos
                    veces y el cuadriculado se despegaria del modelo. */}
                <path
                  d="M12 0v60M24 0v60M36 0v60M48 0v60M0 12h60M0 24h60M0 36h60M0 48h60"
                  fill="none"
                  stroke="var(--reticula)"
                  strokeWidth={1}
                />
                <path d="M60 0H0v60" fill="none" stroke="var(--reticula-mayor)" strokeWidth={1} />
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#reticula)" />

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
              {primerExtremo ? (
                <>
                  Ahora hacé clic en la clase del <strong>otro extremo</strong>.
                </>
              ) : (
                <>
                  Hacé clic en la clase donde <strong>empieza</strong> la{' '}
                  {RELACIONES.find((r) => r.tipo === relacionando)!.nombre.toLowerCase()}.
                </>
              )}
            </div>
          )}
          {modelo && modelo.clases.length === 0 && !relacionando && !dictando && (
            <div className="pista">
              La hoja está vacía. Empezá con <strong>Clase</strong>, o dictá el modelo y dejá que
              FORJA lo dibuje.
            </div>
          )}
        </div>

        {/* El panel de la clase no se reemplaza por el agente: el agente se
            superpone. Asi se puede leer un consejo sobre una clase con esa
            misma clase abierta al lado. */}
        <aside className="panel">
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
        </aside>
      </div>

      {creandoClase && (
        <DialogoNombre
          titulo="Clase nueva"
          bajada="En UML el nombre va en singular y con mayúscula inicial: Paciente, no pacientes."
          etiqueta="Nombre de la clase"
          marcador="Paciente"
          aceptar="Crear la clase"
          alAceptar={crearClase}
          alCerrar={() => setCreandoClase(false)}
        />
      )}

      {relacionPendiente && (
        <DialogoMultiplicidades
          pendiente={relacionPendiente}
          nombreDe={(id) => modelo?.clases.find((c) => c.id === id)?.nombre ?? '?'}
          alTrazar={trazarRelacion}
          alCerrar={() => setRelacionPendiente(null)}
        />
      )}

      {confirmandoBorrado && (
        <DialogoConfirmar
          titulo={`¿Borrar ${modelo?.clases.find((c) => c.id === confirmandoBorrado)?.nombre ?? 'la clase'}?`}
          cuerpo="Se van con ella sus atributos, sus operaciones y todas las relaciones que la tocan. No se puede deshacer."
          aceptar="Borrar la clase"
          alAceptar={() => eliminarClase(confirmandoBorrado)}
          alCerrar={() => setConfirmandoBorrado(null)}
        />
      )}

      {mostrandoGuia && (
        <Guia
          guia={guia}
          enUnDiagrama
          alDescartar={descartar}
          alCerrar={() => setMostrandoGuia(false)}
          alSenalar={senalar}
        />
      )}

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

// ---------- Desplegable -----------------------------------------------------

/**
 * Un menu que cuelga de un boton.
 *
 * Se cierra al hacer clic afuera y con Escape, que es lo que cualquiera espera
 * y lo que un `<select>` daba gratis. Se cambio el `<select>` igual porque no
 * admite dibujos adentro, y el selector de relaciones necesita mostrar el
 * conector de UML en vez de su nombre.
 *
 * El estado vive afuera: la barra necesita saber si el menu esta abierto para
 * marcar `aria-expanded` en el disparador.
 */
function Desplegable({
  abierto,
  alCambiar,
  disparador,
  children,
}: {
  abierto: boolean
  alCambiar: (abierto: boolean) => void
  disparador: React.ReactElement
  children: (cerrar: () => void) => React.ReactNode
}) {
  const caja = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return

    const afuera = (evento: MouseEvent) => {
      if (!caja.current?.contains(evento.target as Node)) alCambiar(false)
    }
    const escape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') alCambiar(false)
    }

    // En la fase de captura: sin esto, el clic sobre otro disparador lo abriria
    // y este cierre lo volveria a cerrar en el mismo gesto.
    document.addEventListener('mousedown', afuera, true)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', afuera, true)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto, alCambiar])

  return (
    <div className="desplegable" ref={caja}>
      <div onClick={() => alCambiar(!abierto)}>{disparador}</div>
      {abierto && (
        <div className="menu izquierda" role="menu">
          {children(() => alCambiar(false))}
        </div>
      )}
    </div>
  )
}

// ---------- Dialogos --------------------------------------------------------

/**
 * Envoltura comun de los dialogos: telon, Escape y foco.
 *
 * El foco se lleva adentro al abrir y Escape cierra. Un dialogo que deja el
 * foco detras obliga a usar el raton para algo que se estaba haciendo con el
 * teclado, y es de las cosas que mas se notan al probar la herramienta en
 * serio.
 */
function Dialogo({
  ancho,
  alCerrar,
  children,
}: {
  ancho?: number
  alCerrar: () => void
  children: React.ReactNode
}) {
  const caja = useRef<HTMLDivElement>(null)

  useEffect(() => {
    caja.current?.querySelector<HTMLElement>('input, button')?.focus()
    const escape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') alCerrar()
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [alCerrar])

  return (
    <div className="telon" onMouseDown={alCerrar}>
      <div
        className="dialogo"
        ref={caja}
        role="dialog"
        aria-modal="true"
        style={ancho ? { maxWidth: ancho } : undefined}
        onMouseDown={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  )
}

/** Pide un solo texto. Reemplaza a `window.prompt`. */
function DialogoNombre({
  titulo,
  bajada,
  etiqueta,
  marcador,
  aceptar,
  alAceptar,
  alCerrar,
}: {
  titulo: string
  bajada?: string
  etiqueta: string
  marcador?: string
  aceptar: string
  alAceptar: (valor: string) => void
  alCerrar: () => void
}) {
  const [valor, setValor] = useState('')

  return (
    <Dialogo ancho={400} alCerrar={alCerrar}>
      <h2>{titulo}</h2>
      {bajada && <p className="bajada">{bajada}</p>}
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (valor.trim()) alAceptar(valor.trim())
        }}
      >
        <label htmlFor="valor-dialogo">{etiqueta}</label>
        <input
          id="valor-dialogo"
          value={valor}
          placeholder={marcador}
          onChange={(e) => setValor(e.target.value)}
          autoFocus
        />
        <div className="botonera">
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button className="principal" type="submit" disabled={!valor.trim()}>
            {aceptar}
          </button>
        </div>
      </form>
    </Dialogo>
  )
}

/**
 * Las multiplicidades de una relacion recien trazada.
 *
 * Las dos se piden juntas y no una tras otra, que es lo que hacian los dos
 * `window.prompt` encadenados: son un solo dato -como se lee la relacion- y
 * hay que verlas al lado para elegirlas bien. El dialogo dice ademas la frase
 * que resulta, porque "1 a 0..*" no se interpreta igual leido de izquierda a
 * derecha que de derecha a izquierda, y ahi es donde se equivoca todo el mundo.
 */
function DialogoMultiplicidades({
  pendiente,
  nombreDe,
  alTrazar,
  alCerrar,
}: {
  pendiente: RelacionPendiente
  nombreDe: (id: string) => string
  alTrazar: (pendiente: RelacionPendiente, origen: string, destino: string) => void
  alCerrar: () => void
}) {
  const [origen, setOrigen] = useState('1')
  const [destino, setDestino] = useState('0..*')

  const nombreOrigen = nombreDe(pendiente.origenId)
  const nombreDestino = nombreDe(pendiente.destinoId)
  const relacion = RELACIONES.find((r) => r.tipo === pendiente.tipo)!

  return (
    <Dialogo ancho={430} alCerrar={alCerrar}>
      <h2>
        {nombreOrigen} y {nombreDestino}
      </h2>
      <p className="bajada">
        {relacion.nombre}: {relacion.glosa.toLowerCase()}.
      </p>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          alTrazar(pendiente, origen.trim() || '1', destino.trim() || '1')
        }}
      >
        <div className="par">
          <div>
            <label htmlFor="mult-origen">Del lado de {nombreOrigen}</label>
            <input
              id="mult-origen"
              value={origen}
              onChange={(e) => setOrigen(e.target.value)}
              autoFocus
            />
          </div>
          <div>
            <label htmlFor="mult-destino">Del lado de {nombreDestino}</label>
            <input
              id="mult-destino"
              value={destino}
              onChange={(e) => setDestino(e.target.value)}
            />
          </div>
        </div>

        <p className="bajada" style={{ margin: '14px 0 0' }}>
          Se lee: cada <strong>{nombreOrigen}</strong> se relaciona con{' '}
          <strong>{destino.trim() || '1'}</strong> {nombreDestino}.
        </p>

        <div className="botonera">
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button className="principal" type="submit">
            Trazar la relación
          </button>
        </div>
      </form>
    </Dialogo>
  )
}

/** Confirma algo que no se puede deshacer. Reemplaza a `window.confirm`. */
function DialogoConfirmar({
  titulo,
  cuerpo,
  aceptar,
  alAceptar,
  alCerrar,
}: {
  titulo: string
  cuerpo: string
  aceptar: string
  alAceptar: () => void
  alCerrar: () => void
}) {
  return (
    <Dialogo ancho={400} alCerrar={alCerrar}>
      <h2>{titulo}</h2>
      <p className="bajada">{cuerpo}</p>
      <div className="botonera">
        {/* Cancelar recibe el foco, no el boton que borra: si el dialogo se
            abre sin querer, la tecla Enter no tiene que destruir nada. */}
        <button onClick={alCerrar} autoFocus>
          Cancelar
        </button>
        <button className="destructivo" onClick={alAceptar}>
          {aceptar}
        </button>
      </div>
    </Dialogo>
  )
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
            placeholder="Paquete base (por omisión, com.ejemplo.<diagrama>)"
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
