$lines = [System.IO.File]::ReadAllLines('C:\00_Data\RGM\NoteGT\_sources\noteblock-wiki.txt')
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^\s*\|\{\{sound\|([^|]+)\|') {
        $inst = $Matches[1]
        # find next range line
        for ($j = $i + 1; $j -le [Math]::Min($i + 3, $lines.Length - 1); $j++) {
            $r = $lines[$j]
            if ($r -match 'style="text-align:center"\s*\|\s*(.+?)\s*$') {
                $range = $Matches[1].Replace('sub>','').Replace('</sub>','')
                "{0,-22} {1}" -f $inst, $range
                break
            }
        }
    }
}
