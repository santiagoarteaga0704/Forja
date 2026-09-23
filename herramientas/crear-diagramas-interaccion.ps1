# Genera en Enterprise Architect las treinta y seis figuras de interaccion de
# la seccion 2.2.4 del documento: un diagrama de SECUENCIA por cada uno de los
# diecinueve casos de uso, y un diagrama de COMUNICACION por cada uno de los
# diecisiete que todavia no lo tenian. Los de comunicacion de CU10 y CU13 ya
# estaban hechos y verificados, los genera herramientas/crear-diagramas.ps1 y
# este guion NO los toca.
#
# Secuencia y comunicacion son la misma interaccion vista de dos formas, asi
# que de cada caso salen los dos diagramas con los mismos participantes y los
# mismos mensajes numerados. La interaccion de cada caso esta derivada del
# codigo real; la tabla de mensajes que el documento pone debajo de cada figura
# lleva la firma completa, y el rotulo del dibujo solo el numero y el nombre.
#
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas/crear-diagramas-interaccion.ps1
# Requiere Enterprise Architect 15.0 Trial en
# C:\Program Files (x86)\Sparx Systems\EA Trial\EA.exe.
#
# ---------------------------------------------------------------------------
# LO QUE COSTO APRENDER, POR SI HAY QUE VOLVER A TOCARLO
#
# 1. El orden vertical de los mensajes de un diagrama de secuencia NO es el
#    orden en que se crean los conectores. Creandolos en orden salieron en el
#    orden 7,1,2,3,5,4,6,8,9: EA los reacomoda. Lo que manda es
#    Connector.SequenceNo, y hay que fijarlo DESPUES del primer Update().
#
# 2. Un nombre de elemento demasiado largo para su caja NO se ajusta: EA lo
#    CORTA. Con cajas de 75 unidades, «:ControladorAutenticacion» salia como
#    «:ControladorAutentic». Y las cajas no se pueden ensanchar, porque con
#    ocho o nueve participantes el PNG se va a 1600 puntos y en la hoja la
#    letra queda ilegible.
#    La salida es que el NOMBRE LLEVE UN SALTO DE LINEA: EA respeta un LF
#    dentro de Element.Name y dibuja la etiqueta en dos o tres renglones. Por
#    eso existe Quebrar(), que corta en las fronteras de mayuscula. Asi entran
#    nueve participantes por debajo de los 1050 puntos sin recortar un nombre.
#
# 3. EA le agrega «()» al final de todo nombre de mensaje que no termine en
#    «)». Un rotulo «8: registrar comando» sale «8: registrar comando()».
#    Todos los rotulos de aqui estan redactados para cerrar con parentesis.
#
# 4. En los diagramas de comunicacion el tipo de diagrama es 'Collaboration',
#    pero los conectores de tipo 'Sequence' NO se dibujan ahi: salen los
#    objetos sueltos y ningun mensaje, sin un solo error. Hay que usar
#    'Association' con Direction = 'Source -> Destination' y el mensaje
#    numerado como nombre del conector. Cuando dos mensajes viajan entre el
#    mismo par de objetos comparten un solo enlace, porque dos conectores entre
#    las mismas cajas quedan uno encima del otro.
#
# 5. l = 0 hace que EA descarte la ubicacion entera y no dibuje el elemento,
#    sin aviso. Ninguna columna arranca en 0.
#
# 6. Cada diagrama va en su PROPIO paquete, con COPIAS propias de sus
#    elementos. EA dibuja todo conector cuyos dos extremos esten en el
#    diagrama: compartiendo elementos entre el diagrama de secuencia y el de
#    comunicacion del mismo caso, cada uno habria arrastrado los conectores del
#    otro.
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'

$base    = 'C:\Program Files (x86)\Sparx Systems\EA Trial\EABase.feap'
$trabajo = Join-Path $env:TEMP 'forja-diagramas-interaccion.feap'
$salida  = Join-Path $PSScriptRoot '..\docs\diagramas'

if (-not (Test-Path $base)) { throw "No se encontro el modelo base de EA en $base" }

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

function Ubicar($diagrama, $elemento, $l, $t, $ancho, $alto) {
  # t y b van negados: EA cuenta el eje vertical hacia arriba.
  #
  # OJO: l NO puede valer 0. Con «l=0;...» EA descarta la ubicacion entera y
  # el elemento no se dibuja: no avisa y no falla. La primera columna arranca
  # en 20.
  $o = $diagrama.DiagramObjects.AddNew("l=$l;r=$($l+$ancho);t=$(-$t);b=$(-($t+$alto));", '')
  $o.ElementID = $elemento.ElementID
  [void]$o.Update()
}

function UbicarLinea($diagrama, $elemento, $l, $t, $ancho, $alto) {
  # Igual que Ubicar, pero fijando las coordenadas UNA SEGUNDA VEZ sobre el
  # objeto ya creado. Hace falta para las lineas de vida de los diagramas de
  # secuencia: con un solo Update() EA las deja en su ancho por defecto, y el
  # ancho es justo lo que hay que controlar para que nueve participantes
  # entren en la hoja.
  #
  # La segunda pasada NO se puede usar con los objetos de los diagramas de
  # comunicacion. Si el elemento ya tiene conectores -y ahi los tiene, porque
  # los mensajes se crean antes que el diagrama-, el DiagramObject queda
  # obsoleto despues del primer Update() y asignarle «left» revienta con
  # InvalidCastException. Por eso son dos funciones y no una.
  $o = $diagrama.DiagramObjects.AddNew("l=$l;r=$($l+$ancho);t=$(-$t);b=$(-($t+$alto));", '')
  $o.ElementID = $elemento.ElementID
  [void]$o.Update()
  # La referencia que devuelve AddNew queda OBSOLETA despues del Update: al
  # asignarle «left» revienta con InvalidCastException, y no siempre -depende
  # de lo que EA tenga en su cache-, que es lo peor que puede pasar. Hay que
  # volver a pedir el objeto a la coleccion ya refrescada.
  $diagrama.DiagramObjects.Refresh()
  $o2 = $diagrama.DiagramObjects.GetAt($diagrama.DiagramObjects.Count - 1)
  # Los cuatro valores van CONVERTIDOS A ENTERO. EA no acepta un Double en
  # estas propiedades y contesta InvalidCastException. La trampa es que
  # [math]::Floor devuelve Decimal cuando la division dio exacta y Double
  # cuando no, de modo que el guion andaba con seis participantes -630/6- y
  # reventaba con ocho -630/8-: el error parecia depender del caso de uso y en
  # realidad dependia de si el reparto daba resto.
  $o2.left   = [int]$l
  $o2.right  = [int]($l + $ancho)
  $o2.top    = [int](-$t)
  $o2.bottom = [int](-($t + $alto))
  [void]$o2.Update()
}

function Exportar($diagrama, $archivo) {
  $ruta = Join-Path $salida $archivo
  Remove-Item $ruta -ErrorAction SilentlyContinue
  [void]$proyecto.PutDiagramImageToFile($diagrama.DiagramGUID, $ruta, 1)   # 1 = PNG
  if (Test-Path $ruta) {
    Add-Type -AssemblyName System.Drawing
    $b = New-Object System.Drawing.Bitmap $ruta
    $w = $b.Width; $h = $b.Height
    $b.Dispose()
    $aviso = ''
    if ($w -gt 1050) { $aviso = '   <<< SE PASA DE 1050' }
    "  -> $archivo  $w x $h$aviso"
  } else { "  !! no se genero $archivo" }
}

function Quebrar($nombre, $max) {
  # EA respeta un salto de linea dentro del nombre del elemento, y es la unica
  # forma de meter «:ControladorAutenticacion» en una caja angosta sin que lo
  # corte. Se trocea en las fronteras de mayuscula -las de camelCase de
  # verdad, mirando que el caracter anterior sea minuscula o digito, para no
  # partir «:C» ni «(P»- y en los espacios, y despues se juntan las piezas
  # mientras entren en $max caracteres.
  if ($nombre.Length -le $max) { return $nombre }
  # Se corta PRIMERO por los espacios y solo despues, si una palabra sigue sin
  # entrar, por las fronteras de mayuscula. Al reves, «:ClienteWeb
  # (PanelClase.tsx)» se partia en «:ClienteWeb (Panel» y «Clase.tsx)», que se
  # lee peor que «:ClienteWeb» y «(PanelClase.tsx)».
  $lineas = @(); $linea = ''
  foreach ($palabra in $nombre.Split(' ')) {
    $trozos = @($palabra)
    if ($palabra.Length -gt $max) { $trozos = TrozosDeCamello $palabra $max }
    foreach ($t in $trozos) {
      if ($linea.Length -eq 0) { $linea = $t }
      elseif (($linea.Length + 1 + $t.Length) -le $max) { $linea += ' ' + $t }
      else { $lineas += $linea; $linea = $t }
    }
  }
  if ($linea.Length -gt 0) { $lineas += $linea }
  return ($lineas -join [string][char]10)
}

function TrozosDeCamello($palabra, $max) {
  # Trocea en las fronteras de camelCase de verdad -mayuscula cuyo caracter
  # anterior es minuscula o digito-, para no partir «:C» ni «(P», y despues
  # junta los trozos mientras entren en $max.
  $piezas = New-Object System.Collections.ArrayList
  $act = ''
  for ($i = 0; $i -lt $palabra.Length; $i++) {
    $ch = $palabra[$i]
    if ($i -gt 0 -and [char]::IsUpper($ch) -and
        ([char]::IsLower($palabra[$i-1]) -or [char]::IsDigit($palabra[$i-1]))) {
      if ($act.Length -gt 0) { [void]$piezas.Add($act); $act = '' }
    }
    $act += $ch
  }
  if ($act.Length -gt 0) { [void]$piezas.Add($act) }
  $salida = @(); $linea = ''
  foreach ($pz in $piezas) {
    if ($linea.Length -eq 0) { $linea = $pz }
    elseif (($linea.Length + $pz.Length) -le $max) { $linea += $pz }
    else { $salida += $linea; $linea = $pz }
  }
  if ($linea.Length -gt 0) { $salida += $linea }
  return $salida
}

# ---------- Generadores -----------------------------------------------------

# Ancho util en unidades de EA. El PNG sale a razon de 1,48 puntos por unidad,
# de modo que 698 unidades son unos 1025 puntos: por debajo del limite de 1050
# que hace falta para que el texto se lea al pegar la figura en una hoja de
# dieciseis centimetros.
$ANCHO_UTIL = 698
# El rotulo de un mensaje se dibuja centrado sobre la flecha, y EA calcula el
# recorte de la imagen por las CAJAS, no por el texto: un rotulo del ultimo
# tramo se sale del marco y aparece cortado. La ultima columna se ensancha
# para que el marco llegue mas a la derecha y el rotulo entre entero.
$COLA = 40

function DiagramaDeSecuencia($caso) {
  $id    = $caso.id
  $part  = $caso.part
  $n     = $part.Count
  $paso  = [int][math]::Floor(($ANCHO_UTIL - 6 - $COLA) / $n)
  $caja  = $paso - 14
  # 6,8 puntos por caracter, medido sobre los PNG ya exportados: con 5,6 -la
  # primera estimacion- «:AlmacenRegistros» entraba en la cuenta y en el
  # dibujo salia desbordado de su caja.
  $max   = [int][math]::Floor(($caja * 1.48 - 10) / 6.8)
  if ($max -lt 9) { $max = 9 }

  $paq = NuevoPaquete $modelo "Secuencia $id"
  $el = @()
  foreach ($p in $part) {
    $tipo = 'Sequence'
    $nom  = $p
    if ($p.StartsWith('@')) { $tipo = 'Actor'; $nom = $p.Substring(1) }
    $el += NuevoElemento $paq (Quebrar $nom $max) $tipo
  }

  $dia = $paq.Diagrams.AddNew($caso.titulo, 'Sequence')
  [void]$dia.Update(); $paq.Diagrams.Refresh()
  for ($i = 0; $i -lt $n; $i++) {
    $ancho = $caja
    if ($i -eq $n - 1) { $ancho = $caja + $COLA }
    UbicarLinea $dia $el[$i] (20 + $i * $paso) 40 $ancho 78
  }
  $dia.DiagramObjects.Refresh()

  $orden = 1
  foreach ($m in $caso.msgs) {
    $c = $el[$m[0]].Connectors.AddNew($m[2], 'Sequence')
    $c.SupplierID = $el[$m[1]].ElementID
    [void]$c.Update()
    $c.SequenceNo = $orden      # sin esto EA reacomoda los mensajes solo
    [void]$c.Update()
    $orden++
  }
  $dia.DiagramObjects.Refresh(); $dia.DiagramLinks.Refresh()
  Exportar $dia "secuencia-$id.png"
}

# La rejilla de los diagramas de comunicacion es la misma que usan las dos
# figuras que ya estaban -CU10 y CU13-, para que las diecinueve se vean
# iguales: tres columnas de 125 unidades separadas 185, y tres filas separadas
# 260. Cada objeto se coloca de modo que sus interlocutores le queden en una
# casilla VECINA -en linea o en diagonal-, que es lo que evita que un enlace
# cruce por encima de otra caja.
$COL = @(20, 205, 390)
$FIL = @(40, 300, 560)

function DiagramaDeComunicacion($caso) {
  $id  = $caso.id
  $obj = $caso.commObj
  $pos = $caso.commPos

  $paq = NuevoPaquete $modelo "Comunicacion $id"
  $el = @()
  # 25 caracteres es lo que entra en una caja de 125 unidades. Con 30, EA
  # dejaba «:ProyectoMiembroRepositorio» desbordado de su caja.
  foreach ($o in $obj) { $el += NuevoElemento $paq (Quebrar $o 25) 'Object' }

  foreach ($e in $caso.commLinks) {
    $c = $el[$e[0]].Connectors.AddNew($e[2], 'Association')
    $c.SupplierID = $el[$e[1]].ElementID
    $c.Direction = 'Source -> Destination'
    [void]$c.Update()
  }

  $dia = $paq.Diagrams.AddNew($caso.titulo, 'Collaboration')
  [void]$dia.Update(); $paq.Diagrams.Refresh()
  for ($i = 0; $i -lt $obj.Count; $i++) {
    Ubicar $dia $el[$i] $COL[$pos[$i][0]] $FIL[$pos[$i][1]] 125 70
  }
  $dia.DiagramObjects.Refresh()
  Exportar $dia "comunicacion-$id.png"
}

# =====================================================================
# Las diecinueve interacciones, derivadas del codigo
# =====================================================================
# `part` es el orden de izquierda a derecha del diagrama de secuencia; el «@»
# delante marca a los actores humanos, que se dibujan como monigote.
# `msgs` son @(indiceDeOrigen, indiceDeDestino, rotulo), en orden.
# `commObj` / `commPos` / `commLinks` describen la misma interaccion sobre la
# rejilla de tres por tres; en `commLinks` los mensajes que viajan entre el
# mismo par de objetos van juntos en un solo enlace.

$casos = @(

  @{ id = 'cu01'; titulo = 'CU1 Registrar usuario'; comm = $true
     part = @('@Modelador', ':ControladorAutenticacion', ':ServicioAutenticacion',
              ':UsuarioRepositorio', ':PasswordEncoder', ':JwtEncoder')
     msgs = @(
       @(0,1,'1: registrar(SolicitudRegistro)'),
       @(1,2,'2: registrar(email, nombre, password)'),
       @(2,3,'3: existsByEmail(email)'),
       @(2,4,'4: encode(password)'),
       @(2,3,'5: save(usuario)'),
       @(2,5,'6: encode(declaraciones)'),
       @(1,0,'7: 201 CREATED (Credencial)'))
     commObj = @(':Modelador', ':ControladorAutenticacion', ':ServicioAutenticacion',
                 ':UsuarioRepositorio', ':PasswordEncoder', ':JwtEncoder')
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,1), @(2,1), @(1,2))
     commLinks = @(
       @(0,1,'1: registrar   /   7: 201 CREATED'),
       @(1,2,'2: registrar'),
       @(2,3,'3: existsByEmail   /   5: save'),
       @(2,4,'4: encode'),
       @(2,5,'6: encode'))
  },

  @{ id = 'cu02'; titulo = 'CU2 Iniciar sesion'; comm = $true
     part = @('@Modelador', ':ControladorAutenticacion', ':ServicioAutenticacion',
              ':UsuarioRepositorio', ':PasswordEncoder', ':JwtEncoder',
              ':ClienteWeb (sesion.ts)', ':ControladorProyectos')
     msgs = @(
       @(0,1,'1: iniciarSesion(SolicitudSesion)'),
       @(1,2,'2: iniciarSesion(email, password)'),
       @(2,3,'3: findByEmail(email)'),
       @(2,4,'4: matches(password, passwordHash)'),
       @(2,5,'5: encode(vigencia 12 h)'),
       @(1,6,'6: Credencial(token, expiraEn)'),
       @(6,6,'7: guardarCredencial(credencial)'),
       @(6,7,'8: mios(GET /api/proyectos)'))
     commObj = @(':Modelador', ':ControladorAutenticacion', ':ServicioAutenticacion',
                 ':UsuarioRepositorio', ':PasswordEncoder', ':JwtEncoder',
                 ':ClienteWeb (sesion.ts)', ':ControladorProyectos')
     # «:ClienteWeb (sesion.ts)» NO puede ir en la columna de la derecha: EA
     # dibuja el mensaje a si mismo -el 7- como un lazo que sale por la derecha
     # de la caja, y ahi no tiene nada al lado, de modo que el PNG se estiraba
     # a 1147 puntos por culpa de un lazo. En la columna del medio el lazo cae
     # sobre la caja vecina, se lee igual, y el dibujo no crece.
     commPos = @(@(0,0), @(0,1), @(1,1), @(2,1), @(0,2), @(1,2), @(1,0), @(2,0))
     commLinks = @(
       @(0,1,'1: iniciarSesion'),
       @(1,2,'2: iniciarSesion'),
       @(2,3,'3: findByEmail'),
       @(2,4,'4: matches'),
       @(2,5,'5: encode'),
       @(1,6,'6: Credencial'),
       @(6,6,'7: guardar'),
       @(6,7,'8: mios'))
  },

  @{ id = 'cu03'; titulo = 'CU3 Cerrar sesion'; comm = $true
     part = @('@Modelador', ':ClienteWeb (App.tsx)', ':ClienteWeb (sesion.ts)',
              ':ClienteWeb (api.ts)', ':ClienteWeb (canal.ts)', ':ManejadorLienzo',
              ':RegistroDeSesiones', ':ServicioBloqueo')
     msgs = @(
       @(0,1,'1: salir()'),
       @(1,2,'2: guardarCredencial(null)'),
       @(2,3,'3: fijarToken(null)'),
       @(1,1,'4: setCredencial(null), setAbierto(null)'),
       @(4,5,'5: afterConnectionClosed(sesion, estado)'),
       @(5,6,'6: quitar(diagramaId, sesion)'),
       @(5,7,'7: liberarSesion(sesionId)'),
       @(5,6,'8: difundir(sesionCerrada)'))
     commObj = @(':Modelador', ':ClienteWeb (App.tsx)', ':ClienteWeb (sesion.ts)',
                 ':ClienteWeb (api.ts)', ':ClienteWeb (canal.ts)', ':ManejadorLienzo',
                 ':RegistroDeSesiones', ':ServicioBloqueo')
     commPos = @(@(0,0), @(1,0), @(1,1), @(2,0), @(0,2), @(1,2), @(2,2), @(2,1))
     commLinks = @(
       @(0,1,'1: salir'),
       @(1,2,'2: guardarCredencial'),
       @(2,3,'3: fijarToken'),
       @(1,1,'4: setCredencial'),
       @(4,5,'5: afterConnectionClosed'),
       @(5,6,'6: quitar   /   8: difundir'),
       @(5,7,'7: liberarSesion'))
  },

  @{ id = 'cu04'; titulo = 'CU4 Administrar proyecto'; comm = $true
     part = @('@Modelador', ':ControladorProyectos', ':ServicioProyectos',
              ':UsuarioRepositorio', ':ProyectoRepositorio', ':ProyectoMiembroRepositorio')
     msgs = @(
       @(0,1,'1: crear(NuevoProyecto)'),
       @(1,2,'2: crear(autorId, nombre, descripcion)'),
       @(2,3,'3: findById(autorId)'),
       @(2,4,'4: save(proyecto)'),
       @(2,5,'5: save(ProyectoMiembro PROPIETARIO)'),
       @(1,0,'6: 201 CREATED (ProyectoVista)'),
       @(0,1,'7: mios(GET /api/proyectos)'),
       @(2,4,'8: buscarPorParticipante(usuarioId)'))
     commObj = @(':Modelador', ':ControladorProyectos', ':ServicioProyectos',
                 ':UsuarioRepositorio', ':ProyectoRepositorio', ':ProyectoMiembroRepositorio')
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,1), @(2,1), @(1,2))
     commLinks = @(
       @(0,1,'1: crear   /   6: 201 CREATED   /   7: mios'),
       @(1,2,'2: crear'),
       @(2,3,'3: findById'),
       @(2,4,'4: save   /   8: buscarPorParticipante'),
       @(2,5,'5: save (miembro propietario)'))
  },

  @{ id = 'cu05'; titulo = 'CU5 Invitar colaborador'; comm = $true
     part = @('@Modelador', ':ControladorProyectos', ':ServicioProyectos',
              ':ProyectoRepositorio', ':ProyectoMiembroRepositorio',
              ':UsuarioRepositorio', '@Colaborador')
     msgs = @(
       @(0,1,'1: invitar(proyectoId, NuevoMiembro)'),
       @(1,2,'2: invitar(solicitanteId, email, rol)'),
       @(2,3,'3: findById(proyectoId)'),
       @(2,4,'4: findByProyectoIdAndUsuarioId(...)'),
       @(2,5,'5: findByEmail(email)'),
       @(2,4,'6: save(ProyectoMiembro EDITOR)'),
       @(1,0,'7: 201 CREATED (invitado)'),
       @(1,6,'8: buscarPorParticipante(ya ve el proyecto)'))
     commObj = @(':Modelador', ':ControladorProyectos', ':ServicioProyectos',
                 ':ProyectoRepositorio', ':ProyectoMiembroRepositorio',
                 ':UsuarioRepositorio', ':Colaborador')
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,1), @(1,2), @(2,1), @(2,0))
     commLinks = @(
       @(0,1,'1: invitar   /   7: 201 CREATED'),
       @(1,2,'2: invitar'),
       @(2,3,'3: findById'),
       @(2,4,'4: findByProyectoIdAndUsuarioId   /   6: save'),
       @(2,5,'5: findByEmail'),
       @(1,6,'8: buscarPorParticipante'))
  },

  @{ id = 'cu06'; titulo = 'CU6 Administrar diagrama'; comm = $true
     part = @('@Modelador', ':ControladorProyectos', ':ServicioProyectos',
              ':ProyectoMiembroRepositorio', ':DiagramaRepositorio', ':ClienteWeb',
              ':ControladorDiagramas', ':ConsultaDeDiagrama', ':ManejadorLienzo')
     msgs = @(
       @(0,1,'1: crearDiagrama(proyectoId, NuevoDiagrama)'),
       @(1,2,'2: crearDiagrama(autorId, nombre, tipo)'),
       @(2,3,'3: findByProyectoIdAndUsuarioId(...)'),
       @(2,4,'4: save(diagrama en version 0)'),
       @(1,5,'5: 201 CREATED (DiagramaResumen)'),
       @(5,6,'6: ver(diagramaId)'),
       @(6,7,'7: completo(diagramaId, usuarioId)'),
       @(5,8,'8: afterConnectionEstablished(registrar)'))
     commObj = @(':Modelador', ':ControladorProyectos', ':ServicioProyectos',
                 ':ProyectoMiembroRepositorio', ':DiagramaRepositorio', ':ClienteWeb',
                 ':ControladorDiagramas', ':ConsultaDeDiagrama', ':ManejadorLienzo')
     # El repositorio de miembros va al lado -enlace horizontal- y el de
     # diagramas en la diagonal. Al reves, el rotulo largo de
     # «3: findByProyectoIdAndUsuarioId» caia en la diagonal y tapaba al de
     # «2: crearDiagrama», que sale del enlace vertical a la misma altura.
     commPos = @(@(0,0), @(1,0), @(1,1), @(2,1), @(2,0), @(0,1), @(1,2), @(2,2), @(0,2))
     commLinks = @(
       @(0,1,'1: crearDiagrama'),
       @(1,2,'2: crearDiagrama'),
       @(2,3,'3: puedeEditar'),
       @(2,4,'4: save'),
       @(1,5,'5: 201 CREATED'),
       @(5,6,'6: ver'),
       @(6,7,'7: completo'),
       @(5,8,'8: afterConnectionEstablished'))
  },

  @{ id = 'cu07'; titulo = 'CU7 Modelar clases en el lienzo'; comm = $true
     part = @('@Modelador', ':ClienteWeb (Lienzo.tsx)', ':ControladorDiagramas',
              ':ServicioOperaciones', ':AplicadorComando', ':PostgreSQL',
              ':RegistroDeSesiones', '@Colaborador')
     msgs = @(
       @(0,1,'1: crearClase(nombre)'),
       @(1,1,'2: sitioLibre(x, y)'),
       @(1,2,'3: registrar(EnvioDeOperacion CLASE_CREAR)'),
       @(2,3,'4: registrar(comando, LIENZO, tokenCliente)'),
       @(3,5,'5: buscarParaActualizar(diagramaId)'),
       @(3,4,'6: aplicar(diagrama, CrearClase)'),
       @(3,5,'7: save(registro) y fijarVersion(...)'),
       @(2,6,'8: difundir(EventoLienzo.operacion)'),
       @(6,7,'8.1: el evento (por WebSocket)'))
     commObj = @(':Modelador', ':ClienteWeb (Lienzo.tsx)', ':ControladorDiagramas',
                 ':ServicioOperaciones', ':AplicadorComando', ':PostgreSQL',
                 ':RegistroDeSesiones', ':Colaborador')
     commPos = @(@(0,0), @(1,0), @(2,0), @(2,1), @(1,2), @(2,2), @(1,1), @(0,1))
     commLinks = @(
       @(0,1,'1: crearClase'),
       @(1,1,'2: sitioLibre'),
       @(1,2,'3: registrar'),
       @(2,3,'4: registrar'),
       @(3,5,'5: bloquear   /   7: guardar'),
       @(3,4,'6: aplicar'),
       @(2,6,'8: difundir'),
       @(6,7,'8.1: evento por WebSocket'))
  },

  @{ id = 'cu08'; titulo = 'CU8 Trazar relaciones'; comm = $true
     part = @('@Modelador', ':ClienteWeb (Lienzo.tsx)', ':ControladorDiagramas',
              ':ServicioOperaciones', ':AplicadorComando', ':PostgreSQL',
              ':RegistroDeSesiones')
     msgs = @(
       @(0,1,'1: elegirExtremo(claseId)'),
       @(1,1,'2: trazarRelacion(tipo, origenId, destinoId)'),
       @(1,2,'3: EnvioDeOperacion(RELACION_CREAR)'),
       @(2,3,'4: registrar(comando, LIENZO, tokenCliente)'),
       @(3,5,'5: buscarParaActualizar(diagramaId)'),
       @(3,4,'6: aplicar(diagrama, CrearRelacion)'),
       @(4,5,'7: relaciones.save(relacion)'),
       @(2,6,'8: difundir(EventoLienzo.operacion)'))
     commObj = @(':Modelador', ':ClienteWeb (Lienzo.tsx)', ':ControladorDiagramas',
                 ':ServicioOperaciones', ':AplicadorComando', ':PostgreSQL',
                 ':RegistroDeSesiones')
     commPos = @(@(0,0), @(1,0), @(2,0), @(2,1), @(1,2), @(2,2), @(1,1))
     commLinks = @(
       @(0,1,'1: elegirExtremo'),
       @(1,1,'2: trazarRelacion'),
       @(1,2,'3: EnvioDeOperacion'),
       @(2,3,'4: registrar'),
       @(3,5,'5: buscarParaActualizar'),
       @(3,4,'6: aplicar'),
       @(4,5,'7: relaciones.save'),
       @(2,6,'8: difundir'))
  },

  @{ id = 'cu09'; titulo = 'CU9 Editar atributos y operaciones'; comm = $true
     part = @('@Modelador', ':ClienteWeb (PanelClase.tsx)', ':ControladorDiagramas',
              ':ServicioOperaciones', ':ServicioBloqueo', ':PostgreSQL',
              ':AplicadorComando', ':RegistroDeSesiones')
     msgs = @(
       @(0,1,'1: agregarAtributo(evento)'),
       @(1,2,'2: EnvioDeOperacion(ATRIBUTO_AGREGAR)'),
       @(2,3,'3: registrar(comando, LIENZO, tokenCliente)'),
       @(3,4,'4: adquirir(CLASE, claseId, usuarioId, sesionId)'),
       @(4,5,'5: intentarAdquirir(INSERT ON CONFLICT)'),
       @(3,6,'6: aplicar(diagrama, AgregarAtributo)'),
       @(3,4,'7: liberar(solo si fue CONCEDIDO)'),
       @(2,7,'8: difundir(EventoLienzo.operacion)'))
     commObj = @(':Modelador', ':ClienteWeb (PanelClase.tsx)', ':ControladorDiagramas',
                 ':ServicioOperaciones', ':ServicioBloqueo', ':PostgreSQL',
                 ':AplicadorComando', ':RegistroDeSesiones')
     commPos = @(@(0,0), @(1,0), @(2,0), @(1,1), @(1,2), @(2,2), @(0,2), @(2,1))
     commLinks = @(
       @(0,1,'1: agregarAtributo'),
       @(1,2,'2: EnvioDeOperacion'),
       @(2,3,'3: registrar'),
       @(3,4,'4: adquirir   /   7: liberar'),
       @(4,5,'5: intentarAdquirir'),
       @(3,6,'6: aplicar'),
       @(2,7,'8: difundir'))
  },

  @{ id = 'cu10'; titulo = 'CU10 Editar en forma concurrente'; comm = $false
     part = @(':ClienteWeb (A)', ':ControladorDiagramas', ':ServicioOperaciones',
              ':ServicioBloqueo', ':PostgreSQL', ':AplicadorComando',
              ':RegistroDeSesiones', ':ClienteWeb (B)')
     msgs = @(
       @(0,1,'1: aplicar(comando, sesionId, tokenCliente)'),
       @(1,2,'2: registrar(comando, sesionId, tokenCliente)'),
       @(2,4,'3: buscarParaActualizar(diagramaId)'),
       @(2,3,'4: adquirir(elemento, usuarioId, sesionId)'),
       @(3,4,'5: insertar(ON CONFLICT DO NOTHING)'),
       @(2,5,'6: aplicar(diagrama, comando)'),
       @(2,4,'7: guardar(secuencia = version + 1)'),
       @(1,6,'8: difundir(evento, sesionOrigen)'),
       @(6,7,'9: el evento de la operacion (WebSocket)'),
       @(7,1,'10: aplicar(el mismo elemento, sesionB)'),
       @(1,7,'11: rechazo por bloqueo (lo retiene A)'))
  },

  @{ id = 'cu11'; titulo = 'CU11 Dictar cambios por voz'; comm = $true
     part = @('@Modelador', ':ClienteWeb (Dictado.tsx)', ':ControladorVoz',
              ':ServicioVoz', ':ServicioProyectos', ':DictadoConRespaldo',
              ':Cuadricula', ':ServicioOperaciones')
     msgs = @(
       @(0,1,'1: dictar la frase (se reconoce en el navegador)'),
       @(1,2,'2: dictar(diagramaId, Dictado(frase, sesionId))'),
       @(2,3,'3: dictar(usuarioId, sesionId, frase)'),
       @(3,4,'4: diagramaAccesible(diagramaId, usuarioId)'),
       @(3,5,'5: interpretar(parser, traductor, frase, contexto)'),
       @(3,6,'6: ubicar(comando, siguienteCasilla)'),
       @(3,7,'7: registrar(comando, VOZ, token + i)'),
       @(2,0,'8: ResultadoDictado(aplicadas, problemas)'))
     commObj = @(':Modelador', ':ClienteWeb (Dictado.tsx)', ':ControladorVoz',
                 ':ServicioVoz', ':ServicioProyectos', ':DictadoConRespaldo',
                 ':Cuadricula', ':ServicioOperaciones')
     commPos = @(@(0,0), @(0,1), @(1,0), @(1,1), @(2,0), @(2,1), @(1,2), @(0,2))
     commLinks = @(
       @(0,1,'1: dictar la frase'),
       @(1,2,'2: dictar'),
       @(2,3,'3: dictar'),
       @(3,4,'4: diagramaAccesible'),
       @(3,5,'5: interpretar'),
       @(3,6,'6: ubicar'),
       @(3,7,'7: registrar'),
       @(2,0,'8: ResultadoDictado'))
  },

  @{ id = 'cu12'; titulo = 'CU12 Leer el diagrama desde una fotografia de pizarra'; comm = $true
     part = @('@Modelador', ':ClienteWeb (Foto.tsx)', ':ControladorFoto',
              ':ServicioFoto', ':LectorVisualGemini', ':ParserPizarra',
              ':ServicioOperaciones')
     msgs = @(
       @(0,1,'1: elegir la imagen (Foto.tsx)'),
       @(1,2,'2: transcribir(diagramaId, imagen)'),
       @(2,3,'3: transcribir(usuarioId, bytes, tipoMime)'),
       @(3,4,'4: aNotacionDePizarra(imagen, presupuesto)'),
       @(2,0,'5: Transcripcion(texto) -> revisar -> leer(texto)'),
       @(0,2,'6: aplicar(TextoDePizarra(texto, token))'),
       @(3,5,'7: interpretar(texto, contexto)'),
       @(3,6,'8: registrar(comando, FOTO, token + i + tipo)'))
     commObj = @(':Modelador', ':ClienteWeb (Foto.tsx)', ':ControladorFoto',
                 ':ServicioFoto', ':LectorVisualGemini', ':ParserPizarra',
                 ':ServicioOperaciones')
     commPos = @(@(0,0), @(0,1), @(1,0), @(1,1), @(2,0), @(2,1), @(1,2))
     commLinks = @(
       @(0,1,'1: elegir la imagen'),
       @(1,2,'2: transcribir'),
       @(2,3,'3: transcribir'),
       @(3,4,'4: aNotacionDePizarra'),
       @(2,0,'5: Transcripcion   /   6: aplicar'),
       @(3,5,'7: interpretar'),
       @(3,6,'8: registrar'))
  },

  @{ id = 'cu13'; titulo = 'CU13 Pedir un elemento en lenguaje libre'; comm = $false
     part = @('@Modelador', ':ControladorPedido', ':ServicioPedido',
              ':TraductorOllama', ':Gemma 3 4B (Ollama)', ':ParserVoz',
              ':AlcanceDelPedido', ':PropuestasEnRevision', ':ServicioOperaciones')
     msgs = @(
       @(0,1,'1: pedir(creame la clase Paciente...)'),
       @(1,2,'2: leer(pedido, tokenLectura)'),
       @(2,3,'3: aFrasesCanonicas(pedido, contexto)'),
       @(3,4,'4: HTTP local (frases del idioma controlado)'),
       @(2,5,'5: interpretar(frase)'),
       @(2,6,'6: recortar(comandos)'),
       @(2,7,'7: guardar(la propuesta recortada)'),
       @(1,0,'8: la propuesta (para revisarla)'),
       @(0,1,'9: aplicar(tokenLectura)'),
       @(2,7,'10: recuperar(la propuesta guardada)'),
       @(2,8,'11: registrar(cada comando)'))
  },

  @{ id = 'cu14'; titulo = 'CU14 Consultar al agente guia'; comm = $true
     part = @('@Modelador', ':ControladorGuia', ':ServicioAgente',
              ':BaseDeLaHerramienta', ':BaseDeConocimiento', ':ServicioUso',
              ':Preguntas', ':RespondedorOllama')
     msgs = @(
       @(0,1,'1: guia(diagramaId, descartados)'),
       @(1,2,'2: guia(usuarioId, diagramaId, descartados)'),
       @(2,2,'3: mirarLaAplicacion(usuarioId, enUnDiagrama)'),
       @(2,3,'4: reglas().evaluar(panorama)'),
       @(2,4,'4.1: evaluar(observar(diagramaId, usuarioId))'),
       @(0,1,'5: preguntar(Pregunta(texto, sobre))'),
       @(2,5,'6: anotar(usuarioId, AGENTE_CONSULTADO)'),
       @(2,6,'7: responder(texto, sobre)'),
       @(2,7,'8: responder(texto, contexto, 25 s)'))
     commObj = @(':Modelador', ':ControladorGuia', ':ServicioAgente',
                 ':BaseDeLaHerramienta', ':BaseDeConocimiento', ':ServicioUso',
                 ':Preguntas', ':RespondedorOllama')
     # El almacen va A LA IZQUIERDA del sincronizador, no en diagonal: en
     # diagonal su rotulo quedaba a la misma altura que el del enlace vertical
     # que baja del cliente movil y salia «2: ejecuta3: guardarDiagrama».
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,1), @(2,1), @(2,2), @(1,2), @(0,2))
     commLinks = @(
       @(0,1,'1: guia   /   5: preguntar'),
       @(1,2,'2: guia'),
       @(2,2,'3: mirarLaAplicacion'),
       @(2,3,'4: reglas().evaluar'),
       @(2,4,'4.1: evaluar(observar)'),
       @(2,5,'6: anotar'),
       @(2,6,'7: responder'),
       @(2,7,'8: responder'))
  },

  @{ id = 'cu15'; titulo = 'CU15 Generar el backend Spring Boot'; comm = $true
     part = @('@Modelador', ':ControladorGeneracion', ':ServicioGeneracion',
              ':ServicioModelo', ':PlanificadorGeneracion', ':EscritorAndamiaje',
              ':EscritorEntidad', ':EscritorCapas', ':ServicioUso')
     msgs = @(
       @(0,1,'1: resumen(diagramaId, paquete)'),
       @(1,2,'2: archivos(diagramaId, usuarioId, paqueteBase)'),
       @(2,3,'3: clasesCompletas(id) y relacionesDe(id)'),
       @(2,4,'4: planificar(paquete, clases, relaciones)'),
       @(2,5,'5: pom(plan), readme(plan), compose(plan)'),
       @(2,6,'6: escribir(plan, clase)'),
       @(2,7,'6.1: repositorio, servicio, controlador(plan, clase)'),
       @(1,8,'7: anotar(usuarioId, BACKEND_GENERADO)'),
       @(0,1,'8: zip(diagramaId, paquete)'))
     commObj = @(':Modelador', ':ControladorGeneracion', ':ServicioGeneracion',
                 ':ServicioModelo', ':PlanificadorGeneracion', ':EscritorAndamiaje',
                 ':EscritorEntidad', ':EscritorCapas', ':ServicioUso')
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,1), @(2,1), @(1,2), @(0,2), @(2,2), @(2,0))
     commLinks = @(
       @(0,1,'1: resumen   /   8: zip'),
       @(1,2,'2: archivos'),
       @(2,3,'3: clasesCompletas'),
       @(2,4,'4: planificar'),
       @(2,5,'5: pom, readme'),
       @(2,6,'6: escribir'),
       @(2,7,'6.1: las capas'),
       @(1,8,'7: anotar'))
  },

  @{ id = 'cu16'; titulo = 'CU16 Exportar el modelo a XMI'; comm = $true
     part = @('@Modelador', ':ControladorXmi', ':ServicioXmi', ':ServicioProyectos',
              ':ServicioModelo', ':ExportadorXmi', ':ServicioUso',
              ':EnterpriseArchitect')
     msgs = @(
       @(0,1,'1: exportar(diagramaId)'),
       @(1,2,'2: exportar(diagramaId, usuarioId)'),
       @(2,3,'3: diagramaAccesible(diagramaId, usuarioId)'),
       @(2,4,'4: clasesCompletas(id) y relacionesDe(id)'),
       @(2,5,'5: exportar(diagrama, clases, relaciones)'),
       @(1,6,'6: anotar(usuarioId, XMI_EXPORTADO)'),
       @(1,0,'7: 200 attachment (modelo-id.xmi)'),
       @(0,7,'8: importar el documento (Ctrl+Alt+I)'))
     commObj = @(':Modelador', ':ControladorXmi', ':ServicioXmi', ':ServicioProyectos',
                 ':ServicioModelo', ':ExportadorXmi', ':ServicioUso',
                 ':EnterpriseArchitect')
     commPos = @(@(0,0), @(1,0), @(1,1), @(0,2), @(2,1), @(1,2), @(2,0), @(0,1))
     commLinks = @(
       @(0,1,'1: exportar   /   7: 200 attachment'),
       @(1,2,'2: exportar'),
       @(2,3,'3: diagramaAccesible'),
       @(2,4,'4: clasesCompletas'),
       @(2,5,'5: exportar'),
       @(1,6,'6: anotar'),
       @(0,7,'8: importar (Ctrl+Alt+I)'))
  },

  @{ id = 'cu17'; titulo = 'CU17 Importar un modelo desde XMI'; comm = $true
     part = @('@Modelador', ':EnterpriseArchitect', ':ControladorXmi', ':ServicioXmi',
              ':ServicioProyectos', ':ImportadorXmi', ':ServicioOperaciones',
              ':ServicioBloqueo', ':AplicadorComando')
     msgs = @(
       @(1,0,'0: el documento XMI (exportado de EA)'),
       @(0,2,'1: importarArchivo(diagramaId, sesionId, token, archivo)'),
       @(2,3,'2: importar(usuarioId, sesionId, documento, token)'),
       @(3,4,'3: diagramaAccesible(diagramaId, usuarioId)'),
       @(3,5,'4: interpretar(xmi)'),
       @(3,6,'5: registrar(comando, IMPORTACION, token + i)'),
       @(6,7,'6: adquirir(elemento, usuarioId, sesionId)'),
       @(6,8,'7: aplicar(diagrama, comando)'),
       @(2,0,'8: ResumenImportacion(aplicadas, problemas)'))
     commObj = @(':Modelador', ':EnterpriseArchitect', ':ControladorXmi', ':ServicioXmi',
                 ':ServicioProyectos', ':ImportadorXmi', ':ServicioOperaciones',
                 ':ServicioBloqueo', ':AplicadorComando')
     commPos = @(@(1,0), @(0,0), @(2,0), @(2,1), @(1,1), @(2,2), @(1,2), @(0,1), @(0,2))
     commLinks = @(
       @(1,0,'0: el documento XMI'),
       @(0,2,'1: importarArchivo   /   8: ResumenImportacion'),
       @(2,3,'2: importar'),
       @(3,4,'3: diagramaAccesible'),
       @(3,5,'4: interpretar'),
       @(3,6,'5: registrar'),
       @(6,7,'6: adquirir'),
       @(6,8,'7: aplicar'))
  },

  @{ id = 'cu18'; titulo = 'CU18 Modelar sin conexion y sincronizar'; comm = $true
     part = @('@Modelador', ':ClienteMovil (lienzo.dart)', ':Sincronizador',
              ':Almacen', ':Api (movil)', ':ControladorDiagramas',
              ':ServicioOperaciones', ':PostgreSQL')
     msgs = @(
       @(0,1,'1: _ejecutar(Comando.crearClase(...))'),
       @(1,2,'2: ejecutar(comando) en la copia local (no espera)'),
       @(2,3,'3: guardarDiagrama(diagrama) y guardarCola(...)'),
       @(2,4,'4: enviar(...) lanza SinConexion (queda encolada)'),
       @(2,4,'5: _vaciarCola(id) al volver la red (en orden)'),
       @(4,5,'6: POST operaciones (tokenCliente propio)'),
       @(5,6,'6.1: registrar(comando, sesionId, tokenCliente)'),
       @(6,7,'6.2: findByDiagramaIdAndTokenCliente(token)'),
       @(6,2,'7: RECHAZADA_POR_BLOQUEO (intentos, maximo 5)'),
       @(2,4,'8: delta(diagramaId, diagrama.version)'))
     commObj = @(':Modelador', ':ClienteMovil (lienzo.dart)', ':Sincronizador',
                 ':Almacen', ':Api (movil)', ':ControladorDiagramas',
                 ':ServicioOperaciones', ':PostgreSQL')
     commPos = @(@(0,0), @(1,0), @(1,1), @(2,0), @(2,1), @(2,2), @(1,2), @(0,2))
     commLinks = @(
       @(0,1,'1: _ejecutar'),
       @(1,2,'2: ejecutar'),
       @(2,3,'3: guardarDiagrama'),
       @(2,4,'4: enviar   /   5: vaciar   /   8: delta'),
       @(4,5,'6: POST operaciones'),
       @(5,6,'6.1: registrar'),
       @(6,7,'6.2: findByDiagramaIdAndTokenCliente'),
       @(6,2,'7: RECHAZADA_POR_BLOQUEO'))
  },

  @{ id = 'cu19'; titulo = 'CU19 Cargar registros en el backend generado'; comm = $true
     part = @('@OperadorDeDatos', ':PantallaEntidades', ':PantallaRegistros',
              ':Repositorio', ':AlmacenRegistros', ':ApiGenerada', ':BackendGenerado')
     msgs = @(
       @(0,1,'1: elegir el diagrama (una entidad por clase)'),
       @(0,1,'2: guardarDireccionBackend(limpia)'),
       @(0,2,'3: completar el formulario (ubicarDictado)'),
       @(2,3,'4: crear(clase.nombre, valores)'),
       @(3,4,'5: guardarFilas(...) y encolar(OperacionPendiente)'),
       @(3,5,'6: sincronizar() -> crear(clase, datos)'),
       @(5,6,'6.1: POST base/api/ruta (BackendGenerado)'),
       @(5,3,'7: ErrorDelBackendGenerado (definitivo o se conserva)'),
       @(3,5,'8: listar(clase) y guardarFilas(clase, filas)'))
     commObj = @(':OperadorDeDatos', ':PantallaEntidades', ':PantallaRegistros',
                 ':Repositorio', ':AlmacenRegistros', ':ApiGenerada', ':BackendGenerado')
     commPos = @(@(1,0), @(0,0), @(1,1), @(2,1), @(2,0), @(2,2), @(1,2))
     commLinks = @(
       @(0,1,'1: elegir diagrama   /   2: guardarDireccion'),
       @(0,2,'3: completar el formulario'),
       @(2,3,'4: crear'),
       @(3,4,'5: guardarFilas   /   encolar'),
       @(3,5,'6: sincronizar   /   7: error   /   8: listar'),
       @(5,6,'6.1: POST base/api/ruta'))
  }
)

# =====================================================================

foreach ($caso in $casos) {
  "== $($caso.titulo)"
  DiagramaDeSecuencia $caso
  if ($caso.comm) { DiagramaDeComunicacion $caso }
}

$repo.CloseFile()
$repo.Exit()
[void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($repo)
Get-Process EA -ErrorAction SilentlyContinue | Stop-Process -Force
'listo'
