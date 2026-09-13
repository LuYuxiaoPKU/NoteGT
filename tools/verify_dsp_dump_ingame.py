#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M2a ③ 游戏内端到端核对：DEV_DUMP 转储自校验（不依赖夹具/资源包）。

对每件乐器的 <name>-original.f32.wav（游戏内 MC 解码器 16bit→float32，stereo 已降混）
重跑 Python l0shape（与 NoteInstruments.java 同源参数），对比 <name>-shaped.f32.wav
（Java L0Shaper 输出，16bit 量化前 float32）。

判定：
  ① max|diff| < 5e-3（与 verify_dsp_dump.py 同门槛）——验证 解码→降混→整形 整条 Java 链路；
  ② shaped 长度 = int(round(len_ms*sr/1000))（≤ 原始长度时截尾）±2。
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "_sources" / "dsp-dump"
sys.path.insert(0, str(ROOT / "tools"))
from l0shape import shape  # noqa: E402

# 与 NoteInstruments.java 同源（M2a 定参，AGENTS #27/#30）
# name: (len_ms, fade_in_ms, fade_out_ms, flatten)
PARAMS = {
    "banjo": (62.5, 8.0, 8.0, True),
    "bassattack": (62.5, 8.0, 8.0, True),
    "bd": (48.0, 0.0, 4.0, False),
    "bell": (150.0, 1.4, 1.4, True),
    "bit": (57.5, 8.0, 8.0, True),
    "cow_bell": (55.0, 2.7, 2.7, True),
    "didgeridoo": (62.5, 8.0, 8.0, True),
    "flute": (112.5, 2.7, 2.7, True),
    "guitar": (155.0, 8.0, 8.0, True),
    "harp2": (62.5, 8.0, 8.0, True),
    "hat": (48.0, 0.0, 4.0, False),
    "icechime": (52.5, 1.4, 1.4, True),
    "iron_xylophone": (65.0, 8.0, 8.0, True),
    "pling": (60.0, 8.0, 8.0, True),
    "snare": (48.0, 0.0, 4.0, False),
    "xylobone": (52.5, 1.4, 1.4, True),
}


def main() -> int:
    if not DUMP.exists():
        print(f"转储目录不存在: {DUMP}", file=sys.stderr)
        return 1
    names = sorted({p.name.split("-original.f32.wav")[0] for p in DUMP.glob("*-original.f32.wav")})
    print(f"转储乐器: {len(names)} 件（端到端: 游戏内 original → Python l0shape vs Java shaped）\n")
    bad = 0
    for name in names:
        p = PARAMS.get(name)
        if p is None:
            print(f"  {name:<16} 跳过（无参数）")
            continue
        len_ms, fi, fo, flat = p
        orig, sr = sf.read(DUMP / f"{name}-original.f32.wav", dtype="float32")
        shaped_game, sr2 = sf.read(DUMP / f"{name}-shaped.f32.wav", dtype="float32")
        shaped_ref, _ = shape(orig, sr, len_ms, fi, fo, flatten=flat)
        # Java L0Shaper.shape() 内部即 0.95 峰值归一（契约与 Python 管线写文件行为一致，
        # l0shape.py main() 第 156 行同一式）——参考侧补同一归一
        pk = float(np.max(np.abs(shaped_ref)))
        shaped_ref = shaped_ref / max(pk, 1e-12) * 0.95
        n = min(len(shaped_game), len(shaped_ref))
        a = shaped_game[:n].astype(np.float64)
        b = shaped_ref[:n].astype(np.float64)
        both_nan = np.isnan(a) & np.isnan(b)
        finite = np.isfinite(a) & np.isfinite(b)
        d = float(np.max(np.abs(a[finite] - b[finite]))) if finite.any() else 0.0
        if (np.isnan(a) | np.isnan(b)).any() and not (both_nan | finite).all():
            d = float("inf")  # 单侧 NaN 视为不一致
        expect_len = min(int(round(len_ms / 1000.0 * sr2)), len(orig))
        len_ok = abs(len(shaped_game) - expect_len) <= 2
        ok = d < 5e-3 and len_ok
        if not ok:
            bad += 1
        print(f"  {name:<16} sr={sr2:<6} maxDiff={d:.2e} (门槛5e-3)  "
              f"len={len(shaped_game)}/{expect_len} {'OK' if len_ok else 'BAD'}  "
              f"{'PASS' if ok else 'FAIL'}")
    print(f"\n{'PASS' if bad == 0 else f'FAIL ({bad} 项)'}")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
