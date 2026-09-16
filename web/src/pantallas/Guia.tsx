import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import { IconoGuia, IconoPreguntar, IconoTilde } from '../iconos'
import type { CategoriaConsejo, Consejo, Guia as GuiaVista } from '../tipos'

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
  const [respuestas, setRespuestas] = useState<Consejo[] | null>(null)
  const [preguntando, setPreguntando] = useState(false)
  const campo = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const escape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') alCerrar()
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [alCerrar])

  const preguntar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!pregunta.trim()) return
    setPreguntando(true)
    try {
      setRespuestas(await api.preguntar(pregunta.trim()))
    } catch {
      setRespuestas(null)
    } finally {
      setPreguntando(false)
      campo.current?.focus()
    }
  }

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

        {respuestas ? (
          <section>
            <div className="recorrido-titulo">
              <h3>Lo que sé de eso</h3>
              <button className="desnudo" onClick={() => setRespuestas(null)}>
                Volver a los consejos
              </button>
            </div>
            {respuestas.map((respuesta) => (
              <Tarjeta key={respuesta.id} consejo={respuesta} />
            ))}
          </section>
        ) : (
          <section>
            <h3 style={{ marginBottom: 10 }}>
              {enUnDiagrama ? 'Sobre esto que estás haciendo' : 'Por dónde seguir'}
            </h3>
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
                  consejo.elementoId && alSenalar
                    ? () => alSenalar(consejo.elementoId!)
                    : undefined
                }
              />
            ))}
          </section>
        )}
      </div>

      {/* El campo va fijo abajo: se puede preguntar en cualquier momento, sin
          tener que desplazarse hasta el final de los consejos. */}
      <form className="cajon-pregunta" onSubmit={preguntar}>
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
