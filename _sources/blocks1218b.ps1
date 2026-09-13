param([string]$Jar)
$ErrorActionPreference = 'Stop'
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
$cands = @()
foreach ($e in $z.Entries) {
    if ($e.Name -match '\.class$' -and $e.Length -lt 500000) {
        $s = $e.Open()
        $buf = New-Object byte[] $e.Length
        [void]$s.Read($buf, 0, [int]$e.Length)
        $s.Close()
        if (FindS $buf $nb) { $cands += ($e.FullName + ' [' + $e.Length + ']') }
    }
}
$z.Dispose()
"classes containing oak_planks:"
$cands
