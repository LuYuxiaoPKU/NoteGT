# -*- coding: utf-8 -*-
"""banjo find_loop 中间量诊断：与 Java LoopDetector 对齐排查（M2a 交叉核对分歧点）。"""
import os, sys, struct, json
import numpy as np
import loopdetect as ld

ROOT = r'C:\00_Data\RGM\NoteGT'
WAV = os.path.join(ROOT, 'notesoundopt/src/test/resources/dsp/banjo.wav')

def read_wav32f(path):
    import soundfile as sf
    x, sr = sf.read(path, dtype='float32', always_2d=True)
    return x[:, 0].astype(np.float64), int(sr)

x32, sr = read_wav32f(WAV)
print('sr =', sr, 'len =', len(x32))

ld.SR = sr
x = ld.preprocess(x32)
f0, mult = ld.est_f0_full(x, subharm=True, fmin=30.0)
print('f0 = %.6f  mult = %d' % (f0, mult))

E = ld.renv(x, 0.030)
NF = ld.nfl_est(x)
thr = E.max() * 10 ** (-40.0 / 20.0)
nfp = ld.nfl_plateau(x, E)
print('E.max = %.6f  thr_base = %.6f  nfp = %s  NF = %.6f' % (E.max(), thr, nfp, NF))
if nfp is not None:
    thr = max(thr, nfp * 10 ** (20.0 / 20.0))
print('thr_final = %.6f' % thr)
ok = np.where(E > thr)[0]
u1 = ok[-1]
P = sr / f0
ipk = int(np.argmax(E))
a_lo = ipk + int(2 * P)
a_hi = min(u1 - int(0.10 * sr), len(x) - int(0.10 * sr))
print('u1 = %d  P = %.6f  ipk = %d  a_lo = %d  a_hi = %d' % (u1, P, ipk, a_lo, a_hi))

xf = x / np.maximum(E, 1e-12) * np.median(E)
Ws = int(0.060 * sr)
ncc_min = 0.95
best = None
top = []
for a in range(a_lo, max(a_hi, a_lo + 1), int(0.005 * sr)):
    kmax = int(min(u1 - a, 1.2 * sr) / P)
    if kmax < 5:
        continue
    got = None
    for k in range(kmax, 5 - 1, -1):
        Li = k * P
        if a + 2 * Li + Ws > len(x):
            continue
        span = int(0.5 * P)
        for dL in range(-span, span + 1):
            L = int(round(Li)) + dL
            if L < 10 or a + 2 * L + Ws > len(x):
                continue
            c = ld.ncc(xf[a:a + Ws], xf[a + L:a + L + Ws])
            if c >= ncc_min and (got is None or c > got['c']):
                got = dict(a=a, L=L, k=k, c=c)
        if got is not None:
            break
    if got is not None:
        top.append(got)
        if best is None or got['L'] > best['L']:
            best = got
print('candidates(>=0.95): %d' % len(top))
for g in top[:10]:
    print('  a=%d L=%d k=%d c=%.6f' % (g['a'], g['L'], g['k'], g['c']))
print('best =', best)

# 0.95 附近的全部 NCC（看阈值边缘）
top2 = []
for a in range(a_lo, max(a_hi, a_lo + 1), int(0.005 * sr)):
    kmax = int(min(u1 - a, 1.2 * sr) / P)
    if kmax < 5:
        continue
    got = None
    for k in range(kmax, 5 - 1, -1):
        Li = k * P
        if a + 2 * Li + Ws > len(x):
            continue
        span = int(0.5 * P)
        for dL in range(-span, span + 1):
            L = int(round(Li)) + dL
            if L < 10 or a + 2 * L + Ws > len(x):
                continue
            c = ld.ncc(xf[a:a + Ws], xf[a + L:a + L + Ws])
            if c >= 0.90 and (got is None or c > got['c']):
                got = dict(a=a, L=L, k=k, c=c)
        if got is not None:
            break
    if got is not None:
        top2.append(got)
# 具体数值点（与 Java DiagBanjoTest 对齐）
import numpy as _np
a, L = 7139, 649
Ws2 = 1800
u = xf[a:a+Ws2]
v = xf[a+L:a+L+Ws2]
print('E[%d] = %.12f  E[%d] = %.12f  medE = %.12f' % (a, E[a], a+L, E[a+L], np.median(E)))
print('xf[%d..%d] = %s' % (a, a+3, _np.array2string(xf[a:a+4], precision=12, separator=', ')))
print('NCC(a=%d, L=%d, Ws=%d) = %.12f' % (a, L, Ws, ld.ncc(xf[a:a+Ws], xf[a+L:a+L+Ws])))
print('NCC(a=%d, L=%d, Ws2=%d) = %.12f' % (a, L, Ws2, ld.ncc(u, v)))
# 固定 a=7139，全 dL 扫（无阈值）
cmax = -2; cmax_l = -1
for dL in range(-65, 66):
    Lc = 652 + dL
    if Lc < 10 or a + 2*Lc + Ws > len(x):
        continue
    c = ld.ncc(xf[a:a+Ws], xf[a+Lc:a+Lc+Ws])
    if c > cmax:
        cmax = c; cmax_l = Lc
print('a=%d: max NCC over dL = %.12f at L=%d' % (a, cmax, cmax_l))
