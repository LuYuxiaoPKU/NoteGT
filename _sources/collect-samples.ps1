param()
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
$base = 'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'

function GetBytes([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    if ($r.Content -is [byte[]]) { return $r.Content }
    return [System.Text.Encoding]::UTF8.GetBytes($r.Content)
}
function GetText([string]$u) {
    return [System.Text.Encoding]::UTF8.GetString((GetBytes $u))
}
function AssetObjects([string]$id) {
    $mb = GetBytes 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
    $man = [System.Text.Encoding]::UTF8.GetString($mb) | ConvertFrom-Json
    $v = $man.versions | Where-Object { $_.id -eq $id } | Select-Object -First 1
    $vb = GetBytes $v.url
    $vj = [System.Text.Encoding]::UTF8.GetString($vb) | ConvertFrom-Json
    $ab = GetBytes $vj.assetIndex.url
    return [System.Text.Encoding]::UTF8.GetString($ab) | ConvertFrom-Json
}

# 1) user bank
$ub = Join-Path $base 'user-bank'
New-Item -ItemType Directory -Force -Path $ub | Out-Null
$src = 'C:\Users\Yuxiao Lu\AppData\Roaming\Minecraft_Note_Block_Studio\data\sounds'
Get-ChildItem $src -Filter *.ogg -File | ForEach-Object { Copy-Item $_.FullName (Join-Path $ub $_.Name) -Force }
"USER: " + (Get-ChildItem $ub -File).Count + " files"

# 2) vanilla 1.21.11 note/
$v1 = Join-Path $base 'vanilla-12111'
New-Item -ItemType Directory -Force -Path $v1 | Out-Null
$ai = AssetObjects '1.21.11'
$names = @('harp2','bd','bassattack','snare','hat','guitar','flute','bell','icechime','xylobone','iron_xylophone','cow_bell','didgeridoo','bit','banjo','pling')
foreach ($n in $names) {
    $k = "minecraft/sounds/note/$n.ogg"
    $o = $ai.objects.$k
    if (-not $o) { "MISSING $k"; continue }
    $u = 'https://resources.download.minecraft.net/' + $o.hash.Substring(0,2) + '/' + $o.hash
    [System.IO.File]::WriteAllBytes((Join-Path $v1 ($n + '.ogg')), (GetBytes $u))
}
"VANILLA-12111: " + (Get-ChildItem $v1 -File).Count + " files"

# 3) vanilla 26.2 trumpets
$v2 = Join-Path $base 'vanilla-262-trumpets'
New-Item -ItemType Directory -Force -Path $v2 | Out-Null
$ai2 = AssetObjects '26.2'
foreach ($n in @('trumpet','trumpet_exposed','trumpet_weathered','trumpet_oxidized')) {
    $k = "minecraft/sounds/note/$n.ogg"
    $o = $ai2.objects.$k
    if (-not $o) { "MISSING $k"; continue }
    $u = 'https://resources.download.minecraft.net/' + $o.hash.Substring(0,2) + '/' + $o.hash
    [System.IO.File]::WriteAllBytes((Join-Path $v2 ($n + '.ogg')), (GetBytes $u))
}
"VANILLA-262: " + (Get-ChildItem $v2 -File).Count + " files"
