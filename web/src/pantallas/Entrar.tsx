import { useState } from 'react'
import { ErrorApi, api } from '../api'
import HojaMuestra from '../lienzo/HojaMuestra'
import type { Credencial } from '../tipos'

/**
 * Entrada y alta de cuenta, en una sola pantalla que alterna de modo.
 *
 * La mitad izquierda no es decoracion: es una hoja de FORJA con un diagrama
 * real y la notacion correcta. Quien abre la herramienta por primera vez ve
 * antes que nada que hace, y no una promesa escrita.
 */
export default function Entrar({ alEntrar }: { alEntrar: (credencial: Credencial) => void }) {
  const [esAlta, setEsAlta] = useState(false)
  const [email, setEmail] = useState('')
  const [nombre, setNombre] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const enviar = async (evento: React.FormEvent) => {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      const credencial = esAlta
        ? await api.registro(email, nombre, password)
        : await api.sesion(email, password)
      alEntrar(credencial)
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="entrar">
      <div className="entrar-cuerpo">
        <section className="entrar-presentacion">
          <div className="marca">
            <span className="yunque" />
            FORJA
          </div>
          <h1>Del pizarrón al backend andando.</h1>
          <p>
            Dibujá el diagrama de clases entre varios, dictalo o sacale una foto a la pizarra.
            FORJA lo convierte en un proyecto Spring Boot que compila, y lo intercambia con
            Enterprise Architect.
          </p>
          <HojaMuestra />
        </section>

        <form className="tarjeta" onSubmit={enviar}>
          <h2>{esAlta ? 'Crear una cuenta' : 'Entrar'}</h2>
          <p className="bajada">
            {esAlta
              ? 'Con la cuenta creada podés abrir proyectos y que te inviten a los de otros.'
              : 'Entrá con la cuenta que usás en este servidor.'}
          </p>

          {error && <div className="mensaje error">{error}</div>}

          <div className="campos">
            <div>
              <label htmlFor="email">Correo</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
              />
            </div>

            {esAlta && (
              <div>
                <label htmlFor="nombre">Nombre</label>
                <input
                  id="nombre"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  autoComplete="name"
                  placeholder="Como te van a ver los demás"
                  required
                />
              </div>
            )}

            <div>
              <label htmlFor="password">Contraseña</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={esAlta ? 'new-password' : 'current-password'}
                required
                minLength={esAlta ? 8 : undefined}
              />
            </div>

            <button className="principal" type="submit" disabled={enviando}>
              {enviando
                ? esAlta
                  ? 'Creando la cuenta…'
                  : 'Entrando…'
                : esAlta
                  ? 'Crear la cuenta'
                  : 'Entrar'}
            </button>
          </div>

          <div className="alterna">
            {esAlta ? '¿Ya tenés cuenta?' : '¿Primera vez?'}
            <button
              type="button"
              onClick={() => {
                setEsAlta(!esAlta)
                setError(null)
              }}
            >
              {esAlta ? 'Entrar' : 'Crear una cuenta'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
