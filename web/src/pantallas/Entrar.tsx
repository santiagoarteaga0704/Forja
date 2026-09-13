import { useState } from 'react'
import { ErrorApi, api } from '../api'
import type { Credencial } from '../tipos'

/** Entrada y alta de cuenta, en una sola pantalla que alterna de modo. */
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
      <form className="tarjeta" onSubmit={enviar}>
        <div className="marca">
          <span className="yunque" />
          FORJA
        </div>
        <h1>{esAlta ? 'Crear una cuenta' : 'Entrar'}</h1>
        <p className="bajada">Herramienta CASE colaborativa para diagramas de clases</p>

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
                required
              />
            </div>
          )}

          <div>
            <label htmlFor="password">Contrasena</label>
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
            {enviando ? 'Un momento...' : esAlta ? 'Crear la cuenta' : 'Entrar'}
          </button>
        </div>

        <div className="alterna">
          {esAlta ? 'Ya tenes cuenta?' : 'Primera vez?'}
          <button type="button" onClick={() => { setEsAlta(!esAlta); setError(null) }}>
            {esAlta ? 'Entrar' : 'Crear una cuenta'}
          </button>
        </div>
      </form>
    </div>
  )
}
