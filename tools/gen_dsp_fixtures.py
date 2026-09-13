# -*- coding: utf-8 -*-
"""
gen_dsp_fixtures.py —— M2a Java DSP 交叉核对夹具生成（一次性 + 可重跑）

为 16 件原版音符样本生成测试夹具（守 Hy4 #12：Java 移植必须与 Python 管线逐样本对上）：
  notesoundopt/src/test/resources/dsp/
    <name>.wav      输入夹具（32-bit float 单声道 WAV，= 解码后 float32 样本）
    <name>.ref      参考输出（raw little-endian float32，l0shape.shape + 0.95 峰值归一）
    fixtures.json   参数表 [{name, rate, len_ms, fade_in_ms, fade_out_ms, flatten}]

定参来源（AGENTS #27 / docs/06 L2b）：verify-sweeps-vanilla/ 的 fine/ext 扫描最终 pick；
fade 值直接读扫描条目的 fade_ms 字段（权威，不重算）。打击乐 = Hy4 5b 规格 48/0/4 不压平。

用法: python gen_dsp_fixtures.py   （工作区根或任意 cwd，路径自定位）
"""
import os, sys, json
import numpy as np
import soundfile as sf

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, 'tools'))
sys.path.insert(0, os.path.join(ROOT, '_sources/loopdetect-run'))
import l0shape

SWEEP = os.path.join(ROOT, '_sources/loopdetect-run/verify-sweeps-vanilla')
VAN = os.path.join(ROOT, '_sources/vanilla-cdn-1.21.11')
OUT = os.path.join(ROOT, 'notesoundopt/src/test/resources/dsp')

# 最终定参 pick：name -> (sweep 文件, len_ms)。fade 从扫描条目读取。
TUNED = {
    'banjo':          ('banjo-fine',          62.5),
    'bassattack':     ('bassattack-fine',     62.5),
    'bell':           ('bell-ext',            150.0),
    'bit':            ('bit-fine',            57.5),
    'cow_bell':       ('cow_bell-fine',       55.0),
    'didgeridoo':     ('didgeridoo-fine',     62.5),
    'flute':          ('flute-fine',          112.5),
    'guitar':         ('guitar-ext',          155.0),
    'harp2':          ('harp2-fine',          62.5),
    'icechime':       ('icechime-fine',       52.5),
    'iron_xylophone': ('iron_xylophone-fine', 65.0),
    'pling':          ('pling-fine',          60.0),
    'xylobone':       ('xylobone-fine',       52.5),
}
PERC = {'bd', 'hat', 'snare'}   # Hy4 5b：48ms + fade-out 4ms、不压平


def load_sweep_point(name, sweep_file, len_ms):
    j = json.load(open(os.path.join(SWEEP, f'{sweep_file}.json'), encoding='utf-8-sig'))
    for e in j[0]['sweep']:
        if abs(e['len_ms'] - len_ms) < 1e-9:
            fi_s, fo_s = e['fade_ms'].split('/')
            return float(fi_s), float(fo_s)
    raise SystemExit(f'{name}: 扫描点 {len_ms}ms 不在 {sweep_file} 中（{[x["len_ms"] for x in j[0]["sweep"]]}）')


def main():
    os.makedirs(OUT, exist_ok=True)
    rows = []
    ogg_paths = {}
    for name in sorted(list(TUNED) + list(PERC)):
        ogg = os.path.join(VAN, f'{name}.ogg')
        ogg_paths[name] = ogg
        x, rate = sf.read(ogg, dtype='float64', always_2d=True)
        x = x.mean(axis=1) if x.shape[1] > 1 else x[:, 0]
        x32 = x.astype(np.float32)          # = Java 读 32F WAV 的输入（float32 → double 计算）

        if name in PERC:
            len_ms, fi, fo, flat = 48.0, 0.0, 4.0, False
        else:
            sweep_file, len_ms = TUNED[name]
            fi, fo = load_sweep_point(name, sweep_file, len_ms)
            flat = True

        out, meta = l0shape.shape(x32.astype(np.float64), rate, len_ms, fi, fo, flat)
        norm = (out / max(meta['peak'], 1e-12) * 0.95).astype(np.float32)

        sf.write(os.path.join(OUT, f'{name}.wav'), x32, rate, subtype='FLOAT')
        norm.tofile(os.path.join(OUT, f'{name}.ref'))
        rows.append(dict(name=name, rate=int(rate), len_ms=len_ms,
                         fade_in_ms=fi, fade_out_ms=fo, flatten=flat, n_out=len(norm)))
        print(f'  {name:<16} rate={rate}  len={len_ms:6.1f}ms  fade={fi}/{fo}ms  flat={int(flat)}  '
              f'N={len(norm)} ({len(norm)/rate*1000:.1f}ms)  peak_in={np.abs(x).max():.4f}')

    with open(os.path.join(OUT, 'fixtures.json'), 'w', encoding='utf-8') as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
    print(f'fixtures -> {OUT}（{len(rows)} 件）')

    # ── L2 循环点参考（loopdetect v5 主流程）──
    # 参数 = 规范 v5 全库（CLI 默认 ncc 0.95、span 0.5，与 result-v5-vanilla12111 一致）；
    # didgeridoo 用其 2026-09-13 定参配置（ncc 0.90 + span 1.0，AGENTS #27 PASS 130.5ms/8.42°）。
    import argparse
    import loopdetect
    ref = {}
    for name in sorted(list(TUNED) + list(PERC)):
        if name == 'didgeridoo':
            ncc_min, span = 0.90, 1.0
        else:
            ncc_min, span = 0.95, 0.5
        args = argparse.Namespace(snr=20.0, ncc_min=ncc_min, xfade=0.005, dynrange=40.0,
                                  total=1.0, render=None, pitch=[0.5, 1.0, 2.0], json=True,
                                  no_subharm=False, fmin=30.0, span_cycles=span)
        r = loopdetect.analyze(ogg_paths[name], args)
        if r.get('ok'):
            sm = 1 if r['f0Source'] == 'fundamental' else int(r['f0Source'].replace('subharmonic', ''))
            ref[name] = dict(span=span, nccMin=ncc_min, a=r['attackEnd'], L=r['loopSamples'], k=r['periods'],
                             f0=r['f0'], subharmMult=sm, ncc=r['ncc'],
                             loopMs=r['loopMs'], rateHz=r['loopRateHz'],
                             loopRippleDb=r['loopRippleDb'], longRippleDb=r['longRippleDb'],
                             modDbc=r['modDbc'], seamRatio=r['seamRatio'],
                             seamErrDeg=r['seamErrDeg'], fadeNcc=r['fadeNcc'],
                             fadeDipDb=r['fadeDipDb'], periodErrDeg=r['periodErrDeg'],
                             xfadeMs=r['fadeTrace'][-1]['xfadeMs'] if r.get('fadeTrace') else None,
                             verdict=r['verdict'])
            print(f'  loop {name:<16} span={span}  a={r["attackEnd"]} L={r["loopSamples"]} '
                  f'k={r["periods"]} f0={r["f0"]}  seam={r["seamErrDeg"]}  '
                  f'lp={r["loopRippleDb"]} lg={r["longRippleDb"]} mod={r["modDbc"]}  {r["verdict"]}')
        else:
            ref[name] = dict(span=span, nccMin=ncc_min, ok=False, reason=r.get('reason'))
            print(f'  loop {name:<16} span={span}  REJECT: {r.get("reason")}')
    with open(os.path.join(OUT, 'loop-ref.json'), 'w', encoding='utf-8') as f:
        json.dump(ref, f, ensure_ascii=False, indent=1)
    print('loop-ref.json -> %d 件' % len(ref))


if __name__ == '__main__':
    main()
