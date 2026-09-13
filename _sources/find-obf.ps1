param([string]$Dir, [string]$Out)
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
$n1 = [System.Text.Encoding]::ASCII.GetBytes('FOR THE DEBUG!')          # SoundEngine
$n2 = [System.Text.Encoding]::ASCII.GetBytes('OpenAL 1.1 not supported') # Library
$n3 = [System.Text.Encoding]::ASCII.GetBytes('block.note_block.harp')    # SoundEvents
$n4 = [System.Text.Encoding]::ASCII.GetBytes('NOTE_VOLUME')              # (unlikely, name is obf) skip
$r1 = $r2 = $r3 = $null
$noteBlock = $null
$n5 = [System.Text.Encoding]::ASCII.GetBytes('note')
$n6 = [System.Text.Encoding]::ASCII.GetBytes('instrument')
$n7 = [System.Text.Encoding]::ASCII.GetBytes('powered')
$cands = @()
$files = Get-ChildItem $Dir -Recurse -Filter *.class
"scanning $($files.Count) classes..."
foreach ($f in $files) {
    $b = [System.IO.File]::ReadAllBytes($f.FullName)
    if (-not $r1 -and ($b.Length -gt 500) -and (Contains $b $n1)) { $r1 = $f.FullName }
    if (-not $r2 -and (Contains $b $n2)) { $r2 = $f.FullName }
    if (-not $r3 -and (Contains $b $n3)) { $r3 = $f.FullName }
    if (-not $noteBlock -and $b.Length -gt 800 -and $b.Length -lt 12000 -and (Contains $b $n5) -and (Contains $b $n6) -and (Contains $b $n7)) {
        $cands += $f.FullName
    }
}
"SoundEngine => $r1"
"Library => $r2"
"SoundEvents => $r3"
"NoteBlock candidates: $($cands.Count)"
$cands | ForEach-Object { $_ }
$out = "SOUNDENGINE=$r1`nLIBRARY=$r2`nSOUNDEVENTS=$r3`nNOTEBLOCK_CANDS=" + ($cands -join ';')
Set-Content -Path $Out -Value $out -Encoding UTF8
