param([string]$Dir)
$ErrorActionPreference = 'Continue'
function Contains([byte[]]$haystack, [byte[]]$needle) {
    for ($i = 0; $i -le $haystack.Length - $needle.Length; $i++) {
        $ok = $true
        for ($j = 0; $j -lt $needle.Length; $j++) {
            if ($haystack[$i + $j] -ne $needle[$j]) { $ok = $false; break }
        }
        if ($ok) { return $true }
    }
    return $false
}
$files = Get-ChildItem $Dir -Recurse -Filter *.class
$harp = [System.Text.Encoding]::ASCII.GetBytes('harp')
$didg = [System.Text.Encoding]::ASCII.GetBytes('didgeridoo')
$sj   = [System.Text.Encoding]::ASCII.GetBytes('sounds.json')
$ie   = [System.Text.Encoding]::ASCII.GetBytes('intentionally_empty')
$lin  = [System.Text.Encoding]::ASCII.GetBytes('LINEAR')
$none = [System.Text.Encoding]::ASCII.GetBytes('NONE')
$nbi = $sm = $att = $null
$bsp = $null
foreach ($f in $files) {
    $b = [System.IO.File]::ReadAllBytes($f.FullName)
    if (-not $nbi -and $b.Length -lt 8000 -and $b.Length -gt 300 -and (Contains $b $harp) -and (Contains $b $didg)) { $nbi = $f.FullName }
    if (-not $sm -and (Contains $b $sj) -and (Contains $b $ie)) { $sm = $f.FullName }
    if (-not $att -and $b.Length -lt 700 -and (Contains $b $lin) -and (Contains $b $none)) { $att = $f.FullName }
}
"NoteBlockInstrument => $nbi"
"SoundManager => $sm"
"Attenuation enum => $att"
