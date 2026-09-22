/*
 * El identificador tiene que salir tambien en un contexto NO seguro.
 *
 * El navegador solo entrega crypto.randomUUID sobre https:// o localhost. En
 * el despliegue la aplicacion se sirve por http:// contra una IP, y alli el
 * metodo sencillamente no existe: la primera llamada lanza y, si ocurre al
 * importar un modulo, la pagina queda en negro sin pintar nada.
 *
 *   node --experimental-strip-types src/id.prueba.mjs
 */
import assert from 'node:assert/strict'

const RFC4122 =
  /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

function ponerCrypto(valor) {
  Object.defineProperty(globalThis, 'crypto', {
    value: valor,
    configurable: true,
    writable: true,
  })
}

const getRandomValues = (bytes) => {
  for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  return bytes
}

// Contexto inseguro: hay crypto y hay getRandomValues, pero no randomUUID.
ponerCrypto({ getRandomValues })

const { nuevoId } = await import('./id.ts')

const id = nuevoId()
assert.match(id, RFC4122, `sin randomUUID salio un id invalido: ${id}`)

const muchos = new Set(Array.from({ length: 5000 }, () => nuevoId()))
assert.equal(muchos.size, 5000, 'hubo identificadores repetidos')

// Contexto seguro: si el navegador lo trae, se usa el del navegador. Se
// comprueba despues de la primera llamada a proposito, porque el modulo no
// debe haberse quedado con la decision tomada al importarse.
let usoElDelNavegador = false
ponerCrypto({
  getRandomValues,
  randomUUID: () => {
    usoElDelNavegador = true
    return '11111111-2222-4333-8444-555555555555'
  },
})
assert.equal(nuevoId(), '11111111-2222-4333-8444-555555555555')
assert.ok(usoElDelNavegador, 'no uso crypto.randomUUID habiendolo')

// Sin crypto ninguno (navegador viejo): igual tiene que devolver algo valido.
ponerCrypto(undefined)
assert.match(nuevoId(), RFC4122, 'sin crypto no devolvio un id valido')

console.log('ok - nuevoId() funciona en contexto seguro e inseguro')
