# -*- coding: utf-8 -*-
"""边界外推：bell/flute/guitar 粗最优在 120ms 网格边缘，扫 125-160ms 确认趋势"""
import json, os, subprocess
PY = r'C:\Users\Yuxiao Lu\AppData\Local\Programs\Python\Python313\python.exe'
B = r'C:\00_Data\RGM\NoteGT'
BANK = os.path.join(B, r'_sources\loopdetect-run\vanilla-12111')
OUT = os.path.join(B, r'_sources\loopdetect-run\verify-sweeps-vanilla')
F0 = os.path.join(B, r'_sources\loopdetect-run\result-v5-vanilla12111-utf8.json')

for n in ['bell', 'flute', 'guitar']:
    outp = os.path.join(OUT, n + '-ext.json')
    r = subprocess.run([PY, os.path.join(B, r'tools\verify_l0.py'), os.path.join(BANK, n + '.ogg'),
                        '--bank', os.path.join(B, r'tools\instruments-vanilla.json'),
                        '--f0', F0, '--len-sweep', '125,130,135,140,145,150,155,160', '--out', outp],
                       capture_output=True, errors='replace')
    data = json.load(open(outp, encoding='utf-8'))
    s = data[0]['sweep']
    cells = '  '.join(f"{row['len_ms']:.0f}:{row['worst_pp']:.2f}/{row['worst_mod']:.1f}" for row in s)
    best = min(s, key=lambda row: (row['worst_pp'] or 9e9, row['worst_mod'] or 9e9))
    ok = [row for row in s if row['worst_pp'] <= 3.0 and row['worst_mod'] is not None and row['worst_mod'] <= -30.0]
    tag = f"PASS-LINE @{ok[0]['len_ms']:.0f}ms" if ok else 'no new pass'
    print(f"{n:<8} {cells}  -> best {best['len_ms']:.0f}ms pp={best['worst_pp']:.2f} mod={best['worst_mod']:.1f}  [{tag}]")
