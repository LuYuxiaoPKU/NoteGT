# -*- coding: utf-8 -*-
"""汇总原版 L0 长度扫描：每文件各 T 的 worst-pp/worst-mod + 按筛选线(pp<=3dB & mod<=-30dBc)选最短达标 T"""
import json, os
d = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run\verify-sweeps-vanilla'
melodic = ['banjo','bassattack','bell','bit','cow_bell','didgeridoo','flute','guitar','harp2','icechime','iron_xylophone','pling','xylobone']
lens = (40, 50, 60, 75, 90, 120)
print(f"{'file':<15} {'reg':<5} | " + ' | '.join(f'{l:>15}' for l in lens) + ' | pick')
for n in melodic:
    p = os.path.join(d, n + '.json')
    if not os.path.exists(p):
        print(f'{n:<15} MISSING'); continue
    data = json.load(open(p, encoding='utf-8'))
    s = data[0]['sweep']; reg = s[0]['reg']
    cells = []
    for r in s:
        pp, md = r['worst_pp'], r['worst_mod']
        if pp is None:
            cells.append(f'{"-":>15}')
        else:
            cells.append(f'{pp:6.2f}/{md:7.2f}')
    ok = [r for r in s if r['worst_pp'] is not None and r['worst_pp'] <= 3.0
          and r['worst_mod'] is not None and r['worst_mod'] <= -30.0]
    if ok:
        pick = f"{ok[0]['len_ms']:.0f}ms pp={ok[0]['worst_pp']:.2f} mod={ok[0]['worst_mod']:.1f}"
    else:
        detail = ', '.join(f"{r['len_ms']:.0f}:pp{r['worst_pp']:.1f}/mod{r['worst_mod']:.0f}"
                           for r in s if r['worst_pp'] is not None)
        pick = 'FAIL(' + detail + ')'
    print(f'{n:<15} {reg:<5} | ' + ' | '.join(f'{c:>15}' for c in cells) + f' | {pick}')
