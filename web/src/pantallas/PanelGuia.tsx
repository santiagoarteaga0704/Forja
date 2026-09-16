import type { CategoriaConsejo, Consejo } from '../tipos'

/**
 * Los consejos del agente guia.
 *
 * Cada tarjeta muestra las tres partes que el agente produce -que noto, por que
 * importa, como se hace- y no solo la primera. Un aviso que dice "Paciente no
 * tiene atributos" y nada mas obliga a la persona a deducir si eso es un
 * problema; decirle que su tabla va a salir con una sola columna le da lo que
 * necesita para decidir.
 *
 * La instruccion es dictable, asi que se ofrece como texto copiable: el mismo
 * parser que entiende el dictado entiende esa frase.
 */
export default function PanelGuia({
  consejos,
  cargando,
  alDescartar,
  alSenalar,
}: {
  consejos: Consejo[]
  cargando: boolean
  alDescartar: (id: string) => void
  /** Selecciona en el lienzo el elemento del que habla el consejo. */
  alSenalar: (elementoId: string) => void
}) {
  if (cargando && consejos.length === 0) {
    return (
      <>
        <h3>Guía</h3>
        <p className="vacio">Mirando el diagrama…</p>
      </>
    )
  }

  if (consejos.length === 0) {
    return (
      <>
        <h3>Guía</h3>
        <p className="vacio">
          Nada que señalar por ahora. El agente vuelve a mirar el diagrama con cada cambio.
        </p>
      </>
    )
  }

  return (
    <>
      <h3>
        Guía <span className="cuenta">{consejos.length}</span>
      </h3>
      {consejos.map((consejo) => (
        <article key={consejo.id} className={`consejo ${consejo.categoria.toLowerCase()}`}>
          <header>
            <span className="etiqueta">{etiqueta(consejo.categoria)}</span>
            <button
              className="quitar"
              title="No mostrar más este aviso"
              aria-label="Descartar"
              onClick={() => alDescartar(consejo.id)}
            >
              ×
            </button>
          </header>

          <p className="que">{consejo.queNote}</p>
          <p className="porque">{consejo.porQueImporta}</p>
          <p className="como">{consejo.comoSeHace}</p>

          {consejo.elementoId && (
            <button className="senalar" onClick={() => alSenalar(consejo.elementoId!)}>
              Ver en el lienzo
            </button>
          )}
        </article>
      ))}
    </>
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
