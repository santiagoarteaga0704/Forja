import { Component, type ReactNode } from 'react'

/**
 * Lo que se ve cuando algo se rompe, en vez de una pantalla en negro.
 * <p>
 * React desmonta el arbol entero cuando un componente lanza y nadie lo
 * atrapa. El 21 de septiembre de 2026 paso de verdad: un ref que se leia
 * dentro de un actualizador de estado valia null al ejecutarse, y arrastrar el
 * lienzo dejaba la aplicacion COMPLETAMENTE NEGRA. Sin mensaje, sin forma de
 * volver, y sin ninguna pista de que habia ocurrido.
 *
 * Ese defecto se arreglo, pero lo que importa es el otro: no habia red. El
 * proximo error que se nos escape -y en una herramienta de esta superficie se
 * va a escapar alguno- tiene que terminar en un cartel del que se pueda salir,
 * no en una pantalla apagada delante de un tribunal.
 *
 * No se oculta lo que fallo: el texto del error se muestra. Un "algo salio
 * mal" sin detalle obliga a abrir la consola del navegador, que es justo lo
 * que no se hace delante de nadie.
 */
export default class LimiteDeError extends Component<
  { children: ReactNode; alVolver?: () => void },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  componentDidCatch(error: Error, info: { componentStack?: string | null }) {
    // Queda en la consola ademas de en pantalla: en pantalla va el mensaje,
    // aca la pila, que es lo que sirve despues para encontrarlo.
    console.error('Se rompio algo dentro de la aplicacion:', error, info.componentStack)
  }

  render() {
    const { error } = this.state
    if (!error) return this.props.children

    return (
      <div className="pantalla-rota">
        <div className="pantalla-rota-caja">
          <h1>Se rompió algo</h1>
          <p>
            La herramienta encontró un error que no supo manejar y cerró esta pantalla para no
            seguir con datos a medias.
          </p>
          <p className="tranquilo">
            <strong>Lo que ya aplicaste está guardado.</strong> Cada cambio viaja al servidor en
            el momento en que se hace, así que el diagrama está entero: volvé a abrirlo y lo vas a
            encontrar como estaba.
          </p>

          <pre className="pantalla-rota-detalle">{error.message}</pre>

          <div className="pantalla-rota-botones">
            {this.props.alVolver && (
              <button
                type="button"
                className="primario"
                onClick={() => {
                  this.setState({ error: null })
                  this.props.alVolver?.()
                }}
              >
                Volver a los proyectos
              </button>
            )}
            <button type="button" onClick={() => window.location.reload()}>
              Recargar la página
            </button>
          </div>
        </div>
      </div>
    )
  }
}
