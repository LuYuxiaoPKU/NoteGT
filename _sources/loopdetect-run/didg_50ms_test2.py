# didgeridoo ripple 复测 v2：分离 attack 污染，只测 loop 稳态区
import sys
import numpy as np
import soundfile as sf

sys.path.insert(0, r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run')
import loopdetect as ld


def seq_pp(y, wms, t0, t1):
    w = wms / 1000.0
    n = int((t1 - t0) / w)
    vals = []
    for i in range(n):
        s = int((t0 + i * w) * ld.SR)
        e = s + int(w * ld.SR)
        if e > len(y):
            break
        vals.append(np.sqrt(np.mean(y[s:e] ** 2)))
    vals = np.array(vals)
    return 20 * np.log10(vals.max() / max(vals.min(), 1e-12)), vals


for path, label in [
    (r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run\user-bank\didgeridoo.ogg', 'didgeridoo(用户)'),
    (r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run\vanilla-12111\didgeridoo.ogg', 'didgeridoo(原版)'),
]:
    x, sr = sf.read(path, dtype='float64')
    ld.SR = int(sr)
    x = ld.preprocess(x)
    b, why = ld.find_loop(x, snr_min=20.0, ncc_min=0.95, dynrange=40.0, subharm=True, fmin=30.0)
    if b is None:
        print(f'{label}: REJECT {why}')
        continue
    a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
    loop, diag = ld.build_loop_diag(x, a, L, k, f0, xfade=0.005)
    best_sm = ld.seam_ratio(loop)
    for xf in (0.010, 0.020, 0.040):
        if best_sm < 2.0:
            break
        alt, adiag = ld.build_loop_diag(x, a, L, k, f0, xfade=xf)
        if len(alt) != len(loop):
            continue
        if ld.seam_ratio(alt) < best_sm - 0.05:
            loop, diag, best_sm = alt, adiag, ld.seam_ratio(alt)
    y = ld.render(x, a, loop, total=12.0)
    a_s = a / ld.SR
    # 纯 loop 稳态区：attack 结束(+crossfade 0.035) 到 release 前 0.2s
    t0, t1 = a_s + 0.05, 12.0 - 0.3
    pp_def, v_def = seq_pp(y, 1000.0 / f0 * 10, t0, t1)   # 10 周期窗（官方口径，但只在稳态区）
    pp_50, v_50 = seq_pp(y, 50.0, t0, t1)                  # 50ms 窗
    # 稳态区 10 周期窗的逐窗序列 → 20Hz/loop率 频谱
    dc = v_def.mean()
    fr = np.fft.rfftfreq(len(v_def), 10.0 / f0 / 1000.0 * 1000.0)  # 占位
    sp = np.abs(np.fft.rfft((v_def - dc) * np.hanning(len(v_def))))
    fr = np.fft.rfftfreq(len(v_def), (10.0 / f0))
    i_rate = int(np.argmin(np.abs(fr - ld.SR / L)))
    mod_rate = 20 * np.log10((2 * sp[i_rate] / len(v_def)) / max(dc, 1e-12) + 1e-12)
    # 趋势：稳态区前1/3 vs 后1/3 均值比（build-up 方向性）
    third = len(v_def) // 3
    trend = 20 * np.log10(v_def[-third:].mean() / max(v_def[:third].mean(), 1e-12))
    print(f'{label}: f0={f0:.2f} a={a_s*1000:.0f}ms loop={L/ld.SR*1000:.0f}ms '
          f'| 稳态区10周期窗pp={pp_def:.2f}dB | 50ms窗pp={pp_50:.2f}dB | '
          f'mod@{ld.SR/L:.2f}Hz={mod_rate:.2f}dBc | 后1/3 vs 前1/3 = {trend:+.2f}dB')
