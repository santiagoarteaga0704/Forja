/**
 * Un identificador unico, haya o no contexto seguro.
 *
 * `crypto.randomUUID` solo existe sobre https:// o en localhost. Durante el
 * desarrollo las dos condiciones se cumplen siempre, asi que el problema no
 * aparece nunca; en el despliegue la aplicacion se sirve por http:// contra
 * una IP y el metodo no esta. Llamarlo alli lanza un TypeError, y como el
 * primer uso ocurria al importar un modulo -no dentro de un componente- la
 * pagina quedaba NEGRA: la excepcion corta la carga antes de que React exista,
 * donde ningun error boundary puede alcanzarla.
 *
 * `getRandomValues` si esta disponible en contexto inseguro, asi que el
 * respaldo mantiene la aleatoriedad del sistema; solo un navegador sin crypto
 * alguno cae en Math.random, que para identificar una pestana o un elemento
 * del diagrama alcanza, porque no protegen nada.
 */
export function nuevoId(): string {
  const cripto = (globalThis as { crypto?: Crypto }).crypto

  if (typeof cripto?.randomUUID === 'function') return cripto.randomUUID()

  const bytes = new Uint8Array(16)
  if (typeof cripto?.getRandomValues === 'function') {
    cripto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  }

  // Las dos marcas que distinguen un UUID versión 4 de una tira de bytes al
  // azar: la version en el nibble alto del septimo byte y la variante en los
  // dos bits altos del noveno.
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
