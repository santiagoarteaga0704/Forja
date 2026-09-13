import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api'
import type { Credencial, DiagramaResumen, ProyectoVista } from '../tipos'

interface Props {
  credencial: Credencial
  alAbrir: (proyecto: ProyectoVista, diagrama: DiagramaResumen) => void
  alSalir: () => void
}

/** Eleccion del proyecto y del diagrama sobre el que se va a trabajar. */
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
        <span className="sutil">{credencial.nombre}</span>
        <button onClick={alSalir}>Salir</button>
      </header>

      <div className="proyectos">
        {error && <div className="mensaje error">{error}</div>}
        {aviso && <div className="mensaje bien">{aviso}</div>}

        <h2>Proyectos</h2>
        <form className="formulario-linea" onSubmit={crearProyecto}>
          <input
            placeholder="Nombre del proyecto nuevo"
            value={nombreProyecto}
            onChange={(e) => setNombreProyecto(e.target.value)}
          />
          <button className="principal" type="submit" disabled={!nombreProyecto.trim()}>
            Crear
          </button>
        </form>

        {proyectos.length === 0 ? (
          <p className="ficha nueva">Todavia no hay proyectos. Crea el primero.</p>
        ) : (
          <div className="rejilla">
            {proyectos.map((proyecto) => (
              <button
                key={proyecto.id}
                className="ficha"
                onClick={() => setElegido(proyecto)}
                style={
                  elegido?.id === proyecto.id ? { borderColor: 'var(--ambar)' } : undefined
                }
              >
                <div className="nombre">{proyecto.nombre}</div>
                <div className="detalle">
                  {proyecto.propietarioId === credencial.usuarioId ? 'Tuyo' : 'Compartido con vos'}
                </div>
              </button>
            ))}
          </div>
        )}

        {elegido && (
          <>
            <h2 style={{ marginTop: 30 }}>Diagramas de {elegido.nombre}</h2>
            <form className="formulario-linea" onSubmit={crearDiagrama}>
              <input
                placeholder="Nombre del diagrama nuevo"
                value={nombreDiagrama}
                onChange={(e) => setNombreDiagrama(e.target.value)}
              />
              <button className="principal" type="submit" disabled={!nombreDiagrama.trim()}>
                Crear y abrir
              </button>
            </form>

            {diagramas.length === 0 ? (
              <p className="ficha nueva">Este proyecto todavia no tiene diagramas.</p>
            ) : (
              <div className="rejilla">
                {diagramas.map((diagrama) => (
                  <button
                    key={diagrama.id}
                    className="ficha"
                    onClick={() => alAbrir(elegido, diagrama)}
                  >
                    <div className="nombre">{diagrama.nombre}</div>
                    <div className="detalle">
                      {diagrama.tipo === 'CLASES' ? 'Clases' : 'Secuencia'} · version{' '}
                      {diagrama.version}
                    </div>
                  </button>
                ))}
              </div>
            )}

            {elegido.propietarioId === credencial.usuarioId && (
              <>
                <h2 style={{ marginTop: 30 }}>Invitar a colaborar</h2>
                <form className="formulario-linea" onSubmit={invitar}>
                  <input
                    type="email"
                    placeholder="Correo de una cuenta ya registrada"
                    value={invitado}
                    onChange={(e) => setInvitado(e.target.value)}
                  />
                  <button type="submit" disabled={!invitado.trim()}>
                    Invitar como editor
                  </button>
                </form>
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
