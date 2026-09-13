#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
l0shape.py —— L0 样本整形：平台包络（Hy4 规格）

规格（_hy4/导出区/对话记录 第2轮 + 2026-08 第二轮修正）：
    样本时长 = 60ms 平台（F#3）/ 55ms（F#4, F#5+）   ← 总时长（文件时间），首尾淡化包含在内
    包络     = 中间平坦（压平自然衰减），不衰减
    首尾淡化 = 各 8ms sin²（F#3）/ ~1 周期 sin²（F#4, F#5+），等功率（线性会掉 -3dB 坑）

    Hy4 在合成 185Hz 谐波栈上验证：20Hz 调制 -19.56 → -56.65 dBc（RMS 口径复现 ≈ -36dBc，见 docs）。
    「60ms = 1.2 层」指 60ms 播放 / 50ms(1gt) 间隔。真实样本的 pitch 域映射有歧义，
    故本脚本以「文件时长 T_file」为唯一长度参数，验证时扫多 pitch + 多 T_file 找最优点（见 verify_l0.py）。

分音域参数（2026-08 修正：60ms+8ms 只在 185Hz 有效，换音域失效；F#2/F#1 走 L1+L2）：
    F#5+ (bells/chimes/xylophone) : T_file=55ms, fade=~1 周期(≈1.35ms @740Hz)
    F#4  (flute/cowbell)          : T_file=55ms, fade=~1 周期(≈2.7ms @370Hz)
    F#3  (harp/pling/bit/banjo/iron_xylophone/trumpet) : T_file=60ms, fade=8ms
    F#2/F#1 (guitar/bass/didgeridoo): L0 无效 → L1+L2（可 --len-ms/--fade-ms 强行整形做对照）

打击乐（2026-08 第三轮修正：Hy4 判定 pp 对打击乐无效——单个孤立 hit 的 pp 就是 64.6dB，
「压平」无意义且与 50ms 间隔错配）：
    percussion : T_file=48ms, fade-in=0（保留自然 attack）, fade-out=4ms sin², **不压平**，
                 峰值归一。目标 = 零重叠（≤1gt）+ 保留自然衰减；L1 对打击乐关闭、L2 不适用。

用法:
    python l0shape.py <ogg...|目录> --bank instruments.json [--out DIR]
        [--len-ms 60] [--fade-ms 8] [--fade-in-ms 0] [--fade-out-ms 4] [--no-flatten] [--dry-run]
instruments.json: {"harp": "F#3", "bdrum": "percussion", ...}
    melodic 条目 = 音域（F#1-F#5）；"percussion" 条目自动应用打击乐规格（可用上述 flag 覆盖）。
输出: <out>/<名>.l0.wav + <名>.l0.ogg（soundfile 支持时）
"""
import os, json, argparse
import numpy as np
import soundfile as sf

# 分音域默认参数（文件时间 ms）；fade 包含在 len 内
REGISTERS = {
    'F#5': {'len_ms': 55.0, 'fade_ms': 1.0 / 740.0 * 1000},   # ~1 周期 @740Hz ≈1.35ms
    'F#4': {'len_ms': 55.0, 'fade_ms': 1.0 / 370.0 * 1000},   # ~1 周期 @370Hz ≈2.7ms
    'F#3': {'len_ms': 60.0, 'fade_ms': 8.0},
}
FALLBACK = REGISTERS['F#3']


def params_for(reg):
    """reg: 'F#5'|'F#4'|'F#3'（melodic，未知回退 F#3）| 'percussion'（打击乐）。
    返回 (len_ms, fade_in_ms, fade_out_ms, flatten)。"""
    if reg == 'percussion':
        return 48.0, 0.0, 4.0, False
    p = REGISTERS.get(reg, FALLBACK)
    return p['len_ms'], p['fade_ms'], p['fade_ms'], True


def load_mono(path):
    x, sr = sf.read(path, dtype='float64', always_2d=True)
    if x.shape[1] > 1:
        x = x.mean(axis=1)      # 立体声 → 单声道
    else:
        x = x[:, 0]
    return x, sr


def env_rms(x, w):
    w = max(int(w), 3)
    win = np.hanning(w)
    win /= win.sum()
    return np.sqrt(np.convolve(x * x, win, mode='same') + 1e-24)


def shape(x, R, len_ms, fade_in_ms, fade_out_ms, flatten=True, flatten_clip=(0.5, 2.5)):
    """x: 原始样本 @R。返回 (shaped 文件样本 @R, meta)。
    melodic : shaped = 前 len_ms 的样本，首尾 sin² 等功率淡化，中间压平自然衰减。
    打击乐  : fade_in=0（保留 attack）+ 短 fade-out + flatten=False（不压平）。"""
    N = int(round(len_ms / 1000.0 * R))
    if N >= len(x):
        N = len(x)                      # 样本更短 → 整体使用，淡出贴尾
    seg = x[:N].copy()
    fi = int(round(fade_in_ms / 1000.0 * R))
    fo = int(round(fade_out_ms / 1000.0 * R))
    fi = min(max(fi, 0), N // 4)
    fo = min(max(fo, 0), N // 4)
    g = np.ones(N)
    # 首段 sin² 淡入（等功率）
    if fi > 1:
        g[:fi] = np.sin(np.pi * np.arange(fi) / (2 * fi)) ** 2
    # 尾段 sin²（cos² 形状）淡出
    if fo > 1:
        idx = np.arange(fo)
        g[N - fo:] = np.sin(np.pi * (fo - idx) / (2 * fo)) ** 2
    # 中间压平自然衰减（限幅防 pumping/噪声放大），增益缓变
    if flatten:
        e = env_rms(seg, max(int(0.005 * R), 8))
        mid = slice(fi, N - fo)
        if N - fi - fo > 16:
            ref = float(np.median(e[mid]))
            if ref > 1e-6:
                gm = np.clip(ref / np.maximum(e[mid], 1e-9), *flatten_clip)
                h = np.hanning(17)
                gm = np.convolve(gm, h / max(h.sum(), 1e-9), 'same')
                gm = np.clip(gm, *flatten_clip)
                g[mid] *= gm
    out = seg * g
    out -= out.mean()                    # 去 DC（Vorbis pre-skip 后复查首尾≈0）
    meta = dict(N=N, fi=fi, file_ms=N / R * 1000.0,
                fade_in_ms=fi / R * 1000.0, fade_out_ms=fo / R * 1000.0,
                flatten=flatten,
                len_req=len_ms, fade_req=f'{fade_in_ms}/{fade_out_ms}ms',
                head_abs=float(np.abs(out[0])), tail_abs=float(np.abs(out[-1])),
                peak=float(np.abs(out).max()))
    return out, meta


def main():
    ap = argparse.ArgumentParser(description='L0 平台包络整形')
    ap.add_argument('paths', nargs='+')
    ap.add_argument('--bank', required=True)
    ap.add_argument('--out', default=None)
    ap.add_argument('--len-ms', type=float, default=None, help='覆盖总文件时长 ms')
    ap.add_argument('--fade-ms', type=float, default=None, help='覆盖单侧淡化 ms（同时设首尾）')
    ap.add_argument('--fade-in-ms', type=float, default=None, help='覆盖首段淡化 ms（打击乐=0）')
    ap.add_argument('--fade-out-ms', type=float, default=None, help='覆盖尾段淡化 ms（打击乐=4）')
    ap.add_argument('--no-flatten', action='store_true', help='不压平自然衰减（打击乐）')
    ap.add_argument('--dry-run', action='store_true')
    args = ap.parse_args()

    bank = json.load(open(args.bank, encoding='utf-8-sig'))
    files = []
    for pth in args.paths:
        if os.path.isdir(pth):
            files += [os.path.join(pth, f) for f in sorted(os.listdir(pth)) if f.lower().endswith('.ogg')]
        else:
            files.append(pth)
    outdir = args.out or '.'
    os.makedirs(outdir, exist_ok=True)

    rows = []
    for f in files:
        name = os.path.splitext(os.path.basename(f))[0]
        reg = bank.get(name)
        if reg is None:
            print(f'  {name:20s} SKIP（bank 未登记）')
            continue
        len_ms, fi_d, fo_d, flatten = params_for(reg)
        # CLI 覆盖
        len_ms = args.len_ms if args.len_ms is not None else len_ms
        if args.fade_ms is not None:
            fi_d = fo_d = args.fade_ms
        fi_d = args.fade_in_ms if args.fade_in_ms is not None else fi_d
        fo_d = args.fade_out_ms if args.fade_out_ms is not None else fo_d
        flatten = flatten and not args.no_flatten
        x, R = load_mono(f)
        out, meta = shape(x, R, len_ms, fi_d, fo_d, flatten)
        meta.update(name=name, reg=reg, rate=R)
        if not args.dry_run:
            norm = (out / max(meta['peak'], 1e-12) * 0.95).astype(np.float32)
            wpath = os.path.join(outdir, f'{name}.l0.wav')
            sf.write(wpath, norm, R)
            try:
                opath = os.path.join(outdir, f'{name}.l0.ogg')
                sf.write(opath, norm, R, format='OGG')
                meta['ogg'] = os.path.basename(opath)
            except Exception as ex:
                meta['ogg'] = f'FAIL({type(ex).__name__})'
        rows.append(meta)
        print(f'  {name:20s} {meta["reg"]:>10s}  file={meta["file_ms"]:6.1f}ms '
              f'fade={meta["fade_in_ms"]:4.1f}/{meta["fade_out_ms"]:4.1f}ms flat={int(meta["flatten"])} '
              f'tail|amp|={meta["tail_abs"]:.1e}' + ('' if args.dry_run else f'  -> {meta.get("ogg") or (name+".l0.wav")}'))
    if not args.dry_run:
        jp = os.path.join(outdir, 'l0-meta.json')
        merged = {r['name']: r for r in rows}
        if os.path.exists(jp):
            try:
                for old in json.load(open(jp, encoding='utf-8')):
                    merged.setdefault(old['name'], old)
            except Exception:
                pass
        json.dump(list(merged.values()), open(jp, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
        print(f'meta -> {jp}（{len(merged)} 条）')


if __name__ == '__main__':
    main()
