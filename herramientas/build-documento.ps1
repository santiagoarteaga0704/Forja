<#
    Genera el documento entregable de FORJA (Parcial 1).

      1. Verifica que esten las imagenes que el documento referencia
      2. Convierte docs\documento.md a un HTML con estilos de Word
      3. Antepone la portada, que trae la marca del indice
      4. Abre el HTML en Word y lo guarda como .docx y como .pdf

    Es un proceso aparte del de herramientas\crear-pdf.mjs, que sigue siendo
    valido: aquel produce un PDF de lectura rapida con marked + playwright; este
    produce el entregable con formato academico -caratula, indice generado por
    Word y tablas con el aspecto de Word-, cosas que no salen de una hoja de
    estilos sino del propio Word armando el documento.

    IMPORTANTE: ejecutar con pwsh (PowerShell 7), NO con powershell (Windows
    PowerShell 5.1). Bajo 5.1 la automatizacion de Word se cuelga de forma
    indefinida en Documents.Open, sin devolver error ni mensaje.

    Uso:   pwsh -NoProfile -ExecutionPolicy Bypass -File herramientas\build-documento.ps1
           ... -SoloImagenes       solo verifica las imagenes
           ... -SinPdf             omite la exportacion a PDF
#>

param(
    [switch]$SoloImagenes,
    [switch]$SinPdf
)

$ErrorActionPreference = 'Stop'

$raiz    = Split-Path -Parent $PSScriptRoot
$docs    = Join-Path $raiz 'docs'
$diagram = Join-Path $docs 'diagramas'

# El cuerpo es UN SOLO archivo y se nombra explicitamente. En docs\ conviven
# guia-de-pruebas.md, despliegue.md y como-probarlo-en-el-telefono.md, que son
# documentacion interna y NO forman parte del entregable: barrer la carpeta con
# un filtro *.md los meteria adentro.
$cuerpoMd = Join-Path $docs 'documento.md'
if (-not (Test-Path $cuerpoMd)) { throw "No se encontro $cuerpoMd" }

# --- 1. Imagenes -----------------------------------------------------------
Write-Host "`n[1/4] Verificando las imagenes del documento..." -ForegroundColor Cyan

# Las ocho figuras salen de Enterprise Architect con herramientas\crear-diagramas.ps1
# y los dos codigos QR de herramientas\crear-qr.mjs. Este guion NO las genera:
# solo comprueba que esten, para no tapar con otra herramienta las que ya hay.
# El Markdown las referencia como diagramas\xxx.png, o sea relativas a docs\.
$texto = Get-Content $cuerpoMd -Raw -Encoding UTF8
$referidas = @()
$faltantes = @()
foreach ($m in [regex]::Matches($texto, '!\[[^\]]*\]\(([^)]+\.png)\)')) {
    $relativa = $m.Groups[1].Value
    $referidas += $relativa
    if (-not (Test-Path (Join-Path $docs $relativa))) { $faltantes += $relativa }
}

if ($faltantes.Count -gt 0) {
    $faltantes | Sort-Object -Unique | ForEach-Object { Write-Host "      FALTA: $_" -ForegroundColor Red }
    throw "Faltan imagenes. Genera los diagramas con herramientas\crear-diagramas.ps1 y los QR con herramientas\crear-qr.mjs"
}
$enCarpeta = (Get-ChildItem -Path $diagram -Filter *.png -ErrorAction SilentlyContinue).Count
Write-Host "      $($referidas.Count) imagenes referidas, todas presentes ($enCarpeta png en docs\diagramas)" -ForegroundColor Green

if ($SoloImagenes) { Write-Host "`nListo (solo imagenes).`n"; exit 0 }

# --- 2. Markdown -> HTML ---------------------------------------------------
Write-Host "`n[2/4] Convirtiendo el documento a HTML..." -ForegroundColor Cyan

# Todo el armado se hace en una carpeta local temporal. FORJA no esta en
# OneDrive, pero Word se cuelga indefinidamente al abrir archivos sincronizados
# -la sincronizacion dispara un dialogo modal invisible cuando Word corre
# oculto- y el rodeo es barato: protege tambien de un repositorio que algun dia
# se mueva, o de un antivirus que se entretenga con la carpeta del proyecto.
$temporal = Join-Path $env:TEMP ("forja-doc-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $temporal | Out-Null

$cuerpoHtml = Join-Path $temporal '_cuerpo.html'
& node (Join-Path $PSScriptRoot 'md-a-html.mjs') $cuerpoHtml $cuerpoMd
if ($LASTEXITCODE -ne 0) { throw "La conversion Markdown fallo" }

# --- 3. Portada + cuerpo ---------------------------------------------------
Write-Host "`n[3/4] Ensamblando el documento..." -ForegroundColor Cyan

$portada = Join-Path $docs '00-portada.html'
$html    = Get-Content $cuerpoHtml -Raw -Encoding UTF8

if (Test-Path $portada) {
    $fragmentoPortada = Get-Content $portada -Raw -Encoding UTF8
    # se inserta inmediatamente despues de <body>
    $html = $html -replace '(?s)(<body>)', "`$1`n$fragmentoPortada"
} else {
    Write-Warning "No se encontro ${portada}: el documento sale sin caratula ni indice."
}

$htmlLocal = Join-Path $temporal 'documento.html'
[System.IO.File]::WriteAllText($htmlLocal, $html, (New-Object System.Text.UTF8Encoding $true))
Write-Host "      $htmlLocal" -ForegroundColor Green

# --- 4. HTML -> DOCX / PDF -------------------------------------------------
Write-Host "`n[4/4] Generando Word y PDF..." -ForegroundColor Cyan

$docxLocal = Join-Path $temporal 'documento.docx'
$pdfLocal  = Join-Path $temporal 'documento.pdf'

# La salida final va a docs\, al lado del documento.
$docx = Join-Path $docs 'FORJA-Parcial1.docx'
$pdf  = Join-Path $docs 'FORJA-Parcial1.pdf'

$word = $null
try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0

    # Open(ruta, ConfirmConversions, ReadOnly, AddToRecentFiles)
    $doc = $word.Documents.Open($htmlLocal, $false, $true, $false)

    # Word abre el HTML en vista Web, donde el documento no tiene paginas y la
    # tabla de contenido saldria con todos los numeros en 1.
    try { $doc.ActiveWindow.View.Type = 3 } catch { }   # 3 = wdPrintView

    # Word importa las tablas del HTML con autoajuste al contenido: una celda
    # con texto largo sin espacios -- los nombres de columna y los tipos de dato
    # del diseno de datos -- ensancha la tabla mas alla de la hoja. Al paginar en
    # pantalla Word la reacomoda y parece entrar, pero al exportar respeta el
    # ancho guardado y el PDF sale con la ultima columna cortada.
    # Se apaga el autoajuste y se reparte el ancho util en la misma proporcion
    # que Word habia calculado, para no perder el equilibrio entre columnas.
    # Se pagina primero: recien ahi las anchuras que informa Word son las que
    # dibuja. Sin esto informa las del HTML, que son otras, y la comparacion no
    # sirve para decidir nada. Por eso no se compara: se fija el ancho de TODAS
    # las tablas, que ademas ya venian todas al 100% por hoja de estilos.
    $doc.Repaginate()
    $ps = $doc.PageSetup
    $util = $ps.PageWidth - $ps.LeftMargin - $ps.RightMargin
    $encajadas = 0
    for ($i = 1; $i -le $doc.Tables.Count; $i++) {
        $t = $doc.Tables.Item($i)
        try {
            $anchos = @($t.Rows.Item(1).Cells | ForEach-Object { $_.Width })
            $total = ($anchos | Measure-Object -Sum).Sum
            if ($total -le 0) { continue }

            $t.AllowAutoFit = $false
            $t.PreferredWidthType = 1          # 1 = wdPreferredWidthPoints
            $t.PreferredWidth = $util
            for ($c = 1; $c -le $anchos.Count; $c++) {
                $t.Columns.Item($c).Width = $util * $anchos[$c - 1] / $total
            }
            $encajadas++
        } catch {
            # Tablas con celdas combinadas: no se puede tocar columna por
            # columna, pero repartir en partes iguales igual la mete en la hoja.
            try { $t.AllowAutoFit = $false; $t.Columns.DistributeWidth(); $encajadas++ }
            catch { Write-Warning "No se pudo encajar la tabla $i en la pagina." }
        }
    }
    if ($encajadas -gt 0) {
        Write-Host "      $encajadas tablas encajadas en el ancho de la pagina" -ForegroundColor Green
    }

    # El indice lo arma Word con los estilos Titulo 1 a 4 que aplico el
    # conversor, sobre la marca que deja la portada. Antes era un paso a mano y
    # el entregable salia con la instruccion impresa en lugar del indice.
    $marca = $doc.Content
    $marca.Find.Text = '[[INDICE]]'
    if ($marca.Find.Execute()) {
        # Add(Range, UseHeadingStyles, UpperHeadingLevel, LowerHeadingLevel)
        $toc = $doc.TablesOfContents.Add($marca, $true, 1, 4)
        $doc.Repaginate()
        $toc.Update()
        Write-Host "      indice generado con $($toc.Range.Paragraphs.Count) entradas" -ForegroundColor Green
    } else {
        Write-Warning "No se encontro la marca [[INDICE]]: el documento sale sin indice."
    }

    # Word inserta las imagenes del HTML como VINCULOS a la ruta absoluta del
    # PNG, no embebidas: el .docx pesa la mitad y, abierto en otra maquina, las
    # figuras salen como cuadros vacios. Se incrustan antes de guardar. Se
    # recorre al reves porque BreakLink reindexa la coleccion.
    $incrustadas = 0
    for ($i = $doc.InlineShapes.Count; $i -ge 1; $i--) {
        $sh = $doc.InlineShapes.Item($i)
        # wdInlineShapeLinkedPicture = 4
        if ($sh.Type -eq 4) {
            $sh.LinkFormat.SavePictureWithDocument = $true
            $sh.LinkFormat.BreakLink()
            $incrustadas++
        }
    }
    Write-Host "      $incrustadas imagenes incrustadas en el documento" -ForegroundColor Green

    $paginas = $doc.ComputeStatistics(2)   # 2 = wdStatisticPages
    Write-Host "      $paginas paginas" -ForegroundColor Green

    # wdFormatDocumentDefault = 16
    $doc.SaveAs2($docxLocal, 16)

    if (-not $SinPdf) {
        # wdFormatPDF = 17
        $doc.SaveAs2($pdfLocal, 17)
    }

    $doc.Close(0)
}
finally {
    if ($word) {
        $word.Quit()
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    }
}

Copy-Item $docxLocal $docx -Force
Write-Host "      $docx" -ForegroundColor Green
if (-not $SinPdf -and (Test-Path $pdfLocal)) {
    Copy-Item $pdfLocal $pdf -Force
    Write-Host "      $pdf" -ForegroundColor Green
}
Remove-Item $temporal -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "`nDocumento generado en: $docs`n" -ForegroundColor Cyan
