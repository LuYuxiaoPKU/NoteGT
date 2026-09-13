# -*- coding: utf-8 -*-
"""StringF#4：span-cycles 1.0 重检 + 12s A/B 渲染（attack swell + loop）"""
import os, sys, json
import numpy as np
import soundfile as sf
D = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
sys.path.insert(0, D)
import loopdetect as ld
import importlib
importlib.reload(ld)

p = os.path.join(D, 'test-bank', 'StringF#4.ogg')
x, sr = sf.read(p, dtype='float64')
if x.ndim == 2:
    x = x.mean(axis=1)
ld.SR = int(sr)
x = ld.preprocess(x)
b, why = ld.find_loop(x, snr_min=20.0, ncc_min=0.90, dynrange=40.0, subharm=True, fmin=30.0, span_cycles=1.0)
if b is None:
    print('REJECT:', why); sys.exit(0)
a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
loop, diag = ld.build_loop_diag(x, a, L, k, f0, xfade=0.005)
print(f'span1.0: a={a/sr*1000:.1f}ms L={L/sr*1000:.2f}ms k={k} f0={f0:.2f}Hz '
      f'rate={sr/L:.2f}Hz seam={diag["seamErrDeg"]} fadeNcc={diag["fadeNcc"]} dip={diag["fadeDipDb"]}')

# 渲染 12s：attack(0→a) + loop×N，峰值归一
os.makedirs(os.path.join(D, 'ab', 'stringf4'), exist_ok=True)
y = ld.render(x, a, loop, total=12.0)
out = os.path.join(D, 'ab', 'stringf4', 'strf4-A.wav')
sf.write(out, (y / np.abs(y).max() * 0.9).astype(np.float32), sr)
print('rendered ab/stringf4/strf4-A.wav (12s, 5ms xfade)')
