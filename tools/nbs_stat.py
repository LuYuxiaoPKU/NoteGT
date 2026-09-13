#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
nbs_stat.py —— .nbs 乐谱统计：L1 合并 key 可行性（Hy4 桥梁问题 6）

前提（.nbs 格式 [pynbs/OpenNBS v5，已用 pynbs 解析验证]）：
  每个音符 = (tick, layer, instrument, key, velocity, panning, pitch)。
  .nbs 不存 3D 坐标；物理音符盒位置由 (layer, instrument) 唯一决定
  （layer=行、instrument=列的布局是构建器的确定函数）。
  → 「同一音符盒连续 1gt 触发」= 同 (layer, instrument) 且相邻 tick 差 = 1。
  → L1 持续音合并 = 同 (layer, instrument, key) 且 tick 差 = 1。

统计：
  1) 总音符数 / 时长 / 每 tick 音符数分布（1gt 密度）
  2) 同盒 1gt 重触发对（任意 key）——L0 受益面（20Hz 重触发问题的实际发生频率）
  3) L1 可持续音对：同 (layer,instr,key) 且 delta=1；及其 run 长度分布
  4) 分乐器（instrument id）拆解
  5) 多盒拼持续音（同 key 同 tick 相邻 tick 但不同盒 / 同 key delta=1 不同盒）——L1 失效面

用法: python nbs_stat.py <nbs 文件...|目录> [--out nbs_stat.json]
"""
import os, sys, json, argparse
from collections import Counter, defaultdict
import pynbs


def analyze(path):
    f = pynbs.read(path)
    h = f.header
    notes = sorted(f.notes, key=lambda n: (n.tick, n.layer, n.instrument))
    by_id = {}
    for i in range(len(f.instruments)):
        by_id[i] = f.instruments[i].name
    for i in range(len(f.instruments), 16 + len(f.instruments)):
        by_id[i] = f'default{i - len(f.instruments) + 1}'

    n = len(notes)
    res = {
        'file': os.path.basename(path),
        'name': h.song_name or None, 'origin': h.song_origin or None,
        'version': h.version, 'length_ticks': h.song_length, 'layers': h.song_layers,
        'notes': n, 'instruments_used': len(f.instruments) + min(h.default_instruments, 16),
    }
    if n == 0:
        res['error'] = 'empty'
        return res

    # 每 tick 音符数
    per_tick = Counter(x.tick for x in notes)
    res['max_notes_per_tick'] = max(per_tick.values())
    res['ticks_with_notes'] = len(per_tick)

    # 按 (layer, instr) 分组的 tick 序列
    blocks = defaultdict(list)
    for x in notes:
        blocks[(x.layer, x.instrument)].append((x.tick, x.key))
    for v in blocks.values():
        v.sort()

    same_block_1gt = 0          # 同盒 delta=1（任意 key）
    same_block_1gt_samekey = 0  # L1 可持续音对
    samekey_1gt_diffblock = 0   # 同 key delta=1 但不同盒（多盒拼音 → L1 失效）
    any_retrigger = 0          # 同盒任意 delta 再触发对
    delta_dist = Counter()
    l1_runs = Counter()        # L1 run 长度（tick 数，含首）
    instr_pairs = Counter()    # 分乐器 L1 对
    instr_notes = Counter()
    for x in notes:
        instr_notes[x.instrument] += 1

    for (lay, ins), seq in blocks.items():
        for i in range(len(seq) - 1):
            d = seq[i + 1][0] - seq[i][0]
            any_retrigger += 1
            if d == 1:
                same_block_1gt += 1
                instr_pairs[ins] += 1
                if seq[i + 1][1] == seq[i][1]:
                    same_block_1gt_samekey += 1
        # L1 runs：同 key 连续 delta=1
        i = 0
        while i < len(seq):
            j = i
            while j + 1 < len(seq) and seq[j + 1][0] - seq[j][0] == 1 and seq[j + 1][1] == seq[j][1]:
                j += 1
            if j > i:
                l1_runs[j - i + 1] += 1
            i = j + 1

    # 同 key 相邻 tick 但不同盒（全局 tick→(key→blocks)）
    tickmap = defaultdict(lambda: defaultdict(Counter))
    for x in notes:
        tickmap[x.tick][x.key][(x.layer, x.instrument)] += 1
    for t in sorted(tickmap):
        if t + 1 not in tickmap:
            continue
        for key in tickmap[t]:
            if key not in tickmap[t + 1]:
                continue
            b1, b2 = tickmap[t][key], tickmap[t + 1][key]
            total = sum(b1.values()) * sum(b2.values())
            same = sum(min(b1[k], b2[k]) for k in set(b1) & set(b2))
            samekey_1gt_diffblock += total - same

    total_pairs = any_retrigger  # 同盒相邻触发对（含 delta>1）
    res.update(
        same_block_pairs=any_retrigger,
        same_block_1gt=same_block_1gt,
        same_block_1gt_pct=round(100.0 * same_block_1gt / max(any_retrigger, 1), 1),
        l1_pairs=same_block_1gt_samekey,
        l1_pct_of_sameblock1gt=round(100.0 * same_block_1gt_samekey / max(same_block_1gt, 1), 1),
        l1_pct_of_all_notes=round(100.0 * same_block_1gt_samekey / max(n, 1), 1),
        multi_block_sustain_pairs=samekey_1gt_diffblock,
        l1_run_hist={str(k): v for k, v in sorted(l1_runs.items())},
        l1_max_run=max(l1_runs) if l1_runs else 0,
        per_instrument={str(k): dict(notes=v, l1_pairs=instr_pairs.get(k, 0)) for k, v in sorted(instr_notes.items())},
    )
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('paths', nargs='+')
    ap.add_argument('--out', default='nbs_stat.json')
    args = ap.parse_args()
    files = []
    for pth in args.paths:
        if os.path.isdir(pth):
            files += [os.path.join(pth, x) for x in sorted(os.listdir(pth)) if x.lower().endswith('.nbs')]
        else:
            files.append(pth)
    rows = []
    for f in files:
        try:
            r = analyze(f)
        except Exception as e:
            r = {'file': os.path.basename(f), 'error': f'{type(e).__name__}: {e}'}
        rows.append(r)
        if r.get('error'):
            print(f'== {r["file"]}: {r["error"]}')
            continue
        print(f'== {r["file"]}: {r["notes"]} 音符 / {r["length_ticks"]} ticks / {r["layers"]} 层')
        print(f'   同盒重触发对 {r["same_block_pairs"]}，其中 1gt {r["same_block_1gt"]} ({r["same_block_1gt_pct"]}%)')
        print(f'   L1 可持续音对 {r["l1_pairs"]}（占同盒1gt {r["l1_pct_of_sameblock1gt"]}%，占全音符 {r["l1_pct_of_all_notes"]}%），最长 run {r["l1_max_run"]}gt')
        print(f'   多盒拼持续音对 {r["multi_block_sustain_pairs"]}（L1 失效面）')
        top = sorted(r['per_instrument'].items(), key=lambda kv: -kv[1]['l1_pairs'])[:6]
        print('   分乐器 L1 对 top: ' + ', '.join(f'{k}({v["l1_pairs"]})' for k, v in top))
    json.dump(rows, open(args.out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(f'\nJSON -> {args.out}')


if __name__ == '__main__':
    main()
