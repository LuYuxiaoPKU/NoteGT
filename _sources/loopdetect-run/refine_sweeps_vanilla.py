# -*- coding: utf-8 -*-
"""细化扫描：对每个 vanilla 文件，在粗网格最优点 ±10ms 内以 2.5ms 步长重扫，输出精调最优点"""
import json, os, subprocess, sys
PY = r'C:\Users\Yuxiao Lu\AppData\Local\Programs\Python\Python313\python.exe'
B = r'C:\00_Data\RGM\NoteGT'
BANK = os.path.join(B, r'_sources\loopdetect-run\vanilla-12111')
OUT = os.path.join(B, r'_sources\loopdetect-run\verify-sweeps-vanilla')
F0 = os.path.join(B, r'_sources\loopdetect-run\result-v5-vanilla12111-utf8.json')
MELodic = ['banjo','bassattack','bell','bit','cow_bell','didgeridoo','flute','guitar','harp2','icechime','iron_xylophone','pling','xylobone']

def worst(srow):
    return srow['worst_pp'], srow['worst_mod']

results = {}
for n in MELodic:
    p = os.path.join(OUT, n + '.json')
    data = json.load(open(p, encoding='utf-8'))
    coarse = data[0]['sweep']
    # 粗最优点 = worst_pp 最小（同 pp 时取 mod 更差者？取 pp 最小，并列取 mod 最接近 -30 之上者）
    best = min(coarse, key=lambda r: (r['worst_pp'] if r['worst_pp'] is not None else 9e9))
    t0 = best['len_ms']
    cand = [round(t0 + d * 2.5, 1) for d in range(-4, 5)]  # t0±10, step 2.5
    cand = sorted({c for c in cand if c >= 20})
    lens = ','.join(str(c) for c in cand)
    outp = os.path.join(OUT, n + '-fine.json')
    r = subprocess.run([PY, os.path.join(B, r'tools\verify_l0.py'), os.path.join(BANK, n + '.ogg'),
                        '--bank', os.path.join(B, r'tools\instruments-vanilla.json'),
                        '--f0', F0, '--len-sweep', lens, '--out', outp],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(n, 'ERROR', r.stderr[-300:]); continue
    fdata = json.load(open(outp, encoding='utf-8'))
    s = fdata[0]['sweep']
    # 合并粗+细选最优点：pp<=3 且 mod<=-30 中取最短；否则取 pp 最小（并列 mod 最小）
    allrows = coarse + s
    ok = [r for r in allrows if r['worst_pp'] is not None and r['worst_pp'] <= 3.0
          and r['worst_mod'] is not None and r['worst_mod'] <= -30.0]
    if ok:
        pick = min(ok, key=lambda r: r['len_ms'])
        verdict = 'PASS-LINE'
    else:
        pick = min(allrows, key=lambda r: (r['worst_pp'] if r['worst_pp'] is not None else 9e9,
                                           r['worst_mod'] if r['worst_mod'] is not None else 9e9))
        verdict = 'NEAR' if pick['worst_pp'] <= 6.0 else 'WEAK'
    results[n] = dict(verdict=verdict, t=pick['len_ms'], pp=pick['worst_pp'], mod=pick['worst_mod'],
                      reg=pick['reg'])
    print(f"{n:<15} {pick['reg']:<5} {verdict:<9} T={pick['len_ms']:6.1f}ms pp={pick['worst_pp']:5.2f} mod={pick['worst_mod']:7.2f}")

json.dump(results, open(os.path.join(OUT, 'fine-picks.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('saved fine-picks.json')
