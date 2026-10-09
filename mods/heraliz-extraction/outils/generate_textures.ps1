# Génère toutes les textures du mod (pixel art procédural).
# Usage : powershell -ExecutionPolicy Bypass -File mods\heraliz-extraction\outils\generate_textures.ps1
Add-Type -AssemblyName System.Drawing

$assets = Join-Path $PSScriptRoot "..\src\client\resources\assets\heraliz_extraction\textures"
$rand = New-Object System.Random 1337

function Clamp([double]$v, [double]$lo = 0, [double]$hi = 255) { [Math]::Max($lo, [Math]::Min($hi, $v)) }

function New-Tex([int]$w, [int]$h) {
    New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Set-Px($bmp, [int]$x, [int]$y, [double]$r, [double]$g, [double]$b, [double]$a = 255) {
    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb([int](Clamp $a), [int](Clamp $r), [int](Clamp $g), [int](Clamp $b)))
}

function Save-Tex($bmp, [string]$rel) {
    $path = Join-Path $assets $rel
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Remplit un rectangle avec une couleur + bruit
function Fill-Noise($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [double[]]$c, [double]$noise) {
    for ($y = $y0; $y -lt $y1; $y++) {
        for ($x = $x0; $x -lt $x1; $x++) {
            $n = ($rand.NextDouble() - 0.5) * 2 * $noise
            Set-Px $bmp $x $y ($c[0] + $n) ($c[1] + $n) ($c[2] + $n)
        }
    }
}

function Fill-Rect($bmp, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [double[]]$c) {
    for ($y = $y0; $y -lt $y1; $y++) { for ($x = $x0; $x -lt $x1; $x++) { Set-Px $bmp $x $y $c[0] $c[1] $c[2] } }
}

# Taches (rouille, saleté) : n points de couleur aléatoire dans un rectangle
function Add-Specks($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h, [int]$n, [double[]]$c, [double]$var) {
    for ($i = 0; $i -lt $n; $i++) {
        $v = ($rand.NextDouble() - 0.5) * 2 * $var
        Set-Px $bmp ($x0 + $rand.Next(0, $w)) ($y0 + $rand.Next(0, $h)) ($c[0] + $v) ($c[1] + $v * 0.6) ($c[2] + $v * 0.3)
    }
}

# Rayures : petites lignes diagonales claires
function Add-Scratches($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h, [int]$n, [double]$light) {
    for ($i = 0; $i -lt $n; $i++) {
        $sx = $x0 + $rand.Next(0, $w); $sy = $y0 + $rand.Next(0, $h); $len = $rand.Next(2, 6)
        $dir = if ($rand.NextDouble() -lt 0.5) { 1 } else { -1 }
        for ($k = 0; $k -lt $len; $k++) {
            $px = $sx + $k * $dir; $py = $sy + [int]($k / 2)
            if ($px -ge $x0 -and $px -lt $x0 + $w -and $py -lt $y0 + $h) {
                $c = $bmp.GetPixel($px, $py)
                Set-Px $bmp $px $py ($c.R + $light) ($c.G + $light) ($c.B + $light)
            }
        }
    }
}

# Glyphes 3x5 pour les pochoirs
$glyphs = @{
    'E' = @('111', '100', '110', '100', '111'); 'X' = @('101', '101', '010', '101', '101')
    'F' = @('111', '100', '110', '100', '100'); 'I' = @('111', '010', '010', '010', '111')
    'L' = @('100', '100', '100', '100', '111'); 'A' = @('010', '101', '111', '101', '101')
    'R' = @('110', '101', '110', '101', '101'); 'S' = @('011', '100', '010', '001', '110')
    'O' = @('010', '101', '101', '101', '010'); 'K' = @('101', '110', '100', '110', '101')
    'Z' = @('111', '001', '010', '100', '111'); 'H' = @('101', '101', '111', '101', '101')
    'T' = @('111', '010', '010', '010', '010'); 'N' = @('101', '111', '111', '101', '101')
}
function Draw-Text($bmp, [string]$text, [int]$x, [int]$y, [int]$scale, [double[]]$c, [double]$wear) {
    foreach ($ch in $text.ToCharArray()) {
        $g = $glyphs[[string]$ch]
        if ($null -ne $g) {
            for ($gy = 0; $gy -lt 5; $gy++) {
                for ($gx = 0; $gx -lt 3; $gx++) {
                    if ($g[$gy][$gx] -eq '1') {
                        for ($sy = 0; $sy -lt $scale; $sy++) {
                            for ($sx = 0; $sx -lt $scale; $sx++) {
                                if ($rand.NextDouble() -gt $wear) { Set-Px $bmp ($x + $gx * $scale + $sx) ($y + $gy * $scale + $sy) $c[0] $c[1] $c[2] }
                            }
                        }
                    }
                }
            }
        }
        $x += 4 * $scale
    }
}

# ================================================================ terminal (atlas 128x128, tuiles de 16)
$t = New-Tex 128 128
for ($y = 0; $y -lt 128; $y++) { for ($x = 0; $x -lt 128; $x++) { Set-Px $t $x $y 40 40 44 } }
function Tile([int]$col, [int]$row) { return @(($col * 16), ($row * 16)) }

# ---- rangée 0 : plastiques, caoutchouc, clavier
$p = Tile 0 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(64, 65, 70) 6            # PLASTIC
Add-Scratches $t $p[0] $p[1] 16 16 5 22; Add-Specks $t $p[0] $p[1] 16 16 4 @(40, 38, 34) 6
$p = Tile 1 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(38, 38, 42) 4            # PLASTIC_DARK
Add-Scratches $t $p[0] $p[1] 16 16 3 15
$p = Tile 2 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(27, 27, 29) 4            # RUBBER
for ($y = 0; $y -lt 16; $y += 2) { for ($x = ($y / 2) % 2; $x -lt 16; $x += 2) { Set-Px $t ($p[0] + $x) ($p[1] + $y) 34 34 36 } }
Add-Specks $t $p[0] $p[1] 16 16 5 @(70, 62, 50) 10
$p = Tile 3 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(20, 20, 23) 3            # DECK
Add-Specks $t $p[0] $p[1] 16 16 6 @(60, 50, 40) 10
$p = Tile 4 0                                                                                   # KEY_TOP (touche entière)
Fill-Rect $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(46, 46, 50)
Fill-Noise $t ($p[0] + 1) ($p[1] + 1) ($p[0] + 15) ($p[1] + 15) @(84, 84, 90) 4
for ($i = 1; $i -lt 15; $i++) { Set-Px $t ($p[0] + $i) ($p[1] + 1) 108 108 114; Set-Px $t ($p[0] + 1) ($p[1] + $i) 100 100 106 }
for ($i = 2; $i -lt 15; $i++) { Set-Px $t ($p[0] + $i) ($p[1] + 14) 62 62 66; Set-Px $t ($p[0] + 14) ($p[1] + $i) 66 66 70 }
Fill-Rect $t ($p[0] + 6) ($p[1] + 5) ($p[0] + 10) ($p[1] + 6) @(160, 160, 165)
Fill-Rect $t ($p[0] + 7) ($p[1] + 6) ($p[0] + 9) ($p[1] + 10) @(160, 160, 165)
Add-Specks $t ($p[0] + 2) ($p[1] + 2) 12 12 4 @(50, 44, 38) 8
$p = Tile 5 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(42, 42, 46) 3            # KEY_SIDE
$p = Tile 6 0                                                                                   # TRACKPAD
Fill-Rect $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(40, 40, 44)
Fill-Noise $t ($p[0] + 1) ($p[1] + 1) ($p[0] + 15) ($p[1] + 15) @(56, 56, 61) 3
Add-Scratches $t ($p[0] + 1) ($p[1] + 1) 14 14 4 18; Add-Specks $t ($p[0] + 1) ($p[1] + 1) 14 14 5 @(90, 40, 30) 15
$p = Tile 7 0; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(30, 30, 34) 3            # BEZEL

# ---- rangée 1 : caisse, métal, scotch, câbles, fumigène
$p = Tile 0 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(80, 88, 48) 6            # OLIVE
Add-Specks $t $p[0] $p[1] 16 16 12 @(125, 72, 32) 18; Add-Scratches $t $p[0] $p[1] 16 16 4 25
$p = Tile 1 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(62, 68, 37) 5            # OLIVE_DARK
Add-Specks $t $p[0] $p[1] 16 16 8 @(115, 66, 30) 15
$p = Tile 2 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(118, 118, 116) 10        # STEEL
Add-Specks $t $p[0] $p[1] 16 16 14 @(120, 70, 35) 20
$p = Tile 3 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(26, 27, 22) 4            # BOX_INNER
$p = Tile 4 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(168, 168, 162) 7         # TAPE
for ($x = 0; $x -lt 16; $x += 3) { for ($y = 0; $y -lt 16; $y++) { if ($rand.NextDouble() -lt 0.55) { Set-Px $t ($p[0] + $x) ($p[1] + $y) 138 138 133 } } }
$p = Tile 5 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(24, 24, 26) 4            # WIRE_BLACK
$p = Tile 6 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(150, 24, 22) 10          # WIRE_RED
$p = Tile 7 1; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(196, 32, 26) 9           # FLARE_RED
Add-Scratches $t $p[0] $p[1] 16 16 3 30

# ---- rangée 2 : fumigène, voyants, détails
$p = Tile 0 2; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(30, 30, 32) 4            # FLARE_CAP
$p = Tile 1 2                                                                                   # FLARE_TIP
for ($y = 0; $y -lt 16; $y++) { for ($x = 0; $x -lt 16; $x++) {
    $d = [Math]::Sqrt(($x - 7.5) * ($x - 7.5) + ($y - 7.5) * ($y - 7.5)) / 10.6
    Set-Px $t ($p[0] + $x) ($p[1] + $y) 255 (245 - 150 * $d) (190 - 170 * $d) } }
$p = Tile 2 2; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(70, 255, 120) 10         # LED_GREEN
$p = Tile 3 2; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(255, 55, 40) 10          # LED_RED
$p = Tile 4 2; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(64, 65, 70) 5            # VENT
for ($x = 1; $x -lt 16; $x += 2) { for ($y = 3; $y -lt 13; $y++) { Set-Px $t ($p[0] + $x) ($p[1] + $y) 16 16 18 } }
$p = Tile 5 2; Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(64, 65, 70) 5            # PORTS
foreach ($px in @(1, 6, 11)) { Fill-Rect $t ($p[0] + $px) ($p[1] + 5) ($p[0] + $px + 4) ($p[1] + 11) @(110, 110, 115); Fill-Rect $t ($p[0] + $px + 1) ($p[1] + 6) ($p[0] + $px + 3) ($p[1] + 10) @(12, 12, 14) }
$p = Tile 6 2                                                                                   # STICKER danger, déchiré
Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(222, 186, 40) 8
for ($y = 3; $y -lt 13; $y++) { $half = [int](($y - 3) / 2); for ($x = 8 - $half; $x -le 7 + $half; $x++) { Set-Px $t ($p[0] + $x) ($p[1] + $y) 20 20 20 } }
for ($y = 5; $y -lt 10; $y++) { Set-Px $t ($p[0] + 7) ($p[1] + $y) 222 186 40; Set-Px $t ($p[0] + 8) ($p[1] + $y) 222 186 40 }
Set-Px $t ($p[0] + 7) ($p[1] + 11) 222 186 40; Set-Px $t ($p[0] + 8) ($p[1] + 11) 222 186 40
for ($y = 0; $y -lt 5; $y++) { for ($x = 16 - (5 - $y); $x -lt 16; $x++) { Set-Px $t ($p[0] + $x) ($p[1] + $y) 64 65 70 } }
$p = Tile 7 2                                                                                   # FLARE_LABEL
Fill-Noise $t $p[0] $p[1] ($p[0] + 16) ($p[1] + 16) @(228, 225, 214) 6
Fill-Rect $t $p[0] ($p[1] + 1) ($p[0] + 16) ($p[1] + 3) @(230, 110, 20)
Fill-Rect $t $p[0] ($p[1] + 13) ($p[0] + 16) ($p[1] + 15) @(230, 110, 20)
Draw-Text $t "SOS" ($p[0] + 2) ($p[1] + 5) 1 @(25, 25, 25) 0.05

# ---- SCREEN (0,48) 64x44 : écran noir-vert à balayage, fissuré
for ($y = 0; $y -lt 44; $y++) {
    for ($x = 0; $x -lt 64; $x++) {
        $scan = if ($y % 2 -eq 0) { 1.0 } else { 0.78 }
        $vig = 1.0 - 0.6 * ([Math]::Pow(($x - 32) / 32.0, 2) + [Math]::Pow(($y - 22) / 22.0, 2))
        Set-Px $t $x (48 + $y) (5 * $scan) ((16 + 16 * $vig) * $scan) ((10 + 6 * $vig) * $scan)
    }
}
# fissure en étoile depuis le coin haut-droit
$cx = 56.0; $cy = 6.0
foreach ($ang in @(150, 175, 200, 225, 250, 120)) {
    $a = $ang * [Math]::PI / 180.0; $len = 10 + $rand.Next(0, 14)
    $x = $cx; $y = $cy
    for ($k = 0; $k -lt $len; $k++) {
        $x += [Math]::Cos($a) + ($rand.NextDouble() - 0.5) * 0.6; $y += [Math]::Sin($a) * -1 + ($rand.NextDouble() - 0.5) * 0.6
        $ix = [int]$x; $iy = [int]$y
        if ($ix -ge 0 -and $ix -lt 64 -and $iy -ge 0 -and $iy -lt 44) { Set-Px $t $ix (48 + $iy) 150 175 158 }
    }
}
Fill-Rect $t 54 (48 + 4) 59 (48 + 9) @(4, 8, 6)
Fill-Rect $t 50 (48 + 36) 58 (48 + 44) @(2, 4, 3)          # pixels morts
Set-Px $t 9 (48 + 30) 70 255 120; Set-Px $t 40 (48 + 39) 255 60 60

# ---- LID_BACK (64,48) 64x44 : dos de l'écran, rayé, cabossé, logo
Fill-Noise $t 64 48 128 92 @(62, 63, 68) 6
for ($i = 0; $i -lt 6; $i++) {
    $dx = 64 + $rand.Next(4, 60); $dy = 48 + $rand.Next(4, 40)
    for ($y = -2; $y -le 2; $y++) { for ($x = -2; $x -le 2; $x++) { if ($x * $x + $y * $y -le 4) {
        $c = $t.GetPixel($dx + $x, $dy + $y); Set-Px $t ($dx + $x) ($dy + $y) ($c.R - 14) ($c.G - 14) ($c.B - 14) } } }
}
Add-Scratches $t 64 48 64 44 30 25
for ($y = 0; $y -lt 44; $y++) { Set-Px $t 64 (48 + $y) 42 42 46; Set-Px $t 127 (48 + $y) 42 42 46 }
Draw-Text $t "HZ" 89 64 3 @(92, 94, 100) 0.0

# ---- STENCIL (0,96) 48x16 : côté de la caisse avec pochoir "EXFIL"
Fill-Noise $t 0 96 48 112 @(80, 88, 48) 6
Add-Specks $t 0 96 48 16 20 @(125, 72, 32) 18
Draw-Text $t "EXFIL" 4 99 2 @(210, 190, 75) 0.12

Save-Tex $t "entity\extraction_terminal.png"

# ================================================================ mur de la zone (dégradé + hachures)
$bmp = New-Tex 32 32
for ($y = 0; $y -lt 32; $y++) {
    $f = $y / 31.0
    $a = [Math]::Pow($f, 2.2) * 210
    for ($x = 0; $x -lt 32; $x++) {
        $stripe = if ((($x + $y) % 8) -lt 3) { 1.0 } else { 0.55 }
        $v = 255 * $stripe
        $alpha = $a * (0.6 + 0.4 * $stripe)
        if ($y -ge 30) { $v = 255; $alpha = 255 }
        if ($y -eq 0) { $alpha = 0 }
        Set-Px $bmp $x $y $v $v $v $alpha
    }
}
Save-Tex $bmp "entity\extraction_zone.png"

# ================================================================ particules : fumée (8 frames)
for ($i = 0; $i -lt 8; $i++) {
    $bmp = New-Tex 16 16
    $r = 4.2 + $i * 0.5
    $density = 1.0 - $i * 0.09
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $ddx = $x - 7.5; $ddy = $y - 7.5
            $d = [Math]::Sqrt($ddx * $ddx + $ddy * $ddy)
            $lump = 0.72 + 0.28 * [Math]::Sin($x * 1.3 + $i) * [Math]::Cos($y * 1.1 + $i * 0.7)
            $rr = $r * (0.85 + 0.25 * $lump)
            if ($d -lt $rr) {
                $a = [Math]::Pow(1 - $d / $rr, 0.9) * 255 * $density * (0.75 + 0.25 * $rand.NextDouble())
                $v = 200 + 55 * $lump
                Set-Px $bmp $x $y $v $v $v $a
            } else {
                Set-Px $bmp $x $y 0 0 0 0
            }
        }
    }
    Save-Tex $bmp "particle\extraction_smoke_$i.png"
}

# ================================================================ particules : étincelles (4 frames)
for ($i = 0; $i -lt 4; $i++) {
    $bmp = New-Tex 8 8
    $arm = 3.5 - $i * 0.8
    for ($y = 0; $y -lt 8; $y++) {
        for ($x = 0; $x -lt 8; $x++) {
            $ddx = [Math]::Abs($x - 3.5); $ddy = [Math]::Abs($y - 3.5)
            $core = [Math]::Sqrt($ddx * $ddx + $ddy * $ddy)
            $a = 0
            if ($core -lt 1.6) { $a = 255 }
            elseif (($ddx -lt 0.6 -and $ddy -lt $arm) -or ($ddy -lt 0.6 -and $ddx -lt $arm)) { $a = 255 * (1 - [Math]::Max($ddx, $ddy) / ($arm + 0.5)) }
            elseif ($core -lt 2.6 - $i * 0.3) { $a = 90 }
            Set-Px $bmp $x $y 255 255 255 $a
        }
    }
    Save-Tex $bmp "particle\extraction_spark_$i.png"
}

# ================================================================ particules : marqueur de zone (losange)
$bmp = New-Tex 8 8
for ($y = 0; $y -lt 8; $y++) {
    for ($x = 0; $x -lt 8; $x++) {
        $m = [Math]::Abs($x - 3.5) + [Math]::Abs($y - 3.5)
        if ($m -le 3.5) {
            $a = 255 * [Math]::Pow(1 - $m / 4.0, 0.6)
            Set-Px $bmp $x $y 255 255 255 $a
        } else {
            Set-Px $bmp $x $y 0 0 0 0
        }
    }
}
Save-Tex $bmp "particle\extraction_ring.png"

Write-Host "Textures generees dans $assets"
