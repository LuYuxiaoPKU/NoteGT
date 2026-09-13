param([string]$Id, [string]$OutDir, [string]$BankDir)
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetBytes([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    if ($r.Content -is [byte[]]) { return $r.Content }
    return [System.Text.Encoding]::UTF8.GetBytes($r.Content)
}
function GetJson([string]$u) {
    $b = GetBytes $u
    return ([System.Text.Encoding]::UTF8.GetString($b) | ConvertFrom-Json)
}
$man = GetJson 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$v = $man.versions | Where-Object { $_.id -eq $Id } | Select-Object -First 1
if (-not $v) { throw "$Id not in manifest" }
$vj = GetJson $v.url
$ai = GetJson $vj.assetIndex.url
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$lines = @("=== $Id note files: official hash vs vbank ===")
foreach ($k in ($ai.objects.PSObject.Properties | Where-Object { $_.Name -like 'minecraft/sounds/note/*.ogg' } | Sort-Object Name)) {
    $rel = $k.Name.Replace('minecraft/', '')          # sounds/block/note/X.ogg
    $fn  = Split-Path $k.Name -Leaf                     # X.ogg
    $h   = $k.Value.hash                               # sha1 (40)
    $url = 'https://resources.download.minecraft.net/' + $h.Substring(0,2) + '/' + $h
    $out = Join-Path $OutDir $fn
    if (-not (Test-Path $out)) {
        $bytes = GetBytes $url
        [System.IO.File]::WriteAllBytes($out, $bytes)
    }
    $actual = (Get-FileHash $out -Algorithm SHA1).Hash
    $ok = if ($actual -eq $h) { 'CDN-OK' } else { "CDN-MISMATCH($actual)" }
    $vmark = '--'
    if ($BankDir) {
        $bf = Join-Path $BankDir $fn
        if (Test-Path $bf) {
            $bv = (Get-FileHash $bf -Algorithm SHA1).Hash
            $vmark = if ($bv -eq $h) { 'VBANK=SAME-AS-OFFICIAL' } else { "VBANK-DIFF($($bv.Substring(0,10)))" }
        }
    }
    $lines += ('{0,-24} official={1}  {2}  {3}' -f $fn, $h.Substring(0,10), $ok, $vmark)
}
$lines | ForEach-Object { Write-Output $_ }
[System.IO.File]::WriteAllLines((Join-Path $OutDir 'hash-report.txt'), $lines, [System.Text.Encoding]::UTF8)
