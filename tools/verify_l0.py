#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_l0.py —— 20Hz(1gt) 重触发叠加模拟验证（RMS 包络口径，Hy4 2026-08 修正版度量）

对每个样本 × 每个音高 n（p = 2^((n-12)/12)）：
    play = resample(文件, p)        ← 该音高下的播放信号（1 样本 = 1/(R·p) 秒）
    I    = 0.05 · R · p             ← 50ms(1gt) 间隔（样本数）
    y    = Σ_k play[k·I:]           ← 连续重触发叠加 K 份
    指标（稳态中段）：
      pp_db     : RMS 包络（5ms 窗）峰峰值
      mod_dbc   : 包络 15-30Hz 带内峰值相对载波（dBc）——对应 20Hz 锯齿
      perperiod : f0 逐周期 RMS 起伏（f0_note<250Hz 时报告；Hy4：低频看逐周期）

长度扫描：--len-sweep 对单个文件扫多个 T_file 找 worst-case-pitch 最优点。

用法:
    python verify_l0.py <ogg...|目录> --bank instruments.json [--f0 result-user.json]
                        [--notes 0,6,12,18,24] [--len-ms 60] [--fade-ms 8]
                        [--len-sweep 30,45,60,80,120] [--out verify.json]
"""
import os, json, argparse
import numpy as np
import soundfile as sf
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from l0shape import shape, load_mono, params_for


def resample(x, p):
    if abs(p - 1.0) < 1e-9:
        return x
    n = max(int(round(len(x) * p)), 8)
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)


def rms_env(x, w):
    w = max(int(w), 4)
    win = np.hanning(w)
    win /= win.sum()
    return np.sqrt(np.convolve(x * x, win, mode='same') + 1e-24)


def metrics(y, R, f0_note):
    n = len(y)
    a, b = int(n * 0.25), int(n * 0.95)
    if b - a < 80:
        a, b = 0, n
    w = max(int(0.005 * R), 4)
    rr = rms_env(y, w)[a:b]
    rr = rr[rr > 1e-9]
    if len(rr) < 16:
        return None
    pp_db = float(20 * np.log10(rr.max() / max(rr.min(), 1e-12)))
    seg = rr - rr.mean()
    spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg))))
    fr = np.fft.rfftfreq(len(seg), 1.0 / R)     # 帧率 = R 帧/s（帧重叠 w-1）
    band = (fr > 12) & (fr < 30)
    mod_dbc = None
    if band.sum() > 0:
        amp = 2 * spec[band] / max(seg.size, 1)
        mod_dbc = float(20 * np.log10(amp.max() / max(rr.mean(), 1e-12)))
    out = dict(pp_db=round(pp_db, 2), mod_dbc=round(mod_dbc, 2) if mod_dbc is not None else None)
    if f0_note and 20 < f0_note < 250:
        P = int(round(R / f0_note))
        tail = int(0.8 * n)
        m = (tail // P) * P
        if m > 4 * P and tail - m >= 0:
            per = y[tail - m:tail].reshape(-1, P)
            pr = np.sqrt(np.mean(per ** 2, axis=1) + 1e-24)
            out['perperiod_db'] = round(float(20 * np.log10(pr.max() / max(pr.min(), 1e-12))), 2)
    return out


def retrigger(file_x, R, p, K=40):
    play = resample(file_x, p)
    I = int(round(0.05 * R * p))
    total = len(play) + (K - 1) * I
    out = np.zeros(total)
    for k in range(K):
        out[k * I:k * I + len(play)] += play
    return out, R * p


def run_one(name, x, R, reg, f0f, notes, len_ms, fade_in_ms, fade_out_ms, flatten):
    shaped, meta = shape(x, R, len_ms, fade_in_ms, fade_out_ms, flatten)
    row = dict(name=name, reg=reg, len_ms=round(meta['file_ms'], 1),
               fade_ms=f"{meta['fade_in_ms']:.1f}/{meta['fade_out_ms']:.1f}", f0_file=f0f)
    row['notes'] = {}
    for n in notes:
        p = 2 ** ((n - 12) / 12.0)
        yn, Rn = retrigger(x, R, p)
        ys, Rs = retrigger(shaped, R, p)
        f0n = (f0f or 0) * p or None
        mn = metrics(yn, Rn, f0n)
        ms = metrics(ys, Rs, f0n)
        row['notes'][str(n)] = dict(p=round(p, 4), naive=mn, shaped=ms)
    return row


def fmt(m):
    if not m:
        return '-'
    mod = m.get('mod_dbc')
    return f"{m['pp_db']:>6.2f} {mod if mod is not None else float('nan'):>7.1f}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('paths', nargs='+')
    ap.add_argument('--bank', required=True)
    ap.add_argument('--f0', default=None)
    ap.add_argument('--notes', default='0,6,12,18,24')
    ap.add_argument('--len-ms', type=float, default=None)
    ap.add_argument('--fade-ms', type=float, default=None)
    ap.add_argument('--len-sweep', default=None, help='逗号分隔的 T_file 列表 ms（单文件模式）')
    ap.add_argument('--out', default='verify.json')
    args = ap.parse_args()

    bank = json.load(open(args.bank, encoding='utf-8-sig'))
    f0map = {}
    if args.f0 and os.path.exists(args.f0):
        for r in json.load(open(args.f0, encoding='utf-8-sig')):
            if r.get('ok'):
                f0map[os.path.splitext(os.path.basename(r['file']))[0]] = r['f0']
    notes = [int(t) for t in args.notes.split(',')]

    files = []
    for pth in args.paths:
        if os.path.isdir(pth):
            files += [os.path.join(pth, f) for f in sorted(os.listdir(pth)) if f.lower().endswith('.ogg')]
        else:
            files.append(pth)

    allrows = []
    for f in files:
        name = os.path.splitext(os.path.basename(f))[0]
        reg = bank.get(name, 'F#3')
        d_len, d_fi, d_fo, d_flat = params_for(reg)
        f0f = f0map.get(name)
        x, R = load_mono(f)
        # CLI 覆盖
        len_ms = args.len_ms
        fi = fo = args.fade_ms
        if fi is None:
            fi = d_fi
        if fo is None:
            fo = d_fo
        if args.len_sweep:
            lens = [float(t) for t in args.len_sweep.split(',')]
            sweep = []
            for L in lens:
                row = run_one(name, x, R, reg, f0f, notes, L, fi, fo, d_flat)
                # worst-case 20Hz（shaped）
                mods = [row['notes'][str(n)]['shaped']['mod_dbc'] for n in notes
                        if row['notes'][str(n)]['shaped'] and row['notes'][str(n)]['shaped'].get('mod_dbc') is not None]
                pps = [row['notes'][str(n)]['shaped']['pp_db'] for n in notes if row['notes'][str(n)]['shaped']]
                row['worst_mod'] = max(mods) if mods else None
                row['worst_pp'] = max(pps) if pps else None
                sweep.append(row)
            print(f'== {name} ({reg}) 长度扫描')
            print(f'   {"T_file":>7} | ' + ' '.join(f'{n:>13}' for n in notes) + f' {"worst20Hz":>10} {"worstPP":>8}')
            for row in sweep:
                line = ' '.join(fmt(row['notes'][str(n)]['shaped']) for n in notes)
                wm = f"{row['worst_mod']:.1f}" if row['worst_mod'] is not None else '-'
                print(f'   {row["len_ms"]:>6.1f}ms | {line} {wm:>10} {row["worst_pp"]:>8.2f}')
            allrows.append(dict(name=name, sweep=sweep))
        else:
            row = run_one(name, x, R, reg, f0f, notes,
                          len_ms if len_ms is not None else d_len, fi, fo, d_flat)
            allrows.append(row)
            print(f'== {name} ({reg}, T_file={row["len_ms"]}ms, f0={f0f})')
            print(f'   {"note":>4} {"p":>6} | {"naive(pp/20Hz)":>15} | {"shaped(pp/20Hz)":>15} | perperiod n/s')
            for n in notes:
                d = row['notes'][str(n)]
                pn, ps = d['naive'], d['shaped']
                ppn = pn['perperiod_db'] if pn and 'perperiod_db' in pn else ''
                pps = ps['perperiod_db'] if ps and 'perperiod_db' in ps else ''
                print(f'   {n:>4} {d["p"]:>6.3f} | {fmt(pn):>15} | {fmt(ps):>15} | {ppn!s:>6}/{pps!s:>6}')
    json.dump(allrows, open(args.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f'\nJSON -> {args.out}')


if __name__ == '__main__':
    main()
