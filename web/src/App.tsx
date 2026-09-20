import { useEffect, useState } from 'react'
import { cuandoCaduqueLaSesion } from './api'
import Entrar from './pantallas/Entrar'
import Lienzo from './pantallas/Lienzo'
import Proyectos from './pantallas/Proyectos'
import { guardarCredencial, leerCredencial } from './sesion'
import type { Credencial, DiagramaResumen, ProyectoVista } from './tipos'

/**
 * Raiz de la aplicacion.
 *
 * La navegacion se resuelve con estado y no con un enrutador porque son tres
 * pantallas y una jerarquia estricta -entrar, elegir proyecto y diagrama,
 * modelar-. Un enrutador agregaria una dependencia y direcciones que aqui no
 * aportan: el lienzo no es algo que se comparta por URL, se comparte invitando
 * a alguien al proyecto.
 */
export default function App() {
  const [credencial, setCredencial] = useState<Credencial | null>(leerCredencial)
  const [abierto, setAbierto] = useState<{ proyecto: ProyectoVista; diagrama: DiagramaResumen } | null>(
    null,
  )

  const entrar = (nueva: Credencial) => {
    guardarCredencial(nueva)
    setCredencial(nueva)
  }

  const salir = () => {
    guardarCredencial(null)
    setCredencial(null)
    setAbierto(null)
  }

  /*
   * Si el servidor dice que la credencial ya no vale, se vuelve al login sin
   * preguntar. El caso que lo motivo: la sesion no tiene estado y el token dura
   * doce horas, asi que sobrevive a que la cuenta desaparezca de la base -lo
   * que ocurre cada vez que se rehace-, y la persona quedaba apretando botones
   * contra un error en rojo.
   */
  useEffect(() => {
    cuandoCaduqueLaSesion(salir)
    return () => cuandoCaduqueLaSesion(null)
  })

  if (!credencial) return <Entrar alEntrar={entrar} />

  if (abierto) {
    return (
      <Lienzo
        credencial={credencial}
        proyecto={abierto.proyecto}
        diagrama={abierto.diagrama}
        alVolver={() => setAbierto(null)}
      />
    )
  }

  return (
    <Proyectos
      credencial={credencial}
      alAbrir={(proyecto, diagrama) => setAbierto({ proyecto, diagrama })}
      alSalir={salir}
    />
  )
}
