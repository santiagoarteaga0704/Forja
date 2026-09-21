import { useEffect, useRef, useState } from 'react'
import { ErrorApi, api } from '../api'
import { IconoCamara } from '../iconos'
import { SESION_ID } from '../sesion'
import type { Lectura } from '../tipos'

/**
 * Construir el diagrama desde la foto de una pizarra.
 *
 * El flujo tiene tres pasos y el del medio es el que hace que esto funcione:
 * se elige la imagen, **se revisa el texto leido**, y solo entonces se aplica.
 * Ninguna lectura de una foto es exacta; poder corregir antes de tocar el
 * modelo es la diferencia entre una funcion que se puede demostrar y una que
 * depende de la suerte.
 *
 * **La lee un modelo de vision, en el servidor.** Antes la leia un motor de
 * reconocimiento de caracteres aqui mismo, sin conexion, y eso tenia una
 * virtud: la imagen no salia de la maquina. Lo que no podia era leer un
 * diagrama DIBUJADO, y es el que la gente fotografia. El reconocimiento lee
 * texto, y en un diagrama las relaciones son flechas: medido el 21 de
 * septiembre de 2026 sobre un diagrama de nueve clases, devolvia renglones que
 * cruzaban tres cajas y ni una relacion. El modelo de vision devolvio las nueve
 * clases con sus veintiun atributos y ocho de sus diez relaciones, en 3,2 s.
 *
 * El precio esta escrito en la pantalla: por esta via **la imagen sale de la
 * maquina**, y sin conexion la lectura no esta. Por eso el cuadro de texto
 * sigue ahi y se puede escribir o pegar a mano, que es el camino que nunca
 * depende de nadie.
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

  // En que paso esta la lectura. Se deduce del estado en vez de guardarse
  // aparte: guardarlo abriria la puerta a que el indicador y la pantalla
  // discrepen, que es el error clasico de los asistentes por pasos.
  const paso = !texto.trim() ? 1 : lectura ? 3 : 2

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
    setProgreso('Leyendo la imagen…')

    try {
      const { texto: leido } = await api.transcribirFoto(diagramaId, archivo)
      setProgreso(null)
      setTexto(leido)
      areaDeTexto.current?.focus()
      if (!leido.trim()) {
        setAviso({
          clase: 'informacion',
          texto: 'No se reconoció nada en la imagen. Probá con más luz y más de frente, '
            + 'o escribí el contenido en el cuadro.',
        })
      }
    } catch (error) {
      setProgreso(null)
      setAviso({
        clase: 'error',
        texto: error instanceof ErrorApi && error.estado === 503
          ? 'La lectura de imágenes no está configurada en este servidor. Podés escribir '
            + 'el contenido de la pizarra en el cuadro y seguir igual.'
          : 'No se pudo leer la imagen. Podés escribir el contenido en el cuadro y seguir '
            + 'igual. (' + (error instanceof ErrorApi ? error.message : String(error)) + ')',
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
            + (resultado.problemas.length ? `. No entró: ${resultado.problemas[0]}` : ''),
        })
        setLectura(resultado.lectura)
      } else if (resultado.retenidoPor) {
        setAviso({
          clase: 'informacion',
          texto: `${resultado.retenidoPor} tiene tomado un elemento. Intentá en un momento.`,
        })
      } else {
        setAviso({ clase: 'informacion', texto: 'No se reconoció nada que aplicar.' })
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
        <p className="bajada">
          La imagen se manda al servidor, que la lee con un modelo de visión. Revisá el texto
          antes de aplicarlo: lo que entra al diagrama lo decidís vos.
        </p>

        {/*
          Los tres pasos se muestran porque son una secuencia de verdad, y
          porque el del medio -revisar- es el que hace que esto funcione. Sobre
          letra manuscrita el reconocimiento nunca es exacto, y que se vea que
          hay un paso de revision antes de tocar el modelo evita que alguien
          espere magia y lo aplique a ciegas.
        */}
        <ol className="pasos">
          {['Elegir la foto', 'Revisar el texto', 'Aplicar'].map((nombre, indice) => (
            <li
              key={nombre}
              className={indice + 1 < paso ? 'hecho' : indice + 1 === paso ? 'aqui' : ''}
            >
              {nombre}
            </li>
          ))}
        </ol>

        <div className="pasos-foto">
          <label className="elegir-imagen">
            <span>
              <IconoCamara />
              {imagen ? 'Elegir otra foto' : 'Elegir la foto'}
            </span>
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

        <label htmlFor="texto-pizarra">Revisar el texto</label>
        <p className="sutil" style={{ margin: '0 0 6px' }}>
          Corregí lo que la lectura haya entendido mal antes de aplicarlo. También podés
          escribirlo a mano, que es el camino que funciona siempre.
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
            Ver qué entiendo
          </button>
          <button className="principal" onClick={aplicar} disabled={trabajando || !texto.trim()}>
            Aplicar al diagrama
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
      <h3>Esto entendí</h3>

      {nada && <p className="vacio">Ninguna clase. Revisá el formato del texto.</p>}

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
            <span style={{ color: 'var(--acero-debil)' }}>
              {clase.atributos} atributos, {clase.operaciones} operaciones
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
          <span style={{ color: 'var(--acero-debil)' }}>{relacion.multiplicidades}</span>
        </div>
      ))}

      {lectura.ignoradas.length > 0 && (
        <>
          {/* Lo que no se entendio se muestra con su numero de linea, en lugar de
              descartarse en silencio. */}
          <h3 style={{ marginTop: 14 }}>Estas líneas no las entendí</h3>
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
