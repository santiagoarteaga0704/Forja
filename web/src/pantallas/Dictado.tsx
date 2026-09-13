import { useEffect, useRef, useState } from 'react'
import { ErrorApi, api } from '../api'
import { SESION_ID } from '../sesion'
import type { ResultadoDictado } from '../tipos'

/**
 * Barra de dictado.
 *
 * Tiene microfono y tambien campo de texto, y el campo no es un respaldo de
 * segunda: es lo que hace que la funcion se pueda demostrar aunque el
 * microfono no ande, el aula tenga ruido o el navegador no traiga
 * reconocimiento de voz. El servidor recibe texto en los dos casos, asi que la
 * gramatica que se ejercita es exactamente la misma.
 *
 * El reconocimiento ocurre en el navegador. No se sube audio al servidor: no
 * haria falta montar un reconocedor propio cuando el navegador ya tiene uno, y
 * el resultado llega como texto, que es lo unico que el parser necesita.
 */
export default function Dictado({
  diagramaId,
  alAplicar,
  alCerrar,
}: {
  diagramaId: string
  /** Se llama cuando el dictado cambio el modelo, para volver a pedirlo. */
  alAplicar: () => void
  alCerrar: () => void
}) {
  const [frase, setFrase] = useState('')
  const [escuchando, setEscuchando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [resultado, setResultado] = useState<ResultadoDictado | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reconocedor = useRef<any>(null)
  const campo = useRef<HTMLInputElement>(null)

  // El reconocimiento de voz no es estandar en todos los navegadores: se
  // resuelve una sola vez y si no esta, solo se oculta el boton del microfono.
  const hayMicrofono =
    typeof window !== 'undefined' &&
    ((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition)

  useEffect(() => {
    campo.current?.focus()
    return () => {
      try {
        reconocedor.current?.abort()
      } catch {
        // Si ya estaba detenido no hay nada que hacer.
      }
    }
  }, [])

  const escuchar = () => {
    if (!hayMicrofono) return
    const Reconocedor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition

    const instancia = new Reconocedor()
    // Una instruccion por vez: el dictado de un diagrama son ordenes cortas, no
    // un parrafo, y cerrar el reconocimiento en cada una evita que se mezclen.
    instancia.lang = 'es-419'
    instancia.continuous = false
    instancia.interimResults = false
    instancia.maxAlternatives = 1

    instancia.onstart = () => {
      setEscuchando(true)
      setError(null)
    }
    instancia.onerror = (evento: any) => {
      setEscuchando(false)
      setError(
        evento.error === 'not-allowed'
          ? 'El navegador no dio permiso para usar el microfono'
          : 'No se pudo escuchar: ' + evento.error,
      )
    }
    instancia.onend = () => setEscuchando(false)
    instancia.onresult = (evento: any) => {
      const dicho = evento.results[0][0].transcript as string
      setFrase(dicho)
      // Se aplica de una: hablar y despues tener que apretar un boton anula la
      // ventaja de dictar.
      aplicar(dicho)
    }

    reconocedor.current = instancia
    instancia.start()
  }

  const aplicar = async (texto?: string) => {
    const dicho = (texto ?? frase).trim()
    if (!dicho) return

    setEnviando(true)
    setError(null)
    try {
      const respuesta = await api.dictar(diagramaId, dicho, SESION_ID)
      setResultado(respuesta)
      if (respuesta.aplicadas > 0) {
        alAplicar()
        setFrase('')
      }
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No responde el servidor')
    } finally {
      setEnviando(false)
      campo.current?.focus()
    }
  }

  return (
    <div className="dictado">
      <div className="dictado-linea">
        {hayMicrofono && (
          <button
            type="button"
            className={`microfono${escuchando ? ' escuchando' : ''}`}
            onClick={escuchar}
            disabled={escuchando || enviando}
            title="Dictar una instruccion"
            aria-label="Dictar una instruccion"
          >
            {escuchando ? '●' : '🎙'}
          </button>
        )}

        <input
          ref={campo}
          value={frase}
          placeholder={
            escuchando
              ? 'Escuchando...'
              : 'un Paciente tiene muchas Consultas'
          }
          onChange={(e) => setFrase(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') aplicar()
            if (e.key === 'Escape') alCerrar()
          }}
          disabled={enviando}
        />

        <button className="principal" onClick={() => aplicar()} disabled={enviando || !frase.trim()}>
          {enviando ? '...' : 'Aplicar'}
        </button>
        <button onClick={alCerrar} title="Cerrar">
          ×
        </button>
      </div>

      {error && <div className="mensaje error dictado-respuesta">{error}</div>}

      {resultado && !error && (
        <div
          className={`mensaje ${resultado.aplicadas > 0 ? 'bien' : 'informacion'} dictado-respuesta`}
        >
          {resultado.aplicadas > 0 ? (
            <>
              {resultado.explicacion}
              {resultado.problemas.length > 0 && (
                <div style={{ marginTop: 4 }}>No entro: {resultado.problemas.join('; ')}</div>
              )}
            </>
          ) : resultado.retenidoPor ? (
            <>Entendi la instruccion, pero {resultado.retenidoPor} tiene tomado ese elemento.</>
          ) : (
            <>
              <strong>{resultado.explicacion}.</strong> Proba con alguna de estas:
              <ul className="sugerencias">
                {resultado.sugerencias.slice(0, 4).map((sugerencia) => (
                  <li key={sugerencia}>
                    <button type="button" onClick={() => setFrase(sugerencia)}>
                      {sugerencia}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  )
}
