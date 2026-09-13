# -*- coding: utf-8 -*-
"""StringF#4 诊断：① head/tail lag 曲线（seamErr NaN 真因）② 50ms 电平包络 ③ 宽搜索重跑"""
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
b, why = ld.find_loop(x, snr_min=20.0, ncc_min=0.90, dynrange=40.0, subharm=True, fmin=30.0, span_cycles=0.5)
a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
SR = ld.SR
P = SR / f0
print(f'attack={a/SR*1000:.1f}ms L={L/SR*1000:.2f}ms k={k} f0={f0:.2f}Hz P={P:.1f}samp')

# ① head/tail lag 曲线（与 build_loop_diag 同口径：renv 包络归一化后）
E = ld.renv(x, 0.030)
need = a + L + max(220, int(0.004 * SR))
seg = x[a:need]
ef = np.maximum(E[a:need], 1e-12)
src = seg / ef * np.median(ef)
Wd = 220
head, tail = src[:Wd], src[L:L + Wd]
lags = np.arange(-260, 261)
curve = np.array([ld._ncc_shift(head, tail, int(l)) for l in lags])
imax = int(np.argmax(curve))
print(f'lag 曲线 argmax = {lags[imax]} samp = {lags[imax]/P*360:.1f}°  (搜索范围 ±{int(P/2)})')
for l in (-260, -200, -150, -100, -60, -30, 0, 30, 60, 100, 150, 200, 260):
    i = int(l + 260)
    bar = '#' * int(max(0, (curve[i] + 0.2) * 40))
    print(f'  lag={l:+5d} ({l/P*360:+7.1f}°) ncc={curve[i]:+.4f} {bar}')

# ② 50ms 电平包络
print('电平包络（50ms 窗 RMS, vs 峰值）:')
w = int(0.05 * SR)
env = np.sqrt(np.convolve(x * x, np.hanning(w) / np.hanning(w).sum(), 'same'))
pk = env.max()
for t in range(0, len(x), int(0.1 * SR)):
    db = 20 * np.log10(max(env[t] / pk, 1e-9))
    bar = '#' * int(max(0, (db + 40) / 0.8))
    print(f'  t={t/SR*1000:6.0f}ms {db:7.2f} dB {bar}')
