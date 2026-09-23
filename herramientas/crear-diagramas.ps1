# Genera en Enterprise Architect las ocho figuras del documento y las exporta
# como PNG a docs/diagramas.
#
# Se hace con EA y no con una herramienta de dibujo por dos razones: la notacion
# sale correcta sin tener que cuidarla a mano, y un documento de una materia que
# evalua UML conviene que lleve diagramas hechos con una herramienta UML.
#
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas/crear-diagramas.ps1
# Requiere Enterprise Architect instalado. Lo que hay en este equipo, y con lo
# que se generaron las figuras del documento, es la version de prueba 15.0
# (build 1514), en C:\Program Files (x86)\Sparx Systems\EA Trial\EA.exe.

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

function Ajena($hijo, $padre, $nombre = '') {
  # Clave ajena del modelo fisico: se traza de la tabla que la declara hacia la
  # referenciada, con la multiplicidad del lado que corresponde. No lleva rombo
  # -eso es composicion, y aqui lo que hay es una restriccion referencial-.
  $c = $hijo.Connectors.AddNew($nombre, 'Association')
  $c.SupplierID = $padre.ElementID
  $c.Direction = 'Source -> Destination'
  [void]$c.Update()
  $c.ClientEnd.Cardinality = '0..*'
  $c.SupplierEnd.Cardinality = '1'
  [void]$c.Update()
  return $c
}

# t y b van negados: EA cuenta el eje vertical hacia arriba.
#
# OJO: l NO puede valer 0. Con «l=0;...» EA descarta la ubicacion entera y el
# elemento no se dibuja: no avisa, no falla, simplemente falta en el PNG. Al
# angostar los diagramas se puso la primera columna en x=0 y desaparecieron
# tres clases del modelo de datos sin un solo error. La primera columna
# arranca en 20.
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
# Las tres columnas van juntas a proposito. Lo unico que decide si el texto se
# lee al pegar la figura en Word es la relacion entre el alto de linea y el
# ancho de la imagen: Word la reduce al ancho util de la pagina, de modo que
# cuanto mas ancho el PNG, mas chica la letra. Con los actores secundarios en
# x=1120 el PNG salia de 1822 puntos de ancho y la letra quedaba en dos puntos.
# La correccion es angostar el dibujo -acercar las columnas-, NO agrandar la
# fuente: agrandarla ensancha las cajas y el resultado es el mismo. Medido: el
# PNG baja de 1822 a poco mas de 900 puntos, por debajo de los 1050 que hacen
# falta para que el texto salga a ocho puntos en una pagina de dieciseis
# centimetros. El orden vertical no se toca, que es lo que evita los cruces.
Ubicar $dia2 $mod2   20 380  90 100
Ubicar $dia2 $llm2  430 180 110 100
Ubicar $dia2 $ea2   430 680 110 100
$y = 40
foreach ($k in 'CU11','CU12','CU13','CU14','CU15','CU16','CU17','CU18') {
  Ubicar $dia2 $c2[$k] 160 $y 260 70; $y += 110
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
# Tres columnas juntas, por el mismo limite de ancho que las demas figuras: el
# PNG venia de 1384 puntos. Las cajas bajan de 220 a 175, que es lo que ocupa
# el nombre mas largo -«P5 Colaboracion en Tiempo Real»-, y las dependencias
# no llevan rotulo, asi que aqui no hay nada mas que acomodar.
# P5 pasa a la columna de la izquierda en lugar de quedar a media calle: con
# las columnas tan juntas, el desplazamiento de antes lo montaba sobre P3.
Ubicar $diaPk $p['P1'] 230   40 175 70
Ubicar $diaPk $p['P2'] 230  180 175 70
Ubicar $diaPk $p['P6']  20  340 175 70
Ubicar $diaPk $p['P4'] 230  340 175 70
Ubicar $diaPk $p['P8'] 440  340 175 70
Ubicar $diaPk $p['P5']  20  520 175 70
Ubicar $diaPk $p['P3'] 230  520 175 70
Ubicar $diaPk $p['P7'] 440  520 175 70
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
# Los nombres de objeto van cortos por lo mismo que los rotulos: son ellos los
# que fijan el ancho minimo de la caja, y la caja fija el ancho del dibujo.
# Quien es A y quien es B lo dice el pie de la figura.
$a    = NuevoElemento $paqM1 'a :ClienteWeb (A)' 'Object'
$b    = NuevoElemento $paqM1 'b :ClienteWeb (B)' 'Object'
$ctrl = NuevoElemento $paqM1 ':ControladorDiagramas' 'Object'
$ops  = NuevoElemento $paqM1 ':ServicioOperaciones' 'Object'
$blo  = NuevoElemento $paqM1 ':ServicioBloqueo' 'Object'
$apl  = NuevoElemento $paqM1 ':AplicadorComando' 'Object'
$reg  = NuevoElemento $paqM1 ':RegistroDeSesiones' 'Object'
$bd   = NuevoElemento $paqM1 ':PostgreSQL' 'Object'

[void](Mensaje $a    $ctrl '1: aplicar(comando)')
[void](Mensaje $ctrl $ops  '2: registrar(comando)')
# «3: bloquear» es buscarParaActualizar, el SELECT ... FOR UPDATE que serializa
# la secuencia. El nombre completo del metodo no entra: el rotulo se dibuja en
# el medio del enlace y chocaba con el del mensaje 4, tapandole el numero.
[void](Mensaje $ops  $bd   '3: bloquear / 7: guardar')
[void](Mensaje $ops  $blo  '4: adquirir')
[void](Mensaje $blo  $bd   '5: INSERT ON CONFLICT')
[void](Mensaje $ops  $apl  '6: aplicar(comando)')
[void](Mensaje $ctrl $reg  '8: difundir')
[void](Mensaje $reg  $b    '9: evento de operacion')
[void](Mensaje $b    $ctrl '10: aplicar / 11: rechazo')

$diaM1 = $paqM1.Diagrams.AddNew('CU10 Editar en forma concurrente', 'Collaboration')
[void]$diaM1.Update(); $paqM1.Diagrams.Refresh()
# Rejilla de tres por tres: cada objeto sale hacia un lado distinto, de modo que
# ningun enlace cruza a otro ni pasa sobre una caja.
#
# Las columnas van juntas. Con las cajas de 220 puntos separadas 420 el PNG
# salia de 1700 de ancho y, reducido al ancho util de la hoja, quedaba al
# treinta y tres por ciento: la letra era ilegible. Ahora el PNG esta por
# debajo de 1050 y la misma letra sale al cincuenta y tres por ciento, o sea
# un sesenta por ciento mas grande, SIN tocar la fuente: agrandarla ensancha
# las cajas y el resultado en la hoja es el mismo.
#
# Lo que fijaba el ancho no eran las cajas -los nombres de objeto entran en
# 150 puntos- sino los rotulos de los mensajes, que se dibujan en el medio del
# enlace: cuatro firmas completas una al lado de la otra no entran en una hoja.
# Por eso arriba quedan el numero y el nombre del mensaje, y la firma con sus
# parametros vive en la tabla de mensajes que el documento pone debajo de la
# figura, que es donde se lee de verdad.
Ubicar $diaM1 $a      20   40 125 70
Ubicar $diaM1 $ctrl  205   40 125 70
Ubicar $diaM1 $apl   390   40 125 70
Ubicar $diaM1 $b      20  300 125 70
Ubicar $diaM1 $reg   205  300 125 70
Ubicar $diaM1 $ops   390  300 125 70
Ubicar $diaM1 $bd    205  560 125 70
Ubicar $diaM1 $blo   390  560 125 70
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
$gem   = NuevoElemento $paqM2 ':Gemma 3 4B (Ollama)' 'Object'
$par   = NuevoElemento $paqM2 ':ParserVoz' 'Object'
$ops2  = NuevoElemento $paqM2 ':ServicioOperaciones' 'Object'
$alc   = NuevoElemento $paqM2 ':AlcanceDelPedido' 'Object'

[void](Mensaje $per  $cped  '1: pedir   /   8: propuesta   /   9: aplicar')
[void](Mensaje $cped $sped  '2: leer(pedido)')
[void](Mensaje $sped $tra   '3: aFrasesCanonicas()')
[void](Mensaje $tra  $gem   '4: HTTP local  ->  frases')
[void](Mensaje $sped $par   '5: interpretar(frase)')
# Cortas a proposito: en la rejilla, estas dos etiquetas salen del mismo objeto
# hacia abajo y con el texto largo se pisaban entre si. El detalle completo va en
# la tabla de mensajes del documento, que es lo que se lee de verdad.
[void](Mensaje $sped $alc   '6: recortar')
[void](Mensaje $sped $props '7: guardar   /   10: leer')
[void](Mensaje $sped $ops2  '11: registrar')

$diaM2 = $paqM2.Diagrams.AddNew('CU13 Pedir un elemento en lenguaje libre', 'Collaboration')
[void]$diaM2.Update(); $paqM2.Diagrams.Refresh()
# Misma rejilla compacta que la figura anterior, y por el mismo motivo: el PNG
# venia de 1662 puntos de ancho y en la hoja no se leia. Las cajas van a 125 y
# los rotulos quedan reducidos al numero y al nombre del mensaje; la firma
# completa esta en la tabla de mensajes del documento.
Ubicar $diaM2 $per     20   40 125 70
Ubicar $diaM2 $cped   215   40 125 70
Ubicar $diaM2 $gem    410   40 125 70
Ubicar $diaM2 $par     20  300 125 70
Ubicar $diaM2 $sped   215  300 125 70
Ubicar $diaM2 $tra    410  300 125 70
Ubicar $diaM2 $ops2    20  560 125 70
Ubicar $diaM2 $alc    215  560 125 70
Ubicar $diaM2 $props  410  560 125 70
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
# Por el mismo limite de ancho: el PNG venia de 1328 puntos. Los nodos bajan a
# 290 y los componentes de adentro a 250, que es lo que necesita el nombre mas
# largo -«forja-backend (Spring Boot 4.1.1 / Java 21)»-. El servidor deja de
# ser un nodo ancho de 480: con los dos componentes apilados uno sobre otro no
# hace falta mas ancho que el de ellos.
Ubicar $diaDep $navegador  20   40 290 150
Ubicar $diaDep $web        40   90 250  60
Ubicar $diaDep $android   340   40 290 150
Ubicar $diaDep $movil     360   90 250  60
Ubicar $diaDep $servidor   20  280 290 230
Ubicar $diaDep $api        40  330 250  60
Ubicar $diaDep $bdDep      40  430 250  60
Ubicar $diaDep $equipoIa  340  280 290 130
Ubicar $diaDep $ollama    360  330 250  60
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
# Misma rejilla de tres columnas que antes, pero angostada: la disposicion no
# cambia, cambia el ancho. Las cajas eran de 220 a 240 puntos y la linea mas
# larga -«multiplicidadDestino: String»- ocupa bastante menos, de modo que
# sobraba casi un tercio de caja vacia y la tercera columna arrancaba en x=880.
# El PNG salia de 1686 puntos y en la hoja, reducido al ancho util, la letra
# quedaba diminuta. Los huecos entre columnas se dejan holgados a proposito:
# ahi van los rotulos de multiplicidad, y pegando las columnas se montaban
# sobre el borde de la caja vecina.
Ubicar $diaC $usuario   20   40 140 130
Ubicar $diaC $proy     200   40 140 120
Ubicar $diaC $oper     380   40 150 150
Ubicar $diaC $uso       20  260 140  90
Ubicar $diaC $miembro  200  260 140  90
Ubicar $diaC $bloq     380  260 150 130
Ubicar $diaC $diag     200  450 140 120
Ubicar $diaC $rel      380  450 150 130
Ubicar $diaC $clase    200  660 140 160
Ubicar $diaC $atr       20  900 140 160
Ubicar $diaC $met      200  900 140 130
Ubicar $diaC $parU     200 1110 140 100
$diaC.DiagramObjects.Refresh()
Exportar $diaC 'modelo-de-datos.png'

# =====================================================================
# Figura 8. Modelo fisico de datos
# =====================================================================
# La figura anterior es el modelo LOGICO: clases con atributos de dominio. Esta
# es el esquema tal como existe en PostgreSQL, y por eso no se copia de las
# entidades de Java sino de las migraciones -V1__init.sql y
# V2__uso_herramienta.sql-, que son las que corre Flyway y por lo tanto la
# unica fuente de verdad. Nombres y tipos van literales del SQL: `pos_x`, no
# `posX`; `VARCHAR(180)`, no `String`.
#
# Las claves se marcan en el propio tipo -«id: UUID PK», «proyecto_id: UUID
# FK»- en lugar de con un estereotipo o un icono, porque se lee sin leyenda y
# sobrevive a la reduccion de la imagen en la hoja.
$paqF = NuevoPaquete $modelo 'Modelo Fisico'

function Tabla($paquete, $nombre, $columnas) {
  $e = NuevoElemento $paquete $nombre 'Class'
  # Sin estereotipo a proposito: «table» y «entity» hacen que EA dibuje un
  # icono en lugar de la caja con compartimentos, que es justo lo que se
  # necesita para ver las columnas.
  foreach ($c in $columnas) { Atributo $e $c[0] $c[1] }
  return $e
}

$tb = @{}
$tb['usuario'] = Tabla $paqF 'usuario' @(
  @('id','UUID PK'), @('email','VARCHAR(180) UNIQUE'), @('password_hash','VARCHAR(120)'),
  @('nombre','VARCHAR(120)'), @('activo','BOOLEAN'), @('creado_en','TIMESTAMPTZ'))
$tb['proyecto'] = Tabla $paqF 'proyecto' @(
  @('id','UUID PK'), @('nombre','VARCHAR(150)'), @('descripcion','TEXT'),
  @('propietario_id','UUID FK'), @('creado_en','TIMESTAMPTZ'), @('actualizado_en','TIMESTAMPTZ'))
$tb['proyecto_miembro'] = Tabla $paqF 'proyecto_miembro' @(
  @('proyecto_id','UUID PK FK'), @('usuario_id','UUID PK FK'), @('rol','VARCHAR(20)'),
  @('invitado_en','TIMESTAMPTZ'))
$tb['diagrama'] = Tabla $paqF 'diagrama' @(
  @('id','UUID PK'), @('proyecto_id','UUID FK'), @('nombre','VARCHAR(150)'),
  @('tipo','VARCHAR(20)'), @('version','BIGINT'), @('creado_en','TIMESTAMPTZ'),
  @('actualizado_en','TIMESTAMPTZ'))
$tb['clase_uml'] = Tabla $paqF 'clase_uml' @(
  @('id','UUID PK'), @('diagrama_id','UUID FK'), @('nombre','VARCHAR(120)'),
  @('estereotipo','VARCHAR(60)'), @('es_abstracta','BOOLEAN'), @('pos_x','DOUBLE PRECISION'),
  @('pos_y','DOUBLE PRECISION'), @('ancho','DOUBLE PRECISION'), @('alto','DOUBLE PRECISION'),
  @('creado_en','TIMESTAMPTZ'))
$tb['atributo_uml'] = Tabla $paqF 'atributo_uml' @(
  @('id','UUID PK'), @('clase_id','UUID FK'), @('nombre','VARCHAR(120)'),
  @('tipo','VARCHAR(80)'), @('visibilidad','VARCHAR(12)'), @('es_identificador','BOOLEAN'),
  @('es_requerido','BOOLEAN'), @('es_unico','BOOLEAN'), @('longitud','INTEGER'),
  @('valor_defecto','VARCHAR(120)'), @('orden','INTEGER'))
$tb['metodo_uml'] = Tabla $paqF 'metodo_uml' @(
  @('id','UUID PK'), @('clase_id','UUID FK'), @('nombre','VARCHAR(120)'),
  @('tipo_retorno','VARCHAR(80)'), @('visibilidad','VARCHAR(12)'), @('es_abstracto','BOOLEAN'),
  @('es_estatico','BOOLEAN'), @('orden','INTEGER'))
$tb['parametro_uml'] = Tabla $paqF 'parametro_uml' @(
  @('id','UUID PK'), @('metodo_id','UUID FK'), @('nombre','VARCHAR(120)'),
  @('tipo','VARCHAR(80)'), @('orden','INTEGER'))
$tb['relacion_uml'] = Tabla $paqF 'relacion_uml' @(
  @('id','UUID PK'), @('diagrama_id','UUID FK'), @('origen_id','UUID FK'),
  @('destino_id','UUID FK'), @('tipo','VARCHAR(20)'), @('multiplicidad_origen','VARCHAR(10)'),
  @('multiplicidad_destino','VARCHAR(10)'), @('rol_origen','VARCHAR(120)'),
  @('rol_destino','VARCHAR(120)'), @('etiqueta','VARCHAR(150)'))
$tb['bloqueo_elemento'] = Tabla $paqF 'bloqueo_elemento' @(
  @('id','UUID PK'), @('diagrama_id','UUID FK'), @('elemento_tipo','VARCHAR(20)'),
  @('elemento_id','UUID'), @('usuario_id','UUID FK'), @('sesion_id','VARCHAR(80)'),
  @('adquirido_en','TIMESTAMPTZ'), @('expira_en','TIMESTAMPTZ'))
$tb['operacion'] = Tabla $paqF 'operacion' @(
  @('id','UUID PK'), @('diagrama_id','UUID FK'), @('secuencia','BIGINT'),
  @('usuario_id','UUID FK'), @('tipo','VARCHAR(40)'), @('carga','JSONB'),
  @('token_cliente','VARCHAR(80)'), @('origen','VARCHAR(20)'), @('creada_en','TIMESTAMPTZ'))
$tb['uso_herramienta'] = Tabla $paqF 'uso_herramienta' @(
  @('usuario_id','UUID PK FK'), @('herramienta','VARCHAR(40) PK'), @('veces','INTEGER'),
  @('primera_vez','TIMESTAMPTZ'), @('ultima_vez','TIMESTAMPTZ'))

[void](Ajena $tb['proyecto']         $tb['usuario'])
[void](Ajena $tb['proyecto_miembro'] $tb['proyecto'])
[void](Ajena $tb['proyecto_miembro'] $tb['usuario'])
[void](Ajena $tb['uso_herramienta']  $tb['usuario'])
[void](Ajena $tb['diagrama']         $tb['proyecto'])
[void](Ajena $tb['clase_uml']        $tb['diagrama'])
[void](Ajena $tb['bloqueo_elemento'] $tb['diagrama'])
[void](Ajena $tb['bloqueo_elemento'] $tb['usuario'])
[void](Ajena $tb['operacion']        $tb['diagrama'])
[void](Ajena $tb['operacion']        $tb['usuario'])
[void](Ajena $tb['relacion_uml']     $tb['diagrama'])
# relacion_uml tiene DOS ajenas hacia clase_uml, origen_id y destino_id, pero
# se dibuja un solo conector rotulado: dos conectores entre el mismo par de
# cajas quedan uno encima del otro y se ven como uno solo, con la diferencia de
# que asi el rotulo miente menos. Las dos columnas figuran igual en la caja.
[void](Ajena $tb['atributo_uml']     $tb['clase_uml'])
[void](Ajena $tb['metodo_uml']       $tb['clase_uml'])
[void](Ajena $tb['parametro_uml']    $tb['metodo_uml'])
[void](Ajena $tb['relacion_uml']     $tb['clase_uml'] 'origen / destino')

$diaF = $paqF.Diagrams.AddNew('Modelo Fisico de Datos', 'Logical')
[void]$diaF.Update(); $paqF.Diagrams.Refresh()
# Doce tablas en tres columnas por cuatro filas, y no en una sola fila, por el
# limite de ancho: en fila unica el PNG pasaria de los cuatro mil puntos. El
# reparto no es arbitrario, agrupa por quien referencia a quien para que las
# ajenas queden entre cajas vecinas: `usuario` arriba a la izquierda con lo que
# cuelga de el, `diagrama` en el centro con el modelo UML debajo.
# Las columnas de la izquierda y del centro llevan 160 puntos; la de la derecha
# 190, porque ahi vive `multiplicidad_destino: VARCHAR(10)`, la linea mas larga
# del esquema, y con menos se recortaria. Los 70 puntos de hueco entre columnas
# no son decoracion: son el lugar donde EA dibuja los rotulos de multiplicidad,
# y con las columnas pegadas se montaban sobre el borde de la caja de al lado.
Ubicar $diaF $tb['usuario']           20   40 165 112
Ubicar $diaF $tb['proyecto']         215   40 165 112
Ubicar $diaF $tb['proyecto_miembro'] 405   40 190  86
Ubicar $diaF $tb['uso_herramienta']   20  222 165  99
Ubicar $diaF $tb['diagrama']         215  222 165 125
Ubicar $diaF $tb['bloqueo_elemento'] 405  222 190 138
Ubicar $diaF $tb['operacion']         20  430 165 151
Ubicar $diaF $tb['clase_uml']        215  430 165 164
Ubicar $diaF $tb['relacion_uml']     405  430 190 164
Ubicar $diaF $tb['atributo_uml']      20  664 165 177
Ubicar $diaF $tb['metodo_uml']       215  664 165 138
Ubicar $diaF $tb['parametro_uml']    405  664 190  99
$diaF.DiagramObjects.Refresh()
Exportar $diaF 'modelo-fisico.png'

$repo.CloseFile()
$repo.Exit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($repo)
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
'listo'
