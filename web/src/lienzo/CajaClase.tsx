import { ANCHO_CLASE, altoDe } from '../modelo'
import type { AtributoVista, BloqueoVista, ClaseVista, MetodoVista, Visibilidad } from '../tipos'

interface Props {
  clase: ClaseVista
  seleccionada: boolean
  /** Quien la retiene, si alguien la retiene. */
  bloqueo?: BloqueoVista
  /** Cierto si el bloqueo es del propio usuario. */
  propio: boolean
  /** Cierto mientras se elige el segundo extremo de una relacion. */
  senalada: boolean
  alPresionar: (evento: React.MouseEvent) => void
}

/** Simbolos de visibilidad de UML. */
const SIMBOLO: Record<Visibilidad, string> = {
  PUBLICO: '+',
  PRIVADO: '-',
  PROTEGIDO: '#',
  PAQUETE: '~',
}

const ALTO_FILA = 18

/**
 * Una clase dibujada segun la notacion de UML: tres compartimientos -nombre,
 * atributos, operaciones-, el nombre en cursiva si es abstracta y el
 * estereotipo entre comillas francesas.
 *
 * Se dibuja con SVG y no con elementos HTML posicionados porque el lienzo tiene
 * que acercarse, alejarse y desplazarse como una sola pieza: una transformacion
 * en el grupo que las contiene mueve todo a la vez, y las lineas de relacion
 * quedan en el mismo sistema de coordenadas que las cajas.
 */
export default function CajaClase({
  clase,
  seleccionada,
  bloqueo,
  propio,
  senalada,
  alPresionar,
}: Props) {
  const alto = altoDe(clase)
  const cabecera = clase.estereotipo ? 44 : 30
  const ajeno = bloqueo && !propio

  const borde = ajeno
    ? 'var(--ajeno)'
    : seleccionada || senalada
      ? 'var(--ambar)'
      : 'var(--borde)'

  // Las posiciones se calculan por indice en lugar de ir acumulando una
  // variable mientras se dibuja: el orden en que React evalua el JSX no es algo
  // sobre lo que convenga apoyarse, y asi cada fila sabe donde va sin depender
  // de las anteriores.
  const inicioAtributos = cabecera + 10
  const yAtributo = (indice: number) => inicioAtributos + (indice + 1) * ALTO_FILA - 5
  const ySeparador = inicioAtributos + clase.atributos.length * ALTO_FILA + 6
  const yMetodo = (indice: number) => ySeparador + (indice + 1) * ALTO_FILA - 5

  return (
    <g
      className={`caja-clase${ajeno ? ' bloqueada' : ''}`}
      transform={`translate(${clase.posX}, ${clase.posY})`}
      onMouseDown={alPresionar}
    >
      <rect
        width={ANCHO_CLASE}
        height={alto}
        rx={4}
        fill="var(--superficie)"
        stroke={borde}
        strokeWidth={seleccionada || senalada || ajeno ? 2 : 1}
      />

      {/* Cabecera con un fondo apenas distinto: separa el nombre del contenido
          sin necesidad de una linea mas. */}
      <path
        d={`M 0 4 a 4 4 0 0 1 4 -4 h ${ANCHO_CLASE - 8} a 4 4 0 0 1 4 4 v ${cabecera - 4} h -${ANCHO_CLASE} z`}
        fill="var(--superficie-alta)"
      />
      <line x1={0} y1={cabecera} x2={ANCHO_CLASE} y2={cabecera} stroke={borde} />

      {clase.estereotipo && (
        <text
          x={ANCHO_CLASE / 2}
          y={16}
          textAnchor="middle"
          fontSize={10.5}
          fill="var(--ambar-claro)"
          fontFamily="var(--sans)"
        >
          {`«${clase.estereotipo}»`}
        </text>
      )}
      <text
        x={ANCHO_CLASE / 2}
        y={clase.estereotipo ? 34 : 20}
        textAnchor="middle"
        fontSize={13}
        fontWeight={600}
        fontStyle={clase.esAbstracta ? 'italic' : 'normal'}
        fill="var(--texto)"
        fontFamily="var(--sans)"
      >
        {clase.nombre}
      </text>

      {clase.atributos.map((atributo, indice) => (
        <text
          key={atributo.id}
          x={9}
          y={yAtributo(indice)}
          fontSize={11.5}
          fill="var(--texto-medio)"
          fontFamily="var(--mono)"
        >
          {textoDeAtributo(atributo)}
        </text>
      ))}

      {clase.metodos.length > 0 && (
        <line x1={0} y1={ySeparador} x2={ANCHO_CLASE} y2={ySeparador} stroke="var(--borde-suave)" />
      )}

      {clase.metodos.map((metodo, indice) => (
        <text
          key={metodo.id}
          x={9}
          y={yMetodo(indice)}
          fontSize={11.5}
          fill="var(--texto-medio)"
          fontFamily="var(--mono)"
          fontStyle={metodo.esAbstracto ? 'italic' : 'normal'}
        >
          {textoDeMetodo(metodo)}
        </text>
      ))}

      {ajeno && (
        <>
          <rect
            x={0}
            y={-20}
            width={Math.min(ANCHO_CLASE, 9 + bloqueo.poseedorNombre.length * 6.2)}
            height={16}
            rx={3}
            fill="var(--ajeno)"
          />
          <text x={5} y={-8} fontSize={10} fill="#fff" fontFamily="var(--sans)">
            {bloqueo.poseedorNombre} esta editando
          </text>
        </>
      )}
      {propio && (
        <circle cx={ANCHO_CLASE - 9} cy={9} r={3.5} fill="var(--ambar)" />
      )}
    </g>
  )
}

function textoDeAtributo(atributo: AtributoVista) {
  const marcas = [
    atributo.esIdentificador ? 'PK' : null,
    atributo.esUnico && !atributo.esIdentificador ? 'U' : null,
    atributo.esRequerido ? '*' : null,
  ].filter(Boolean)

  const longitud = atributo.longitud ? `(${atributo.longitud})` : ''
  const sufijo = marcas.length ? ` ${marcas.join(' ')}` : ''
  return `${SIMBOLO[atributo.visibilidad]} ${atributo.nombre}: ${atributo.tipo}${longitud}${sufijo}`
}

function textoDeMetodo(metodo: MetodoVista) {
  const estatico = metodo.esEstatico ? ' {static}' : ''
  return `${SIMBOLO[metodo.visibilidad]} ${metodo.nombre}(): ${metodo.tipoRetorno}${estatico}`
}
