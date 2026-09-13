# -*- coding: utf-8 -*-
"""16 件原版 v5 全字段表（L2 可行性排序用）"""
import json
d = json.load(open(r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run\result-v5-vanilla12111-utf8.json', encoding='utf-8'))
print(f"{'file':<16} {'verdict':<10} {'seamDeg':<8} {'loopMs':<8} {'rateHz':<7} {'lp':<6} {'mod':<7} {'f0':<7} {'ncc':<7}")
for r in d:
    n = r['file'].split('\\')[-1].replace('.ogg', '')
    seam = r.get('seamErrDeg')
    print(f"{n:<16} {str(r.get('verdict', '?')):<10} {str(seam):<8} {str(r.get('loopMs', '-')):<8} "
          f"{str(r.get('loopRateHz', '-')):<7} {str(r.get('loopRippleDb', '-')):<6} {str(r.get('modDbc', '-')):<7} "
          f"{(str(round(r['f0'], 1)) if r.get('f0') else '-'):>7} {str(r.get('ncc', '-')):<7}")
