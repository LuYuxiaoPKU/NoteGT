param([string]$Id)
$ErrorActionPreference = 'Stop'
$p = "C:\00_Data\RGM\NoteGT\_sources\vanilla-sounds-$Id.json"
$nums = Get-Content $p | ForEach-Object { [int]$_ }
$bytes = New-Object byte[] $nums.Count
for ($i = 0; $i -lt $nums.Count; $i++) { $bytes[$i] = [byte]$nums[$i] }
$json = [System.Text.Encoding]::UTF8.GetString($bytes)
$dest = "C:\00_Data\RGM\NoteGT\_sources\vanilla-sounds-$Id.raw.json"
[System.IO.File]::WriteAllText($dest, $json, [System.Text.Encoding]::UTF8)
$sj = $json | ConvertFrom-Json
$nb = $sj.PSObject.Properties | Where-Object { $_.Name -like 'block.note_block*' } | Sort-Object Name
$lines = @("=== $Id : $($nb.Count) note_block entries ===")
foreach ($k in $nb) {
    $j = $k.Value | ConvertTo-Json -Compress -Depth 5
    $lines += ('{0,-55} {1}' -f $k.Name, $j)
}
# also count total and check a note file entry
$lines += ''
$lines += "total entries: $($sj.PSObject.Properties.Name.Count)"
[System.IO.File]::WriteAllLines("C:\00_Data\RGM\NoteGT\_sources\note-entries-$Id.txt", $lines, [System.Text.Encoding]::UTF8)
Write-Output $lines
