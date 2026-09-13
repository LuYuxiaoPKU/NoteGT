param([string]$Id, [string]$JarOut)
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetText([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    $b = if ($r.Content -is [byte[]]) { $r.Content } else { [System.Text.Encoding]::UTF8.GetBytes($r.Content) }
    return [System.Text.Encoding]::UTF8.GetString($b)
}
$man = GetText 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' | ConvertFrom-Json
$v = $man.versions | Where-Object { $_.id -eq $Id } | Select-Object -First 1
if (-not $v) { throw "$Id not in manifest" }
$vj = GetText $v.url | ConvertFrom-Json
$url = $vj.downloads.client.url
"client url: $url"
$r = Invoke-WebRequest -Uri $url -Headers $hdr -UseBasicParsing
$b = if ($r.Content -is [byte[]]) { $r.Content } else { [System.Text.Encoding]::UTF8.GetBytes($r.Content) }
[System.IO.File]::WriteAllBytes($JarOut, $b)
"saved $JarOut : $($b.Length) bytes"
