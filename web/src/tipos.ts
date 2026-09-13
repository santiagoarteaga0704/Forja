// Contrato con el backend. Los nombres coinciden exactamente con los de los
// registros de Java: si alguno cambia alla, TypeScript no lo detecta, y por eso
// se mantienen iguales en lugar de traducirlos a una convencion propia.

export type TipoDiagrama = 'CLASES' | 'SECUENCIA'
export type Visibilidad = 'PUBLICO' | 'PRIVADO' | 'PROTEGIDO' | 'PAQUETE'
export type TipoElemento = 'CLASE' | 'RELACION' | 'DIAGRAMA'
export type OrigenOperacion = 'LIENZO' | 'VOZ' | 'FOTO' | 'IMPORTACION' | 'AGENTE'

export type TipoRelacion =
  | 'ASOCIACION'
  | 'AGREGACION'
  | 'COMPOSICION'
  | 'HERENCIA'
  | 'DEPENDENCIA'
  | 'REALIZACION'

export interface Credencial {
  token: string
  expiraEn: string
  usuarioId: string
  nombre: string
  email: string
}

export interface ProyectoVista {
  id: string
  nombre: string
  descripcion: string | null
  propietarioId: string
  actualizadoEn: string
}

export interface DiagramaResumen {
  id: string
  nombre: string
  tipo: TipoDiagrama
  version: number
}

export interface AtributoVista {
  id: string
  nombre: string
  tipo: string
  visibilidad: Visibilidad
  esIdentificador: boolean
  esRequerido: boolean
  esUnico: boolean
  longitud: number | null
  orden: number
}

export interface MetodoVista {
  id: string
  nombre: string
  tipoRetorno: string
  visibilidad: Visibilidad
  esAbstracto: boolean
  esEstatico: boolean
  orden: number
}

export interface ClaseVista {
  id: string
  nombre: string
  estereotipo: string | null
  esAbstracta: boolean
  posX: number
  posY: number
  ancho: number
  alto: number
  atributos: AtributoVista[]
  metodos: MetodoVista[]
}

export interface RelacionVista {
  id: string
  origenId: string
  destinoId: string
  tipo: TipoRelacion
  multiplicidadOrigen: string
  multiplicidadDestino: string
  rolOrigen: string | null
  rolDestino: string | null
  etiqueta: string | null
}

export interface BloqueoVista {
  elementoTipo: TipoElemento
  elementoId: string
  poseedorId: string
  poseedorNombre: string
  expiraEn: string
}

export interface DiagramaCompleto {
  id: string
  nombre: string
  tipo: TipoDiagrama
  version: number
  clases: ClaseVista[]
  relaciones: RelacionVista[]
  bloqueos: BloqueoVista[]
}

// ---------- Comandos -------------------------------------------------------
// El tipo viaja como campo aparte y no dentro de la carga, igual que se guarda
// en la bitacora del servidor.

export type TipoOperacion =
  | 'CLASE_CREAR'
  | 'CLASE_RENOMBRAR'
  | 'CLASE_MARCAR'
  | 'CLASE_MOVER'
  | 'CLASE_ELIMINAR'
  | 'ATRIBUTO_AGREGAR'
  | 'ATRIBUTO_ELIMINAR'
  | 'METODO_AGREGAR'
  | 'METODO_ELIMINAR'
  | 'RELACION_CREAR'
  | 'RELACION_ELIMINAR'

export interface ParametroComando {
  parametroId: string
  nombre: string
  tipo: string
}

export type Comando =
  | { claseId: string; nombre: string; estereotipo?: string | null; esAbstracta: boolean; posX: number; posY: number }
  | { claseId: string; nombre: string }
  | { claseId: string; estereotipo?: string | null; esAbstracta: boolean }
  | { claseId: string; posX: number; posY: number }
  | { claseId: string }
  | {
      claseId: string
      atributoId: string
      nombre: string
      tipo: string
      visibilidad: Visibilidad
      esIdentificador: boolean
      esRequerido: boolean
      esUnico: boolean
      longitud?: number | null
    }
  | { claseId: string; atributoId: string }
  | {
      claseId: string
      metodoId: string
      nombre: string
      tipoRetorno: string
      visibilidad: Visibilidad
      esAbstracto: boolean
      esEstatico: boolean
      parametros: ParametroComando[]
    }
  | { claseId: string; metodoId: string }
  | {
      relacionId: string
      origenId: string
      destinoId: string
      tipo: TipoRelacion
      multiplicidadOrigen: string
      multiplicidadDestino: string
      rolOrigen?: string | null
      rolDestino?: string | null
      etiqueta?: string | null
    }
  | { relacionId: string }

export type EstadoOperacion = 'APLICADA' | 'DUPLICADA' | 'RECHAZADA_POR_BLOQUEO'
export type EstadoBloqueo = 'CONCEDIDO' | 'RENOVADO' | 'RECHAZADO'

export interface ResultadoBloqueo {
  estado: EstadoBloqueo
  elementoTipo: TipoElemento
  elementoId: string
  poseedorId: string | null
  poseedorNombre: string | null
  expiraEn: string | null
}

export interface ResultadoOperacion {
  estado: EstadoOperacion
  operacionId: string | null
  secuencia: number
  versionDiagrama: number
  tipo: TipoOperacion | null
  creadaEn: string | null
  bloqueo: ResultadoBloqueo | null
}

export interface OperacionRegistrada {
  operacionId: string
  secuencia: number
  tipo: TipoOperacion
  comando: Record<string, unknown>
  origen: OrigenOperacion
  autorId: string
  creadaEn: string
}

// ---------- Avisos del canal ------------------------------------------------

export type EventoLienzo =
  | { evento: 'OPERACION'; contenido: OperacionRegistrada }
  | { evento: 'BLOQUEO_TOMADO'; contenido: { elementoTipo: TipoElemento; elementoId: string; poseedorId: string; poseedorNombre: string } }
  | { evento: 'BLOQUEO_LIBERADO'; contenido: { elementoTipo: TipoElemento; elementoId: string } }
  | { evento: 'SESION_CERRADA'; contenido: { sesionId: string; bloqueosLiberados: number } }

// ---------- Generacion e intercambio ---------------------------------------

export interface ResumenGeneracion {
  cantidadDeArchivos: number
  rutas: string[]
}

export interface ResumenImportacion {
  comandosLeidos: number
  aplicadas: number
  duplicadas: number
  rechazadasPorBloqueo: number
  problemas: string[]
  versionDelDiagrama: number
}

// ---------- Agente guia -----------------------------------------------------

export type CategoriaConsejo = 'DESCUBRIMIENTO' | 'MODELO' | 'DISENO'

export interface Consejo {
  id: string
  categoria: CategoriaConsejo
  prioridad: number
  queNote: string
  porQueImporta: string
  comoSeHace: string
  /** Clase o relacion de la que habla, para poder senalarla en el lienzo. */
  elementoId: string | null
}

// ---------- Dictado por voz -------------------------------------------------

export interface Paso {
  tipo: TipoOperacion
  comando: Record<string, unknown>
}

export interface Interpretacion {
  frase: string
  entendida: boolean
  pasos: Paso[]
  explicacion: string
  sugerencias: string[]
}

export interface ResultadoDictado {
  entendida: boolean
  explicacion: string
  sugerencias: string[]
  comandosLeidos: number
  aplicadas: number
  problemas: string[]
  /** Nombre de quien tenia tomado el elemento, si el dictado choco con un bloqueo. */
  retenidoPor: string | null
  versionDelDiagrama: number
}

/** Cuerpo de error de la RFC 7807, que es como responde el backend. */
export interface Problema {
  title?: string
  detail?: string
  status?: number
  campos?: Record<string, string>
}
