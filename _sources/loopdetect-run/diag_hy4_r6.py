# -*- coding: utf-8 -*-
"""Hy4 第六轮优先问题诊断（2026-08）
① didgeridoo(用户) 0-3.8s 电平曲线：lead-in 还是真 swell？
② dbass(用户) lp 7.09 定位：loop 内容 vs 接缝无关
③ guitar@0.90 A/B 渲染（xfade vs 硬循环，隔离 12.8° seam）
"""
import sys, json
import numpy as np
import soundfile as sf

D = r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
sys.path.insert(0, D)
import importlib
import loopdetect as ld
importlib.reload(ld)

print('loopdetect v5 loaded:', ld.__file__)

# ─────────────────────────── ① didgeridoo 电平曲线 ───────────────────────────
def didg_curve():
    print('\n=== ① didgeridoo(user) 0-4s 电平曲线（50ms 窗 RMS，分析域=preprocess 后）===')
    x, sr = sf.read(D + r'\user-bank\didgeridoo.ogg', dtype='float64')
    ld.SR = int(sr)
    x = ld.preprocess(x)
    peak = float(np.abs(x).max())
    steady = float(np.sqrt(np.mean(x[int(3.0*sr):]**2)))   # 3s 后视为稳态
    steady_db = 20*np.log10(steady/peak)
    print(f'sr={sr} 峰值归一后: steady(3s+)={steady_db:.2f} dB vs peak')
    w = int(0.050*sr)
    rows = []
    for i in range(0, min(len(x), int(4.0*sr)) - w, w):
        rms = float(np.sqrt(np.mean(x[i:i+w]**2)))
        db = 20*np.log10(rms/peak)
        rows.append((i/sr, db))
    # 打印：每 200ms 一行 + 跨阈点
    thr_done = set()
    for t, db in rows:
        for thr in (-6.0, -12.0, -18.0, -24.0):
            prev = None
        if int(t*5) % 4 == 0 or t < 0.3:   # 前 0.3s 逐 50ms，之后每 200ms
            mark = ''
            for thr in (-6.0, -12.0, -18.0, -24.0):
                if thr in thr_done or db >= steady_db + thr:
                    continue
                # 第一次从低于阈值到达到
                thr_done.add(thr)
                mark = f'  << 首次 >= steady{thr:+.0f}dB'
            print(f't={t*1000:7.0f}ms  {db:8.2f} dB{mark}')
    # 结论辅助：前 500ms 均值 vs 稳态
    head = float(np.sqrt(np.mean(x[:int(0.5*sr)]**2)))
    print(f'前 500ms RMS = {20*np.log10(head/peak):.2f} dB vs peak（稳态 {steady_db:.2f}）')
    print('判定参考：若前几百 ms ≈ 噪声底（<-40dB）→ lead-in（可裁）；若缓慢爬升 → 真 swell')

# ─────────────────────────── ② dbass lp 7.09 定位 ───────────────────────────
def dbass_lp():
    print('\n=== ② dbass(user) lp 7.09 定位（loop 内容诊断）===')
    x, sr = sf.read(D + r'\user-bank\dbass.ogg', dtype='float64')
    ld.SR = int(sr)
    x = ld.preprocess(x)
    b, why = ld.find_loop(x)
    if b is None:
        print('find_loop 拒绝:', why); return
    a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
    print(f'a={a/sr*1000:.1f}ms L={L/sr*1000:.1f}ms k={k} f0={f0:.2f}Hz ncc={b["c"]:.4f}')
    loop = ld.build_loop(x, a, L, k, f0, xfade=0.005)
    pr = ld.per_period_rms(loop, k)
    pr_db = 20*np.log10(pr/max(pr.min(),1e-12))
    hi = int(np.argmax(pr)); lo = int(np.argmin(pr))
    print(f'per-period RMS: k={k} 期, 最高=第{hi}期 {pr_db[hi]:.2f}dB, 最低=第{lo}期 {pr_db[lo]:.2f}dB, pp={pr_db.max()-pr_db.min():.2f}dB')
    # 源信号 loop 区电平轮廓（10ms 步进 50ms 窗）
    E = ld.renv(x, 0.030)
    peak = float(np.abs(x).max())
    w = int(0.050*sr)
    step = int(0.010*sr)
    print('源信号 loop 区 [a, a+L] 电平轮廓:')
    for i in range(0, L - w + 1, step):
        t = (a + i)/sr
        rms = float(np.sqrt(np.mean(x[a+i:a+i+w]**2)))
        db = 20*np.log10(rms/peak)
        bar = '#' * int(max(0, (db + 30) * 1.5))
        print(f't={t*1000:7.1f}ms  {db:8.2f} dB {bar}')

# ─────────────────────────── ③ guitar A/B 渲染 ───────────────────────────
def guitar_ab():
    print('\n=== ③ guitar@0.90 A/B 渲染（4s，隔离 seam）===')
    x, sr = sf.read(D + r'\user-bank\guitar.ogg', dtype='float64')
    ld.SR = int(sr)
    x = ld.preprocess(x)
    b, why = ld.find_loop(x, ncc_min=0.90)
    a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
    print(f'a={a/sr*1000:.1f}ms L={L/sr*1000:.1f}ms k={k} f0={f0:.2f}Hz rate={sr/L:.2f}Hz ncc={b["c"]:.4f}')
    # A: v5 官方 build（含 5ms xfade 接缝）
    loopA = ld.build_loop(x, a, L, k, f0, xfade=0.005)
    yA = ld.render(x, a, loopA, total=4.0)
    # B: 硬循环（loop 区无 xfade，其余与 render 相同）
    E = ld.renv(x, 0.030)
    ef = np.maximum(E[a:a+L], 1e-12)
    loB = x[a:a+L].copy()
    loB = loB/ef*np.median(ef)
    yB = ld.render(x, a, loB, total=4.0)
    import os
    for name, y in (('ngs-gtr-A.wav', yA), ('ngs-gtr-B.wav', yB)):
        out = os.path.join(D, 'ab', 'guitar', name)
        y32 = (y/np.abs(y).max()*0.9).astype(np.float32)
        sf.write(out, y32, sr)
        print('rendered:', out)

if __name__ == '__main__':
    import os
    os.makedirs(os.path.join(D, 'ab', 'guitar'), exist_ok=True)
    didg_curve()
    dbass_lp()
    guitar_ab()
    print('\nDONE')
