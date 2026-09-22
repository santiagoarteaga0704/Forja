import { useState } from 'react'
import type {
  BloqueoVista,
  ClaseVista,
  Comando,
  RelacionVista,
  TipoOperacion,
  Visibilidad,
} from '../tipos'
import { nuevoId } from '../id'

interface Props {
  clase?: ClaseVista
  relacion?: RelacionVista
  clases: ClaseVista[]
  relaciones?: RelacionVista[]
  bloqueo?: BloqueoVista
  usuarioId: string
  alEnviar: (tipo: TipoOperacion, comando: Comando, descripcion: string) => Promise<boolean>
  alSenalar?: (elementoId: string) => void
}

const VISIBILIDADES: Visibilidad[] = ['PRIVADO', 'PUBLICO', 'PROTEGIDO', 'PAQUETE']

/**
 * Panel de edicion de lo que este seleccionado.
 *
 * Los atributos y los metodos se agregan y se quitan, pero no se modifican en
 * el lugar. Es una consecuencia buscada del modelo de comandos: cada cambio del
 * diagrama queda en la bitacora como una operacion con significado propio, y un
 * comando "editar atributo" que pudiera cambiarle el nombre, el tipo y las
 * marcas a la vez registraria varios cambios distintos bajo una sola entrada.
 * Para corregir un atributo se lo quita y se lo vuelve a agregar, que es lo que
 * de verdad ocurrio.
 */
export default function PanelClase({
  clase,
  relacion,
  clases,
  relaciones = [],
  bloqueo,
  usuarioId,
  alEnviar,
  alSenalar,
}: Props) {
  if (relacion) {
    const origen = clases.find((c) => c.id === relacion.origenId)
    const destino = clases.find((c) => c.id === relacion.destinoId)
    return (
      <>
        <h3>Relación</h3>
        <div className="seccion">
          <p style={{ margin: '0 0 10px' }}>
            <span className="insignia">{relacion.tipo}</span>
          </p>
          <div className="miembro-fila">
            <span style={{ color: 'var(--acero-debil)' }}>desde</span>
            <span>{origen?.nombre ?? '?'}</span>
            <span className="insignia">{relacion.multiplicidadOrigen}</span>
          </div>
          <div className="miembro-fila">
            <span style={{ color: 'var(--acero-debil)' }}>hasta</span>
            <span>{destino?.nombre ?? '?'}</span>
            <span className="insignia">{relacion.multiplicidadDestino}</span>
          </div>
          <p className="vacio" style={{ marginTop: 12 }}>
            Para cambiarla, eliminala y volve a trazarla.
          </p>
        </div>
      </>
    )
  }

  /*
   * Sin nada seleccionado, el panel muestra el INDICE del diagrama.
   *
   * Antes era un parrafo de ayuda en una columna de 380px que quedaba vacia
   * casi todo el tiempo. El indice ocupa ese lugar con lo unico que ahi tiene
   * sentido: que hay en el modelo y donde esta. Ademas resuelve un problema
   * real -una clase arrastrada lejos del resto no se encuentra- porque cada
   * nombre lleva a su caja.
   */
  if (!clase) {
    return (
      <>
        <h3>El diagrama</h3>

        <div className="indice-cifras">
          <span>
            <b>{clases.length}</b>
            {clases.length === 1 ? 'clase' : 'clases'}
          </span>
          <span>
            <b>{relaciones.length}</b>
            {relaciones.length === 1 ? 'relación' : 'relaciones'}
          </span>
        </div>

        {clases.length === 0 ? (
          <p className="vacio">
            La hoja está en blanco. Creá una clase con el botón de arriba, dictala o sacale una
            foto a la pizarra.
          </p>
        ) : (
          <ul className="indice">
            {[...clases]
              .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
              .map((c) => (
                <li key={c.id}>
                  <button type="button" onClick={() => alSenalar?.(c.id)}>
                    <span className={`indice-nombre${c.esAbstracta ? ' abstracta' : ''}`}>
                      {c.nombre}
                    </span>
                    <span className="indice-cuenta">
                      {c.atributos.length}a · {c.metodos.length}m
                    </span>
                  </button>
                </li>
              ))}
          </ul>
        )}

        <p className="vacio indice-ayuda">
          Hacé clic en una clase para editarla, o arrastrala para moverla. Con la rueda se acerca y
          arrastrando el fondo se desplaza la hoja.
        </p>
      </>
    )
  }

  const ajeno = bloqueo && bloqueo.poseedorId !== usuarioId

  return (
    <ContenidoDeClase
      key={clase.id}
      clase={clase}
      ajeno={!!ajeno}
      nombreDelPoseedor={bloqueo?.poseedorNombre}
      alEnviar={alEnviar}
    />
  )
}

/**
 * Se separa en un componente propio con `key` por clase para que los campos de
 * texto se reinicien al cambiar de seleccion: sin eso, el nombre editado de una
 * clase aparecia en el formulario de la siguiente.
 */
function ContenidoDeClase({
  clase,
  ajeno,
  nombreDelPoseedor,
  alEnviar,
}: {
  clase: ClaseVista
  ajeno: boolean
  nombreDelPoseedor?: string
  alEnviar: Props['alEnviar']
}) {
  const [nombre, setNombre] = useState(clase.nombre)
  const [estereotipo, setEstereotipo] = useState(clase.estereotipo ?? '')

  // Otro usuario puede renombrar la clase mientras esta seleccionada aqui. El
  // ajuste se hace durante el render comparando contra el valor anterior, que es
  // la forma que React recomienda para esto: con un efecto habria un render
  // intermedio mostrando el texto viejo.
  const [ultimoDelModelo, setUltimoDelModelo] = useState({
    nombre: clase.nombre,
    estereotipo: clase.estereotipo ?? '',
  })
  if (
    ultimoDelModelo.nombre !== clase.nombre ||
    ultimoDelModelo.estereotipo !== (clase.estereotipo ?? '')
  ) {
    setUltimoDelModelo({ nombre: clase.nombre, estereotipo: clase.estereotipo ?? '' })
    setNombre(clase.nombre)
    setEstereotipo(clase.estereotipo ?? '')
  }

  const [atributo, setAtributo] = useState({
    nombre: '',
    tipo: 'String',
    visibilidad: 'PRIVADO' as Visibilidad,
    esIdentificador: false,
    esRequerido: false,
    esUnico: false,
    longitud: '',
  })
  const [metodo, setMetodo] = useState({
    nombre: '',
    tipoRetorno: 'void',
    visibilidad: 'PUBLICO' as Visibilidad,
    esAbstracto: false,
  })

  const renombrar = () => {
    const limpio = nombre.trim()
    if (!limpio || limpio === clase.nombre) return
    alEnviar('CLASE_RENOMBRAR', { claseId: clase.id, nombre: limpio }, 'renombrar')
  }

  const marcar = (cambios: { estereotipo?: string; esAbstracta?: boolean }) =>
    alEnviar(
      'CLASE_MARCAR',
      {
        claseId: clase.id,
        estereotipo: cambios.estereotipo ?? estereotipo,
        esAbstracta: cambios.esAbstracta ?? clase.esAbstracta,
      },
      'marcar',
    )

  const agregarAtributo = (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!atributo.nombre.trim()) return
    alEnviar(
      'ATRIBUTO_AGREGAR',
      {
        claseId: clase.id,
        atributoId: nuevoId(),
        nombre: atributo.nombre.trim(),
        tipo: atributo.tipo.trim() || 'String',
        visibilidad: atributo.visibilidad,
        esIdentificador: atributo.esIdentificador,
        esRequerido: atributo.esRequerido || atributo.esIdentificador,
        esUnico: atributo.esUnico || atributo.esIdentificador,
        longitud: atributo.longitud ? Number(atributo.longitud) : null,
      },
      'agregar atributo',
    ).then((bien) => {
      if (bien) setAtributo({ ...atributo, nombre: '', longitud: '' })
    })
  }

  const agregarMetodo = (evento: React.FormEvent) => {
    evento.preventDefault()
    if (!metodo.nombre.trim()) return
    alEnviar(
      'METODO_AGREGAR',
      {
        claseId: clase.id,
        metodoId: nuevoId(),
        nombre: metodo.nombre.trim(),
        tipoRetorno: metodo.tipoRetorno.trim() || 'void',
        visibilidad: metodo.visibilidad,
        esAbstracto: metodo.esAbstracto,
        esEstatico: false,
        parametros: [],
      },
      'agregar metodo',
    ).then((bien) => {
      if (bien) setMetodo({ ...metodo, nombre: '' })
    })
  }

  return (
    <>
      {ajeno && (
        <div className="aviso-bloqueo">
          {nombreDelPoseedor} tiene tomada esta clase. Podes verla pero no cambiarla hasta que la
          suelte.
        </div>
      )}

      <h3>Clase</h3>
      <div className="seccion">
        <label htmlFor="nombre-clase">Nombre</label>
        <input
          id="nombre-clase"
          value={nombre}
          disabled={ajeno}
          onChange={(e) => setNombre(e.target.value)}
          onBlur={renombrar}
          onKeyDown={(e) => {
            if (e.key === 'Enter') e.currentTarget.blur()
            if (e.key === 'Escape') setNombre(clase.nombre)
          }}
        />

        <div style={{ marginTop: 10 }}>
          <label htmlFor="estereotipo">Estereotipo</label>
          <input
            id="estereotipo"
            placeholder="interface, enumeration, catálogo…"
            value={estereotipo}
            disabled={ajeno}
            onChange={(e) => setEstereotipo(e.target.value)}
            onBlur={() => {
              if ((clase.estereotipo ?? '') !== estereotipo) marcar({ estereotipo })
            }}
          />
        </div>

        <label className="casilla" style={{ marginTop: 10 }}>
          <input
            type="checkbox"
            checked={clase.esAbstracta}
            disabled={ajeno}
            onChange={(e) => marcar({ esAbstracta: e.target.checked })}
          />
          Es abstracta
        </label>
      </div>

      <h3>Atributos</h3>
      <div className="seccion">
        {clase.atributos.length === 0 && <p className="vacio">Todavía no tiene atributos.</p>}
        {clase.atributos.map((a) => (
          <div className="miembro-fila" key={a.id}>
            <span>
              {a.nombre}: {a.tipo}
              {a.longitud ? `(${a.longitud})` : ''}
            </span>
            {a.esIdentificador && <span className="insignia">PK</span>}
            {a.esRequerido && !a.esIdentificador && <span className="insignia">req</span>}
            {!ajeno && (
              <button
                className="quitar"
                title="Quitar"
                onClick={() =>
                  alEnviar(
                    'ATRIBUTO_ELIMINAR',
                    { claseId: clase.id, atributoId: a.id },
                    'quitar atributo',
                  )
                }
              >
                ×
              </button>
            )}
          </div>
        ))}

        {!ajeno && (
          <form className="alta-embebida" onSubmit={agregarAtributo}>
            <div className="par">
              <input
                placeholder="nombre"
                value={atributo.nombre}
                onChange={(e) => setAtributo({ ...atributo, nombre: e.target.value })}
              />
              <input
                placeholder="tipo"
                value={atributo.tipo}
                onChange={(e) => setAtributo({ ...atributo, tipo: e.target.value })}
              />
            </div>
            <div className="par">
              <select
                value={atributo.visibilidad}
                onChange={(e) =>
                  setAtributo({ ...atributo, visibilidad: e.target.value as Visibilidad })
                }
              >
                {VISIBILIDADES.map((v) => (
                  <option key={v} value={v}>
                    {v.toLowerCase()}
                  </option>
                ))}
              </select>
              <input
                placeholder="longitud"
                inputMode="numeric"
                value={atributo.longitud}
                onChange={(e) => setAtributo({ ...atributo, longitud: e.target.value })}
              />
            </div>
            <div className="casillas">
              <label className="casilla">
                <input
                  type="checkbox"
                  checked={atributo.esIdentificador}
                  onChange={(e) =>
                    setAtributo({ ...atributo, esIdentificador: e.target.checked })
                  }
                />
                clave
              </label>
              <label className="casilla">
                <input
                  type="checkbox"
                  checked={atributo.esRequerido}
                  onChange={(e) => setAtributo({ ...atributo, esRequerido: e.target.checked })}
                />
                obligatorio
              </label>
              <label className="casilla">
                <input
                  type="checkbox"
                  checked={atributo.esUnico}
                  onChange={(e) => setAtributo({ ...atributo, esUnico: e.target.checked })}
                />
                unico
              </label>
            </div>
            <button type="submit">
              Agregar atributo
            </button>
          </form>
        )}
      </div>

      <h3>Operaciones</h3>
      <div className="seccion">
        {clase.metodos.length === 0 && <p className="vacio">Todavía no tiene operaciones.</p>}
        {clase.metodos.map((m) => (
          <div className="miembro-fila" key={m.id}>
            <span style={{ fontStyle: m.esAbstracto ? 'italic' : 'normal' }}>
              {m.nombre}(): {m.tipoRetorno}
            </span>
            {!ajeno && (
              <button
                className="quitar"
                title="Quitar"
                onClick={() =>
                  alEnviar('METODO_ELIMINAR', { claseId: clase.id, metodoId: m.id }, 'quitar metodo')
                }
              >
                ×
              </button>
            )}
          </div>
        ))}

        {!ajeno && (
          <form className="alta-embebida" onSubmit={agregarMetodo}>
            <div className="par">
              <input
                placeholder="nombre"
                value={metodo.nombre}
                onChange={(e) => setMetodo({ ...metodo, nombre: e.target.value })}
              />
              <input
                placeholder="retorno"
                value={metodo.tipoRetorno}
                onChange={(e) => setMetodo({ ...metodo, tipoRetorno: e.target.value })}
              />
            </div>
            <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
              <select
                value={metodo.visibilidad}
                onChange={(e) =>
                  setMetodo({ ...metodo, visibilidad: e.target.value as Visibilidad })
                }
              >
                {VISIBILIDADES.map((v) => (
                  <option key={v} value={v}>
                    {v.toLowerCase()}
                  </option>
                ))}
              </select>
              <label className="casilla">
                <input
                  type="checkbox"
                  checked={metodo.esAbstracto}
                  onChange={(e) => setMetodo({ ...metodo, esAbstracto: e.target.checked })}
                />
                abstracta
              </label>
            </div>
            <button type="submit">
              Agregar operacion
            </button>
          </form>
        )}
      </div>
    </>
  )
}
