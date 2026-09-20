import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api'
import HojaMuestra, { DICTADOS, VIAS, type Via } from '../lienzo/HojaMuestra'
import { IconoCamara, IconoClase, IconoMicrofono } from '../iconos'
import { Marca } from '../marca'
import type { Credencial } from '../tipos'

/**
 * Entrada y alta de cuenta, en una sola pantalla que alterna de modo.
 *
 * La mitad izquierda no es decoracion ni una ilustracion: es una hoja de FORJA
 * donde el modelo SE CONSTRUYE, por etapas, y cada etapa es una de las tres
 * vias de entrada que tiene la herramienta. La lista de la derecha se enciende
 * en sincronia, asi que la explicacion escrita y la demostracion son el mismo
 * objeto: nadie tiene que creer en una promesa que no puede ver.
 */

/** Cuanto dura cada etapa. La de voz dura mas porque muestra dos frases. */
const DURACION: Record<Via, number> = { dibujo: 3200, voz: 5200, foto: 5400 }

const ROTULOS: Record<Via, { titulo: string; texto: string; icono: React.ReactNode }> = {
  dibujo: {
    titulo: 'Dibujalo',
    texto: 'Arrastrás las clases sobre la hoja y el equipo lo ve al instante.',
    icono: <IconoClase tamano={15} />,
  },
  voz: {
    titulo: 'Dictalo',
    texto: 'Decís «Paciente tiene muchas Consultas» y la relación aparece.',
    icono: <IconoMicrofono tamano={15} />,
  },
  foto: {
    titulo: 'Fotografialo',
    texto: 'Le sacás una foto a la pizarra y se lee sin salir del navegador.',
    icono: <IconoCamara tamano={15} />,
  },
}

export default function Entrar({ alEntrar }: { alEntrar: (credencial: Credencial) => void }) {
  const [esAlta, setEsAlta] = useState(false)
  const [email, setEmail] = useState('')
  const [nombre, setNombre] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const enviar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const credencial = esAlta
        ? await api.registro(email, nombre, password)
        : await api.sesion(email, password)
      alEntrar(credencial)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor')
    } finally {
      setEnviando(false)
    }
  }

  const { via, ciclo, fijar, soltar, fijada } = useDemostracion()

  return (
    <div className="entrar">
      <div className="entrar-hoja">
        <HojaMuestra via={via} ciclo={ciclo} />

        {/*
          El subtitulo del dictado. Aparece SOLO en la etapa de voz porque es
          lo que explica que esas clases no se dibujaron: alguien las dijo.
        */}
        {via === 'voz' && (
          <div className="entrar-dictado" key={`dictado-${ciclo}`}>
            <IconoMicrofono tamano={14} />
            <span className="entrar-frases">
              {DICTADOS.map((d, indice) => (
                <span
                  key={d.frase}
                  className={indice === 0 ? 'primera' : 'segunda'}
                  style={{ animationDelay: `${d.desde}s` }}
                >
                  {d.frase}
                </span>
              ))}
            </span>
          </div>
        )}
      </div>

      <div className="entrar-mesa">
        <Marca tamano={28} />

        <div className="entrar-titulo">
          <h1>Del pizarrón al backend andando.</h1>
          <p>Un diagrama de clases que varios editan a la vez, sobre papel de ingeniería.</p>
        </div>

        <form onSubmit={enviar}>
          {error && <div className="mensaje error">{error}</div>}

          <div className="campos">
            <div>
              <label htmlFor="email">Correo</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </div>

            {esAlta && (
              <div>
                <label htmlFor="nombre">Nombre</label>
                <input
                  id="nombre"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  autoComplete="name"
                  placeholder="Como te van a ver los demás"
                  required
                />
              </div>
            )}

            <div>
              <label htmlFor="password">Contraseña</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={esAlta ? 'new-password' : 'current-password'}
                required
                minLength={esAlta ? 8 : undefined}
              />
            </div>

            <button className="principal" type="submit" disabled={enviando}>
              {enviando
                ? esAlta
                  ? 'Creando la cuenta…'
                  : 'Entrando…'
                : esAlta
                  ? 'Crear la cuenta'
                  : 'Entrar'}
            </button>
          </div>

          <div className="alterna">
            {esAlta ? '¿Ya tenés cuenta?' : '¿Primera vez?'}
            <button
              type="button"
              onClick={() => {
                setEsAlta(!esAlta)
                setError(null)
              }}
            >
              {esAlta ? 'Entrar' : 'Crear una cuenta'}
            </button>
          </div>
        </form>

        {/*
          Las tres vias no son un catalogo: son el indice de lo que esta
          pasando en la hoja. Se puede apuntar a una para quedarse en ella, que
          es lo que hace alguien que quiere mirar de nuevo la que se perdio.
        */}
        <ul className="entrar-vias" onMouseLeave={soltar}>
          {VIAS.map((v) => (
            <li key={v}>
              <button
                type="button"
                className={`entrar-via${v === via ? ' activa' : ''}`}
                aria-pressed={v === fijada}
                onMouseEnter={() => fijar(v)}
                onFocus={() => fijar(v)}
                onBlur={soltar}
                onClick={() => fijar(v)}
              >
                <span className="entrar-via-icono">{ROTULOS[v].icono}</span>
                <span>
                  <b>{ROTULOS[v].titulo}</b>
                  {ROTULOS[v].texto}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

/**
 * El ciclo de la demostracion.
 *
 * Avanza solo de etapa en etapa y vuelve a empezar; `ciclo` va en la `key` de
 * cada elemento dibujado, que es lo que hace que al reiniciar se vuelvan a
 * trazar en vez de aparecer ya hechos. Apuntar a una via la fija: la
 * demostracion se detiene ahi hasta que el puntero se va, porque una animacion
 * que sigue corriendo mientras alguien intenta leerla es una molestia.
 *
 * Con `prefers-reduced-motion` no hay ciclo: se muestra el modelo completo y
 * quieto. Dice lo mismo sin moverse.
 */
function useDemostracion() {
  const quieto =
    typeof window !== 'undefined' &&
    window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  const [via, setVia] = useState<Via>(quieto ? 'foto' : 'dibujo')
  const [ciclo, setCiclo] = useState(0)
  const [fijada, setFijada] = useState<Via | null>(null)

  useEffect(() => {
    if (quieto || fijada) return
    const reloj = setTimeout(() => {
      const indice = VIAS.indexOf(via)
      if (indice === VIAS.length - 1) {
        setCiclo((c) => c + 1)
        setVia(VIAS[0])
      } else {
        setVia(VIAS[indice + 1])
      }
    }, DURACION[via])
    return () => clearTimeout(reloj)
  }, [via, ciclo, fijada, quieto])

  return {
    via,
    ciclo,
    fijada,
    fijar: (v: Via) => {
      setFijada(v)
      setVia(v)
    },
    soltar: () => setFijada(null),
  }
}
