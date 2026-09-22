import { BASE } from './api'
import type { EventoLienzo } from './tipos'

/**
 * Canal de avisos del lienzo colaborativo.
 *
 * Solo recibe: los cambios se envian por HTTP, donde el reenvio es idempotente.
 * Por eso este objeto no expone ningun metodo para mandar nada mas que el
 * latido.
 *
 * Reconecta con espera creciente. La primera reconexion es casi inmediata
 * porque el corte mas frecuente es momentaneo, y se alarga hasta medio minuto
 * para no castigar a un servidor que esta caido.
 */
export interface Canal {
  cerrar(): void
  conectado(): boolean
}

interface Opciones {
  diagramaId: string
  sesionId: string
  token: string
  alRecibir: (evento: EventoLienzo) => void
  alCambiarEstado: (conectado: boolean) => void
}

const ESPERAS = [500, 1000, 2000, 5000, 10000, 30000]
const LATIDO_MS = 25000

export function abrirCanal(opciones: Opciones): Canal {
  let socket: WebSocket | null = null
  let intento = 0
  let cerradoAProposito = false
  let latido: number | undefined
  let reintento: number | undefined

  const url = () => {
    // En el despliegue la web viaja dentro del mismo servidor que la API, asi
    // que BASE queda vacio y todas las llamadas son relativas. Un WebSocket no
    // puede serlo: hay que decirle el servidor, y ademas el esquema tiene que
    // seguir al de la pagina -desde https solo se puede abrir wss-.
    const base = BASE
      ? BASE.replace(/^http/, 'ws')
      : `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}`
    const consulta = new URLSearchParams({
      token: opciones.token,
      diagrama: opciones.diagramaId,
      sesion: opciones.sesionId,
    })
    return `${base}/ws/diagramas?${consulta}`
  }

  const conectar = () => {
    socket = new WebSocket(url())

    socket.onopen = () => {
      intento = 0
      opciones.alCambiarEstado(true)
      // El latido evita que un intermediario corte por inactividad un canal
      // que solo esta esperando cambios.
      latido = window.setInterval(() => socket?.send('ping'), LATIDO_MS)
    }

    socket.onmessage = (mensaje) => {
      if (mensaje.data === 'pong') return
      try {
        opciones.alRecibir(JSON.parse(mensaje.data) as EventoLienzo)
      } catch {
        // Un aviso ilegible no debe tumbar el canal: se descarta.
      }
    }

    socket.onclose = () => {
      window.clearInterval(latido)
      opciones.alCambiarEstado(false)
      if (cerradoAProposito) return

      const espera = ESPERAS[Math.min(intento, ESPERAS.length - 1)]
      intento += 1
      reintento = window.setTimeout(conectar, espera)
    }

    socket.onerror = () => {
      // El cierre llega igual y es ahi donde se decide reintentar.
    }
  }

  conectar()

  return {
    cerrar() {
      cerradoAProposito = true
      window.clearInterval(latido)
      window.clearTimeout(reintento)
      socket?.close()
    },
    conectado() {
      return socket?.readyState === WebSocket.OPEN
    },
  }
}
