/**
 * La marca de la casa: un yunque cortado a 45 grados.
 *
 * Esta dibujado aca adentro y no traido como <img src="/marca/...">
 * por dos razones. La primera es que asi hereda los tokens de color de la
 * hoja de estilos -el cuerpo es --fuego y la cara --fuego-claro-, de modo que
 * hay un solo ambar en toda la aplicacion y no dos parecidos. La segunda es
 * que un <img> aparece despues del primer pintado; la cabecera no puede
 * parpadear sin logo.
 *
 * Los archivos sueltos de public/marca/ son para lo que vive fuera de la
 * aplicacion: el favicon, las diapositivas, el documento.
 *
 * El contorno esta trazado sobre una grilla de 64 y cada corte interno es de
 * 45 grados, el mismo angulo con el que el editor dibuja las relaciones. El
 * rombo del talon va calado -fill-rule evenodd, no pintado encima- asi que
 * deja pasar el fondo y funciona sobre cualquier superficie.
 *
 * Tamano minimo de uso: 20. Por debajo de eso el rombo se cierra y hay que
 * usar forja-favicon.svg, que es la silueta sola.
 */

const CUERPO =
  'M3 20 L18 12 L53 12 L59 18 L59 28 L44 28 L38 34 L38 40 L45 47 L46 47 ' +
  'L46 53 L17 53 L17 47 L18 47 L25 40 L25 34 L19 28 L14 28 Z ' +
  'M46 17.5 L50.5 22 L46 26.5 L41.5 22 Z'

/** El bisel de arriba: el metal caliente. Da volumen sin un degradado. */
const CARA = 'M18 12 L53 12 L57 16 L10.5 16 Z'

export function Yunque({ tamano = 22 }: { tamano?: number }) {
  return (
    <svg
      className="yunque"
      width={tamano}
      height={tamano}
      viewBox="0 0 64 64"
      aria-hidden="true"
    >
      <path fill="var(--fuego)" fillRule="evenodd" d={CUERPO} />
      <path fill="var(--fuego-claro)" d={CARA} />
    </svg>
  )
}

/**
 * El lockup completo. El logotipo es texto de verdad y no parte del dibujo:
 * asi se lee con un lector de pantalla, se busca con Ctrl+F y usa la Plex que
 * viaja empaquetada con la aplicacion.
 */
export function Marca({ tamano }: { tamano?: number }) {
  return (
    <div className="marca">
      <Yunque tamano={tamano} />
      FORJA
    </div>
  )
}
