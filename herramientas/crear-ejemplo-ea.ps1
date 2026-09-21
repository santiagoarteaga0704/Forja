# Genera un modelo en Enterprise Architect de verdad y lo exporta a XMI.
#
# No se escribe el XMI a mano a proposito: un archivo escrito a mano solo
# confirma las suposiciones de quien lo escribe. Lo que hace falta para probar
# la importacion es un documento con las rarezas reales de EA -sus
# identificadores EAID_, su bloque de extension, su dialecto- y eso solo lo
# produce EA.

$ErrorActionPreference = 'Stop'

$base    = 'C:\Program Files (x86)\Sparx Systems\EA Trial\EABase.feap'
$trabajo = 'C:\dev\forja\docs\ejemplos\clinica.feap'
$salida  = 'C:\dev\forja\docs\ejemplos\clinica-desde-ea.xmi'

# EA bloquea el archivo mientras corre: hay que cerrarlo antes de cada corrida.
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

New-Item -ItemType Directory -Force -Path (Split-Path $trabajo) | Out-Null
Remove-Item $trabajo -ErrorAction SilentlyContinue
Copy-Item $base $trabajo

$repo = New-Object -ComObject EA.Repository
if (-not $repo.OpenFile($trabajo)) { throw "No se pudo abrir $trabajo" }
"repositorio abierto"

$modelo = $repo.Models.GetAt(0)
$paq = $modelo.Packages.AddNew('Clinica', 'Package')
[void]$paq.Update()
$modelo.Packages.Refresh()
"paquete Clinica creado"

function NuevaClase($paquete, $nombre, $notas) {
  $c = $paquete.Elements.AddNew($nombre, 'Class')
  $c.Notes = $notas
  [void]$c.Update()
  return $c
}

function NuevoAtributo($clase, $nombre, $tipo) {
  $a = $clase.Attributes.AddNew($nombre, $tipo)
  [void]$a.Update()
  $clase.Attributes.Refresh()
}

function NuevaOperacion($clase, $nombre, $retorno) {
  $m = $clase.Methods.AddNew($nombre, $retorno)
  [void]$m.Update()
  $clase.Methods.Refresh()
}

$paciente = NuevaClase $paq 'Paciente' 'Persona que se atiende en la clinica'
NuevoAtributo $paciente 'ci' 'String'
NuevoAtributo $paciente 'nombre' 'String'
NuevoAtributo $paciente 'nacimiento' 'Date'
NuevaOperacion $paciente 'edad' 'int'

$consulta = NuevaClase $paq 'Consulta' 'Cada visita del paciente'
NuevoAtributo $consulta 'fecha' 'Date'
NuevoAtributo $consulta 'motivo' 'String'
NuevoAtributo $consulta 'diagnostico' 'String'

$medico = NuevaClase $paq 'Medico' 'Profesional que atiende'
NuevoAtributo $medico 'matricula' 'String'
NuevoAtributo $medico 'especialidad' 'String'

$paq.Elements.Refresh()
"3 clases creadas"

# Relaciones
$aso = $paciente.Connectors.AddNew('tiene', 'Association')
$aso.SupplierID = $consulta.ElementID
[void]$aso.Update()
$aso.ClientEnd.Cardinality = '1'
$aso.SupplierEnd.Cardinality = '0..*'
[void]$aso.Update()

$aso2 = $medico.Connectors.AddNew('atiende', 'Association')
$aso2.SupplierID = $consulta.ElementID
[void]$aso2.Update()
$aso2.ClientEnd.Cardinality = '1'
$aso2.SupplierEnd.Cardinality = '0..*'
[void]$aso2.Update()
"2 asociaciones creadas"

# El diagrama, con posiciones DISTINTAS entre si para poder comprobarlas.
$dia = $paq.Diagrams.AddNew('Modelo de la clinica', 'Logical')
[void]$dia.Update()
$paq.Diagrams.Refresh()

function Ubicar($diagrama, $elemento, $l, $t, $r, $b) {
  $o = $diagrama.DiagramObjects.AddNew("l=$l;r=$r;t=$t;b=$b;", '')
  $o.ElementID = $elemento.ElementID
  [void]$o.Update()
}

Ubicar $dia $paciente  60  -80  260 -190
Ubicar $dia $consulta 420 -260 620 -370
Ubicar $dia $medico   420  -40 620 -150
$dia.DiagramObjects.Refresh()
"diagrama armado con 3 cajas en posiciones distintas"

# SaveDiagram necesita el diagrama abierto en la interfaz y no hace falta para
# exportar: los DiagramObjects ya estan guardados por sus propios Update().
$proyecto = $repo.GetProjectInterface()

# TIPO 10 = XMI 2.1. Medido, no supuesto: los tipos 0 a 9 producen dialectos
# viejos -XMI 1.0, 1.1 y 1.2 sobre UML 1.3-, con <UML:Class> en vez de
# <packagedElement>, que FORJA no lee. El 0 es el POR OMISION, asi que exportar
# sin elegir tipo da un archivo inservible para el intercambio.
Remove-Item $salida -ErrorAction SilentlyContinue
# Firma: GUID, tipo, DiagramXML, FormatXML, UseDTD, WriteDTD, archivo.
# Son SIETE: con seis, PowerShell intenta meter el nombre del archivo
# donde va un entero y falla con un error que no menciona el problema.
$ok = $proyecto.ExportPackageXMI($paq.PackageGUID, 10, 1, 1, 0, 0, $salida)
"ExportPackageXMI devolvio: $ok"

$repo.CloseFile()
$repo.Exit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($repo)
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force

if (Test-Path $salida) {
  "XMI escrito: $salida  ($((Get-Item $salida).Length) bytes)"
} else {
  throw "no se genero el XMI"
}
