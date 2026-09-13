// Deja en public/ocr todo lo que el reconocimiento de texto necesita, para que
// el navegador no tenga que buscar nada en internet mientras se usa la
// aplicacion. Se ejecuta al instalar y antes de compilar.
//
// Los archivos NO se guardan en el repositorio: el motor sale de node_modules,
// que ya se descarga con npm install, y los datos de idioma se bajan una sola
// vez. Asi el repositorio no carga diez megas de binarios y la demostracion
// igual funciona sin conexion, que es lo que importa el dia de la defensa.

import { mkdir, copyFile, access, writeFile, stat } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..')
const destino = join(raiz, 'public', 'ocr')

// Se copian las tres variantes del motor porque tesseract.js elige una segun lo
// que el navegador soporte, y pedir la que falta terminaria en un 404.
const DEL_MOTOR = [
  ['node_modules/tesseract.js/dist/worker.min.js', 'worker.min.js'],
  ['node_modules/tesseract.js-core/tesseract-core-lstm.js', 'tesseract-core-lstm.js'],
  ['node_modules/tesseract.js-core/tesseract-core-lstm.wasm', 'tesseract-core-lstm.wasm'],
  ['node_modules/tesseract.js-core/tesseract-core-simd-lstm.js', 'tesseract-core-simd-lstm.js'],
  ['node_modules/tesseract.js-core/tesseract-core-simd-lstm.wasm', 'tesseract-core-simd-lstm.wasm'],
  ['node_modules/tesseract.js-core/tesseract-core-relaxedsimd-lstm.js', 'tesseract-core-relaxedsimd-lstm.js'],
  ['node_modules/tesseract.js-core/tesseract-core-relaxedsimd-lstm.wasm', 'tesseract-core-relaxedsimd-lstm.wasm'],
]

// Castellano y ingles: en una pizarra los nombres van en castellano pero los
// tipos se escriben en ingles -String, Date, int-, asi que hacen falta los dos.
const IDIOMAS = ['spa', 'eng']
const ORIGEN_IDIOMAS = 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main'

const existe = async (ruta) => access(ruta).then(() => true).catch(() => false)

await mkdir(destino, { recursive: true })

let copiados = 0
for (const [desde, nombre] of DEL_MOTOR) {
  const origen = join(raiz, desde)
  if (!(await existe(origen))) {
    console.warn(`  falta ${desde}: corre npm install antes`)
    continue
  }
  await copyFile(origen, join(destino, nombre))
  copiados++
}
console.log(`OCR: ${copiados} archivos del motor en public/ocr`)

for (const idioma of IDIOMAS) {
  const archivo = join(destino, `${idioma}.traineddata`)
  if (await existe(archivo)) {
    const { size } = await stat(archivo)
    console.log(`OCR: ${idioma}.traineddata ya estaba (${(size / 1048576).toFixed(1)} MB)`)
    continue
  }
  try {
    const respuesta = await fetch(`${ORIGEN_IDIOMAS}/${idioma}.traineddata`)
    if (!respuesta.ok) throw new Error(`HTTP ${respuesta.status}`)
    await writeFile(archivo, Buffer.from(await respuesta.arrayBuffer()))
    const { size } = await stat(archivo)
    console.log(`OCR: ${idioma}.traineddata descargado (${(size / 1048576).toFixed(1)} MB)`)
  } catch (error) {
    // No se interrumpe la instalacion: sin los datos de idioma el resto de la
    // aplicacion funciona igual, y la pantalla de la foto avisa que falta.
    console.warn(`OCR: no se pudo bajar ${idioma}.traineddata (${error.message}).`)
    console.warn('     Volve a correr: npm run preparar-ocr')
  }
}
