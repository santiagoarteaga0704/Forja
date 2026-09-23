// Genera los codigos QR que van en los anexos del documento.
//
//     node crear-qr.mjs
//
// Se generan y no se dibujan a mano porque un QR mal hecho no se nota mirandolo:
// se nota cuando alguien lo escanea delante de uno y no lleva a ninguna parte.
// Al estar en un guion, regenerarlos cuando cambie una direccion es un comando.
//
// El margen va en 2 modulos -el minimo que exige la norma es 4, pero impreso en
// una pagina blanca el papel ya hace de zona tranquila- y la correccion de
// errores en M, que tolera un 15% de dano: suficiente para una hoja que se
// dobla, sin agrandar el simbolo mas de la cuenta.

import { mkdir } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import QRCode from 'qrcode'

const aqui = dirname(fileURLToPath(import.meta.url))
const destino = join(aqui, '..', 'docs', 'diagramas')

const codigos = [
  {
    archivo: 'qr-servidor.png',
    texto: 'https://d2x41sl49sltgo.cloudfront.net',
    que: 'la aplicacion desplegada',
  },
  {
    archivo: 'qr-repositorio.png',
    texto: 'https://github.com/santiagoarteaga0704/Forja',
    que: 'el repositorio',
  },
]

await mkdir(destino, { recursive: true })

for (const codigo of codigos) {
  const ruta = join(destino, codigo.archivo)
  await QRCode.toFile(ruta, codigo.texto, {
    errorCorrectionLevel: 'M',
    margin: 2,
    width: 600,
    color: { dark: '#000000ff', light: '#ffffffff' },
  })
  console.log(`${codigo.archivo}  ->  ${codigo.texto}  (${codigo.que})`)
}
