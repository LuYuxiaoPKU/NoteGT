param([string]$Id)
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetText([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    $b = if ($r.Content -is [byte[]]) { $r.Content } else { [System.Text.Encoding]::UTF8.GetBytes($r.Content) }
    return [System.Text.Encoding]::UTF8.GetString($b)
}
function GetBytes([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    if ($r.Content -is [byte[]]) { return $r.Content }
    return [System.Text.Encoding]::UTF8.GetBytes($r.Content)
}
$man = GetText 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' | ConvertFrom-Json
$v = $man.versions | Where-Object { $_.id -eq $Id } | Select-Object -First 1
$vj = GetText $v.url | ConvertFrom-Json
$ai = GetText $vj.assetIndex.url | ConvertFrom-Json
# locate ID header: 01 76 6f 72 62 69 73
function Info([byte[]]$b, [string]$tag) {
    $mag = @(1,118,111,114,98,105,115)
    $idx = -1
    for ($i = 0; $i -le [Math]::Min(64, $b.Length) - $mag.Length; $i++) {
        $ok = $true
        for ($j = 0; $j -lt $mag.Length; $j++) { if ($b[$i+$j] -ne $mag[$j]) { $ok = $false; break } }
        if ($ok) { $idx = $i; break }
    }
    if ($idx -lt 0) { "$tag : no vorbis id header in first 64B"; return }
    $ch = $b[$idx+11]
    $rate = [int]$b[$idx+12] -bor ([int]$b[$idx+13] -shl 8) -bor ([int]$b[$idx+14] -shl 16) -bor ([int]$b[$idx+15] -shl 24)
    $d = ($b.Length / [Math]::Max(1,$rate))
    "{0,-40} ch={1} rate={2}  ~{3:N1}s @rate (raw {4}B)" -f $tag, $ch, $rate, ($b.Length / $rate / 2), $b.Length
}
$files = @('minecraft/sounds/note/harp2.ogg','minecraft/sounds/note/bd.ogg','minecraft/sounds/note/bassattack.ogg','minecraft/sounds/note/snare.ogg','minecraft/sounds/note/hat.ogg','minecraft/sounds/note/guitar.ogg','minecraft/sounds/note/flute.ogg','minecraft/sounds/note/bell.ogg','minecraft/sounds/note/chime.ogg','minecraft/sounds/note/xylobone.ogg','minecraft/sounds/note/iron_xylophone.ogg','minecraft/sounds/note/cow_bell.ogg','minecraft/sounds/note/didgeridoo.ogg','minecraft/sounds/note/bit.ogg','minecraft/sounds/note/banjo.ogg','minecraft/sounds/note/pling.ogg','minecraft/sounds/note/trumpet.ogg')
foreach ($f in $files) {
    $obj = $ai.objects.$f
    if (-not $obj) { "{0,-40} NOT IN {1}" -f $f, $Id; continue }
    $u = 'https://resources.download.minecraft.net/' + $obj.hash.Substring(0,2) + '/' + $obj.hash
    Info (GetBytes $u) $f
}
