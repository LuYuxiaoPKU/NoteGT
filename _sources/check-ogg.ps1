param([string]$Id)
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetText([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    $b = if ($r.Content -is [byte[]]) { $r.Content } else { [System.Text.Encoding]::UTF8.GetBytes($r.Content) }
    return [System.Text.Encoding]::UTF8.GetString($b)
}
$man = GetText 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' | ConvertFrom-Json
$v = $man.versions | Where-Object { $_.id -eq $Id } | Select-Object -First 1
$vj = GetText $v.url | ConvertFrom-Json
$ai = GetText $vj.assetIndex.url | ConvertFrom-Json
function OggInfo([string]$assetPath) {
    $obj = $ai.objects.$assetPath
    if (-not $obj) { return "$assetPath : NOT IN INDEX" }
    $u = 'https://resources.download.minecraft.net/' + $obj.hash.Substring(0,2) + '/' + $obj.hash
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    $b = if ($r.Content -is [byte[]]) { $r.Content } else { [System.Text.Encoding]::UTF8.GetBytes($r.Content) }
    # find "vorbis" magic (01 76 6F 72 62 69 73)
    $mag = @(0x01,0x76,0x6F,0x72,0x62,0x69,0x73)
    $idx = -1
    for ($i = 0; $i -le $b.Length - $mag.Length; $i++) {
        $ok = $true
        for ($j = 0; $j -lt $mag.Length; $j++) { if ($b[$i+$j] -ne $mag[$j]) { $ok = $false; break } }
        if ($ok) { $idx = $i; break }
    }
    if ($idx -lt 0) { return "$assetPath : no vorbis id header (maybe not vorbis?)" }
    $ver = $b[$idx+7]
    $ch  = $b[$idx+8]
    $rate = [int]$b[$idx+9] -bor ([int]$b[$idx+10] -shl 8) -bor ([int]$b[$idx+11] -shl 16) -bor ([int]$b[$idx+12] -shl 24)
    $bitrate = [int]$b[$idx+13] -bor ([int]$b[$idx+14] -shl 8) -bor ([int]$b[$idx+15] -shl 16) -bor ([int]$b[$idx+16] -shl 24)
    return ("{0} : vorbis ver={1} ch={2} rate={3} bitrate~{4}kbps size={5}B" -f $assetPath, $ver, $ch, $rate, $bitrate, $b.Length)
}
$files = @('minecraft/sounds/note/harp2.ogg','minecraft/sounds/note/bd.ogg','minecraft/sounds/note/bassattack.ogg','minecraft/sounds/note/xylobone.ogg','minecraft/sounds/note/trumpet.ogg')
foreach ($f in $files) { OggInfo $f }
