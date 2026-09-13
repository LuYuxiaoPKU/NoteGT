param([string]$Id)
$ErrorActionPreference = 'Stop'
$hdr = @{ UserAgent = 'NoteGT-Research/1.0' }
function GetBytes([string]$u) {
    $r = Invoke-WebRequest -Uri $u -Headers $hdr -UseBasicParsing
    if ($r.Content -is [byte[]]) { return $r.Content }
    return [System.Text.Encoding]::UTF8.GetBytes($r.Content)
}
function GetJson([string]$u) {
    $b = GetBytes $u
    $s = [System.Text.Encoding]::UTF8.GetString($b)
    return ($s | ConvertFrom-Json)
}
$man = GetJson 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
$v = $man.versions | Where-Object { $_.id -eq $Id } | Select-Object -First 1
if (-not $v) { throw "$Id not in manifest" }
$vj = GetJson $v.url
$ai = GetJson $vj.assetIndex.url
$h = $ai.objects.'minecraft/sounds.json'.hash
$u = 'https://resources.download.minecraft.net/' + $h.Substring(0,2) + '/' + $h
$sj = (GetJson $u)
$dest = "C:\00_Data\RGM\NoteGT\_sources\vanilla-sounds-$Id.raw.json"
[System.IO.File]::WriteAllText($dest, ([System.Text.Encoding]::UTF8.GetString((GetBytes $u))), [System.Text.Encoding]::UTF8)
$nb = $sj.PSObject.Properties | Where-Object { $_.Name -like 'block.note_block*' } | Sort-Object Name
$lines = @("=== $Id : $($nb.Count) note_block entries (raw) ===")
foreach ($k in $nb) {
    $j = $k.Value | ConvertTo-Json -Compress -Depth 5
    $lines += ('{0,-55} {1}' -f $k.Name, $j)
}
$lines += ''
$lines += "total entries: $($sj.PSObject.Properties.Name.Count)"
[System.IO.File]::WriteAllLines("C:\00_Data\RGM\NoteGT\_sources\note-entries-$Id.txt", $lines, [System.Text.Encoding]::UTF8)
Write-Output $lines
