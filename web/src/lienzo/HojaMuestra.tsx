/**
 * La hoja de muestra de la pantalla de entrada.
 *
 * Es lo primero que se ve de FORJA, asi que muestra exactamente lo que FORJA
 * hace: un diagrama de clases sobre papel, con la notacion correcta -el rombo
 * lleno de la composicion, las multiplicidades, los simbolos de visibilidad-.
 * Se dibuja sola una vez al abrir, en el mismo orden en que la dibujaria una
 * persona: primero las cajas, despues los conectores, al final los rotulos.
 *
 * Es el unico movimiento no pedido en toda la aplicacion. Un solo momento
 * ordenado se nota; media docena de apariciones sueltas por pantalla se leen
 * como relleno. Con `prefers-reduced-motion` aparece ya terminada.
 *
 * La geometria se CALCULA, con las mismas reglas que `LineaRelacion`: donde la
 * recta entre dos centros cruza el borde de la caja, el rombo apoyado sobre esa
 * direccion, las multiplicidades corridas hacia afuera de la linea. Escribir
 * las coordenadas a mano parecia mas corto y no lo es: sale un rombo torcido y
 * un rotulo debajo de una caja, y hay que rehacerlo igual.
 */

interface Caja {
  nombre: string
  x: number
  y: number
  ancho: number
  filas: string[]
}

const ALTO_CABECERA = 26
const ALTO_FILA = 17
const RELLENO_INFERIOR = 9

const PACIENTE: Caja = {
  nombre: 'Paciente',
  x: 26,
  y: 92,
  ancho: 158,
  filas: ['+ ci: texto  PK', '+ nombre: texto', '+ nacimiento: fecha'],
}
const CONSULTA: Caja = {
  nombre: 'Consulta',
  x: 330,
  y: 30,
  ancho: 152,
  filas: ['+ fecha: fecha', '+ motivo: texto'],
}
const MEDICO: Caja = {
  nombre: 'Médico',
  x: 330,
  y: 198,
  ancho: 152,
  filas: ['+ matrícula: texto', '+ nombre: texto'],
}

const CAJAS = [PACIENTE, CONSULTA, MEDICO]

export default function HojaMuestra() {
  // Paciente compone a sus Consultas; con Medico solo se asocia.
  const compone = conector(PACIENTE, CONSULTA, { rombo: true })
  const atiende = conector(PACIENTE, MEDICO, {})

  return (
    <svg
      className="hoja-muestra"
      viewBox="0 0 512 300"
      role="img"
      aria-label="Diagrama de clases con Paciente, Consulta y Médico, en notación UML"
    >
      <defs>
        <pattern id="muestra-reticula" width="60" height="60" patternUnits="userSpaceOnUse">
          <path
            d="M12 0v60M24 0v60M36 0v60M48 0v60M0 12h60M0 24h60M0 36h60M0 48h60"
            fill="none"
            stroke="var(--reticula)"
            strokeWidth="1"
          />
          <path d="M60 0H0v60" fill="none" stroke="var(--reticula-mayor)" strokeWidth="1" />
        </pattern>
      </defs>

      <rect width="512" height="300" fill="var(--papel)" />
      <rect width="512" height="300" fill="url(#muestra-reticula)" />

      {/* Los conectores van debajo de las cajas, como en el lienzo de verdad. */}
      {[compone, atiende].map((c, indice) => (
        <g key={indice} fill="none" stroke="var(--tinta-media)" strokeWidth="1.4">
          <line
            className="trazo"
            style={retardo(0.7 + indice * 0.14)}
            x1={c.inicio.x}
            y1={c.inicio.y}
            x2={c.fin.x}
            y2={c.fin.y}
          />
          {c.rombo && (
            <polygon
              className="rotulo"
              style={retardo(1.0)}
              points={c.rombo}
              fill="var(--tinta-media)"
            />
          )}
        </g>
      ))}

      <g
        className="rotulo"
        style={retardo(1.12)}
        fontFamily="var(--mono)"
        fontSize="9.5"
        fill="var(--tinta-debil)"
        textAnchor="middle"
      >
        <text {...compone.cerca}>1</text>
        <text {...compone.lejos}>0..*</text>
        <text {...atiende.cerca}>*</text>
        <text {...atiende.lejos}>1</text>
      </g>

      {CAJAS.map((caja, indice) => {
        const alto = altoDe(caja)
        return (
          <g key={caja.nombre}>
            <rect
              className="trazo"
              style={retardo(indice * 0.16)}
              x={caja.x}
              y={caja.y}
              width={caja.ancho}
              height={alto}
              fill="var(--caja)"
              stroke="var(--canto-caja)"
              strokeWidth="1.2"
            />
            <rect
              className="rotulo"
              style={retardo(indice * 0.16 + 0.3)}
              x={caja.x}
              y={caja.y}
              width={caja.ancho}
              height={ALTO_CABECERA}
              fill="var(--caja-cabecera)"
            />
            <line
              className="rotulo"
              style={retardo(indice * 0.16 + 0.3)}
              x1={caja.x}
              y1={caja.y + ALTO_CABECERA}
              x2={caja.x + caja.ancho}
              y2={caja.y + ALTO_CABECERA}
              stroke="var(--canto-caja)"
              strokeWidth="1.2"
            />
            <text
              className="rotulo"
              style={retardo(indice * 0.16 + 0.42)}
              x={caja.x + caja.ancho / 2}
              y={caja.y + 17.5}
              textAnchor="middle"
              fontFamily="var(--sans)"
              fontSize="12.5"
              fontWeight="600"
              fill="var(--tinta)"
            >
              {caja.nombre}
            </text>
            {caja.filas.map((fila, fi) => (
              <text
                key={fila}
                className="rotulo"
                style={retardo(indice * 0.16 + 0.5 + fi * 0.06)}
                x={caja.x + 9}
                y={caja.y + ALTO_CABECERA + 13 + fi * ALTO_FILA}
                fontFamily="var(--mono)"
                fontSize="10"
                fill="var(--tinta-media)"
              >
                {fila}
              </text>
            ))}
          </g>
        )
      })}
    </svg>
  )
}

// ---------- Geometria -------------------------------------------------------

interface Punto {
  x: number
  y: number
}

function altoDe(caja: Caja) {
  return ALTO_CABECERA + caja.filas.length * ALTO_FILA + RELLENO_INFERIOR
}

function centro(caja: Caja): Punto {
  return { x: caja.x + caja.ancho / 2, y: caja.y + altoDe(caja) / 2 }
}

/** Donde la recta entre dos centros cruza el borde del rectangulo. */
function borde(caja: Caja, propio: Punto, ajeno: Punto): Punto {
  const dx = ajeno.x - propio.x
  const dy = ajeno.y - propio.y
  const escala = Math.min(
    dx === 0 ? Infinity : caja.ancho / 2 / Math.abs(dx),
    dy === 0 ? Infinity : altoDe(caja) / 2 / Math.abs(dy),
  )
  return { x: propio.x + dx * escala, y: propio.y + dy * escala }
}

function avanzar(desde: Punto, hacia: Punto, distancia: number): Punto {
  const dx = hacia.x - desde.x
  const dy = hacia.y - desde.y
  const largo = Math.hypot(dx, dy) || 1
  return { x: desde.x + (dx / largo) * distancia, y: desde.y + (dy / largo) * distancia }
}

function perpendicular(desde: Punto, hacia: Punto): Punto {
  const dx = hacia.x - desde.x
  const dy = hacia.y - desde.y
  const largo = Math.hypot(dx, dy) || 1
  return { x: -dy / largo, y: dx / largo }
}

/**
 * Un conector entre dos cajas, con su rombo y el sitio de cada multiplicidad.
 *
 * Las multiplicidades se corren 26 a lo largo de la linea -lo justo para
 * despegarse de la caja- y 9 hacia el mismo lado en las dos, para que se lean
 * como un par y no como dos rotulos sueltos.
 */
function conector(desde: Caja, hasta: Caja, { rombo }: { rombo?: boolean }) {
  const centroDesde = centro(desde)
  const centroHasta = centro(hasta)
  const a = borde(desde, centroDesde, centroHasta)
  const b = borde(hasta, centroHasta, centroDesde)

  const n = perpendicular(a, b)
  const rotulo = (base: Punto, hacia: Punto) => {
    const p = avanzar(base, hacia, 26)
    return { x: p.x - n.x * 9, y: p.y - n.y * 9 + 3 }
  }

  const medio = avanzar(a, b, 7)
  const punta = avanzar(a, b, 14)

  return {
    // La linea arranca despues del rombo para no verse por debajo.
    inicio: rombo ? punta : a,
    fin: b,
    rombo: rombo
      ? [
          `${a.x},${a.y}`,
          `${medio.x + n.x * 4.5},${medio.y + n.y * 4.5}`,
          `${punta.x},${punta.y}`,
          `${medio.x - n.x * 4.5},${medio.y - n.y * 4.5}`,
        ].join(' ')
      : null,
    cerca: rotulo(a, b),
    lejos: rotulo(b, a),
  }
}

function retardo(segundos: number): React.CSSProperties {
  return { animationDelay: `${segundos.toFixed(2)}s` }
}
