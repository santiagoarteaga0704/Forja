import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api'
import { IconoCarpeta, IconoClase, IconoPersona, IconoSalir } from '../iconos'
import type { Credencial, DiagramaResumen, ProyectoVista } from '../tipos'

interface Props {
  credencial: Credencial
  alAbrir: (proyecto: ProyectoVista, diagrama: DiagramaResumen) => void
  alSalir: () => void
}

/**
 * Eleccion del proyecto y del diagrama sobre el que se va a trabajar.
 *
 * Va en lista y no en rejilla de fichas: una rejilla promete que cada elemento
 * tiene algo visual propio -una portada, una miniatura- y aca son nombres. En
 * lista quedan alineados, que es lo que se compara, y se recorren de un
 * vistazo.
 */
export default function Proyectos({ credencial, alAbrir, alSalir }: Props) {
  const [proyectos, setProyectos] = useState<ProyectoVista[]>([])
  const [elegido, setElegido] = useState<ProyectoVista | null>(null)
  const [diagramas, setDiagramas] = useState<DiagramaResumen[]>([])
  const [nombreProyecto, setNombreProyecto] = useState('')
  const [nombreDiagrama, setNombreDiagrama] = useState('')
  const [invitado, setInvitado] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)

  const fallar = (e: unknown) =>
    setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor')

  useEffect(() => {
    api.proyectos().then(setProyectos).catch(fallar)
  }, [])

  useEffect(() => {
    if (!elegido) return
    // La lista no se vacia aqui: al no haber proyecto elegido no se dibuja la
    // seccion, asi que limpiarla solo provocaria un render mas.
    api.diagramas(elegido.id).then(setDiagramas).catch(fallar)
  }, [elegido])

  const crearProyecto = async (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!nombreProyecto.trim()) return
    try {
      const creado = await api.crearProyecto(nombreProyecto.trim())
      setProyectos([creado, ...proyectos])
      setNombreProyecto('')
      setElegido(creado)
      setError(null)
    } catch (e) {
      fallar(e)
    }
  }

  const crearDiagrama = async (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!elegido || !nombreDiagrama.trim()) return
    try {
      const creado = await api.crearDiagrama(elegido.id, nombreDiagrama.trim())
      setDiagramas([...diagramas, creado])
      setNombreDiagrama('')
      setError(null)
      alAbrir(elegido, creado)
    } catch (e) {
      fallar(e)
    }
  }

  const invitar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!elegido || !invitado.trim()) return
    try {
      await api.invitar(elegido.id, invitado.trim(), 'EDITOR')
      setAviso(`${invitado.trim()} ya puede editar este proyecto`)
      setInvitado('')
      setError(null)
    } catch (e) {
      setAviso(null)
      fallar(e)
    }
  }

  return (
    <div className="aplicacion">
      <header className="barra">
        <div className="marca">
          <span className="yunque" />
          FORJA
        </div>
        <div className="crece" />
        <span className="senal">
          <IconoPersona tamano={14} />
          {credencial.nombre}
        </span>
        <button onClick={alSalir}>
          <IconoSalir />
          Salir
        </button>
      </header>

      <div className="proyectos">
        <div className="proyectos-cuerpo">
          {error && <div className="mensaje error">{error}</div>}
          {aviso && <div className="mensaje bien">{aviso}</div>}

          <section className="bloque">
            <div className="seccion-titulo">
              <h2>Proyectos</h2>
              {proyectos.length > 0 && (
                <span className="cuenta">
                  {proyectos.length === 1 ? '1 proyecto' : `${proyectos.length} proyectos`}
                </span>
              )}
            </div>

            <form className="formulario-linea" onSubmit={crearProyecto} style={{ marginBottom: 14 }}>
              <input
                placeholder="Nombre del proyecto nuevo"
                value={nombreProyecto}
                onChange={(e) => setNombreProyecto(e.target.value)}
                style={{ maxWidth: 340 }}
              />
              <button className="principal" type="submit" disabled={!nombreProyecto.trim()}>
                Crear proyecto
              </button>
            </form>

            {proyectos.length === 0 ? (
              <p className="vacio-bloque">
                Todavía no tenés proyectos. Un proyecto agrupa los diagramas de un mismo sistema y
                la gente que puede editarlos.
              </p>
            ) : (
              <div className="lista">
                {proyectos.map((proyecto) => (
                  <button
                    key={proyecto.id}
                    className={`fila${elegido?.id === proyecto.id ? ' elegida' : ''}`}
                    onClick={() => setElegido(proyecto)}
                  >
                    <IconoCarpeta />
                    <span className="nombre">{proyecto.nombre}</span>
                    <span className="detalle">
                      {proyecto.propietarioId === credencial.usuarioId
                        ? 'Tuyo'
                        : 'Compartido con vos'}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>

          {elegido && (
            <>
              <section className="bloque">
                <div className="seccion-titulo">
                  <h2>Diagramas de {elegido.nombre}</h2>
                </div>

                <form
                  className="formulario-linea"
                  onSubmit={crearDiagrama}
                  style={{ marginBottom: 14 }}
                >
                  <input
                    placeholder="Nombre del diagrama nuevo"
                    value={nombreDiagrama}
                    onChange={(e) => setNombreDiagrama(e.target.value)}
                    style={{ maxWidth: 340 }}
                  />
                  <button className="principal" type="submit" disabled={!nombreDiagrama.trim()}>
                    Crear y abrir
                  </button>
                </form>

                {diagramas.length === 0 ? (
                  <p className="vacio-bloque">
                    Este proyecto todavía no tiene diagramas. Creá uno y se abre el lienzo.
                  </p>
                ) : (
                  <div className="lista">
                    {diagramas.map((diagrama) => (
                      <button
                        key={diagrama.id}
                        className="fila"
                        onClick={() => alAbrir(elegido, diagrama)}
                      >
                        <IconoClase />
                        <span className="nombre">{diagrama.nombre}</span>
                        <span className="detalle">
                          {diagrama.tipo === 'CLASES' ? 'Clases' : 'Secuencia'}, versión{' '}
                          {diagrama.version}
                        </span>
                      </button>
                    ))}
                  </div>
                )}
              </section>

              {elegido.propietarioId === credencial.usuarioId && (
                <section className="bloque">
                  <div className="seccion-titulo">
                    <h2>Invitar a colaborar</h2>
                  </div>
                  <form className="formulario-linea" onSubmit={invitar}>
                    <input
                      type="email"
                      placeholder="Correo de una cuenta ya registrada"
                      value={invitado}
                      onChange={(e) => setInvitado(e.target.value)}
                      style={{ maxWidth: 340 }}
                    />
                    <button type="submit" disabled={!invitado.trim()}>
                      Invitar como editor
                    </button>
                  </form>
                </section>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
