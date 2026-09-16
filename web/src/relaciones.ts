import type { TipoRelacion } from './tipos'

/**
 * Los seis tipos de relacion, con el nombre y el significado que ve el usuario.
 *
 * La glosa no es ayuda opcional: quien esta modelando por primera vez duda
 * justo entre agregacion y composicion, y entre herencia y realizacion. Decirlo
 * en el mismo lugar donde se elige evita el error antes de cometerlo, que es
 * mas barato que corregirlo despues en el diagrama.
 *
 * Vive en su propio archivo para que `iconos.tsx` exporte solo componentes: si
 * se mezclan, la recarga en caliente de Vite deja de funcionar en ese archivo.
 */
export const RELACIONES: { tipo: TipoRelacion; nombre: string; glosa: string }[] = [
  { tipo: 'ASOCIACION', nombre: 'Asociación', glosa: 'Las dos se conocen' },
  { tipo: 'AGREGACION', nombre: 'Agregación', glosa: 'Una agrupa a la otra, que vive sin ella' },
  {
    tipo: 'COMPOSICION',
    nombre: 'Composición',
    glosa: 'El todo contiene las partes y las borra con él',
  },
  { tipo: 'HERENCIA', nombre: 'Herencia', glosa: 'La hija es una clase general' },
  { tipo: 'REALIZACION', nombre: 'Realización', glosa: 'La clase cumple una interfaz' },
  { tipo: 'DEPENDENCIA', nombre: 'Dependencia', glosa: 'Una usa a la otra, sin guardarla' },
]
