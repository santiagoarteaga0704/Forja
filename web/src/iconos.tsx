import type { TipoRelacion } from './tipos'

/**
 * Los iconos de la aplicacion, dibujados aca mismo.
 *
 * No hay libreria de iconos a proposito: son quince simbolos y traer un
 * paquete entero por eso sumaria peso y una dependencia mas que mantener. Al
 * estar escritos a mano tambien pueden decir cosas que ninguna libreria
 * generica tiene -una clase de UML con sus tres compartimientos, por ejemplo-,
 * que es justamente lo que conviene mostrar en una herramienta de modelado.
 *
 * Todos heredan el color del texto (`currentColor`) y miden 16, asi que
 * cambian de color con el boton que los contiene sin ninguna regla extra.
 */

interface PropsIcono {
  tamano?: number
}

function Marco({ tamano = 16, children }: PropsIcono & { children: React.ReactNode }) {
  return (
    <svg
      className="icono"
      width={tamano}
      height={tamano}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.5}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

export const IconoVolver = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M10 3 5 8l5 5" />
  </Marco>
)

export const IconoBajar = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M4 6.5 8 10.5l4-4" />
  </Marco>
)

/** Una clase de UML: cabecera y dos compartimientos. */
export const IconoClase = (p: PropsIcono) => (
  <Marco {...p}>
    <rect x="2.25" y="2.75" width="11.5" height="10.5" rx="0.5" />
    <path d="M2.25 6.25h11.5M2.25 10h11.5" />
  </Marco>
)

export const IconoBorrar = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M2.75 4.25h10.5M6.5 4.25V2.75h3v1.5M4.25 4.25l.6 8.25a.75.75 0 0 0 .75.75h4.8a.75.75 0 0 0 .75-.75l.6-8.25" />
  </Marco>
)

export const IconoMicrofono = (p: PropsIcono) => (
  <Marco {...p}>
    <rect x="5.75" y="1.75" width="4.5" height="7.5" rx="2.25" />
    <path d="M3.5 7.25a4.5 4.5 0 0 0 9 0M8 11.75v2.5" />
  </Marco>
)

export const IconoCamara = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M1.75 5.25A1.5 1.5 0 0 1 3.25 3.75h1.1l.9-1.5h5.5l.9 1.5h1.1a1.5 1.5 0 0 1 1.5 1.5v6.5a1.5 1.5 0 0 1-1.5 1.5H3.25a1.5 1.5 0 0 1-1.5-1.5z" />
    <circle cx="8" cy="8.25" r="2.5" />
  </Marco>
)

/** El agente guia: una brujula. Orienta, no ordena. */
export const IconoGuia = (p: PropsIcono) => (
  <Marco {...p}>
    <circle cx="8" cy="8" r="6.25" />
    <path d="m10.5 5.5-1.4 3.6-3.6 1.4 1.4-3.6z" />
  </Marco>
)

/** Generar: el diagrama se convierte en capas de codigo. */
export const IconoGenerar = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M8 1.75 14.25 5 8 8.25 1.75 5z" />
    <path d="m1.75 8 6.25 3.25L14.25 8M1.75 11l6.25 3.25L14.25 11" />
  </Marco>
)

/** Intercambio: sale un archivo, entra otro. */
export const IconoIntercambio = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M2.25 5.25h9.5l-2.5-2.5M13.75 10.75h-9.5l2.5 2.5" />
  </Marco>
)

export const IconoDescargar = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M8 2.25v7.5m0 0L5.25 7m2.75 2.75L10.75 7M2.75 12.25v1.5h10.5v-1.5" />
  </Marco>
)

export const IconoSubir = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M8 10.25v-7.5m0 0L5.25 5.5M8 2.75 10.75 5.5M2.75 12.25v1.5h10.5v-1.5" />
  </Marco>
)

export const IconoSalir = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M6.25 2.75h-3v10.5h3M9.5 5.25 12.75 8 9.5 10.75M12.75 8h-7" />
  </Marco>
)

export const IconoCarpeta = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M1.75 4.25a1 1 0 0 1 1-1h3l1.5 1.75h5a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-9.5a1 1 0 0 1-1-1z" />
  </Marco>
)

export const IconoMas = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M8 3.25v9.5M3.25 8h9.5" />
  </Marco>
)

/** Preguntarle al agente: una flecha que sale hacia el. */
export const IconoPreguntar = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="M2.25 8h10.5M8.75 4 12.75 8l-4 4" />
  </Marco>
)

/** Paso cumplido. */
export const IconoTilde = (p: PropsIcono) => (
  <Marco {...p}>
    <path d="m3 8.5 3.5 3.5L13 4" />
  </Marco>
)

export const IconoPersona = (p: PropsIcono) => (
  <Marco {...p}>
    <circle cx="8" cy="5.25" r="2.75" />
    <path d="M2.75 14a5.25 5.25 0 0 1 10.5 0" />
  </Marco>
)

/* ============================================================
   Los conectores de UML
   ============================================================ */

/**
 * El conector de UML tal como se dibuja en el diagrama.
 *
 * Se muestra en vez del nombre porque es lo que hay que reconocer despues en la
 * hoja. Quien elige "composicion" de una lista de palabras tiene que traducir
 * dos veces -palabra a simbolo y simbolo a significado-, y esa segunda
 * traduccion es la que se le pierde a quien esta aprendiendo UML.
 */
export function GlifoRelacion({ tipo, ancho = 52 }: { tipo: TipoRelacion; ancho?: number }) {
  const punteada = tipo === 'REALIZACION' || tipo === 'DEPENDENCIA'
  const rombo = tipo === 'AGREGACION' || tipo === 'COMPOSICION'
  const triangulo = tipo === 'HERENCIA' || tipo === 'REALIZACION'

  // La linea se recorta donde empieza el adorno para que no se vea por debajo.
  const desde = rombo ? 15 : 2
  const hasta = triangulo ? 39 : tipo === 'DEPENDENCIA' ? 48 : 50

  return (
    <svg
      className="glifo"
      width={ancho}
      height={12}
      viewBox="0 0 52 12"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.4}
      strokeLinecap="round"
      // Esquina viva en los adornos: un triangulo de herencia con las puntas
      // redondeadas se confunde con la flecha abierta de la dependencia, que es
      // justo la distincion que este selector existe para mostrar.
      strokeLinejoin="miter"
      aria-hidden="true"
    >
      <line x1={desde} y1={6} x2={hasta} y2={6} strokeDasharray={punteada ? '4 3' : undefined} />

      {rombo && (
        <polygon
          points="2,6 8.5,2.4 15,6 8.5,9.6"
          fill={tipo === 'COMPOSICION' ? 'currentColor' : 'none'}
        />
      )}

      {triangulo && <polygon points="50,6 39,1.6 39,10.4" fill="none" />}

      {tipo === 'DEPENDENCIA' && <polyline points="42,2 50,6 42,10" />}
    </svg>
  )
}
