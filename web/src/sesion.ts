import { fijarToken } from './api'
import type { Credencial } from './tipos'

const CLAVE = 'forja.credencial'

/**
 * Identificador de esta pestana.
 *
 * Es lo que, junto con el usuario, determina quien posee un bloqueo, y lo que
 * el servidor libera al cerrarse el canal. Se genera una vez por carga de la
 * pagina y no se guarda: dos pestanas del mismo usuario son dos sesiones
 * distintas, y deben poder quitarse el turno la una a la otra igual que dos
 * personas.
 */
export const SESION_ID = `web-${crypto.randomUUID()}`

export function leerCredencial(): Credencial | null {
  try {
    const guardada = localStorage.getItem(CLAVE)
    if (!guardada) return null

    const credencial = JSON.parse(guardada) as Credencial
    // Un token vencido no sirve de nada y produciria un 401 en la primera
    // peticion: es mejor volver a pedir la contrasena.
    if (credencial.expiraEn && new Date(credencial.expiraEn) < new Date()) {
      localStorage.removeItem(CLAVE)
      return null
    }
    fijarToken(credencial.token)
    return credencial
  } catch {
    localStorage.removeItem(CLAVE)
    return null
  }
}

export function guardarCredencial(credencial: Credencial | null) {
  if (credencial) {
    localStorage.setItem(CLAVE, JSON.stringify(credencial))
    fijarToken(credencial.token)
  } else {
    localStorage.removeItem(CLAVE)
    fijarToken(null)
  }
}
