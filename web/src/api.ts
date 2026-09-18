import type {
  Comando,
  Consejo,
  Guia,
  TemaGuia,
  Lectura,
  Credencial,
  DiagramaCompleto,
  DiagramaResumen,
  Disponibilidad,
  OperacionRegistrada,
  OrigenOperacion,
  Pedido,
  Problema,
  ProyectoVista,
  ResultadoBloqueo,
  ResultadoOperacion,
  ResumenGeneracion,
  ResultadoDictado,
  ResultadoFoto,
  ResultadoPedido,
  ResumenImportacion,
  TipoDiagrama,
  TipoElemento,
  TipoOperacion,
} from './tipos'

export const BASE = import.meta.env.VITE_API ?? 'http://localhost:8080'

/**
 * Error de la API con el texto que el servidor quiso mostrar.
 *
 * El backend responde con ProblemDetail de la RFC 7807, asi que hay un mensaje
 * pensado para una persona; usarlo en lugar de un "error 422" generico es la
 * diferencia entre que el usuario sepa que corregir y que no.
 */
export class ErrorApi extends Error {
  // Los campos se declaran aparte y no en la firma del constructor: la
  // configuracion del proyecto exige que el TypeScript se pueda borrar sin
  // transformar el codigo, y las propiedades de parametro no cumplen eso.
  readonly estado: number
  readonly problema?: Problema

  constructor(estado: number, mensaje: string, problema?: Problema) {
    super(mensaje)
    this.estado = estado
    this.problema = problema
  }
}

let token: string | null = null

export function fijarToken(nuevo: string | null) {
  token = nuevo
}

export function tokenActual() {
  return token
}

async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
  const cabeceras: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((opciones.headers as Record<string, string>) ?? {}),
  }
  if (token) cabeceras.Authorization = `Bearer ${token}`

  const respuesta = await fetch(BASE + ruta, { ...opciones, headers: cabeceras })

  if (!respuesta.ok) {
    let problema: Problema | undefined
    let mensaje = `${respuesta.status} ${respuesta.statusText}`
    try {
      problema = await respuesta.json()
      mensaje = problema?.detail ?? problema?.title ?? mensaje
      // La validacion de campos llega aparte; sin mostrarla el usuario solo ve
      // "peticion invalida" y no sabe cual de los campos lo esta.
      if (problema?.campos) {
        mensaje +=
          ': ' +
          Object.entries(problema.campos)
            .map(([campo, detalle]) => `${campo} ${detalle}`)
            .join(', ')
      }
    } catch {
      // Una respuesta sin cuerpo JSON deja el mensaje del estado HTTP.
    }
    throw new ErrorApi(respuesta.status, mensaje, problema)
  }

  if (respuesta.status === 204) return undefined as T
  const texto = await respuesta.text()
  return texto ? (JSON.parse(texto) as T) : (undefined as T)
}

// ---------- Autenticacion ---------------------------------------------------

export const api = {
  registro: (email: string, nombre: string, password: string) =>
    pedir<Credencial>('/api/auth/registro', {
      method: 'POST',
      body: JSON.stringify({ email, nombre, password }),
    }),

  sesion: (email: string, password: string) =>
    pedir<Credencial>('/api/auth/sesion', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  yo: () => pedir<{ usuarioId: string; nombre: string; email: string }>('/api/auth/yo'),

  // ---------- Proyectos ----------------------------------------------------

  proyectos: () => pedir<ProyectoVista[]>('/api/proyectos'),

  crearProyecto: (nombre: string, descripcion?: string) =>
    pedir<ProyectoVista>('/api/proyectos', {
      method: 'POST',
      body: JSON.stringify({ nombre, descripcion }),
    }),

  invitar: (proyectoId: string, email: string, rol: 'EDITOR' | 'LECTOR') =>
    pedir<void>(`/api/proyectos/${proyectoId}/miembros`, {
      method: 'POST',
      body: JSON.stringify({ email, rol }),
    }),

  diagramas: (proyectoId: string) =>
    pedir<DiagramaResumen[]>(`/api/proyectos/${proyectoId}/diagramas`),

  crearDiagrama: (proyectoId: string, nombre: string, tipo: TipoDiagrama = 'CLASES') =>
    pedir<DiagramaResumen>(`/api/proyectos/${proyectoId}/diagramas`, {
      method: 'POST',
      body: JSON.stringify({ nombre, tipo }),
    }),

  // ---------- Diagrama -----------------------------------------------------

  diagrama: (diagramaId: string) => pedir<DiagramaCompleto>(`/api/diagramas/${diagramaId}`),

  operacion: (
    diagramaId: string,
    tipo: TipoOperacion,
    comando: Comando,
    sesionId: string,
    origen: OrigenOperacion = 'LIENZO',
  ) =>
    pedir<ResultadoOperacion>(`/api/diagramas/${diagramaId}/operaciones`, {
      method: 'POST',
      body: JSON.stringify({
        tipo,
        comando,
        origen,
        sesionId,
        // El token lo genera el cliente antes de enviar: es lo que hace que un
        // reintento tras un corte de red no duplique el cambio.
        tokenCliente: crypto.randomUUID(),
      }),
    }),

  delta: (diagramaId: string, desde: number) =>
    pedir<OperacionRegistrada[]>(`/api/diagramas/${diagramaId}/operaciones?desde=${desde}`),

  bloquear: (diagramaId: string, elementoTipo: TipoElemento, elementoId: string, sesionId: string) =>
    pedir<ResultadoBloqueo>(`/api/diagramas/${diagramaId}/bloqueos`, {
      method: 'POST',
      body: JSON.stringify({ elementoTipo, elementoId, sesionId }),
    }),

  liberar: (diagramaId: string, elementoTipo: TipoElemento, elementoId: string, sesionId: string) =>
    pedir<void>(
      `/api/diagramas/${diagramaId}/bloqueos?elementoTipo=${elementoTipo}` +
        `&elementoId=${elementoId}&sesionId=${encodeURIComponent(sesionId)}`,
      { method: 'DELETE' },
    ),

  // ---------- Generacion e intercambio -------------------------------------

  generacion: (diagramaId: string, paquete?: string) =>
    pedir<ResumenGeneracion>(
      `/api/diagramas/${diagramaId}/generacion${paquete ? `?paquete=${paquete}` : ''}`,
    ),

  archivoGenerado: async (diagramaId: string, ruta: string, paquete?: string) => {
    const consulta = new URLSearchParams({ ruta })
    if (paquete) consulta.set('paquete', paquete)
    const respuesta = await fetch(
      `${BASE}/api/diagramas/${diagramaId}/generacion/archivo?${consulta}`,
      { headers: token ? { Authorization: `Bearer ${token}` } : {} },
    )
    if (!respuesta.ok) throw new ErrorApi(respuesta.status, 'No se pudo leer el archivo generado')
    return respuesta.text()
  },

  /**
   * Descarga binaria. No pasa por pedir() porque la respuesta no es JSON y
   * porque el navegador necesita un blob para ofrecer el archivo.
   */
  descargar: async (ruta: string, nombreSugerido: string) => {
    const respuesta = await fetch(BASE + ruta, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!respuesta.ok) throw new ErrorApi(respuesta.status, 'No se pudo descargar')

    const blob = await respuesta.blob()
    const enlace = document.createElement('a')
    enlace.href = URL.createObjectURL(blob)
    enlace.download = nombreSugerido
    enlace.click()
    URL.revokeObjectURL(enlace.href)
  },

  /** Devuelve el documento tal cual: es XML, no pasa por JSON.parse. */
  exportarXmi: async (diagramaId: string) => {
    const respuesta = await fetch(`${BASE}/api/diagramas/${diagramaId}/xmi`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!respuesta.ok) throw new ErrorApi(respuesta.status, 'No se pudo exportar el XMI')
    return respuesta.text()
  },

  // ---------- Foto de pizarra ----------------------------------------------

  /** Primer paso: que se entendio del texto, sin tocar el modelo. */
  leerPizarra: (diagramaId: string, texto: string, sesionId: string) =>
    pedir<Lectura>(`/api/diagramas/${diagramaId}/foto/lectura`, {
      method: 'POST',
      body: JSON.stringify({ texto, sesionId }),
    }),

  aplicarPizarra: (diagramaId: string, texto: string, sesionId: string) =>
    pedir<ResultadoFoto>(`/api/diagramas/${diagramaId}/foto`, {
      method: 'POST',
      body: JSON.stringify({ texto, sesionId, tokenLectura: crypto.randomUUID() }),
    }),

  // ---------- Pedido en una frase ------------------------------------------

  /**
   * Si hay un modelo con que contestar.
   *
   * Se pregunta antes de ofrecer el boton: sin traductor configurado la
   * respuesta seria siempre vacia, y un boton que no hace nada es peor que no
   * tenerlo.
   */
  hayTraductor: (diagramaId: string) =>
    pedir<Disponibilidad>(`/api/diagramas/${diagramaId}/pedido/disponible`),

  /**
   * Primer paso: que propuso el modelo, sin tocar el diagrama.
   *
   * Lleva senal de cancelacion porque este es el unico pedido de la aplicacion
   * que puede tardar treinta segundos: del otro lado hay alguien esperando y
   * tiene que poder arrepentirse.
   */
  pedirLectura: (diagramaId: string, pedido: string, sesionId: string, senal?: AbortSignal) =>
    pedir<Pedido>(`/api/diagramas/${diagramaId}/pedido/lectura`, {
      method: 'POST',
      body: JSON.stringify({ pedido, sesionId }),
      signal: senal,
    }),

  /**
   * Segundo paso: aplicar lo propuesto.
   *
   * El `tokenLectura` es el de la propuesta que se reviso: si la respuesta se
   * pierde y se reintenta, el servidor reconoce los comandos como reenvio en
   * lugar de duplicar el diagrama entero.
   */
  aplicarPedido: (diagramaId: string, pedido: string, sesionId: string, tokenLectura: string) =>
    pedir<ResultadoPedido>(`/api/diagramas/${diagramaId}/pedido`, {
      method: 'POST',
      body: JSON.stringify({ pedido, sesionId, tokenLectura }),
    }),

  // ---------- Agente guia --------------------------------------------------

  /**
   * El agente guia, en cualquier pantalla.
   *
   * Sin `diagramaId` solo puede hablar de la herramienta, que es exactamente lo
   * que necesita quien todavia no creo ningun diagrama; con el, ademas revisa el
   * modelo. Una sola llamada trae los consejos y el recorrido porque el panel
   * los dibuja juntos.
   */
  guia: (diagramaId: string | null, descartados: string[]) => {
    const consulta = new URLSearchParams()
    if (diagramaId) consulta.set('diagramaId', diagramaId)
    descartados.forEach((id) => consulta.append('descartados', id))
    const cola = consulta.toString()
    return pedir<Guia>(`/api/guia${cola ? `?${cola}` : ''}`)
  },

  /**
   * Una pregunta escrita al agente. Responde desde su base de conocimiento.
   *
   * `sobre` es el tema de la ultima respuesta: deja que una repregunta corta
   * -"¿y eso?", "no entendi"- vuelva sobre ese tema en lugar de caer en "no la
   * se contestar", que es la forma mas rapida de que el agente parezca roto.
   */
  preguntar: (texto: string, sobre: string | null) =>
    pedir<Consejo[]>('/api/guia/pregunta', {
      method: 'POST',
      body: JSON.stringify({ texto, sobre }),
    }),

  /** Las preguntas que el agente sabe responder. */
  temas: () => pedir<TemaGuia[]>('/api/guia/temas'),

  // ---------- Dictado ------------------------------------------------------

  dictar: (diagramaId: string, frase: string, sesionId: string) =>
    pedir<ResultadoDictado>(`/api/diagramas/${diagramaId}/voz`, {
      method: 'POST',
      body: JSON.stringify({ frase, sesionId }),
    }),

  importarXmi: (diagramaId: string, documento: string, sesionId: string) =>
    pedir<ResumenImportacion>(
      `/api/diagramas/${diagramaId}/xmi?sesionId=${encodeURIComponent(sesionId)}` +
        `&tokenImportacion=${crypto.randomUUID()}`,
      { method: 'POST', headers: { 'Content-Type': 'application/xml' }, body: documento },
    ),
}
