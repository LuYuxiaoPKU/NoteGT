$ErrorActionPreference = 'Stop'
$p = 'C:\00_Data\RGM\NoteGT\_sources\dec262c\net\minecraft\world\level\block\Blocks.java'
$lines = Get-Content $p
$results = @()
foreach ($line in $lines) {
    if ($line -match 'Blocks\.register\(BlockItemIds\.([A-Z0-9_]+),') {
        $name = $Matches[1]
        $inst = '(default=harp)'
        if ($line -match 'instrument\(NoteBlockInstrument\.([A-Z0-9_]+)\)') {
            $inst = $Matches[1]
        }
        $results += ($inst + "`t" + $name)
    }
}
$results | Sort-Object | Set-Content 'C:\00_Data\RGM\NoteGT\_sources\instrument-mapping-262.txt'
"total: $($results.Count)"
$results | Group-Object { ($_ -split "`t")[0] } | Sort-Object Name | ForEach-Object {
    $blocks = ($_.Group | ForEach-Object { ($_ -split "`t")[1] }) -join ', '
    if ($blocks.Length -gt 300) { $blocks = $blocks.Substring(0,300) + ' ...[+' + ($_.Count) + ' total]' }
    "{0,-20} {1,4}  {2}" -f $_.Name, $_.Count, $blocks
}
