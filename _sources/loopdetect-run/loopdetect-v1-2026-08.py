#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
loopdetect.py —— Minecraft 音符盒音色自动循环点检测器 (L2b)

用法:
    python loopdetect.py 音色.ogg                    # 分析单个文件
    python loopdetect.py 音色.ogg --render out.wav   # 额外渲染试听长音
    python loopdetect.py 音色.ogg --pitch 1.0 2.0    # 在指定音高倍率下验证
    python loopdetect.py 目录/ --batch               # 批量扫描
    python loopdetect.py 目录/ --batch --json        # 输出 JSON 供资源包生成

输出:
    attackEnd / loopStart / loopEnd  (单位: 样本 @ 文件原生采样率)
"""
import sys, os, json, argparse
import numpy as np
import soundfile as sf
from scipy.signal import resample

SR = 44100   # 分析域采样率；analyze() 会按文件原生采样率覆盖


# ────────────────────────────── 工具 ──────────────────────────────
def renv(x, w):
    """RMS 包络（汉宁窗平滑）"""
    w = int(w * SR); w = w + 1 if w % 2 == 0 else w
    win = np.hanning(w); win /= win.sum()
    return np.sqrt(np.convolve(x * x, win, mode='same') + 1e-24)


def seam_ratio(lo, ms=5.0):
    """接缝跳变 / 端点局部平均样本间跳变

    必须用局部差分：循环点淡化会让整体 mean|diff| 变小，
    用全局比值会把本来平滑的接缝误判为咔哒。
    """
    w = max(int(ms / 1000 * SR), 32)
    local = np.concatenate([np.abs(np.diff(lo[:w])), np.abs(np.diff(lo[-w:]))])
    return float(abs(lo[0] - lo[-1]) / max(np.mean(local), 1e-12))


def ncc(u, v):
    """归一化互相关"""
    du, dv = np.sqrt(np.dot(u, u)), np.sqrt(np.dot(v, v))
    return 0.0 if du < 1e-12 or dv < 1e-12 else float(np.dot(u, v) / (du * dv))


def nfl_est(x, tail=0.06):
    """尾部 RMS（仅当尾部确实是噪声平台时才可信）"""
    n = min(int(tail * SR), max(len(x) // 4, 1))
    return float(np.sqrt(np.mean(x[-n:] ** 2)))


def nfl_plateau(x, E, tail=0.25, flat_ratio=0.80):
    """尾端平台检测：尾部走平 → 那是噪声底；仍在衰减 → 那是信号，返回 None

    慢衰减样本的尾部其实还是乐音，用它的 RMS 当噪声底会把可用区算得过短。
    """
    n = len(E)
    t0 = int(n * (1 - tail))
    seg = E[t0:]
    if len(seg) < 64:
        return None
    q = max(len(seg) // 4, 8)
    head_med = float(np.median(seg[:q]))
    tail_med = float(np.median(seg[-q:]))
    if head_med <= 0:
        return None
    if tail_med / head_med >= flat_ratio:      # 走平 → 噪声底
        return tail_med
    return None                                # 仍在衰减 → 是信号


# ────────────────────────── 阶段①② 预处理与 f0 ──────────────────────────
def preprocess(x):
    """单声道化 + 去 DC + 峰值归一"""
    if x.ndim > 1:
        x = x.mean(axis=1)
    x = np.asarray(x, dtype=np.float64)
    x = x - x.mean()
    m = np.abs(x).max()
    return x / m if m > 0 else x


def est_f0(x, t0=0.010, t1=0.210, pad=16):
    """阶段②: 抛物线插值 FFT 基频估计"""
    seg = x[int(t0 * SR):int(t1 * SR)]
    if len(seg) < 256:
        t1 = min(0.5, len(x) / SR * 0.9)
        seg = x[int(t0 * SR):int(t1 * SR)]
    if len(seg) < 64:
        return 0.0
    seg = (seg - seg.mean()) * np.hanning(len(seg))
    N = 1
    while N < len(seg) * pad:
        N *= 2
    S = np.abs(np.fft.rfft(seg, N))
    fr = np.fft.rfftfreq(N, 1.0 / SR)
    m = (fr > 60.0) & (fr < 2000.0)
    i0 = int(np.argmax(np.where(m, S, 0.0)))
    a, b, c = (np.log(S[i0 - 1] + 1e-30), np.log(S[i0] + 1e-30),
               np.log(S[i0 + 1] + 1e-30))
    d = 0.5 * (a - c) / (a - 2 * b + c + 1e-30)
    return float(fr[i0] + d * (fr[1] - fr[0]))


# ─────────────────────── 阶段③④⑤ 循环点搜索 ───────────────────────
def find_loop(x, snr_min=20.0, W=0.060, fwin=0.030, Lmax_s=1.2,
              ncc_min=0.95, nper_min=5, Lmin_s=0.080, dynrange=40.0):
    """阶段③-⑥: 返回 dict 或 (None, 拒绝原因)"""
    f0 = est_f0(x)
    if f0 <= 0:
        return None, '基频估计失败（可能是噪声类/打击乐音色）'

    E = renv(x, fwin)
    NF = nfl_est(x)
    # 双阈值：绝对噪声底 + 相对动态范围。
    # 慢衰减样本的"尾部"其实还是信号，纯绝对阈值会误判可用区过短。
    # 主判据用相对动态范围；绝对噪声底仅当尾部确实走平时才参与
    thr = E.max() * 10 ** (-dynrange / 20.0)
    nfp = nfl_plateau(x, E)
    if nfp is not None:
        thr = max(thr, nfp * 10 ** (snr_min / 20.0))
    ok = np.where(E > thr)[0]
    if len(ok) == 0:
        return None, '无可用区（信噪比不足或样本过短）'

    u1 = ok[-1]
    P = SR / f0
    ipk = int(np.argmax(E))                      # 包络峰
    a_lo = ipk + int(2 * P)                      # 阶段④: 躲开起音瞬态
    a_hi = min(u1 - int(0.10 * SR), len(x) - int(0.10 * SR))
    if a_hi <= a_lo:
        return None, '起音后的可用区过短'

    xf = x / np.maximum(E, 1e-12) * np.median(E)   # ★ 先拉平，再搜 L
    Ws = int(W * SR)
    best = None
    for a in range(a_lo, max(a_hi, a_lo + 1), int(0.005 * SR)):
        kmax = int(min(u1 - a, Lmax_s * SR) / P)
        if kmax < nper_min:
            continue
        got = None
        for k in range(kmax, nper_min - 1, -1):    # 从最长往下，第一个达标即最长
            Li = k * P
            if a + 2 * Li + Ws > len(x):
                continue
            span = int(P / 2)                      # ★ 约束在 ±半周期
            for dL in range(-span, span + 1):
                L = int(round(Li)) + dL
                if L < 10 or a + 2 * L + Ws > len(x):
                    continue
                c = ncc(xf[a:a + Ws], xf[a + L:a + L + Ws])
                if c >= ncc_min and (got is None or c > got['c']):
                    got = dict(a=a, L=L, k=k, c=c)
            if got is not None:
                break
        if got and (best is None or got['L'] > best['L']):
            best = got

    if best is None:
        return None, '未找到满足相关度阈值的循环长度（可能非谐性过强）'
    if best['L'] < Lmin_s * SR:
        return None, '循环长度过短（<80ms），周期性可闻'

    best.update(f0=f0, P=P, nf=NF, rate=SR / best['L'], u1=u1)
    return best, None


# ──────────────────────────── 阶段⑤c 构建循环段 ────────────────────────────
def build_loop(x, a, L, k, f0, fwin=0.030, xfade=0.005):
    """拉平 + 循环点交叉淡化

    交叉淡化把尾部 xfade 混入头部，使 out[0] = src[L]，
    与 out[-1] = src[L-1] 天然相邻 → 接缝恒为平滑，
    可吸收颤音/非谐性造成的累积相位漂移。
    """
    a = int(a); L = int(L)
    Xs = int(round(xfade * SR))
    E = renv(x, fwin)
    need = a + L + Xs
    if need > len(x):                              # 尾部不够，退化为不淡化
        Xs = max(0, len(x) - a - L)
        need = a + L + Xs
    seg = x[a:need]
    ef = np.maximum(E[a:need], 1e-12)
    src = seg / ef * np.median(ef)
    lo = src[:L].copy()
    if Xs > 0:
        r = np.linspace(0, 1, Xs)
        lo[:Xs] = src[:Xs] * r + src[L:L + Xs] * (1 - r)
    return lo


# ──────────────────────────── 渲染与度量 ────────────────────────────
def render(x, a, loop, total=1.0, rel=0.10, cf=0.035):
    """attack(原样) → crossfade → loop → release"""
    out = np.zeros(int(total * SR))
    f = int(cf * SR); u = np.linspace(0, 1, f)
    out[:a] = x[:a]
    lv = renv(x, 0.030)[a] / max(float(np.median(renv(loop, 0.030))), 1e-12)
    out[a:a + f] = x[a:a + f] * (1 - u) + (loop[:f] * lv) * u
    p = a + f
    while p < len(out):
        seg = min(len(loop), len(out) - p)
        out[p:p + seg] = loop[:seg] * lv
        p += seg
    rf = int(rel * SR)
    out[-rf:] *= np.sin(np.linspace(np.pi / 2, 0, rf)) ** 2
    return out


def rms_seq(y, wms=5.0):
    w = int(wms / 1000 * SR); n = len(y) // w
    return np.sqrt(np.mean((y[:n * w].reshape(n, w)) ** 2, axis=1))


def per_period_rms(loop, k):
    n = len(loop) // k
    return np.sqrt(np.mean((loop[:n * k].reshape(k, n)) ** 2, axis=1))


def metrics(x, a, loop, k, f0, rate, total=1.0):
    """返回 (循环内逐周期起伏, 长音包络起伏, 循环率调制, 接缝跳变倍数)"""
    r = per_period_rms(loop, k)
    loop_pp = 20 * np.log10(r.max() / max(r.min(), 1e-12))

    y = render(x, a, loop, total)
    w = 10 / f0                                   # 度量窗口 = 10 个周期
    rr = rms_seq(y, w * 1000)
    rr = rr[int(0.2 / w):int(0.8 / w)]
    long_pp = 20 * np.log10(rr.max() / max(rr.min(), 1e-12))

    dc = rr.mean()
    sp = np.abs(np.fft.rfft((rr - dc) * np.hanning(len(rr))))
    fr = np.fft.rfftfreq(len(rr), w)
    i = int(np.argmin(np.abs(fr - rate)))
    mod = 20 * np.log10((2 * sp[i] / len(rr)) / max(dc, 1e-12) + 1e-12)

    seam = seam_ratio(loop)
    return loop_pp, long_pp, mod, seam


def verify_pitch(loop, k, f0, rate, p):
    """验证变速播放下的表现（AL_PITCH 是整体重采样）"""
    n = int(1.0 * SR)
    idx = (np.arange(n) * p) % len(loop)
    y = np.interp(idx, np.arange(len(loop)), loop)
    w = 10 / (f0 * p)
    r = rms_seq(y, w * 1000)
    r = r[int(0.2 / w):int(0.8 / w)]
    return 20 * np.log10(r.max() / max(r.min(), 1e-12))


# ──────────────────────────── 主流程 ────────────────────────────
def analyze(path, args):
    try:
        x, sr = sf.read(path, dtype='float64')
    except Exception as e:
        return dict(file=path, ok=False, reason=f'解码失败: {e}')

    global SR
    SR = int(sr)          # 分析域 = 文件原生采样率，不做重采样换算
    x = preprocess(x)

    b, why = find_loop(x, snr_min=args.snr, ncc_min=args.ncc_min, dynrange=args.dynrange)
    if b is None:
        return dict(file=path, ok=False, reason=why)

    a, L, k, f0 = b['a'], b['L'], b['k'], b['f0']
    loop = build_loop(x, a, L, k, f0, xfade=args.xfade)
    # 自适应兜底：接缝不达标就加大循环点淡化
    for xf in (0.010, 0.020, 0.040):
        sm = seam_ratio(loop)
        if sm < 3.0:
            break
        alt = build_loop(x, a, L, k, f0, xfade=xf)
        if len(alt) == len(loop):
            loop = alt
    lp, lg, md, sm = metrics(x, a, loop, k, f0, b['rate'], args.total)

    res = dict(file=path, ok=True,
               f0=round(f0, 4),
               attackEnd=a, loopStart=a, loopEnd=a + L,
               loopSamples=L, periods=k,
               loopMs=round(L / SR * 1000, 2),
               attackMs=round(a / SR * 1000, 2),
               loopRateHz=round(SR / L, 2),
               ncc=round(b['c'], 4),
               loopRippleDb=round(lp, 2),
               longRippleDb=round(lg, 2),
               modDbc=round(md, 2),
               seamRatio=round(sm, 2),
               verdict='PASS' if (sm < 3 and lg < 2.0 and md < -40) else
                       ('WARN' if (sm < 3 and lg < 4.0) else 'FAIL'))

    res['pitchCheck'] = {f'{p}x': round(verify_pitch(loop, k, f0, b['rate'], p), 2)
                         for p in args.pitch}
    if args.render:
        y = render(x, a, loop, args.total)
        sf.write(args.render, (y / np.abs(y).max() * 0.9).astype(np.float32), SR)
        res['render'] = args.render
    return res


def main():
    ap = argparse.ArgumentParser(description='音符盒音色自动循环点检测 (L2b)')
    ap.add_argument('path')
    ap.add_argument('--batch', action='store_true')
    ap.add_argument('--snr', type=float, default=20.0, help='可用区信噪比阈值 dB')
    ap.add_argument('--ncc-min', type=float, default=0.95, help='循环段相关度阈值')
    ap.add_argument('--xfade', type=float, default=0.005, help='循环点交叉淡化 秒')
    ap.add_argument('--dynrange', type=float, default=40.0, help='可用区动态范围 dB')
    ap.add_argument('--total', type=float, default=1.0, help='试听长音时长 秒')
    ap.add_argument('--render', default=None, help='渲染试听 WAV 路径')
    ap.add_argument('--pitch', type=float, nargs='*', default=[0.5, 1.0, 2.0])
    ap.add_argument('--json', action='store_true')
    args = ap.parse_args()

    files = []
    if args.batch and os.path.isdir(args.path):
        for r, _, fs in os.walk(args.path):
            files += [os.path.join(r, f) for f in fs if f.lower().endswith('.ogg')]
    else:
        files = [args.path]

    out = [analyze(f, args) for f in sorted(files)]

    if args.json:
        print(json.dumps(out, ensure_ascii=False, indent=2))
        return

    for r in out:
        name = os.path.basename(r['file'])
        if not r['ok']:
            print(f'  {name:34s} ✗ {r["reason"]}')
            continue
        print(f'  {name:34s} [{r["verdict"]}]')
        print(f'      f0 {r["f0"]:.2f} Hz | 循环 {r["loopMs"]:.1f} ms ({r["periods"]} 周期) '
              f'| 速率 {r["loopRateHz"]:.2f} Hz | NCC {r["ncc"]:.4f}')
        print(f'      循环内起伏 {r["loopRippleDb"]:.2f} dB | 长音起伏 {r["longRippleDb"]:.2f} dB '
              f'| 循环率调制 {r["modDbc"]:.1f} dBc | 接缝 {r["seamRatio"]:.1f}×')
        pc = ' '.join(f'{k}:{v:.2f}dB' for k, v in r['pitchCheck'].items())
        print(f'      音高验证 {pc}')
        if 'render' in r:
            print(f'      试听 → {r["render"]}')
    npass = sum(1 for r in out if r.get('verdict') == 'PASS')
    print(f'\n  合计 {len(out)} 个文件，PASS {npass}，'
          f'WARN {sum(1 for r in out if r.get("verdict")=="WARN")}，'
          f'FAIL/跳过 {sum(1 for r in out if r.get("verdict") in ("FAIL",None))}')


if __name__ == '__main__':
    main()
