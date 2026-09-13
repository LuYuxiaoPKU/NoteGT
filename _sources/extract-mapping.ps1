$ErrorActionPreference = 'Stop'
$p = 'C:\00_Data\RGM\NoteGT\_sources\dec12111b\dzs.java'
$map = @{ 'a'='harp'; 'b'='basedrum'; 'c'='snare'; 'd'='hat'; 'e'='bass'; 'f'='flute'; 'g'='bell'; 'h'='guitar'; 'i'='chime'; 'j'='xylophone'; 'k'='iron_xylophone'; 'l'='cow_bell'; 'm'='didgeridoo'; 'n'='bit'; 'o'='banjo'; 'p'='pling' }
$lines = Get-Content $p
$results = @()
foreach ($line in $lines) {
    if ($line -match 'dzs\.a\("([a-z0-9_]+)"') {
        $name = $Matches[1]
        $inst = '(default=harp)'
        if ($line -match 'epi\.([a-p])\)') {
            $c = $Matches[1]
            if ($line -match '\)\.a\(epi\.' -or $line -match '\.a\(epi\.' ) {
                $inst = $map[$c]
            }
        }
        $results += ($inst + "`t" + $name)
    }
}
$results | Sort-Object | Set-Content 'C:\00_Data\RGM\NoteGT\_sources\instrument-mapping-12111.txt'
"total: $($results.Count)"
"--- by instrument:"
$results | Group-Object { ($_ -split "`t")[0] } | Sort-Object Name | ForEach-Object { "{0,-14} {1,4}  {2}" -f $_.Name, $_.Count, (($_.Group | ForEach-Object { ($_ -split "`t")[1] }) -join ', ') }
