# Comprueba que los dos diagramas de cada caso de uso cuenten LA MISMA
# interaccion. Secuencia y comunicacion se escriben por separado en
# crear-diagramas-interaccion.ps1 -una lista de mensajes en orden y una lista
# de enlaces con los mensajes agrupados- y nada impide que se separen: en CU12
# el mensaje 6 estaba puesto sobre el enlace equivocado, y en el dibujo no se
# nota, porque un numero de mas en un rotulo se lee igual de bien.
#
# Para cada caso comprueba:
#   1. que los numeros de mensaje de la secuencia y los de los rotulos de
#      comunicacion sean exactamente los mismos, sin faltar ni sobrar;
#   2. que cada numero viaje entre el MISMO PAR de participantes en los dos;
#   3. que los dos participantes de cada mensaje esten en el diagrama;
#   4. que todo participante reciba o mande al menos un mensaje, porque una
#      linea de vida suelta es un participante que sobra o un mensaje que falta;
#   5. que ningun rotulo de secuencia termine sin «)», que es lo que hace que
#      EA le agregue «()».
#
# Uso:  powershell -ExecutionPolicy Bypass -File herramientas/verificar-interacciones.ps1

$ErrorActionPreference = 'Stop'
$guion = Join-Path $PSScriptRoot 'crear-diagramas-interaccion.ps1'
$texto = Get-Content $guion -Raw
$desde = $texto.IndexOf('$casos = @(')
$hasta = $texto.IndexOf('# =====================================================================', $desde)
Invoke-Expression $texto.Substring($desde, $hasta - $desde)

function Numeros($rotulo) {
  # Los numeros de mensaje son los que abren un tramo del rotulo: «4: ...» o
  # «6.1: ...», al principio o despues de una barra.
  $r = @()
  foreach ($m in [regex]::Matches($rotulo, '(?:^|/)\s*(\d+(?:\.\d+)?)\s*:')) {
    $r += $m.Groups[1].Value
  }
  return $r
}

$fallos = 0
function Mal($caso, $texto) {
  $script:fallos++
  "  MAL  $($caso.id): $texto"
}

foreach ($caso in $casos) {
  $n = $caso.part.Count

  # 5. rotulos de secuencia que no cierran con parentesis
  foreach ($m in $caso.msgs) {
    if (-not $m[2].EndsWith(')')) { Mal $caso "el rotulo «$($m[2])» no termina en «)»: EA le va a agregar «()»" }
    if ($m[0] -ge $n -or $m[1] -ge $n) { Mal $caso "el mensaje «$($m[2])» apunta a un participante que no existe" }
  }

  # 4. participantes sueltos en la secuencia
  $tocados = @{}
  foreach ($m in $caso.msgs) { $tocados[[int]$m[0]] = $true; $tocados[[int]$m[1]] = $true }
  for ($i = 0; $i -lt $n; $i++) {
    if (-not $tocados.ContainsKey($i)) { Mal $caso "el participante «$($caso.part[$i])» no manda ni recibe ningun mensaje" }
  }

  if (-not $caso.comm) { continue }

  if ($caso.commObj.Count -ne $n) { Mal $caso "la secuencia tiene $n participantes y la comunicacion $($caso.commObj.Count)" }
  if ($caso.commPos.Count -ne $caso.commObj.Count) { Mal $caso 'faltan o sobran posiciones en la rejilla' }

  # dos objetos no pueden compartir casilla
  $casillas = @{}
  for ($i = 0; $i -lt $caso.commPos.Count; $i++) {
    $c = "$($caso.commPos[$i][0]),$($caso.commPos[$i][1])"
    if ($casillas.ContainsKey($c)) { Mal $caso "«$($caso.commObj[$i])» y «$($casillas[$c])» caen en la misma casilla $c" }
    $casillas[$c] = $caso.commObj[$i]
  }

  # los dos extremos de un enlace tienen que quedar en casillas VECINAS, o el
  # enlace pasa por encima de otra caja
  foreach ($e in $caso.commLinks) {
    $a = $caso.commPos[$e[0]]; $b = $caso.commPos[$e[1]]
    $dx = [math]::Abs($a[0] - $b[0]); $dy = [math]::Abs($a[1] - $b[1])
    if ($dx -gt 1 -or $dy -gt 1) {
      Mal $caso "el enlace «$($e[2])» une casillas que no son vecinas ($($a[0]),$($a[1])) y ($($b[0]),$($b[1]))"
    }
  }

  # 1 y 2. los mismos numeros, entre los mismos participantes
  $enSecuencia = @{}
  foreach ($m in $caso.msgs) {
    # @(...) y [string] a proposito: con un solo numero, PowerShell desarma el
    # arreglo y «[0]» sobre una cadena devuelve un CARACTER, que no es la misma
    # clave de tabla que la cadena que se guarda del otro lado.
    $num = [string](@(Numeros $m[2])[0])
    if (-not $num) { Mal $caso "el rotulo «$($m[2])» no empieza con un numero de mensaje" ; continue }
    if ($enSecuencia.ContainsKey($num)) { Mal $caso "el numero $num esta dos veces en la secuencia" }
    $enSecuencia[$num] = @([int]$m[0], [int]$m[1])
  }
  $enComunicacion = @{}
  foreach ($e in $caso.commLinks) {
    foreach ($num in @(Numeros $e[2]) | ForEach-Object { [string]$_ }) {
      if ($enComunicacion.ContainsKey($num)) { Mal $caso "el numero $num esta en dos enlaces de comunicacion" }
      $enComunicacion[$num] = @([int]$e[0], [int]$e[1])
    }
  }
  foreach ($num in @($enSecuencia.Keys)) {
    if (-not $enComunicacion.ContainsKey($num)) { Mal $caso "el mensaje $num esta en la secuencia y no en la comunicacion"; continue }
    $s = $enSecuencia[$num] | Sort-Object
    $c = $enComunicacion[$num] | Sort-Object
    if (($s -join '-') -ne ($c -join '-')) {
      Mal $caso ("el mensaje $num va de «$($caso.part[$enSecuencia[$num][0]])» a «$($caso.part[$enSecuencia[$num][1]])» en la secuencia, " +
                 "pero en la comunicacion esta sobre el enlace «$($caso.commObj[$enComunicacion[$num][0]])»-«$($caso.commObj[$enComunicacion[$num][1]])»")
    }
  }
  foreach ($num in @($enComunicacion.Keys)) {
    if (-not $enSecuencia.ContainsKey($num)) { Mal $caso "el mensaje $num esta en la comunicacion y no en la secuencia" }
  }
}

"casos revisados: $($casos.Count)"
if ($fallos -eq 0) { 'todo coincide' } else { throw "$fallos discrepancias" }
