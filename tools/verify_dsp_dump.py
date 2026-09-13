#!/usr/bin/env python3
"""M2a ③ 运行时链路离线核对：游戏内 DEV_DUMP 转储 vs 夹具参考。

输入：
  _sources/dsp-dump/<name>-original.f32.wav  游戏内 MC 解码器 16bit→float32
  _sources/dsp-dump/<name>-shaped.f32.wav    Java L0Shaper 输出（16bit 量化前）
  notesoundopt/src/test/resources/dsp/<name>.wav   夹具输入（soundfile 解码 float32）
  notesoundopt/src/test/resources/dsp/<name>.ref   Python L0Shaper 参考输出 float32

判定：
  ① original(游戏) vs original(夹具)：两个 Vorbis 解码器（jorbis vs libav）的低位差，
     预期 maxDiff < 2e-3（报告值，不设硬门槛——解码器不同属预期）。
  ② shaped(游戏) vs ref(夹具)：同一数学、同一输入路径（仅输入低位差），
     预期 maxDiff < 5e-3；超了说明 Java 运行时链路与 JUnit 交叉核对链不一致。
  ③ shaped 长度 = 平台规格（lenMs*rate，打击乐 48ms）。
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
DUMP = ROOT / "_sources" / "dsp-dump"
FIX = ROOT / "notesoundopt" / "src" / "test" / "resources" / "dsp"

# 平台规格（ms）—— 与 tools/instruments-vanilla.json / NoteInstruments.java 同源
LEN_MS = {
    "banjo": 62.5, "bassattack": 62.5, "bd": 48.0, "bell": 150.0, "bit": 57.5,
    "cow_bell": 55.0, "didgeridoo": 62.5, "flute": 112.5, "guitar": 155.0,
    "harp2": 62.5, "hat": 48.0, "icechime": 52.5, "iron_xylophone": 65.0,
    "pling": 60.0, "snare": 48.0, "xylobone": 52.5,
}

# 游戏事件名 → note 文件名（sounds.json 映射 [1.21.11 已验证]）
EVENT2FILE = {
    "banjo": "banjo", "bd": "bd", "bassattack": "bassattack", "bell": "bell",
    "bit": "bit", "icechime": "icechime", "cow_bell": "cow_bell",
    "didgeridoo": "didgeridoo", "flute": "flute", "guitar": "guitar",
    "harp2": "harp2", "hat": "hat", "iron_xylophone": "iron_xylophone",
    "pling": "pling", "snare": "snare", "xylobone": "xylobone",
}


def main() -> int:
    if not DUMP.exists():
        print(f"转储目录不存在: {DUMP}", file=sys.stderr)
        return 1
    names = sorted({p.name.split("-original.f32.wav")[0] for p in DUMP.glob("*-original.f32.wav")})
    print(f"转储乐器: {len(names)} 件\n")
    bad = 0
    for name in names:
        fname = EVENT2FILE.get(name, name)
        orig_game, sr = sf.read(DUMP / f"{name}-original.f32.wav", dtype="float32")
        shaped_game, sr2 = sf.read(DUMP / f"{name}-shaped.f32.wav", dtype="float32")
        orig_fix, _ = sf.read(FIX / f"{fname}.wav", dtype="float32")
        ref, _ = sf.read(FIX / f"{fname}.ref", dtype="float32")

        d_input = np.max(np.abs(orig_game[: len(orig_fix)] - orig_fix[: len(orig_game)]))
        d_shaped = np.max(np.abs(shaped_game - ref[: len(shaped_game)])) if len(ref) >= len(shaped_game) else np.inf
        expect_len = int(LEN_MS[fname] / 1000.0 * sr2)
        len_ok = abs(len(shaped_game) - expect_len) <= 2

        status = []
        status.append("OK " if d_shaped < 5e-3 else "BAD")
        if d_shaped >= 5e-3:
            bad += 1
        print(f"  {name:<16} sr={sr2} 输入差(解码器)={d_input:.2e}  "
              f"整形差={d_shaped:.2e} (门槛5e-3)  "
              f"len={len(shaped_game)}/{expect_len} {'OK' if len_ok else 'BAD'}")
        if not len_ok:
            bad += 1
    print(f"\n{'PASS' if bad == 0 else f'FAIL ({bad} 项)'}")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
