/**
 * La hoja de la pantalla de entrada: el producto construyendose solo.
 *
 * No es una ilustracion de un diagrama terminado. Es una DEMOSTRACION: el
 * modelo se arma por etapas, y cada etapa es una de las tres vias de entrada
 * que tiene FORJA -dibujar, dictar, fotografiar-. Quien abre la herramienta por
 * primera vez ve lo que hace antes de leer una sola linea de texto, y la lista
 * de la derecha se enciende en sincronia, asi que la explicacion escrita y la
 * demostracion son el mismo objeto.
 *
 * La geometria se CALCULA, con las mismas reglas que `LineaRelacion` del lienzo
 * de verdad: donde la recta entre dos centros cruza el borde de la caja, el
 * rombo apoyado sobre esa direccion, las multiplicidades corridas hacia afuera.
 * Escribir las coordenadas a mano parecia mas corto y no lo es: sale un rombo
 * torcido y un rotulo debajo de una caja, y hay que rehacerlo igual.
 *
 * Con `prefers-reduced-motion` el diagrama aparece entero y quieto: no hay
 * ciclo, no hay trazado. La informacion es la misma.
 */

export type Via = 'dibujo' | 'voz' | 'foto'

export const VIAS: Via[] = ['dibujo', 'voz', 'foto']

interface Caja {
  nombre: string
  x: number
  y: number
  ancho: number
  filas: string[]
  via: Via
  /** Segundos desde que empieza su etapa. */
  retraso: number
}

const ALTO_CABECERA = 26
const ALTO_FILA = 17
const RELLENO_INFERIOR = 9

/*
 * Una clinica, en seis clases. Con tres el papel quedaba casi vacio y la
 * muestra no sostenia media pantalla; con seis se lee un modelo de verdad, del
 * tamano del que alguien construye en una clase.
 */
const HISTORIA: Caja = {
  nombre: 'HistoriaClinica',
  x: 24,
  y: 36,
  ancho: 168,
  filas: ['+ abierta: fecha', '+ resumen: texto'],
  via: 'dibujo',
  retraso: 0,
}
const PACIENTE: Caja = {
  nombre: 'Paciente',
  x: 24,
  y: 196,
  ancho: 168,
  filas: ['+ ci: texto  PK', '+ nombre: texto', '+ nacimiento: fecha'],
  via: 'dibujo',
  retraso: 0.45,
}
const CONSULTA: Caja = {
  nombre: 'Consulta',
  x: 272,
  y: 116,
  ancho: 160,
  filas: ['+ fecha: fecha', '+ motivo: texto', '+ diagnostico: texto'],
  via: 'voz',
  retraso: 0.9,
}
const MEDICO: Caja = {
  nombre: 'Médico',
  x: 512,
  y: 36,
  ancho: 164,
  filas: ['+ matrícula: texto', '+ nombre: texto', '+ especialidad: texto'],
  via: 'voz',
  retraso: 2.5,
}
const RECETA: Caja = {
  nombre: 'Receta',
  x: 272,
  y: 352,
  ancho: 160,
  filas: ['+ codigo: texto', '+ emitida: fecha'],
  via: 'foto',
  retraso: 1.1,
}
const MEDICAMENTO: Caja = {
  nombre: 'Medicamento',
  x: 512,
  y: 340,
  ancho: 164,
  filas: ['+ nombre: texto', '+ droga: texto', '+ stock: entero'],
  via: 'foto',
  retraso: 1.3,
}

const CAJAS = [HISTORIA, PACIENTE, CONSULTA, MEDICO, RECETA, MEDICAMENTO]

interface Vinculo {
  desde: Caja
  hasta: Caja
  rombo?: boolean
  cerca: string
  lejos: string
  via: Via
  retraso: number
}

const VINCULOS: Vinculo[] = [
  { desde: PACIENTE, hasta: HISTORIA, rombo: true, cerca: '1', lejos: '1', via: 'dibujo', retraso: 1.1 },
  { desde: PACIENTE, hasta: CONSULTA, rombo: true, cerca: '1', lejos: '0..*', via: 'voz', retraso: 1.7 },
  { desde: MEDICO, hasta: CONSULTA, cerca: '1', lejos: '0..*', via: 'voz', retraso: 3.2 },
  { desde: CONSULTA, hasta: RECETA, cerca: '1', lejos: '0..1', via: 'foto', retraso: 1.6 },
  { desde: RECETA, hasta: MEDICAMENTO, cerca: '1', lejos: '1..*', via: 'foto', retraso: 1.8 },
]

/** Lo que se dicta en la etapa de voz, con el momento en que se muestra. */
export const DICTADOS = [
  { frase: 'Paciente tiene muchas Consultas', desde: 0.2 },
  { frase: 'Médico atiende muchas Consultas', desde: 1.9 },
]

export default function HojaMuestra({ via, ciclo }: { via: Via; ciclo: number }) {
  const hasta = VIAS.indexOf(via)
  const seVe = (deElemento: Via) => VIAS.indexOf(deElemento) <= hasta

  return (
    <svg
      className="hoja-muestra"
      viewBox="0 0 700 470"
      role="img"
      aria-label="Un diagrama de clases de una clínica construyéndose: historia clínica, paciente, consulta, médico, receta y medicamento"
    >
      {/*
        El papel y su reticula NO se dibujan aca: los pinta el panel en CSS.
        Asi la cuadricula llega a los cuatro bordes de la pantalla por mas que
        el dibujo se escale, que es lo que hace que la hoja parezca seguir mas
        alla del marco en vez de ser una lamina pegada en el medio.
      */}

      {/* Los conectores van debajo de las cajas, como en el lienzo de verdad. */}
      {VINCULOS.filter((v) => seVe(v.via)).map((vinculo) => {
        const c = conector(vinculo.desde, vinculo.hasta, { rombo: vinculo.rombo })
        return (
          <g
            key={`${ciclo}-${vinculo.desde.nombre}-${vinculo.hasta.nombre}`}
            fill="none"
            stroke="var(--tinta-media)"
            strokeWidth="1.4"
          >
            <line
              className="trazo"
              style={retardo(vinculo.retraso)}
              x1={c.inicio.x}
              y1={c.inicio.y}
              x2={c.fin.x}
              y2={c.fin.y}
            />
            {c.rombo && (
              <polygon
                className="rotulo"
                style={retardo(vinculo.retraso + 0.3)}
                points={c.rombo}
                fill="var(--tinta-media)"
              />
            )}
            <g
              className="rotulo"
              style={retardo(vinculo.retraso + 0.4)}
              fontFamily="var(--mono)"
              fontSize="10"
              fill="var(--tinta-debil)"
              textAnchor="middle"
              stroke="none"
            >
              <text {...c.cerca}>{vinculo.cerca}</text>
              <text {...c.lejos}>{vinculo.lejos}</text>
            </g>
          </g>
        )
      })}

      {CAJAS.filter((caja) => seVe(caja.via)).map((caja) => {
        const alto = altoDe(caja)
        return (
          <g key={`${ciclo}-${caja.nombre}`}>
            <rect
              className="trazo"
              style={retardo(caja.retraso)}
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
              style={retardo(caja.retraso + 0.12)}
              x={caja.x}
              y={caja.y}
              width={caja.ancho}
              height={ALTO_CABECERA}
              fill="var(--caja-cabecera)"
              stroke="var(--canto-caja)"
              strokeWidth="1.2"
            />
            <text
              className="rotulo"
              style={retardo(caja.retraso + 0.18)}
              x={caja.x + caja.ancho / 2}
              y={caja.y + 17.5}
              textAnchor="middle"
              fontFamily="var(--sans)"
              fontSize="12"
              fontWeight="600"
              fill="var(--tinta)"
            >
              {caja.nombre}
            </text>
            {caja.filas.map((fila, indice) => (
              <text
                key={fila}
                className="rotulo"
                style={retardo(caja.retraso + 0.26 + indice * 0.06)}
                x={caja.x + 10}
                y={caja.y + ALTO_CABECERA + (indice + 1) * ALTO_FILA - 4}
                fontFamily="var(--mono)"
                fontSize="10.5"
                fill="var(--tinta-media)"
              >
                {fila}
              </text>
            ))}
          </g>
        )
      })}

      {/*
        La barrida de la etapa de foto. Es lo que hace entendible que esas dos
        clases no se dibujaron ni se dictaron: se leyeron de una imagen.
      */}
      {via === 'foto' && (
        <rect
          key={`barrido-${ciclo}`}
          className="barrido"
          x="0"
          y="0"
          width="700"
          height="26"
          fill="var(--fuego)"
          opacity="0.22"
        />
      )}
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

  /*
   * El corrimiento es PROPORCIONAL al largo de la linea, no fijo.
   *
   * Con 26 fijos, en un conector corto las dos multiplicidades caian casi en
   * el mismo punto y se leian encimadas -"0..*" sobre "1" daba un "01.*" que
   * no significa nada-. Un tercio del largo las deja siempre separadas, y el
   * tope de 26 evita que en una linea larga se vayan al medio, donde ya no se
   * sabe a que extremo pertenecen.
   */
  const largo = Math.hypot(b.x - a.x, b.y - a.y)
  const corrimiento = Math.min(18, largo / 3)

  const rotulo = (base: Punto, hacia: Punto) => {
    const p = avanzar(base, hacia, corrimiento)
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
