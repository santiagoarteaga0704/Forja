import { useEffect, useState } from 'react'
import { ErrorApi, api } from '../api'
import { useGuia } from '../guia'
import { IconoCarpeta, IconoClase, IconoGuia, IconoPersona, IconoSalir } from '../iconos'
import { Marca } from '../marca'
import type { Credencial, DiagramaResumen, ProyectoVista } from '../tipos'
import Guia from './Guia'

interface Props {
  credencial: Credencial
  alAbrir: (proyecto: ProyectoVista, diagrama: DiagramaResumen) => void
  alSalir: () => void
}

/**
 * Cuando se toco algo, en palabras.
 *
 * Una fila con solo un nombre no deja elegir: todas se ven igual. La fecha es
 * el dato con el que de verdad se busca un proyecto -"el que estuve tocando
 * ayer"- y en forma relativa se lee sin restar mentalmente.
 */
function desdeCuando(fecha: string): string {
  const minutos = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000)
  if (!Number.isFinite(minutos) || minutos < 0) return ''
  if (minutos < 1) return 'recién'
  if (minutos < 60) return `hace ${minutos} min`
  const horas = Math.floor(minutos / 60)
  if (horas < 24) return `hace ${horas} h`
  const dias = Math.floor(horas / 24)
  if (dias === 1) return 'ayer'
  if (dias < 30) return `hace ${dias} días`
  return new Date(fecha).toLocaleDateString('es', { day: 'numeric', month: 'short' })
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
  const [mostrandoGuia, setMostrandoGuia] = useState(false)

  // El agente tambien vive aca: sin diagrama abierto solo puede hablar de la
  // herramienta, que es lo que necesita quien todavia no creo ninguno.
  const { guia, descartar } = useGuia(null, proyectos.length + diagramas.length)

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
        <Marca />
        <div className="crece" />
        <button
          aria-pressed={mostrandoGuia}
          onClick={() => setMostrandoGuia(!mostrandoGuia)}
          title="Consejos del agente guía"
        >
          <IconoGuia />
          Guía
          {(guia?.consejos.length ?? 0) > 0 && (
            <span className="contador">{guia!.consejos.length}</span>
          )}
        </button>
        <div className="separador" />
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

          {/*
            La cabecera lleva el titulo de la pantalla y su accion principal en
            la misma linea. Antes el alta era un campo suelto flotando sobre la
            lista, sin nada que lo agrupara, y eso -mas que ningun color- era lo
            que hacia ver la pantalla sin terminar.
          */}
          <header className="pagina-cabecera">
            <div>
              <h1>Proyectos</h1>
              <p className="pagina-bajada">
                Un proyecto agrupa los diagramas de un mismo sistema y la gente que puede
                editarlos.
              </p>
            </div>

            <form className="alta-en-linea" onSubmit={crearProyecto}>
              <input
                aria-label="Nombre del proyecto nuevo"
                placeholder="Nombre del proyecto nuevo"
                value={nombreProyecto}
                onChange={(e) => setNombreProyecto(e.target.value)}
              />
              <button className="principal" type="submit" disabled={!nombreProyecto.trim()}>
                Crear proyecto
              </button>
            </form>
          </header>

          {/*
            Maestro y detalle, en dos columnas. Antes los tres bloques
            -proyectos, diagramas, invitaciones- se apilaban a lo largo de una
            sola columna angosta: habia que bajar para ver lo que contenia lo
            que se acababa de elegir, y la mitad derecha de la pantalla no se
            usaba nunca. Elegir a la izquierda y ver a la derecha es como se
            recorre una herramienta.
          */}
          <div className={`tablero${elegido ? '' : ' sin-eleccion'}`}>
          <section className="bloque">
            <div className="seccion-titulo">
              <h2>Tus proyectos</h2>
              {proyectos.length > 0 && (
                <span className="cuenta">
                  {proyectos.length === 1 ? '1 proyecto' : `${proyectos.length} proyectos`}
                </span>
              )}
            </div>

            {proyectos.length === 0 ? (
              <div className="vacio-bloque">
                <p style={{ margin: '0 0 12px' }}>
                  Todavía no tenés proyectos. Un proyecto agrupa los diagramas de un mismo sistema
                  y la gente que puede editarlos.
                </p>
                {/* La forma de descubrir al agente no puede ser descubrir al
                    agente: desde el unico lugar donde alguien se queda sin saber
                    que hacer, hay una puerta que lleva hasta el. */}
                <button onClick={() => setMostrandoGuia(true)}>
                  <IconoGuia />
                  ¿Por dónde empiezo?
                </button>
              </div>
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
                    <span className="fila-meta">{desdeCuando(proyecto.actualizadoEn)}</span>
                    <span
                      className={`etiqueta${
                        proyecto.propietarioId === credencial.usuarioId ? ' propia' : ''
                      }`}
                    >
                      {proyecto.propietarioId === credencial.usuarioId
                        ? 'Tuyo'
                        : 'Compartido con vos'}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>

          {!elegido && proyectos.length > 0 && (
            <aside className="detalle-sin-elegir">
              <IconoCarpeta tamano={26} />
              <p>Elegí un proyecto de la lista para ver sus diagramas y a quién invitaste.</p>
            </aside>
          )}

          {elegido && (
            <div className="tablero-detalle">
              <section className="bloque">
                <div className="seccion-titulo">
                  <h2>Diagramas de {elegido.nombre}</h2>
                  {diagramas.length > 0 && (
                    <span className="cuenta">
                      {diagramas.length === 1 ? '1 diagrama' : `${diagramas.length} diagramas`}
                    </span>
                  )}
                </div>

                <form className="alta-en-linea alta-bloque" onSubmit={crearDiagrama}>
                  <input
                    aria-label="Nombre del diagrama nuevo"
                    placeholder="Nombre del diagrama nuevo"
                    value={nombreDiagrama}
                    onChange={(e) => setNombreDiagrama(e.target.value)}
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
                  <form className="alta-en-linea alta-bloque" onSubmit={invitar}>
                    <input
                      type="email"
                      aria-label="Correo de una cuenta ya registrada"
                      placeholder="Correo de una cuenta ya registrada"
                      value={invitado}
                      onChange={(e) => setInvitado(e.target.value)}
                    />
                    <button type="submit" disabled={!invitado.trim()}>
                      Invitar como editor
                    </button>
                  </form>
                </section>
              )}
            </div>
          )}
          </div>
        </div>
      </div>

      {mostrandoGuia && (
        <Guia
          guia={guia}
          enUnDiagrama={false}
          alDescartar={descartar}
          alCerrar={() => setMostrandoGuia(false)}
        />
      )}
    </div>
  )
}
