# Ejemplos para probar el intercambio

## `clinica-desde-ea.xmi`

**Lo generó Enterprise Architect de verdad**, no está escrito a mano. Un archivo
escrito a mano solo confirma las suposiciones de quien lo escribe; lo que hace
falta para probar la importación son las rarezas reales de EA — sus
identificadores `EAID_`, su bloque `xmi:Extension`, su dialecto.

Contiene tres clases de una clínica:

| Clase | Posición en el diagrama | Atributos | Operaciones |
|---|---|---|---|
| `Paciente` | (60, 80) | ci, nombre, nacimiento | edad(): int |
| `Consulta` | (420, 260) | fecha, motivo, diagnostico | — |
| `Medico` | (420, 40) | matricula, especialidad | — |

Y dos asociaciones con multiplicidad `1 → 0..*`: *Paciente tiene Consultas* y
*Medico atiende Consultas*.

### Cómo usarlo

Abrí un diagrama vacío en FORJA → botón `XMI` → **Importar…** → elegí este
archivo. Tienen que entrar las tres clases **en esas mismas posiciones**, con
sus atributos y las dos relaciones.

Si las clases entran alineadas en una fila, la vista no se está leyendo.

### Para regenerarlo

```powershell
powershell -ExecutionPolicy Bypass -File herramientas\crear-ejemplo-ea.ps1
```

Necesita Enterprise Architect instalado. Crea el modelo por automatización COM,
lo exporta y borra el `.feap` intermedio, que pesa 11 MB y no va al repositorio.

---

## La trampa del formato, que cuesta una demostración

**Enterprise Architect exporta XMI 1.1 sobre UML 1.3 por omisión.** Es el tipo 0
de su exportador y es lo que sale si nadie toca el desplegable del diálogo.

Ese dialecto usa `<UML:Class>` en vez de `<packagedElement>`: son dos
metamodelos distintos, y FORJA no lo lee. **Al exportar hay que elegir XMI 2.1**
en el desplegable de formato.

Medido, no supuesto: de los 17 valores del exportador, los tipos **0 a 9** dan
dialectos viejos (XMI 1.0, 1.1 y 1.2) y del **10 en adelante** dan XMI 2.1 con
la vista incluida.

Si te equivocás, FORJA te lo dice con todas las letras en vez de contestar que
el documento no tiene clases: *«Este documento está en XMI 1.x sobre UML 1.3 […]
Volvé a exportarlo eligiendo XMI 2.1 en el desplegable de formato»*.
