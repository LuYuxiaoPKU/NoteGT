# didgeridoo ripple 复测：默认窗口(10/f0≈109ms) vs 50ms 窗口
# 用途：区分「音色固有噪声」(50ms pp 应更大) vs 循环率泵感(50ms pp 应大幅下降) vs 慢 build-up(两者相近)
import sys, json
import numpy as np
import soundfile as sf

sys.path.insert(0, r'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run')
import loopdetect as ld


def seq_pp(y, wms, trim_s=0.2):
    rr = ld.rms_seq(y, wms)
    n = len(rr)
    i0 = int(trim_s / (wms / 1000.0))
    i1 = n - int(trim_s / (wms / 1000.0))
    rr = rr[i0:i1]
    return 20 * np.log10(rr.max() / max(rr.min(), 1e-12)), rr


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
    w_def = 10.0 / f0
    pp_def, _ = seq_pp(y, w_def * 1000)
    pp_50, _ = seq_pp(y, 50.0)
    lp, lg, md, sm, smlf = ld.metrics(x, a, loop, k, f0, b['rate'], 12.0)
    print(f'{label}: f0={f0:.2f} loop={L/ld.SR*1000:.1f}ms rate={ld.SR/L:.2f}Hz '
          f'| 默认窗({w_def*1000:.0f}ms) long_pp={lg:.2f}dB (复算 {pp_def:.2f}) | '
          f'50ms窗 pp={pp_50:.2f}dB | loop_pp={lp:.2f} mod@rate={md:.2f}dBc')
