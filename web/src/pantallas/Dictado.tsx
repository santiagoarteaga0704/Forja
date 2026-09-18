import { useEffect, useRef, useState } from 'react'
import { ErrorApi, api } from '../api'
import { IconoMicrofono } from '../iconos'
import { SESION_ID } from '../sesion'
import type { Pedido, ResultadoDictado } from '../tipos'

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
 *
 * De la misma barra salen dos caminos que no se tratan igual. **Aplicar** manda
 * la frase a la gramatica, que es exacta, y por eso entra directo: no hay nada
 * que revisar. **Pedir** se la da a un modelo, que propone varias frases, y esas
 * se muestran antes de tocar el diagrama. Es el mismo criterio con el que se lee
 * la foto de una pizarra: lo que adivina una maquina se mira primero.
 *
 * El boton de pedir solo aparece si el servidor tiene con que contestar. Sin
 * traductor configurado esta pantalla es exactamente la de siempre.
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

  // El pedido: lo que el modelo propuso y todavia no se aplico.
  const [hayTraductor, setHayTraductor] = useState(false)
  const [pedido, setPedido] = useState<Pedido | null>(null)
  const [pidiendo, setPidiendo] = useState(false)
  const [aplicandoPedido, setAplicandoPedido] = useState(false)
  const [aviso, setAviso] = useState<{ clase: string; texto: string } | null>(null)

  const reconocedor = useRef<any>(null)
  const campo = useRef<HTMLInputElement>(null)
  const cancelacion = useRef<AbortController | null>(null)
  // El identificador de la propuesta que se esta revisando. Viaja al aplicar
  // para que un reintento no duplique el diagrama.
  const tokenLectura = useRef<string | null>(null)

  // En que paso esta el pedido. Se deduce del estado en vez de guardarse
  // aparte, por lo mismo que en la lectura de una pizarra: guardarlo abriria la
  // puerta a que el indicador y la pantalla discrepen.
  const paso = pedido ? 3 : pidiendo ? 2 : 1

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
      // Un pedido puede estar en vuelo treinta segundos: si la barra se cierra,
      // se corta.
      cancelacion.current?.abort()
    }
  }, [])

  useEffect(() => {
    let vivo = true
    api
      .hayTraductor(diagramaId)
      .then((respuesta) => {
        if (vivo) setHayTraductor(respuesta.hayModelo)
      })
      .catch(() => {
        // Sin respuesta se asume que no hay: el boton no se ofrece y el dictado
        // sigue funcionando igual.
      })
    return () => {
      vivo = false
    }
  }, [diagramaId])

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
    // Aplicar por la gramatica descarta la propuesta que hubiera en revision:
    // el campo ya dice otra cosa, y dejarla a la vista seria mentir.
    setPedido(null)
    setAviso(null)
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

  /** Primer paso del pedido: pedirle al modelo que proponga, sin aplicar nada. */
  const proponer = async () => {
    const texto = frase.trim()
    if (!texto) return

    const control = new AbortController()
    cancelacion.current = control
    tokenLectura.current = crypto.randomUUID()

    setPidiendo(true)
    setPedido(null)
    setResultado(null)
    setError(null)
    setAviso(null)
    try {
      const propuesta = await api.pedirLectura(diagramaId, texto, SESION_ID, control.signal)
      setPedido(propuesta)
      if (propuesta.frases.length === 0) {
        setAviso({
          clase: 'informacion',
          texto: 'El modelo no propuso nada. Probá pidiéndolo con otras palabras, '
            + 'nombrando las clases que querés.',
        })
      }
    } catch (e) {
      // Cancelar no es un error: lo pidio el usuario y no hay nada que contarle.
      if (e instanceof DOMException && e.name === 'AbortError') return
      setAviso({
        clase: 'error',
        texto: e instanceof ErrorApi ? e.message : 'No responde el servidor',
      })
    } finally {
      setPidiendo(false)
      cancelacion.current = null
    }
  }

  const cancelarPedido = () => {
    cancelacion.current?.abort()
    cancelacion.current = null
    setPidiendo(false)
  }

  /** Segundo paso: aplicar lo que se reviso. */
  const aplicarPedido = async () => {
    if (!pedido) return

    setAplicandoPedido(true)
    setAviso(null)
    try {
      const resultado = await api.aplicarPedido(
        diagramaId,
        pedido.pedido,
        SESION_ID,
        tokenLectura.current ?? crypto.randomUUID(),
      )
      if (resultado.aplicadas > 0) {
        alAplicar()
        setFrase('')
        setPedido(null)
        setAviso({
          clase: 'bien',
          texto: `Se aplicaron ${resultado.aplicadas} cambios`
            + (resultado.yaEstaban > 0 ? `, ${resultado.yaEstaban} ya estaban` : '')
            + (resultado.problemas.length ? `. No entró: ${resultado.problemas[0]}` : ''),
        })
      } else if (resultado.retenidoPor) {
        setAviso({
          clase: 'informacion',
          texto: `${resultado.retenidoPor} tiene tomado un elemento. Intentá en un momento.`,
        })
      } else {
        setAviso({ clase: 'informacion', texto: 'No quedó nada que aplicar.' })
      }
    } catch (e) {
      setAviso({
        clase: 'error',
        texto: e instanceof ErrorApi ? e.message : 'No responde el servidor',
      })
    } finally {
      setAplicandoPedido(false)
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
            disabled={escuchando || enviando || pidiendo}
            title="Dictar hablando"
            aria-label="Dictar hablando"
          >
            {escuchando ? <span className="punto-grabando" /> : <IconoMicrofono tamano={17} />}
          </button>
        )}

        <input
          ref={campo}
          value={frase}
          placeholder={
            escuchando
              ? 'Escuchando…'
              : 'un Paciente tiene muchas Consultas'
          }
          onChange={(e) => {
            setFrase(e.target.value)
            // La propuesta era sobre el texto anterior: al cambiarlo deja de
            // corresponder y se descarta.
            if (pedido) setPedido(null)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !pidiendo) aplicar()
            if (e.key === 'Escape') {
              // Escape deshace un paso por vez antes de cerrar la barra.
              if (pidiendo) cancelarPedido()
              else if (pedido) setPedido(null)
              else alCerrar()
            }
          }}
          disabled={enviando}
        />

        {hayTraductor && (
          <button
            onClick={proponer}
            disabled={pidiendo || enviando || aplicandoPedido || !frase.trim()}
            title="Pedir un diagrama entero en una frase, para revisarlo antes de aplicarlo"
          >
            {pidiendo ? '…' : 'Pedir'}
          </button>
        )}

        <button
          className="principal"
          onClick={() => aplicar()}
          disabled={enviando || pidiendo || !frase.trim()}
        >
          {enviando ? '…' : 'Aplicar'}
        </button>
        <button onClick={alCerrar} title="Cerrar">
          ×
        </button>
      </div>

      {/*
        Los tres pasos aparecen solo cuando hay un pedido en curso: el dictado
        no tiene pasos -es una frase y se aplica- y mostrarlos siempre daria a
        entender que si. El del medio, revisar, es el que hace que esto se pueda
        demostrar: lo que propone un modelo chico no se aplica a ciegas.
      */}
      {(pidiendo || pedido) && (
        <ol className="pasos">
          {['Pedir', 'Revisar', 'Aplicar'].map((nombre, indice) => (
            <li
              key={nombre}
              className={indice + 1 < paso ? 'hecho' : indice + 1 === paso ? 'aqui' : ''}
            >
              {nombre}
            </li>
          ))}
        </ol>
      )}

      {pidiendo && (
        <div className="mensaje informacion dictado-respuesta pensando">
          <span>Pensando el diagrama… suele tardar medio minuto, a veces más.</span>
          <button type="button" onClick={cancelarPedido}>
            Cancelar
          </button>
        </div>
      )}

      {aviso && <div className={`mensaje ${aviso.clase} dictado-respuesta`}>{aviso.texto}</div>}

      {pedido && !pidiendo && (
        <>
          <ResumenDelPedido pedido={pedido} />
          <div className="botonera">
            <button onClick={() => setPedido(null)} disabled={aplicandoPedido}>
              Descartar
            </button>
            <button
              className="principal"
              onClick={aplicarPedido}
              disabled={aplicandoPedido || pedido.comandos.length === 0}
            >
              {aplicandoPedido ? 'Aplicando…' : 'Aplicar al diagrama'}
            </button>
          </div>
        </>
      )}

      {error && <div className="mensaje error dictado-respuesta">{error}</div>}

      {resultado && !error && (
        <div
          className={`mensaje ${resultado.aplicadas > 0 ? 'bien' : 'informacion'} dictado-respuesta`}
        >
          {resultado.aplicadas > 0 ? (
            <>
              {resultado.explicacion}
              {resultado.problemas.length > 0 && (
                <div style={{ marginTop: 4 }}>No entró: {resultado.problemas.join('; ')}</div>
              )}
            </>
          ) : resultado.retenidoPor ? (
            <>Entendí la instrucción, pero {resultado.retenidoPor} tiene tomado ese elemento.</>
          ) : (
            <>
              <strong>{resultado.explicacion}.</strong> Probá con alguna de estas:
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

/**
 * Lo que el modelo propuso, antes de aplicarlo.
 *
 * Las frases que no se entendieron se muestran con su contenido y no como un
 * numero, igual que las lineas de una pizarra: "3 frases ignoradas" no le dice a
 * nadie que corregir, y ver la frase suelta suele bastar para entender que fue
 * lo que el modelo inventó.
 */
function ResumenDelPedido({ pedido }: { pedido: Pedido }) {
  const entendidas = pedido.frases.filter((frase) => frase.seEntendio)
  const ignoradas = pedido.frases.filter((frase) => !frase.seEntendio)

  return (
    <div className="resumen-lectura resumen-pedido">
      <h3>Esto voy a hacer</h3>

      {entendidas.length === 0 && pedido.frases.length > 0 && (
        <p className="vacio">Ninguna de las frases propuestas se pudo interpretar.</p>
      )}

      {entendidas.map((frase, indice) => (
        // Por posicion: el modelo puede proponer la misma frase dos veces, y la
        // frase no sirve de clave.
        <div className="miembro-fila" key={indice}>
          <span className="frase-propuesta">{frase.frase}</span>
          <span style={{ color: 'var(--acero-debil)' }}>{frase.explicacion}</span>
        </div>
      ))}

      {ignoradas.length > 0 && (
        <>
          <h3 style={{ marginTop: 14 }}>Estas frases no las entendí</h3>
          <ul className="ignoradas">
            {ignoradas.map((frase, indice) => (
              <li key={indice}>{frase.frase}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
