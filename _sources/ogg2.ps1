$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetBytes([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    if ($r.Content -is [byte[]]) { return $r.Content }
    return [System.Text.Encoding]::UTF8.GetBytes($r.Content)
}
$mb = GetBytes 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$man = [System.Text.Encoding]::UTF8.GetString($mb) | ConvertFrom-Json
$v = $man.versions | Where-Object { $_.id -eq '1.21.11' }
$vb = GetBytes $v.url
$vj = [System.Text.Encoding]::UTF8.GetString($vb) | ConvertFrom-Json
$ab = GetBytes $vj.assetIndex.url
$ai = [System.Text.Encoding]::UTF8.GetString($ab) | ConvertFrom-Json
foreach ($f in @('minecraft/sounds/note/icechime.ogg','minecraft/sounds/note/bassattack.ogg')) {
    $o = $ai.objects.$f
    $u = 'https://resources.download.minecraft.net/' + $o.hash.Substring(0,2) + '/' + $o.hash
    $b = GetBytes $u
    $mag = @(1,118,111,114,98,105,115)
    $idx = -1
    for ($i = 0; $i -le [Math]::Min(64,$b.Length) - $mag.Length; $i++) {
        $ok = $true
        for ($j = 0; $j -lt $mag.Length; $j++) { if ($b[$i+$j] -ne $mag[$j]) { $ok = $false; break } }
        if ($ok) { $idx = $i; break }
    }
    $ch = $b[$idx+11]
    $rate = [int]$b[$idx+12] -bor ([int]$b[$idx+13] -shl 8) -bor ([int]$b[$idx+14] -shl 16) -bor ([int]$b[$idx+15] -shl 24)
    "{0} ch={1} rate={2} size={3}" -f $f, $ch, $rate, $b.Length
}
