import { useEffect, useRef, useState } from 'react'
import { ErrorApi, api } from '../api'
import { SESION_ID } from '../sesion'
import type { Lectura } from '../tipos'

/**
 * Construir el diagrama desde la foto de una pizarra.
 *
 * El flujo tiene tres pasos y el del medio es el que hace que esto funcione:
 * se elige la imagen, **se revisa el texto reconocido**, y solo entonces se
 * aplica. El reconocimiento sobre letra manuscrita nunca es exacto; poder
 * corregir dos caracteres antes de tocar el modelo es la diferencia entre una
 * funcion que se puede demostrar y una que depende de la suerte.
 *
 * El reconocimiento ocurre aqui, en el navegador, con el motor y los datos de
 * idioma servidos desde la propia aplicacion. No se sube la imagen: el servidor
 * no necesita la foto, necesita el texto, y hacerlo asi funciona sin conexion.
 */
export default function Foto({
  diagramaId,
  alAplicar,
  alCerrar,
}: {
  diagramaId: string
  alAplicar: () => void
  alCerrar: () => void
}) {
  const [imagen, setImagen] = useState<string | null>(null)
  const [texto, setTexto] = useState('')
  const [progreso, setProgreso] = useState<string | null>(null)
  const [lectura, setLectura] = useState<Lectura | null>(null)
  const [aviso, setAviso] = useState<{ clase: string; texto: string } | null>(null)
  const [trabajando, setTrabajando] = useState(false)

  const areaDeTexto = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    // La vista previa es un blob: hay que devolverlo o queda retenido.
    return () => {
      if (imagen) URL.revokeObjectURL(imagen)
    }
  }, [imagen])

  const reconocer = async (archivo: File) => {
    setImagen(URL.createObjectURL(archivo))
    setLectura(null)
    setAviso(null)
    setProgreso('Preparando el reconocimiento...')

    try {
      const { createWorker } = await import('tesseract.js')
      const trabajador = await createWorker(['spa', 'eng'], 1, {
        // Todo servido por la propia aplicacion: sin esto el motor lo buscaria
        // en internet y la funcion dependeria de la red del aula.
        workerPath: '/ocr/worker.min.js',
        corePath: '/ocr',
        langPath: '/ocr',
        gzip: false,
        logger: (mensaje: { status?: string; progress?: number }) => {
          if (mensaje.status) {
            const porcentaje = Math.round((mensaje.progress ?? 0) * 100)
            setProgreso(`${traducir(mensaje.status)} ${porcentaje}%`)
          }
        },
      })

      const { data } = await trabajador.recognize(archivo)
      await trabajador.terminate()

      setTexto(data.text.trim())
      setProgreso(null)
      areaDeTexto.current?.focus()
      if (!data.text.trim()) {
        setAviso({
          clase: 'informacion',
          texto: 'No se reconocio texto en la imagen. Proba con mas luz, mas de frente, '
            + 'o escribi el contenido a mano en el cuadro.',
        })
      }
    } catch (error) {
      setProgreso(null)
      setAviso({
        clase: 'error',
        texto: 'No se pudo iniciar el reconocimiento. Podes escribir el contenido de la '
          + 'pizarra en el cuadro y seguir igual. (' + String(error) + ')',
      })
    }
  }

  const revisar = async () => {
    if (!texto.trim()) return
    setTrabajando(true)
    setAviso(null)
    try {
      setLectura(await api.leerPizarra(diagramaId, texto, SESION_ID))
    } catch (e) {
      setAviso({ clase: 'error', texto: e instanceof ErrorApi ? e.message : 'No responde el servidor' })
    } finally {
      setTrabajando(false)
    }
  }

  const aplicar = async () => {
    if (!texto.trim()) return
    setTrabajando(true)
    setAviso(null)
    try {
      const resultado = await api.aplicarPizarra(diagramaId, texto, SESION_ID)
      if (resultado.aplicadas > 0) {
        alAplicar()
        setAviso({
          clase: 'bien',
          texto: `Se aplicaron ${resultado.aplicadas} cambios`
            + (resultado.problemas.length ? `. No entro: ${resultado.problemas[0]}` : ''),
        })
        setLectura(resultado.lectura)
      } else if (resultado.retenidoPor) {
        setAviso({
          clase: 'informacion',
          texto: `${resultado.retenidoPor} tiene tomado un elemento. Intenta en un momento.`,
        })
      } else {
        setAviso({ clase: 'informacion', texto: 'No se reconocio nada que aplicar.' })
      }
    } catch (e) {
      setAviso({ clase: 'error', texto: e instanceof ErrorApi ? e.message : 'No responde el servidor' })
    } finally {
      setTrabajando(false)
    }
  }

  return (
    <div className="telon" onClick={alCerrar}>
      <div className="dialogo foto" onClick={(e) => e.stopPropagation()}>
        <h2>Leer una pizarra</h2>

        <div className="pasos-foto">
          <label className="elegir-imagen">
            <span>1 · Elegir la foto</span>
            <input
              type="file"
              accept="image/*"
              // En un telefono abre la camara directamente.
              capture="environment"
              onChange={(e) => {
                const archivo = e.target.files?.[0]
                if (archivo) reconocer(archivo)
                e.target.value = ''
              }}
            />
          </label>
          {imagen && <img src={imagen} alt="La pizarra elegida" className="vista-previa" />}
        </div>

        {progreso && <div className="mensaje informacion">{progreso}</div>}
        {aviso && <div className={`mensaje ${aviso.clase}`}>{aviso.texto}</div>}

        <label htmlFor="texto-pizarra">2 · Revisar el texto</label>
        <p className="sutil" style={{ margin: '0 0 6px' }}>
          Corregi lo que el reconocimiento haya entendido mal antes de aplicarlo. Tambien podes
          escribirlo directamente.
        </p>
        <textarea
          id="texto-pizarra"
          ref={areaDeTexto}
          className="texto-pizarra"
          rows={12}
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value)
            setLectura(null)
          }}
          placeholder={EJEMPLO}
          spellCheck={false}
        />

        {lectura && <ResumenDeLectura lectura={lectura} />}

        <div className="botonera">
          <button onClick={alCerrar}>Cerrar</button>
          <button onClick={revisar} disabled={trabajando || !texto.trim()}>
            Ver que entiendo
          </button>
          <button className="principal" onClick={aplicar} disabled={trabajando || !texto.trim()}>
            3 · Aplicar al diagrama
          </button>
        </div>
      </div>
    </div>
  )
}

/** Lo que el servidor reconocio, antes de aplicarlo. */
function ResumenDeLectura({ lectura }: { lectura: Lectura }) {
  const nada = lectura.clases.length === 0 && lectura.relaciones.length === 0
  return (
    <div className="resumen-lectura">
      <h3>Entendi esto</h3>

      {nada && <p className="vacio">Ninguna clase. Revisa el formato del texto.</p>}

      {lectura.clases.map((clase) => (
        <div className="miembro-fila" key={clase.nombre}>
          <span>{clase.nombre}</span>
          {clase.estereotipo && <span className="insignia">«{clase.estereotipo}»</span>}
          {clase.esAbstracta && <span className="insignia">abstracta</span>}
          {clase.yaExistia ? (
            <span className="insignia" title="No se vuelve a crear ni se le tocan los miembros">
              ya estaba
            </span>
          ) : (
            <span style={{ color: 'var(--texto-debil)' }}>
              {clase.atributos} atributos · {clase.operaciones} operaciones
            </span>
          )}
        </div>
      ))}

      {lectura.relaciones.map((relacion, indice) => (
        <div className="miembro-fila" key={indice}>
          <span className="insignia">{relacion.tipo}</span>
          <span>
            {relacion.origen} → {relacion.destino}
          </span>
          <span style={{ color: 'var(--texto-debil)' }}>{relacion.multiplicidades}</span>
        </div>
      ))}

      {lectura.ignoradas.length > 0 && (
        <>
          {/* Lo que no se entendio se muestra con su numero de linea, en lugar de
              descartarse en silencio. */}
          <h3 style={{ marginTop: 14 }}>No entendi estas lineas</h3>
          <ul className="ignoradas">
            {lectura.ignoradas.map((linea) => (
              <li key={linea}>{linea}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function traducir(estado: string) {
  switch (estado) {
    case 'loading tesseract core':
    case 'loading core':
      return 'Cargando el motor'
    case 'initializing tesseract':
    case 'initializing api':
      return 'Iniciando'
    case 'loading language traineddata':
      return 'Cargando el idioma'
    case 'recognizing text':
      return 'Leyendo la pizarra'
    default:
      return estado
  }
}

const EJEMPLO = `Paciente
------------------
- historiaClinica: String {PK} (20)
+ nombre: String *
------------------
+ calcularEdad(): int

Consulta
fecha: DateTime
motivo: String

Paciente 1 *-- 0..* Consulta
Paciente --|> Persona`
