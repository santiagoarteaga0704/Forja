/*
 * Las tipografias viajan DENTRO de la aplicacion, no se piden a la red.
 *
 * Es la misma decision que se tomo con el motor de OCR: el dia de la defensa
 * la red del aula puede no estar, y una herramienta que aparece con la
 * tipografia de reserva se ve rota. @fontsource entrega los archivos por npm y
 * Vite los empaqueta.
 *
 * Solo el subconjunto latino: alcanza para el castellano y evita cargar
 * cirilico y griego, que serian mas bytes por nada. Las cursivas se incluyen
 * porque UML las usa con un significado preciso -clase abstracta, operacion
 * abstracta- y dejarselas sintetizar al navegador da un resultado torcido.
 */
import '@fontsource/ibm-plex-sans/latin-400.css'
import '@fontsource/ibm-plex-sans/latin-400-italic.css'
import '@fontsource/ibm-plex-sans/latin-500.css'
import '@fontsource/ibm-plex-sans/latin-600.css'
import '@fontsource/ibm-plex-sans/latin-600-italic.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-400-italic.css'
import '@fontsource/ibm-plex-mono/latin-500.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import './estilos.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
