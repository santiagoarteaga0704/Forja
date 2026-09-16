import { useCallback, useEffect, useState } from 'react'
import { api } from './api'
import type { Guia } from './tipos'

const CLAVE_DESCARTADOS = 'forja.guia.descartados'

/**
 * El enganche del agente guia con el servidor.
 *
 * Vive aparte del panel porque lo necesitan dos cosas a la vez: el panel, para
 * dibujar los consejos, y la barra de cada pantalla, para poner el numero en el
 * boton. Si el panel se trajera sus propios datos, el numero no existiria
 * mientras el panel esta cerrado -que es justo cuando hace falta, porque es lo
 * que invita a abrirlo-.
 *
 * Lo usan las dos pantallas. Sin `diagramaId` el agente solo puede hablar de la
 * herramienta, que es exactamente lo que necesita quien todavia no creo ninguno.
 *
 * @param version cambia con cada operacion aceptada sobre el diagrama; al
 *                cambiar se vuelve a consultar, porque las conclusiones del
 *                agente dependen del modelo
 */
export function useGuia(diagramaId: string | null, version?: number) {
  const [guia, setGuia] = useState<Guia | null>(null)
  const [descartados, setDescartados] = useState<string[]>(leerDescartados)

  const consultar = useCallback(() => {
    api
      .guia(diagramaId, descartados)
      .then(setGuia)
      // Que el agente no responda no puede romper la pantalla: es ayuda, no
      // parte del trabajo. Se queda callado y ya.
      .catch(() => setGuia(null))
  }, [diagramaId, descartados])

  useEffect(consultar, [consultar, version])

  const descartar = useCallback(
    (id: string) => {
      setDescartados((actuales) => {
        const nuevos = [...actuales, id]
        guardarDescartados(nuevos)
        return nuevos
      })
    },
    [],
  )

  return { guia, descartar, recargar: consultar }
}

/**
 * Los avisos cerrados se recuerdan por cuenta, no por diagrama.
 *
 * Antes iban por diagrama, y tenia sentido cuando el agente solo hablaba del
 * modelo. Ahora la mayoria de los consejos ensenan la herramienta, y eso se
 * aprende una vez: cerrar "nunca dictaste" en un diagrama y que reaparezca en el
 * siguiente seria insistir con algo ya visto.
 */
function leerDescartados(): string[] {
  try {
    const guardado = localStorage.getItem(CLAVE_DESCARTADOS)
    return guardado ? (JSON.parse(guardado) as string[]) : []
  } catch {
    return []
  }
}

function guardarDescartados(ids: string[]) {
  try {
    localStorage.setItem(CLAVE_DESCARTADOS, JSON.stringify(ids))
  } catch {
    // Si el navegador no deja guardar, los avisos vuelven a aparecer al
    // recargar. Es una molestia menor y no vale interrumpir por eso.
  }
}
