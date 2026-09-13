# -*- coding: utf-8 -*-
"""Hy4 r6 后续：didgeridoo 3 变体渲染 + dbass loop 起点扫描"""
import os, sys
import numpy as np
import soundfile as sf
import importlib
D = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
sys.path.insert(0, D)
import loopdetect as ld
importlib.reload(ld)

# ── didgeridoo 3 变体 ──
x, sr = sf.read(os.path.join(D, 'user-bank', 'didgeridoo.ogg'), dtype='float64')
ld.SR = int(sr)
x = ld.preprocess(x)
b, why = ld.find_loop(x)
a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
loop = ld.build_loop(x, a, L, k, f0, xfade=0.005)
print(f'didgeridoo: a={a/sr*1000:.1f}ms L={L/sr*1000:.1f}ms k={k} f0={f0:.2f}Hz')

variants_note = 'A=burst 250ms / B=quiet 200ms+10ms fade-in / C=v5 as-is 3.8s attack'
os.makedirs(os.path.join(D, 'ab', 'didgeridoo'), exist_ok=True)

# C: v5 官方（3.8s attack + loop，level-match lv≈1）
yC = ld.render(x, a, loop, total=12.0)
sf.write(os.path.join(D, 'ab', 'didgeridoo', 'didg-C.wav'),
         (yC/np.abs(yC).max()*0.9).astype(np.float32), sr)
print('rendered didg-C.wav (as-is 3.8s attack)')

# A: 保留 burst：attack = x[0:250ms]，render 自动 level-match（drone 提到 burst 电平）
aA = int(0.250*sr)
yA = ld.render(x, aA, loop, total=12.0)
sf.write(os.path.join(D, 'ab', 'didgeridoo', 'didg-A.wav'),
         (yA/np.abs(yA).max()*0.9).astype(np.float32), sr)
print('rendered didg-A.wav (burst attack 250ms + level-matched drone)')

# B: 安静淡入：attack = loop 起点前 200ms，头部 10ms fade-in
xB = x[a-int(0.200*sr):]
aB = int(0.200*sr)
yB = ld.render(xB, aB, loop, total=12.0)
f = int(0.010*sr)
yB[:f] *= np.linspace(0, 1, f)
sf.write(os.path.join(D, 'ab', 'didgeridoo', 'didg-B.wav'),
         (yB/np.abs(yB).max()*0.9).astype(np.float32), sr)
print('rendered didg-B.wav (quiet fade-in 200ms+10ms)')

# ── dbass loop 起点扫描 ──
print()
x2, sr2 = sf.read(os.path.join(D, 'user-bank', 'dbass.ogg'), dtype='float64')
ld.SR = int(sr2)
x2 = ld.preprocess(x2)
b2, why2 = ld.find_loop(x2)
a2, L2, k2, f02 = b2['a'], b2['L'], b2['k'], b2['f0']
P = L2/k2
print(f'dbass: a={a2/sr2*1000:.1f}ms L={L2/sr2*1000:.1f}ms k={k2} P={P/sr2*1000:.2f}ms')
print('loop 起点按周期平移扫描（每 m 个周期），per-period pp:')
best = None
for m in range(0, 16):
    am = a2 + m*int(P)
    if am + L2 > len(x2) - int(0.05*sr2): break
    try:
        lo = ld.build_loop(x2, am, L2, k2, f02, xfade=0.005)
        pr = ld.per_period_rms(lo, k2)
        pp = 20*np.log10(pr.max()/max(pr.min(), 1e-12))
    except Exception as e:
        print(f'm={m:2d}: error {e}'); continue
    tag = ''
    if best is None or pp < best[1]: best = (m, pp)
    if m % 1 == 0:
        print(f'm={m:2d} a={am/sr2*1000:7.1f}ms  pp={pp:6.2f}dB')
print('best:', best)

# 也试 span-cycles 1.0（是否找到更好的 L）
b3, why3 = ld.find_loop(x2, span_cycles=1.0)
if b3 is not None:
    print(f'span=1.0: a={b3["a"]/sr2*1000:.1f}ms L={b3["L"]/sr2*1000:.1f}ms k={b3["k"]} ncc={b3["c"]:.4f}')
print('DONE')
