# Genera en Enterprise Architect un modelo GRANDE y lo exporta a XMI 2.1.
#
# El ejemplo chico (crear-ejemplo-ea.ps1) sirve para comprobar que la
# importacion funciona. Este sirve para otra cosa: ver si aguanta un modelo de
# verdad, con las construcciones que un diagrama de una materia trae y el de
# tres clases no -herencia, interfaz realizada, composicion, muchos a muchos y
# tipos que no son todos texto-.
#
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas\crear-ejemplo-ea-grande.ps1
# Requiere Enterprise Architect instalado.

$ErrorActionPreference = 'Stop'

$base    = 'C:\Program Files (x86)\Sparx Systems\EA Trial\EA Trial\EABase.feap'
if (-not (Test-Path $base)) { $base = 'C:\Program Files (x86)\Sparx Systems\EA Trial\EABase.feap' }
$trabajo = Join-Path $env:TEMP 'forja-universidad.feap'
$salida  = Join-Path $PSScriptRoot '..\docs\ejemplos\universidad-desde-ea.xmi'

if (-not (Test-Path $base)) { throw "No se encontro el modelo base de EA en $base" }

Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Remove-Item $trabajo -ErrorAction SilentlyContinue
Copy-Item $base $trabajo

$repo = New-Object -ComObject EA.Repository
if (-not $repo.OpenFile($trabajo)) { throw "No se pudo abrir $trabajo" }
$modelo = $repo.Models.GetAt(0)
$paq = $modelo.Packages.AddNew('Universidad', 'Package')
[void]$paq.Update()
$modelo.Packages.Refresh()
'paquete Universidad creado'

# ---------- Ayudantes -------------------------------------------------------

function NuevaClase($paquete, $nombre, $notas, $abstracta, $estereotipo) {
    $c = $paquete.Elements.AddNew($nombre, 'Class')
    $c.Notes = $notas
    if ($abstracta) { $c.Abstract = '1' }
    if ($estereotipo) { $c.Stereotype = $estereotipo }
    [void]$c.Update()
    return $c
}

function Atributo($clase, $nombre, $tipo) {
    $a = $clase.Attributes.AddNew($nombre, $tipo)
    [void]$a.Update()
    $clase.Attributes.Refresh()
}

function Operacion($clase, $nombre, $retorno) {
    $m = $clase.Methods.AddNew($nombre, $retorno)
    [void]$m.Update()
    $clase.Methods.Refresh()
}

# El conector va del cliente (origen) al proveedor (destino). En una
# generalizacion, el origen es la SUBCLASE: la flecha apunta de lo particular
# a lo general, como manda UML.
function Conectar($origen, $destino, $tipo, $nombre, $cardOrigen, $cardDestino) {
    $c = $origen.Connectors.AddNew($nombre, $tipo)
    $c.SupplierID = $destino.ElementID
    [void]$c.Update()
    if ($cardOrigen)  { $c.ClientEnd.Cardinality = $cardOrigen }
    if ($cardDestino) { $c.SupplierEnd.Cardinality = $cardDestino }
    [void]$c.Update()
    return $c
}

# ---------- Las clases ------------------------------------------------------

$persona = NuevaClase $paq 'Persona' 'Raiz de la jerarquia de personas' $true $null
Atributo $persona 'ci' 'String'
Atributo $persona 'nombre' 'String'
Atributo $persona 'correo' 'String'
Operacion $persona 'nombreCompleto' 'String'

$auditable = NuevaClase $paq 'Auditable' 'Lo que deja rastro de quien lo cambio' $false 'interface'
Operacion $auditable 'registrarCambio' 'void'

$estudiante = NuevaClase $paq 'Estudiante' 'Persona inscrita en una carrera' $false $null
Atributo $estudiante 'registro' 'String'
Atributo $estudiante 'fechaIngreso' 'Date'

$docente = NuevaClase $paq 'Docente' 'Quien dicta los grupos' $false $null
Atributo $docente 'profesion' 'String'
Atributo $docente 'fechaContrato' 'Date'
Operacion $docente 'antiguedad' 'int'

$carrera = NuevaClase $paq 'Carrera' 'Plan de estudios' $false $null
Atributo $carrera 'codigo' 'String'
Atributo $carrera 'nombre' 'String'
Atributo $carrera 'duracionSemestres' 'int'

$materia = NuevaClase $paq 'Materia' 'Asignatura del plan' $false $null
Atributo $materia 'sigla' 'String'
Atributo $materia 'nombre' 'String'
Atributo $materia 'creditos' 'int'

$grupo = NuevaClase $paq 'Grupo' 'Una materia dictada en un horario' $false $null
Atributo $grupo 'codigo' 'String'
Atributo $grupo 'aula' 'String'
Atributo $grupo 'cupo' 'int'

$inscripcion = NuevaClase $paq 'Inscripcion' 'Un estudiante en un grupo' $false $null
Atributo $inscripcion 'fecha' 'Date'
Atributo $inscripcion 'nota' 'decimal'
Operacion $inscripcion 'aprobada' 'boolean'

$pago = NuevaClase $paq 'Pago' 'Cuota de un estudiante' $false $null
Atributo $pago 'monto' 'decimal'
Atributo $pago 'momento' 'DateTime'
Atributo $pago 'concepto' 'String'

$paq.Elements.Refresh()
'9 clases creadas'

# ---------- Las relaciones --------------------------------------------------

[void](Conectar $estudiante $persona 'Generalization' '' $null $null)
[void](Conectar $docente    $persona 'Generalization' '' $null $null)
[void](Conectar $docente    $auditable 'Realisation'  '' $null $null)

[void](Conectar $carrera    $estudiante  'Association' 'inscribe'  '1'   '0..*')
[void](Conectar $carrera    $materia     'Association' 'contiene'  '1..*' '1..*')
[void](Conectar $materia    $grupo       'Association' 'se dicta'  '1'   '0..*')
[void](Conectar $docente    $grupo       'Association' 'dicta'     '1'   '0..*')
[void](Conectar $grupo      $inscripcion 'Aggregation' 'reune'     '1'   '0..*')
[void](Conectar $estudiante $inscripcion 'Association' 'cursa'     '1'   '0..*')
[void](Conectar $estudiante $pago        'Association' 'paga'      '1'   '0..*')
'10 relaciones creadas'

# ---------- El diagrama -----------------------------------------------------

$dia = $paq.Diagrams.AddNew('Modelo academico', 'Logical')
[void]$dia.Update()
$paq.Diagrams.Refresh()

function Ubicar($diagrama, $elemento, $x, $y) {
    $o = $diagrama.DiagramObjects.AddNew("l=$x;r=$($x + 200);t=$(-$y);b=$(-($y + 110));", '')
    $o.ElementID = $elemento.ElementID
    [void]$o.Update()
}

Ubicar $dia $persona      340   40
Ubicar $dia $auditable    900   40
Ubicar $dia $estudiante    60  240
Ubicar $dia $docente      620  240
Ubicar $dia $carrera       60   40
Ubicar $dia $pago         340  460
Ubicar $dia $inscripcion   60  460
Ubicar $dia $grupo        620  460
Ubicar $dia $materia      900  240
$dia.DiagramObjects.Refresh()
'diagrama armado con las 9 cajas'

# ---------- Exportar --------------------------------------------------------

$proyecto = $repo.GetProjectInterface()
Remove-Item $salida -ErrorAction SilentlyContinue
# TIPO 10 = XMI 2.1. Ver la nota del ejemplo chico: del 0 al 9 salen dialectos
# viejos sobre UML 1.3 que FORJA no lee, y el 0 es el que sale por omision.
$ok = $proyecto.ExportPackageXMI($paq.PackageGUID, 10, 1, 1, 0, 0, $salida)
"ExportPackageXMI devolvio: $ok"

$repo.CloseFile()
$repo.Exit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($repo)
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item $trabajo -ErrorAction SilentlyContinue

if (Test-Path $salida) {
    $f = Get-Item $salida
    "XMI escrito: $($f.FullName)  ($($f.Length) bytes)"
} else {
    throw 'no se genero el XMI'
}
