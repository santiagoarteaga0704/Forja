import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { IconoGuia, IconoPreguntar, IconoTilde } from '../iconos'
import type { CategoriaConsejo, Consejo, Guia as GuiaVista, TemaGuia } from '../tipos'

/** Un turno de la conversacion: lo que se pregunto y lo que se contesto. */
interface Turno {
  pregunta: string
  respuestas: Consejo[]
}

/*
 * Lo que el agente contesta cuando NO SE PUDO PREGUNTAR.
 *
 * El caso importa mas de lo que parece: el agente se va a usar delante de gente,
 * y si la red hipa justo ahi, un panel que no hace nada se lee como "se cayo".
 * Esta respuesta se arma en el cliente, no viaja, y dice la verdad -no se pudo
 * consultar- sin dejar a nadie mirando una pantalla quieta.
 */
const SIN_RESPUESTA: Consejo = {
  id: 'respuesta-sin-servidor',
  categoria: 'DESCUBRIMIENTO',
  prioridad: 0,
  queNote: 'No pude consultar al servidor',
  porQueImporta:
    'La pregunta no llegó. Puede ser la red, o que el backend no esté levantado; el diagrama que tenés en pantalla no se ve afectado.',
  comoSeHace: 'Probá de nuevo en un momento, o tocá una de las preguntas de acá abajo.',
  elementoId: null,
}

interface Props {
  guia: GuiaVista | null
  /** Hay un diagrama abierto: cambia de que habla el agente. */
  enUnDiagrama: boolean
  alDescartar: (id: string) => void
  alCerrar: () => void
  /** Selecciona en el lienzo el elemento del que habla un consejo, si se puede. */
  alSenalar?: (elementoId: string) => void
}

/**
 * El panel del agente guia.
 *
 * Vive en TODA la aplicacion y no solo en el lienzo. Es lo que pide el
 * enunciado -un agente que monitorea la aplicacion y ensena a usarla- y tiene
 * una consecuencia practica: quien recien entra no tiene ningun diagrama que
 * observar, y es justamente quien mas necesita que le expliquen por donde se
 * empieza.
 *
 * Es un cajon que se superpone y no una columna del lienzo. Asi el panel de la
 * clase seleccionada sigue visible mientras se lee un consejo sobre ella, y el
 * mismo componente sirve en la pantalla de proyectos, que no tiene columna
 * lateral donde meterlo.
 *
 * Muestra tres cosas, en este orden: donde estas parado dentro del recorrido
 * completo, que conviene hacer ahora, y un campo para preguntarle. Las
 * respuestas se dibujan igual que los consejos porque tienen la misma forma
 * -que es, por que importa, como se hace- y no hay razon para que quien lee
 * tenga que cambiar de registro segun de donde vino el texto.
 */
export default function Guia({
  guia,
  enUnDiagrama,
  alDescartar,
  alCerrar,
  alSenalar,
}: Props) {
  const [pregunta, setPregunta] = useState('')
  const [conversacion, setConversacion] = useState<Turno[]>([])
  const [preguntando, setPreguntando] = useState(false)
  const [temas, setTemas] = useState<TemaGuia[]>([])
  const campo = useRef<HTMLInputElement>(null)
  const finDeLaCharla = useRef<HTMLDivElement>(null)

  // Las sugerencias vienen del servidor y no de una lista escrita aca: son el
  // mismo catalogo que responde, asi que no puede ofrecerse una pregunta que
  // despues no se sepa contestar. Si no llegan, no pasa nada: no se muestran.
  useEffect(() => {
    api.temas().then(setTemas).catch(() => setTemas([]))
  }, [])

  useEffect(() => {
    const escape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') alCerrar()
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [alCerrar])

  const preguntar = async (texto: string) => {
    const dicha = texto.trim()
    if (!dicha || preguntando) return

    setPregunta('')
    setPreguntando(true)
    // El tema de la ultima respuesta viaja con la pregunta: es lo que permite
    // que "¿y eso?" se entienda como "contame mas de eso".
    const ultimo = conversacion.at(-1)?.respuestas[0]?.id ?? null
    try {
      const respuestas = await api.preguntar(dicha, ultimo)
      setConversacion((charla) => [...charla, { pregunta: dicha, respuestas }])
    } catch {
      // Nunca se deja la pregunta sin contestar, ni siquiera sin servidor.
      setConversacion((charla) => [...charla, { pregunta: dicha, respuestas: [SIN_RESPUESTA] }])
    } finally {
      setPreguntando(false)
      campo.current?.focus()
    }
  }

  // La charla crece hacia abajo: al contestar, se mira lo ultimo.
  useEffect(() => {
    finDeLaCharla.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [conversacion])

  const pasos = guia?.recorrido.pasos ?? []
  const hechos = pasos.filter((paso) => paso.hecho).length
  const siguiente = pasos.find((paso) => !paso.hecho)

  return (
    <aside className="cajon-guia" role="complementary" aria-label="Agente guía">
      <header className="cajon-cabecera">
        <IconoGuia />
        <strong>Guía</strong>
        <button className="desnudo cerrar" onClick={alCerrar} aria-label="Cerrar la guía">
          ×
        </button>
      </header>

      <div className="cajon-cuerpo">
        {/*
          El recorrido va arriba de todo: los consejos sueltos dicen cual es el
          proximo boton, y esto dice a donde lleva todo junto. Ningun paso se
          marca por haber visto una pantalla, sino por la evidencia de haberlo
          hecho.
        */}
        {pasos.length > 0 && (
          <section className="recorrido">
            <div className="recorrido-titulo">
              <h3>Tu recorrido por FORJA</h3>
              <span className="cuenta">
                {hechos} de {pasos.length}
              </span>
            </div>
            <div
              className="barra-avance"
              role="progressbar"
              aria-valuenow={hechos}
              aria-valuemin={0}
              aria-valuemax={pasos.length}
            >
              <span style={{ width: `${(hechos / pasos.length) * 100}%` }} />
            </div>

            <ol className="pasos-recorrido">
              {pasos.map((paso) => (
                <li
                  key={paso.id}
                  className={paso.hecho ? 'hecho' : paso.id === siguiente?.id ? 'siguiente' : ''}
                >
                  <span className="marca">{paso.hecho && <IconoTilde tamano={12} />}</span>
                  <span className="texto">
                    {paso.titulo}
                    {paso.id === siguiente?.id && <span className="glosa">{paso.comoSeHace}</span>}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        )}

        <section>
          <div className="recorrido-titulo">
            <h3>{enUnDiagrama ? 'Sobre esto que estás haciendo' : 'Por dónde seguir'}</h3>
            {conversacion.length > 0 && (
              <button className="desnudo" onClick={() => setConversacion([])}>
                Limpiar la charla
              </button>
            )}
          </div>
          {!guia && <p className="vacio">Mirando…</p>}
          {guia && guia.consejos.length === 0 && (
            <p className="vacio">Nada que señalar por ahora. Vuelvo a mirar con cada cambio.</p>
          )}
          {guia?.consejos.map((consejo) => (
            <Tarjeta
              key={consejo.id}
              consejo={consejo}
              alDescartar={() => alDescartar(consejo.id)}
              alSenalar={
                consejo.elementoId && alSenalar ? () => alSenalar(consejo.elementoId!) : undefined
              }
            />
          ))}
        </section>

        {/*
          La charla queda a la vista, turno por turno.
          Antes la respuesta reemplazaba a los consejos y la pregunta
          desaparecia al contestar: se leia como un buscador. Viendo lo que se
          pregunto arriba de lo que se contesto, se lee como lo que es, y ademas
          se puede repreguntar sabiendo de que se venia hablando.
        */}
        {conversacion.map((turno, indice) => (
          <section key={indice} className="turno">
            <p className="lo-que-pregunte">{turno.pregunta}</p>
            {turno.respuestas.map((respuesta) => (
              <Tarjeta key={respuesta.id} consejo={respuesta} />
            ))}
          </section>
        ))}

        {preguntando && (
          <section>
            <p className="vacio">Buscando en lo que sé…</p>
          </section>
        )}

        <div ref={finDeLaCharla} />
      </div>

      {/*
        Las sugerencias van PEGADAS AL CAMPO, fuera de lo que se desplaza.
        Dentro del cuerpo quedaban debajo del pliegue, que es donde no sirven:
        se ven al momento de escribir o no se ven nunca. Un campo vacio admite
        infinitas formas de no acertar; estas preguntas lo vuelven un menu donde
        todo lo que se toca anda -hay una prueba que garantiza justamente eso-.
      */}
      {temas.length > 0 && (
        <div className="tira-sugerencias">
          {temas.slice(0, 4).map((tema) => (
            <button key={tema.id} disabled={preguntando} onClick={() => preguntar(tema.pregunta)}>
              {tema.pregunta}
            </button>
          ))}
        </div>
      )}

      {/* El campo va fijo abajo: se puede preguntar en cualquier momento, sin
          tener que desplazarse hasta el final de los consejos. */}
      <form
        className="cajon-pregunta"
        onSubmit={(evento) => {
          evento.preventDefault()
          void preguntar(pregunta)
        }}
      >
        <input
          ref={campo}
          value={pregunta}
          onChange={(e) => setPregunta(e.target.value)}
          placeholder="Preguntame algo: ¿cómo exporto a EA?"
          maxLength={300}
          disabled={preguntando}
        />
        <button className="principal" type="submit" disabled={preguntando || !pregunta.trim()}>
          <IconoPreguntar />
          <span className="solo-lectores">Preguntar</span>
        </button>
      </form>
    </aside>
  )
}

/** Un consejo o una respuesta: las dos cosas tienen la misma forma. */
function Tarjeta({
  consejo,
  alDescartar,
  alSenalar,
}: {
  consejo: Consejo
  alDescartar?: () => void
  alSenalar?: () => void
}) {
  return (
    <article className={`consejo ${consejo.categoria.toLowerCase()}`}>
      <header>
        <span className="etiqueta">{etiqueta(consejo.categoria)}</span>
        {alDescartar && (
          <button
            className="quitar"
            title="No mostrar más este aviso"
            aria-label="Descartar"
            onClick={alDescartar}
          >
            ×
          </button>
        )}
      </header>

      <p className="que">{consejo.queNote}</p>
      <p className="porque">{consejo.porQueImporta}</p>
      <p className="como">{consejo.comoSeHace}</p>

      {alSenalar && (
        <button className="senalar" onClick={alSenalar}>
          Ver en el lienzo
        </button>
      )}
    </article>
  )
}

function etiqueta(categoria: CategoriaConsejo) {
  switch (categoria) {
    case 'DESCUBRIMIENTO':
      return 'Podés hacer esto'
    case 'MODELO':
      return 'Afecta lo que se genera'
    case 'DISENO':
      return 'Mejora de diseño'
  }
}
