param([string]$Id, [string]$Asset)
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
$obj = $ai.objects.$Asset
$u = 'https://resources.download.minecraft.net/' + $obj.hash.Substring(0,2) + '/' + $obj.hash
$b = GetBytes $u
$hex = ($b[0..55] | ForEach-Object { $_.ToString('x2') }) -join ' '
$hex
"size: $($b.Length)"
