import { ANCHO_CLASE, altoDe } from '../modelo'
import type { ClaseVista, RelacionVista } from '../tipos'

interface Props {
  relacion: RelacionVista
  origen: ClaseVista
  destino: ClaseVista
  seleccionada: boolean
  alElegir: (evento: React.MouseEvent) => void
}

/**
 * Una relacion con el adorno que le corresponde en UML.
 *
 * Los adornos no son decoracion: son lo que distingue una agregacion de una
 * composicion y una herencia de una realizacion, y es lo unico que permite leer
 * el diagrama sin consultar aparte que tipo tiene cada linea. El rombo va en el
 * extremo del todo, el triangulo en el de la clase general, y la linea punteada
 * marca las relaciones que no implican contencion.
 */
export default function LineaRelacion({
  relacion,
  origen,
  destino,
  seleccionada,
  alElegir,
}: Props) {
  const centroOrigen = centro(origen)
  const centroDestino = centro(destino)

  // La autoasociacion no tiene direccion: se dibuja como un lazo sobre el borde
  // superior de la clase, que es la convencion habitual.
  if (origen.id === destino.id) {
    return (
      <g onMouseDown={alElegir} style={{ cursor: 'pointer' }}>
        <path
          d={`M ${origen.posX + ANCHO_CLASE - 40} ${origen.posY}
              C ${origen.posX + ANCHO_CLASE - 20} ${origen.posY - 46},
                ${origen.posX + ANCHO_CLASE + 30} ${origen.posY - 20},
                ${origen.posX + ANCHO_CLASE} ${origen.posY + 22}`}
          fill="none"
          stroke={seleccionada ? 'var(--fuego)' : 'var(--tinta-media)'}
          strokeWidth={seleccionada ? 2 : 1.4}
        />
      </g>
    )
  }

  const desde = borde(origen, centroOrigen, centroDestino)
  const hasta = borde(destino, centroDestino, centroOrigen)

  const punteada = relacion.tipo === 'REALIZACION' || relacion.tipo === 'DEPENDENCIA'
  const color = seleccionada ? 'var(--fuego)' : 'var(--tinta-media)'
  const grosor = seleccionada ? 2 : 1.4

  // Los adornos se recortan de la linea para que no queden dibujados encima.
  const rombo = relacion.tipo === 'AGREGACION' || relacion.tipo === 'COMPOSICION'
  const triangulo = relacion.tipo === 'HERENCIA' || relacion.tipo === 'REALIZACION'

  const inicio = rombo ? avanzar(desde, hasta, 14) : desde
  const fin = triangulo ? avanzar(hasta, desde, 12) : hasta

  return (
    <g onMouseDown={alElegir} style={{ cursor: 'pointer' }}>
      {/* Trazo invisible y grueso: hace que la linea se pueda tomar con el
          raton sin obligar a acertarle a un pixel. */}
      <line
        x1={desde.x}
        y1={desde.y}
        x2={hasta.x}
        y2={hasta.y}
        stroke="transparent"
        strokeWidth={12}
      />
      <line
        x1={inicio.x}
        y1={inicio.y}
        x2={fin.x}
        y2={fin.y}
        stroke={color}
        strokeWidth={grosor}
        strokeDasharray={punteada ? '7 5' : undefined}
      />

      {rombo && (
        <polygon
          points={puntosRombo(desde, hasta)}
          fill={relacion.tipo === 'COMPOSICION' ? color : 'var(--caja)'}
          stroke={color}
          strokeWidth={grosor}
        />
      )}

      {triangulo && (
        <polygon
          points={puntosTriangulo(hasta, desde)}
          fill="var(--caja)"
          stroke={color}
          strokeWidth={grosor}
        />
      )}

      {relacion.tipo === 'DEPENDENCIA' && (
        <polyline
          points={puntosFlechaAbierta(hasta, desde)}
          fill="none"
          stroke={color}
          strokeWidth={grosor}
        />
      )}

      {/* Multiplicidades: pegadas a su extremo, apenas separadas de la caja. */}
      {relacion.tipo !== 'HERENCIA' && relacion.tipo !== 'REALIZACION' && (
        <>
          <text
            {...corrido(desde, hasta, 20)}
            fontSize={10.5}
            fill="var(--tinta-debil)"
            fontFamily="var(--mono)"
          >
            {relacion.multiplicidadOrigen}
          </text>
          <text
            {...corrido(hasta, desde, 20)}
            fontSize={10.5}
            fill="var(--tinta-debil)"
            fontFamily="var(--mono)"
          >
            {relacion.multiplicidadDestino}
          </text>
        </>
      )}

      {relacion.etiqueta && (
        <text
          x={(desde.x + hasta.x) / 2}
          y={(desde.y + hasta.y) / 2 - 6}
          textAnchor="middle"
          fontSize={10.5}
          fill="var(--tinta-media)"
          fontFamily="var(--sans)"
        >
          {relacion.etiqueta}
        </text>
      )}
    </g>
  )
}

interface Punto {
  x: number
  y: number
}

function centro(clase: ClaseVista): Punto {
  return { x: clase.posX + ANCHO_CLASE / 2, y: clase.posY + altoDe(clase) / 2 }
}

/**
 * Punto en que la linea entre dos centros cruza el borde de la caja.
 *
 * Se resuelve escalando el vector hasta el primero de los dos bordes que
 * alcanza, horizontal o vertical. Es mas simple que intersecar cuatro segmentos
 * y da el mismo resultado para un rectangulo.
 */
function borde(clase: ClaseVista, propio: Punto, ajeno: Punto): Punto {
  const dx = ajeno.x - propio.x
  const dy = ajeno.y - propio.y
  if (dx === 0 && dy === 0) return propio

  const medioAncho = ANCHO_CLASE / 2
  const medioAlto = altoDe(clase) / 2

  const escalaX = dx === 0 ? Infinity : medioAncho / Math.abs(dx)
  const escalaY = dy === 0 ? Infinity : medioAlto / Math.abs(dy)
  const escala = Math.min(escalaX, escalaY)

  return { x: propio.x + dx * escala, y: propio.y + dy * escala }
}

function avanzar(desde: Punto, hacia: Punto, distancia: number): Punto {
  const dx = hacia.x - desde.x
  const dy = hacia.y - desde.y
  const largo = Math.hypot(dx, dy) || 1
  return { x: desde.x + (dx / largo) * distancia, y: desde.y + (dy / largo) * distancia }
}

function corrido(desde: Punto, hacia: Punto, distancia: number) {
  const punto = avanzar(desde, hacia, distancia)
  return { x: punto.x, y: punto.y - 4, textAnchor: 'middle' as const }
}

function puntosRombo(punta: Punto, hacia: Punto): string {
  const eje = avanzar(punta, hacia, 14)
  const perpendicular = normalPerpendicular(punta, hacia)
  const medio = avanzar(punta, hacia, 7)
  return [
    `${punta.x},${punta.y}`,
    `${medio.x + perpendicular.x * 4.5},${medio.y + perpendicular.y * 4.5}`,
    `${eje.x},${eje.y}`,
    `${medio.x - perpendicular.x * 4.5},${medio.y - perpendicular.y * 4.5}`,
  ].join(' ')
}

function puntosTriangulo(punta: Punto, hacia: Punto): string {
  const base = avanzar(punta, hacia, 12)
  const perpendicular = normalPerpendicular(punta, hacia)
  return [
    `${punta.x},${punta.y}`,
    `${base.x + perpendicular.x * 5.5},${base.y + perpendicular.y * 5.5}`,
    `${base.x - perpendicular.x * 5.5},${base.y - perpendicular.y * 5.5}`,
  ].join(' ')
}

function puntosFlechaAbierta(punta: Punto, hacia: Punto): string {
  const base = avanzar(punta, hacia, 11)
  const perpendicular = normalPerpendicular(punta, hacia)
  return [
    `${base.x + perpendicular.x * 5},${base.y + perpendicular.y * 5}`,
    `${punta.x},${punta.y}`,
    `${base.x - perpendicular.x * 5},${base.y - perpendicular.y * 5}`,
  ].join(' ')
}

function normalPerpendicular(desde: Punto, hacia: Punto): Punto {
  const dx = hacia.x - desde.x
  const dy = hacia.y - desde.y
  const largo = Math.hypot(dx, dy) || 1
  return { x: -dy / largo, y: dx / largo }
}
