param([string]$Jar)
$ErrorActionPreference = 'Stop'
$tmp = 'C:\00_Data\RGM\NoteGT\_sources\j1218'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($Jar)
$nb = [System.Text.Encoding]::ASCII.GetBytes('oak_planks')
function FindS([byte[]]$b, [byte[]]$needle) {
    for ($i = 0; $i -le $b.Length - $needle.Length; $i++) {
        $ok = $true
        for ($j = 0; $j -lt $needle.Length; $j++) { if ($b[$i + $j] -ne $needle[$j]) { $ok = $false; break } }
        if ($ok) { return $true }
    }
    return $false
}
$entry = $z.GetEntry('net/minecraft/world/level/levelgen/feature/structure/Structure.class') | Out-Null
# find class(es) containing oak_planks among net/minecraft/world/level/block/*.class
$cands = @()
foreach ($e in $z.Entries) {
    if ($e.Name -match '\.class$' -and $e.FullName -match '^net/minecraft/world/level/block/') {
        $s = $e.Open()
        $ms = [Math]::Min([int]$e.Length, 400000)
        $buf = New-Object byte[] $ms
        [void]$s.Read($buf, 0, $ms)
        $s.Close()
        if (FindS $buf $nb) { $cands += $e.FullName }
    }
}
$z.Dispose()
"candidate classes:"
$cands
