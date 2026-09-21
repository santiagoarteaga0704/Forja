// Saca una foto de una parte del documento ya compuesto, para mirarla sin
// tener que abrir el PDF. Util para revisar una tabla o una figura despues de
// tocar el markdown.
//
// Uso:  node herramientas/ver-documento.mjs "CU13" salida.png
//
// El primer argumento es un texto a buscar; se fotografia desde ahi.

import { readFileSync, existsSync } from 'node:fs'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { marked } from 'marked'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '..')
const docs = join(raiz, 'docs')

const buscado = process.argv[2] ?? 'CU13'
const salida = process.argv[3] ?? join(aqui, 'vista.png')

let md = readFileSync(join(docs, 'documento.md'), 'utf8')
md = md.replace(/!\[([^\]]*)\]\((diagramas\/[^)]+)\)/g, (_, alt, ruta) => {
  const archivo = join(docs, ruta)
  if (!existsSync(archivo)) return `![${alt}]()`
  return `![${alt}](data:image/png;base64,${readFileSync(archivo).toString('base64')})`
})

const html = `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
body { font-family: system-ui, sans-serif; font-size: 14px; line-height: 1.55;
       color: #1c2128; margin: 0; padding: 24px; max-width: 820px; }
h2 { font-size: 20px; border-bottom: 1px solid #d7dbe0; padding-bottom: 5px; }
h4 { font-size: 15px; margin: 18px 0 7px; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 13px; }
th, td { border: 1px solid #ccd1d7; padding: 6px 9px; text-align: left; vertical-align: top; }
th { background: #f1f3f5; }
code { background: #f1f3f5; padding: 1px 4px; border-radius: 2px; font-size: 12px; }
img { max-width: 100%; display: block; margin: 12px auto; }
</style></head><body>${marked.parse(md, { gfm: true })}</body></html>`

const navegador = await chromium.launch()
const pagina = await navegador.newPage({ viewport: { width: 900, height: 1100 } })
await pagina.setContent(html, { waitUntil: 'load' })

const encontrado = await pagina.evaluate((texto) => {
  const nodos = [...document.querySelectorAll('h1,h2,h3,h4,p,strong')]
  const n = nodos.find((e) => e.textContent.includes(texto))
  if (!n) return null
  n.scrollIntoView()
  return n.textContent.slice(0, 90)
}, buscado)

if (!encontrado) {
  console.error(`no se encontro "${buscado}" en el documento`)
  await navegador.close()
  process.exit(1)
}
console.log('encontrado: ' + encontrado)
await pagina.waitForTimeout(300)
await pagina.screenshot({ path: salida })
console.log('foto en ' + salida)
await navegador.close()
