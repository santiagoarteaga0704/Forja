# Genera en Enterprise Architect las siete figuras del documento y las exporta
# como PNG a docs/diagramas.
#
# Se hace con EA y no con una herramienta de dibujo por dos razones: la notacion
# sale correcta sin tener que cuidarla a mano, y un documento de una materia que
# evalua UML conviene que lleve diagramas hechos con una herramienta UML.
#
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas/crear-diagramas.ps1
# Requiere Enterprise Architect instalado (probado con la version 17.2 de prueba).

$ErrorActionPreference = 'Stop'

$base    = 'C:\Program Files (x86)\Sparx Systems\EA Trial\EABase.feap'
$trabajo = Join-Path $env:TEMP 'forja-diagramas.feap'
$salida  = Join-Path $PSScriptRoot '..\docs\diagramas'

if (-not (Test-Path $base)) { throw "No se encontro el modelo base de EA en $base" }

# Los atributos se guardan con su Pos, pero EA igual los dibuja en orden
# alfabetico salvo que se apague esta opcion.
$opciones = 'HKCU:\Software\Sparx Systems\EA400\EA\OPTIONS'
if (Test-Path $opciones) {
  New-ItemProperty -Path $opciones -Name 'SORT_FEATURES' -Value 0 -PropertyType DWord -Force | Out-Null
}

Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
New-Item -ItemType Directory -Force -Path $salida | Out-Null
Remove-Item $trabajo -ErrorAction SilentlyContinue
Copy-Item $base $trabajo

$repo = New-Object -ComObject EA.Repository
if (-not $repo.OpenFile($trabajo)) { throw "No se pudo abrir $trabajo" }
$modelo   = $repo.Models.GetAt(0)
$proyecto = $repo.GetProjectInterface()
'repositorio abierto'

# ---------- Ayudantes -------------------------------------------------------

function NuevoPaquete($padre, $nombre) {
  $p = $padre.Packages.AddNew($nombre, 'Package'); [void]$p.Update()
  $padre.Packages.Refresh(); return $p
}

function NuevoElemento($paquete, $nombre, $tipo) {
  $e = $paquete.Elements.AddNew($nombre, $tipo); [void]$e.Update()
  $paquete.Elements.Refresh(); return $e
}

function Atributo($clase, $nombre, $tipo) {
  $a = $clase.Attributes.AddNew($nombre, $tipo); [void]$a.Update(); $clase.Attributes.Refresh()
}

function Conectar($desde, $hasta, $tipo, $nombre = '', $estereotipo = '') {
  $c = $desde.Connectors.AddNew($nombre, $tipo)
  $c.SupplierID = $hasta.ElementID
  [void]$c.Update()
  if ($estereotipo) { $c.Stereotype = $estereotipo; [void]$c.Update() }
  return $c
}

function Mensaje($desde, $hasta, $texto) {
  $c = $desde.Connectors.AddNew($texto, 'Association')
  $c.SupplierID = $hasta.ElementID
  $c.Direction = 'Source -> Destination'
  [void]$c.Update()
  return $c
}

function Componer($todo, $parte, $multiplicidad = '0..*') {
  # EA dibuja el rombo en el extremo DESTINO del conector, de modo que la
  # composicion se traza de la parte hacia el todo. Trazada al reves el rombo
  # queda sobre la parte, que es justo lo contrario de lo que dice UML.
  # Ademas, Subtype='Strong' por si sola deja el rombo hueco al exportar: la
  # marca de composicion vive en el extremo del conector (0 ninguna,
  # 1 compartida, 2 compuesta).
  $c = $parte.Connectors.AddNew('', 'Aggregation')
  $c.SupplierID = $todo.ElementID
  $c.Subtype = 'Strong'
  [void]$c.Update()
  $c.ClientEnd.Cardinality = $multiplicidad
  $c.SupplierEnd.Cardinality = '1'
  $c.SupplierEnd.Aggregation = 2
  [void]$c.Update()
  return $c
}

# t y b van negados: EA cuenta el eje vertical hacia arriba.
function Ubicar($diagrama, $elemento, $l, $t, $ancho, $alto) {
  $o = $diagrama.DiagramObjects.AddNew("l=$l;r=$($l+$ancho);t=$(-$t);b=$(-($t+$alto));", '')
  $o.ElementID = $elemento.ElementID
  [void]$o.Update()
}

function Exportar($diagrama, $archivo) {
  $ruta = Join-Path $salida $archivo
  Remove-Item $ruta -ErrorAction SilentlyContinue
  [void]$proyecto.PutDiagramImageToFile($diagrama.DiagramGUID, $ruta, 1)   # 1 = PNG
  if (Test-Path $ruta) { "  -> $archivo  ($((Get-Item $ruta).Length) bytes)" }
  else { "  !! no se genero $archivo" }
}

# =====================================================================
# Figura 1. Casos de uso, Ciclo #1
# =====================================================================
# Los casos de uso van en DOS diagramas y no en uno: con los dieciocho juntos el
# actor Modelador se conecta con diecisiete y las lineas se cruzan hasta volver
# el dibujo ilegible. Separarlos por ciclo coincide ademas con como el documento
# los prioriza.
$paq1 = NuevoPaquete $modelo 'Ciclo 1'
$mod1 = NuevoElemento $paq1 'Modelador' 'Actor'
$col1 = NuevoElemento $paq1 'Colaborador' 'Actor'

$c1 = @{}
foreach ($n in @(
  'CU1 Registrar usuario','CU2 Iniciar sesion','CU3 Cerrar sesion',
  'CU4 Administrar proyecto','CU5 Invitar colaborador','CU6 Administrar diagrama',
  'CU7 Modelar clases en el lienzo','CU8 Trazar relaciones',
  'CU9 Editar atributos y operaciones','CU10 Editar en forma concurrente')) {
  $c1[$n.Split(' ')[0]] = NuevoElemento $paq1 $n 'UseCase'
}

foreach ($k in 'CU1','CU2','CU3','CU4','CU5','CU6','CU7','CU8','CU9') {
  [void](Conectar $mod1 $c1[$k] 'Association')
}
foreach ($k in 'CU6','CU7','CU8','CU9','CU10') {
  [void](Conectar $col1 $c1[$k] 'Association')
}

$dia1 = $paq1.Diagrams.AddNew('Casos de Uso - Ciclo 1', 'Use Case')
[void]$dia1.Update(); $paq1.Diagrams.Refresh()
Ubicar $dia1 $mod1 40 180 90 100
Ubicar $dia1 $col1 40 700 90 100
$y = 40
foreach ($k in 'CU1','CU2','CU3','CU4','CU5','CU6','CU7','CU8','CU9','CU10') {
  Ubicar $dia1 $c1[$k] 340 $y 240 60; $y += 90
}
$dia1.DiagramObjects.Refresh()
Exportar $dia1 'casos-de-uso-ciclo1.png'

# =====================================================================
# Figura 2. Casos de uso, Ciclo #2
# =====================================================================
# El modelo de lenguaje atiende los dos casos de arriba y Enterprise Architect
# los dos de abajo: ubicados asi, ningun par de lineas se cruza. Las relaciones
# «include» hacia CU7-CU9 no se dibujan aqui —cinco lineas hacia un mismo
# destino pasaban por encima de los ovalos—; el documento las enuncia y los
# diagramas de comunicacion las muestran realizadas.
$paq2 = NuevoPaquete $modelo 'Ciclo 2'
$mod2 = NuevoElemento $paq2 'Modelador' 'Actor'
$ea2  = NuevoElemento $paq2 'Enterprise Architect' 'Actor'
$llm2 = NuevoElemento $paq2 'Modelo de lenguaje local' 'Actor'

$c2 = @{}
foreach ($n in @(
  'CU11 Dictar cambios por voz','CU12 Leer el diagrama desde una fotografia',
  'CU13 Pedir un elemento en lenguaje libre','CU14 Consultar al agente guia',
  'CU15 Generar el backend Spring Boot','CU16 Exportar el modelo a XMI',
  'CU17 Importar un modelo desde XMI','CU18 Modelar sin conexion y sincronizar')) {
  $c2[$n.Split(' ')[0]] = NuevoElemento $paq2 $n 'UseCase'
}

foreach ($k in 'CU11','CU12','CU13','CU14','CU15','CU16','CU17','CU18') {
  [void](Conectar $mod2 $c2[$k] 'Association')
}
foreach ($k in 'CU13','CU14') { [void](Conectar $c2[$k] $llm2 'Association') }
foreach ($k in 'CU16','CU17') { [void](Conectar $c2[$k] $ea2  'Association') }

$dia2 = $paq2.Diagrams.AddNew('Casos de Uso - Ciclo 2', 'Use Case')
[void]$dia2.Update(); $paq2.Diagrams.Refresh()
Ubicar $dia2 $mod2   40 380  90 100
Ubicar $dia2 $llm2 1120 180 110 100
Ubicar $dia2 $ea2  1120 680 110 100
$y = 40
foreach ($k in 'CU11','CU12','CU13','CU14','CU15','CU16','CU17','CU18') {
  Ubicar $dia2 $c2[$k] 400 $y 260 70; $y += 110
}
$dia2.DiagramObjects.Refresh()
Exportar $dia2 'casos-de-uso-ciclo2.png'

# =====================================================================
# Figura 3. Vista de paquetes
# =====================================================================
$paqPk = NuevoPaquete $modelo 'Vista de Paquetes'
$p = @{}
foreach ($d in @(
  @('P1','P1 Seguridad y Acceso'), @('P2','P2 Gestion de Proyectos'),
  @('P3','P3 Modelo UML'),         @('P4','P4 Operaciones'),
  @('P5','P5 Colaboracion en Tiempo Real'), @('P6','P6 Vias de Entrada'),
  @('P7','P7 Agente Guia'),        @('P8','P8 Salidas del Modelo'))) {
  $p[$d[0]] = NuevoElemento $paqPk $d[1] 'Package'
}
foreach ($par in @(@('P2','P1'), @('P4','P3'), @('P6','P4'), @('P6','P3'),
                   @('P5','P3'), @('P7','P3'), @('P8','P3'), @('P8','P4'),
                   @('P2','P3'), @('P4','P2'))) {
  [void](Conectar $p[$par[0]] $p[$par[1]] 'Dependency')
}

$diaPk = $paqPk.Diagrams.AddNew('Vista de Paquetes', 'Package')
[void]$diaPk.Update(); $paqPk.Diagrams.Refresh()
Ubicar $diaPk $p['P1'] 420   40 220 70
Ubicar $diaPk $p['P2'] 420  180 220 70
Ubicar $diaPk $p['P6']  60  340 220 70
Ubicar $diaPk $p['P4'] 420  340 220 70
Ubicar $diaPk $p['P8'] 780  340 220 70
Ubicar $diaPk $p['P5'] 160  520 220 70
Ubicar $diaPk $p['P3'] 420  520 220 70
Ubicar $diaPk $p['P7'] 780  520 220 70
$diaPk.DiagramObjects.Refresh()
Exportar $diaPk 'paquetes.png'

# =====================================================================
# Figura 4. Comunicacion, CU10: editar en forma concurrente
# =====================================================================
# Los objetos son las clases reales de src/main/java. Cuando dos mensajes viajan
# entre el mismo par de objetos comparten el enlace, porque dos conectores entre
# las mismas cajas quedan uno encima del otro.
#
# Las etiquetas van cortas a proposito: EA calcula el recorte de la imagen por
# las cajas y no por el texto de los enlaces, de modo que una etiqueta larga
# cerca del borde sale cortada. El detalle completo lo lleva la prosa.
$paqM1 = NuevoPaquete $modelo 'Comunicacion CU10'
$a    = NuevoElemento $paqM1 'a :ClienteWeb (persona A)' 'Object'
$b    = NuevoElemento $paqM1 'b :ClienteWeb (persona B)' 'Object'
$ctrl = NuevoElemento $paqM1 ':ControladorDiagramas' 'Object'
$ops  = NuevoElemento $paqM1 ':ServicioOperaciones' 'Object'
$blo  = NuevoElemento $paqM1 ':ServicioBloqueo' 'Object'
$apl  = NuevoElemento $paqM1 ':AplicadorComando' 'Object'
$reg  = NuevoElemento $paqM1 ':RegistroDeSesiones' 'Object'
$bd   = NuevoElemento $paqM1 ':PostgreSQL' 'Object'

[void](Mensaje $a    $ctrl '1: aplicar(comando, sesionId, tokenCliente)')
[void](Mensaje $ctrl $ops  '2: registrar(comando, sesionId, token)')
[void](Mensaje $ops  $bd   '3: buscarParaActualizar   /   7: guardar la operacion')
[void](Mensaje $ops  $blo  '4: adquirir(elemento, usuarioId, sesionId)')
[void](Mensaje $blo  $bd   '5: INSERT ... ON CONFLICT DO NOTHING')
[void](Mensaje $ops  $apl  '6: aplicar(diagrama, comando)')
[void](Mensaje $ctrl $reg  '8: difundir(evento, sesionOrigen)')
[void](Mensaje $reg  $b    '9: evento de operacion')
[void](Mensaje $b    $ctrl '10: aplicar(mismo elemento)   /   11: rechazo')

$diaM1 = $paqM1.Diagrams.AddNew('CU10 Editar en forma concurrente', 'Collaboration')
[void]$diaM1.Update(); $paqM1.Diagrams.Refresh()
# Rejilla de tres por tres: el diagrama tenia mil ochocientos puntos de ancho y
# la imagen, reducida al ancho de la pagina, dejaba la letra en dos puntos. Con
# la mitad de ancho la escala se duplica. Cada objeto sale hacia un lado
# distinto, de modo que ningun enlace cruza a otro ni pasa sobre una caja.
Ubicar $diaM1 $a      40   40 220 70
Ubicar $diaM1 $ctrl  460   40 220 70
Ubicar $diaM1 $apl   880   40 220 70
Ubicar $diaM1 $b      40  300 220 70
Ubicar $diaM1 $reg   460  300 220 70
Ubicar $diaM1 $ops   880  300 220 70
Ubicar $diaM1 $bd    460  560 220 70
Ubicar $diaM1 $blo   880  560 220 70
$diaM1.DiagramObjects.Refresh()
Exportar $diaM1 'comunicacion-cu10.png'

# =====================================================================
# Figura 5. Comunicacion, CU13: pedir un elemento en lenguaje libre
# =====================================================================
$paqM2 = NuevoPaquete $modelo 'Comunicacion CU13'
$per   = NuevoElemento $paqM2 ':Modelador' 'Object'
$cped  = NuevoElemento $paqM2 ':ControladorPedido' 'Object'
$sped  = NuevoElemento $paqM2 ':ServicioPedido' 'Object'
$props = NuevoElemento $paqM2 ':PropuestasEnRevision' 'Object'
$tra   = NuevoElemento $paqM2 ':TraductorOllama' 'Object'
$gem   = NuevoElemento $paqM2 ':Gemma 3 4B (Ollama local)' 'Object'
$par   = NuevoElemento $paqM2 ':ParserVoz' 'Object'
$ops2  = NuevoElemento $paqM2 ':ServicioOperaciones' 'Object'
$alc   = NuevoElemento $paqM2 ':AlcanceDelPedido' 'Object'

[void](Mensaje $per  $cped  '1: pedir(pedido)   /   8: la propuesta, a revisar   /   9: aplicar(token)')
[void](Mensaje $cped $sped  '2: leer(pedido, tokenLectura)')
[void](Mensaje $sped $tra   '3: aFrasesCanonicas(pedido, contexto)')
[void](Mensaje $tra  $gem   '4: HTTP local  ->  frases del idioma controlado')
[void](Mensaje $sped $par   '5: interpretar(frase)  ->  comando, o se descarta')
# Cortas a proposito: en la rejilla, estas dos etiquetas salen del mismo objeto
# hacia abajo y con el texto largo se pisaban entre si. El detalle completo va en
# la tabla de mensajes del documento, que es lo que se lee de verdad.
[void](Mensaje $sped $alc   '6: recortar()  ->  UN elemento')
[void](Mensaje $sped $props '7: guardar   /   10: recuperar')
[void](Mensaje $sped $ops2  '11: registrar(cada comando)')

$diaM2 = $paqM2.Diagrams.AddNew('CU13 Pedir un elemento en lenguaje libre', 'Collaboration')
[void]$diaM2.Update(); $paqM2.Diagrams.Refresh()
# Misma rejilla compacta que la figura anterior, y por el mismo motivo.
Ubicar $diaM2 $per     40   40 220 70
Ubicar $diaM2 $cped   460   40 220 70
Ubicar $diaM2 $gem    880   40 220 70
Ubicar $diaM2 $par     40  300 220 70
Ubicar $diaM2 $sped   460  300 220 70
Ubicar $diaM2 $tra    880  300 220 70
Ubicar $diaM2 $ops2    40  560 220 70
Ubicar $diaM2 $alc    460  560 220 70
Ubicar $diaM2 $props  880  560 220 70
$diaM2.DiagramObjects.Refresh()
Exportar $diaM2 'comunicacion-cu13.png'

# =====================================================================
# Figura 6. Modelo de despliegue
# =====================================================================
$paqDep = NuevoPaquete $modelo 'Despliegue'
$navegador = NuevoElemento $paqDep 'Navegador' 'Node'
$android   = NuevoElemento $paqDep 'Dispositivo Android' 'Node'
$servidor  = NuevoElemento $paqDep 'Servidor (instancia EC2)' 'Node'
$equipoIa  = NuevoElemento $paqDep 'Equipo de demostracion' 'Node'

$web    = NuevoElemento $paqDep 'Cliente web (React + Vite + OCR local)' 'Component'
$movil  = NuevoElemento $paqDep 'Cliente Flutter (con base local)' 'Component'
$api    = NuevoElemento $paqDep 'forja-backend (Spring Boot 4.1.1 / Java 21)' 'Component'
$bdDep  = NuevoElemento $paqDep 'PostgreSQL 17' 'Component'
$ollama = NuevoElemento $paqDep 'Ollama + Gemma 3 4B' 'Component'

[void](Conectar $navegador $servidor 'Association' 'HTTPS / WSS')
[void](Conectar $android   $servidor 'Association' 'HTTPS')
[void](Conectar $api       $bdDep    'Association' 'JDBC')
[void](Conectar $api       $ollama   'Association' 'HTTP local')

$diaDep = $paqDep.Diagrams.AddNew('Modelo de Despliegue', 'Deployment')
[void]$diaDep.Update(); $paqDep.Diagrams.Refresh()
Ubicar $diaDep $navegador  40   40 320 150
Ubicar $diaDep $web        70   90 260  60
Ubicar $diaDep $android   440   40 320 150
Ubicar $diaDep $movil     470   90 260  60
Ubicar $diaDep $servidor   40  280 480 230
Ubicar $diaDep $api        80  330 400  60
Ubicar $diaDep $bdDep      80  430 400  60
Ubicar $diaDep $equipoIa  600  280 340 130
Ubicar $diaDep $ollama    630  330 280  60
$diaDep.DiagramObjects.Refresh()
Exportar $diaDep 'despliegue.png'

# =====================================================================
# Figura 7. Modelo de datos
# =====================================================================
$paqC = NuevoPaquete $modelo 'Modelo de Datos'

$usuario = NuevoElemento $paqC 'Usuario' 'Class'
Atributo $usuario 'id' 'UUID'; Atributo $usuario 'email' 'String'
Atributo $usuario 'passwordHash' 'String'; Atributo $usuario 'nombre' 'String'
Atributo $usuario 'activo' 'boolean'

$proy = NuevoElemento $paqC 'Proyecto' 'Class'
Atributo $proy 'id' 'UUID'; Atributo $proy 'nombre' 'String'
Atributo $proy 'descripcion' 'String'; Atributo $proy 'actualizadoEn' 'Instant'

$miembro = NuevoElemento $paqC 'ProyectoMiembro' 'Class'
Atributo $miembro 'rol' 'RolMiembro'; Atributo $miembro 'invitadoEn' 'Instant'

$diag = NuevoElemento $paqC 'Diagrama' 'Class'
Atributo $diag 'id' 'UUID'; Atributo $diag 'nombre' 'String'
Atributo $diag 'tipo' 'TipoDiagrama'; Atributo $diag 'version' 'long'

$clase = NuevoElemento $paqC 'ClaseUml' 'Class'
Atributo $clase 'id' 'UUID'; Atributo $clase 'nombre' 'String'
Atributo $clase 'estereotipo' 'String'; Atributo $clase 'esAbstracta' 'boolean'
Atributo $clase 'posX' 'double'; Atributo $clase 'posY' 'double'

$atr = NuevoElemento $paqC 'AtributoUml' 'Class'
Atributo $atr 'nombre' 'String'; Atributo $atr 'tipo' 'String'
Atributo $atr 'visibilidad' 'Visibilidad'; Atributo $atr 'esIdentificador' 'boolean'
Atributo $atr 'esRequerido' 'boolean'; Atributo $atr 'esUnico' 'boolean'

$met = NuevoElemento $paqC 'MetodoUml' 'Class'
Atributo $met 'nombre' 'String'; Atributo $met 'tipoRetorno' 'String'
Atributo $met 'visibilidad' 'Visibilidad'; Atributo $met 'esAbstracto' 'boolean'

$parU = NuevoElemento $paqC 'ParametroUml' 'Class'
Atributo $parU 'nombre' 'String'; Atributo $parU 'tipo' 'String'; Atributo $parU 'orden' 'int'

$rel = NuevoElemento $paqC 'RelacionUml' 'Class'
Atributo $rel 'tipo' 'TipoRelacion'; Atributo $rel 'multiplicidadOrigen' 'String'
Atributo $rel 'multiplicidadDestino' 'String'; Atributo $rel 'etiqueta' 'String'

$bloq = NuevoElemento $paqC 'BloqueoElemento' 'Class'
Atributo $bloq 'elementoTipo' 'TipoElemento'; Atributo $bloq 'elementoId' 'UUID'
Atributo $bloq 'sesionId' 'String'; Atributo $bloq 'expiraEn' 'Instant'

$oper = NuevoElemento $paqC 'Operacion' 'Class'
Atributo $oper 'secuencia' 'long'; Atributo $oper 'tipo' 'TipoOperacion'
Atributo $oper 'carga' 'Json'; Atributo $oper 'tokenCliente' 'String'
Atributo $oper 'origen' 'OrigenOperacion'

$uso = NuevoElemento $paqC 'UsoHerramienta' 'Class'
Atributo $uso 'herramienta' 'Herramienta'; Atributo $uso 'veces' 'int'

[void](Conectar $proy $usuario 'Association' 'propietario')
[void](Componer $proy $miembro)
[void](Conectar $miembro $usuario 'Association')
[void](Componer $proy $diag)
[void](Componer $diag $clase)
[void](Componer $clase $atr)
[void](Componer $clase $met)
[void](Componer $met $parU)
[void](Componer $diag $rel)
[void](Conectar $rel $clase 'Association' 'origen / destino')
[void](Componer $diag $bloq)
[void](Componer $diag $oper)
[void](Conectar $uso $usuario 'Association')
[void](Conectar $oper $usuario 'Association')

$diaC = $paqC.Diagrams.AddNew('Modelo de Datos', 'Logical')
[void]$diaC.Update(); $paqC.Diagrams.Refresh()
Ubicar $diaC $usuario   40   40 220 130
Ubicar $diaC $proy     320   40 220 120
Ubicar $diaC $oper     880   40 240 150
Ubicar $diaC $uso       40  260 220  90
Ubicar $diaC $miembro  320  260 220  90
Ubicar $diaC $bloq     880  260 240 130
Ubicar $diaC $diag     320  450 220 120
Ubicar $diaC $rel      880  450 240 130
Ubicar $diaC $clase    320  660 220 160
Ubicar $diaC $atr       20  900 230 160
Ubicar $diaC $met      340  900 220 130
Ubicar $diaC $parU     340 1110 220 100
$diaC.DiagramObjects.Refresh()
Exportar $diaC 'modelo-de-datos.png'

$repo.CloseFile()
$repo.Exit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($repo)
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
'listo'
