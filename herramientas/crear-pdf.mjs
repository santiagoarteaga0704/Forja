// Compone docs/documento.pdf a partir de docs/documento.md.
//
// Se hace con Chromium y no con pandoc para que el PDF salga con la misma
// tipografia que la aplicacion, y para poder medir cada figura ya compuesta y
// avisar si alguna no entra en la pagina.
//
// Uso:  node herramientas/crear-pdf.mjs
// Requiere playwright y marked. Si no estan, el guion lo dice y termina.
//
// DOS TRAMPAS QUE YA COSTARON UNA TARDE CADA UNA:
//
//  1. Las fuentes se leen de web/node_modules/@fontsource y se incrustan como
//     data: URI. Traerlas de Google Fonts NO funciona: la pagina se compone
//     antes de que lleguen y el PDF sale en Arial sin un solo aviso, porque el
//     respaldo tipografico es silencioso.
//
//  2. Las figuras tambien van como data: URI. setContent() no tiene URL base,
//     asi que una ruta relativa a docs/diagramas se resuelve contra la nada y
//     la imagen sale VACIA, tambien sin aviso.

import { readFileSync, existsSync, writeFileSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '..')
const docs = join(raiz, 'docs')
const fuentesDir = join(raiz, 'web', 'node_modules', '@fontsource')

let chromium, marked
try {
  ;({ chromium } = await import('playwright'))
  ;({ marked } = await import('marked'))
} catch (e) {
  console.error(
    'Faltan dependencias. Desde una carpeta cualquiera con node:\n' +
      '  npm install playwright marked\n' +
      'y volve a correr esto con NODE_PATH apuntando ahi, o instalalas en web/.\n' +
      'Detalle: ' + e.message,
  )
  process.exit(1)
}

// ---------- Fuentes ---------------------------------------------------------

const fuente = (familia, archivo) => {
  const ruta = join(fuentesDir, familia, 'files', archivo)
  if (!existsSync(ruta)) throw new Error('no esta la fuente ' + ruta)
  return 'data:font/woff2;base64,' + readFileSync(ruta).toString('base64')
}

const caras = [
  ['IBM Plex Sans', 400, 'normal', fuente('ibm-plex-sans', 'ibm-plex-sans-latin-400-normal.woff2')],
  ['IBM Plex Sans', 600, 'normal', fuente('ibm-plex-sans', 'ibm-plex-sans-latin-600-normal.woff2')],
  ['IBM Plex Sans', 700, 'normal', fuente('ibm-plex-sans', 'ibm-plex-sans-latin-700-normal.woff2')],
  ['IBM Plex Sans', 400, 'italic', fuente('ibm-plex-sans', 'ibm-plex-sans-latin-400-italic.woff2')],
  ['IBM Plex Mono', 400, 'normal', fuente('ibm-plex-mono', 'ibm-plex-mono-latin-400-normal.woff2')],
]

const arroba = caras
  .map(
    ([familia, peso, estilo, datos]) => `@font-face{
      font-family:'${familia}';font-weight:${peso};font-style:${estilo};
      font-display:block;src:url('${datos}') format('woff2');}`,
  )
  .join('\n')

// ---------- El documento ----------------------------------------------------

let md = readFileSync(join(docs, 'documento.md'), 'utf8')

// Las figuras, incrustadas. Ver la trampa 2 del encabezado.
const figuras = []
md = md.replace(/!\[([^\]]*)\]\((diagramas\/[^)]+)\)/g, (_, alt, ruta) => {
  const archivo = join(docs, ruta)
  if (!existsSync(archivo)) throw new Error('falta la figura ' + archivo)
  figuras.push(ruta)
  const datos = readFileSync(archivo).toString('base64')
  return `![${alt}](data:image/png;base64,${datos})`
})
console.log(`figuras incrustadas: ${figuras.length}`)
figuras.forEach((f) => console.log('   ' + f))

const cuerpo = marked.parse(md, { gfm: true, breaks: false })

const html = `<!doctype html><html lang="es"><head><meta charset="utf-8">
<style>
${arroba}
@page { size: A4; margin: 20mm 18mm; }
body {
  font-family: 'IBM Plex Sans', system-ui, sans-serif;
  font-size: 10.5pt; line-height: 1.55; color: #1c2128; margin: 0;
}
h1 { font-size: 20pt; font-weight: 700; margin: 0 0 14pt; page-break-after: avoid; }
h2 { font-size: 15pt; font-weight: 600; margin: 22pt 0 8pt; page-break-after: avoid;
     border-bottom: 1px solid #d7dbe0; padding-bottom: 4pt; }
h3 { font-size: 12pt; font-weight: 600; margin: 16pt 0 6pt; page-break-after: avoid; }
h4 { font-size: 11pt; font-weight: 600; margin: 13pt 0 5pt; page-break-after: avoid; }
p, li { orphans: 3; widows: 3; }
code, pre { font-family: 'IBM Plex Mono', monospace; font-size: 9pt; }
code { background: #f1f3f5; padding: 0.5pt 3pt; border-radius: 2pt; }
pre { background: #f6f8fa; border: 1px solid #e1e4e8; border-radius: 3pt;
      padding: 8pt 10pt; overflow-x: auto; page-break-inside: avoid; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; width: 100%; margin: 9pt 0; font-size: 9.5pt;
        page-break-inside: avoid; }
th, td { border: 1px solid #ccd1d7; padding: 4pt 7pt; text-align: left;
         vertical-align: top; }
th { background: #f1f3f5; font-weight: 600; }
/* El alto tambien se limita, no solo el ancho: los dos diagramas de casos de
   uso son altos y angostos, y con solo max-width entraban 1292 px en una caja
   de 971 y se cortaban al imprimir. 235mm deja aire para el pie de figura. */
img { max-width: 100%; max-height: 235mm; width: auto; height: auto;
      display: block; margin: 10pt auto; page-break-inside: avoid; }
blockquote { margin: 10pt 0; padding: 6pt 12pt; border-left: 3px solid #b8bec6;
             background: #f8f9fa; color: #3a424c; }
hr { border: none; border-top: 1px solid #d7dbe0; margin: 18pt 0; }
a { color: #1c2128; text-decoration: none; }
</style></head><body>${cuerpo}</body></html>`

// ---------- Componer --------------------------------------------------------

const navegador = await chromium.launch()
const pagina = await navegador.newPage()
await pagina.setContent(html, { waitUntil: 'load' })
await pagina.evaluate(() => document.fonts.ready)

// Comprobacion 1: que las fuentes de verdad se hayan usado. Sin esto el PDF
// puede salir entero en Arial y nadie se entera hasta imprimirlo.
const usadas = await pagina.evaluate(() =>
  [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family + ' ' + f.weight),
)
console.log('fuentes cargadas: ' + (usadas.length ? usadas.join(', ') : 'NINGUNA'))
if (!usadas.length) throw new Error('no cargo ninguna fuente: el PDF saldria en Arial')

// Comprobacion 2: que ninguna figura sea mas alta que la caja de la pagina.
// A4 menos margenes = 257mm de alto util, a 96 dpi son unos 971 px.
const ALTO_UTIL = 971
const grandes = await pagina.evaluate(
  (limite) =>
    [...document.images]
      .map((img, i) => ({ i, alto: img.getBoundingClientRect().height }))
      .filter((x) => x.alto > limite),
  ALTO_UTIL,
)
if (grandes.length) {
  grandes.forEach((g) =>
    console.warn(`AVISO: la figura ${g.i + 1} mide ${Math.round(g.alto)} px y no entra en la pagina`),
  )
} else {
  console.log('todas las figuras entran en la pagina')
}

const salida = join(docs, 'documento.pdf')
await pagina.pdf({
  path: salida,
  format: 'A4',
  printBackground: true,
  margin: { top: '20mm', bottom: '20mm', left: '18mm', right: '18mm' },
  displayHeaderFooter: true,
  headerTemplate: '<div></div>',
  footerTemplate:
    '<div style="width:100%;font-size:8pt;color:#767c85;text-align:center;' +
    "font-family:sans-serif;\">FORJA <span class='pageNumber'></span> / <span class='totalPages'></span></div>",
})

const paginas = await pagina.evaluate(() => document.body.scrollHeight)
await navegador.close()

const bytes = readFileSync(salida).length
console.log(`\n${salida}  (${(bytes / 1024 / 1024).toFixed(2)} MB)`)
console.log('alto del documento compuesto: ' + paginas + ' px')
