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
//
// De cada variante va el archivo `.wasm.js`, que trae el wasm adentro, y NO el
// par `.js` + `.wasm`. El paquete publica los dos y es facil elegir el que no
// es: el par sirve cuando se nombra el archivo exacto, pero aqui `corePath` es
// una carpeta y en ese caso el worker construye el nombre el mismo y siempre
// pide el `.wasm.js`. Con el par copiado el reconocimiento no arrancaba nunca.
//
// Son `-lstm` porque la pantalla crea el trabajador con OEM 1 -solo el modelo
// LSTM-, asi que las variantes con el motor antiguo no se piden jamas.
const DEL_MOTOR = [
  ['node_modules/tesseract.js/dist/worker.min.js', 'worker.min.js'],
  ['node_modules/tesseract.js-core/tesseract-core-lstm.wasm.js', 'tesseract-core-lstm.wasm.js'],
  ['node_modules/tesseract.js-core/tesseract-core-simd-lstm.wasm.js', 'tesseract-core-simd-lstm.wasm.js'],
  ['node_modules/tesseract.js-core/tesseract-core-relaxedsimd-lstm.wasm.js', 'tesseract-core-relaxedsimd-lstm.wasm.js'],
]

// Castellano y ingles: en una pizarra los nombres van en castellano pero los
// tipos se escriben en ingles -String, Date, int-, asi que hacen falta los dos.
const IDIOMAS = ['spa', 'eng']
const ORIGEN_IDIOMAS = 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main'

// Lo que el navegador va a pedir de verdad. No es la misma lista que la de
// arriba y esa diferencia ya costo que el reconocimiento no arrancara nunca:
// cuando `corePath` es una carpeta, el worker de tesseract.js pide siempre
// `tesseract-core-<variante>.wasm.js` -la compilacion de un solo archivo, con
// el wasm adentro-, y no el par `.js` + `.wasm`. Si falta, el fallo aparece
// recien al usar la pantalla, como un `importScripts` que no carga.
//
// Se verifica aqui, al preparar, para que el error salte al instalar y no
// delante de un tribunal.
const QUE_PIDE_EL_NAVEGADOR = [
  'worker.min.js',
  'tesseract-core-lstm.wasm.js',
  'tesseract-core-simd-lstm.wasm.js',
  'tesseract-core-relaxedsimd-lstm.wasm.js',
]

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

const faltantes = []
for (const nombre of QUE_PIDE_EL_NAVEGADOR) {
  if (!(await existe(join(destino, nombre)))) faltantes.push(nombre)
}
if (faltantes.length) {
  console.error('OCR: el navegador va a pedir archivos que no estan en public/ocr:')
  for (const nombre of faltantes) console.error(`       ${nombre}`)
  console.error('     Revisa DEL_MOTOR en este script: los nombres no coinciden.')
  process.exit(1)
}
console.log('OCR: estan los tres motores que el navegador puede pedir')

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
