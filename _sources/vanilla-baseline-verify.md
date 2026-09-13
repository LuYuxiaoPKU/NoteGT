# 原版声音基线验证 + NBS 命名映射（2026-08）

> 触发：用户指出「NBS Studio 的声音命名可能和原版不一样，但音频是对的；原版音频在
> `C:\01_Self_Software\NBSstudio\Minecraft Note Block Studio\Data\Sounds\Archive`；
> 我们当前测试数据可能不是原版而是改版」。本文用 Mojang CDN 官方哈希 + 音频 NCC 双重验证，给出结论。

## 1. 权威基准：Mojang CDN 官方文件（SHA1 校验）

`dl-vanilla-note.ps1` 从 piston-meta → assetIndex → resources.download.minecraft.net 拉取官方 ogg，
并与本地 bank 做 SHA1 比对。

### 1.1 1.21.11（16 个经典声音，无铜管）
**我们的 `vanilla-12111/` bank 与官方逐字节一致（16/16 `SAME-AS-OFFICIAL`）**。
官方文件位于 `assets/minecraft/sounds/note/`（注意：是 `sounds/note/`，不是 `sounds/block/note/`）。

| 事件名 | 官方文件名 | 官方 SHA1(前10) | 本地 vbank |
|---|---|---|---|
| block.note_block.banjo | note/banjo | 6a859c09d4 | ✅ 一致 |
| block.note_block.basedrum | note/bd | 11fb23958c | ✅ 一致 |
| block.note_block.bass | note/bassattack | 4211ca09a1 | ✅ 一致 |
| block.note_block.bell | note/bell | a1e833dec6 | ✅ 一致 |
| block.note_block.bit | note/bit | 496a1bcb51 | ✅ 一致 |
| block.note_block.chime | note/icechime | 2368ded1c1 | ✅ 一致 |
| block.note_block.cow_bell | note/cow_bell | d6d79daa62 | ✅ 一致 |
| block.note_block.didgeridoo | note/didgeridoo | ed0556eeed | ✅ 一致 |
| block.note_block.flute | note/flute | 0445f08832 | ✅ 一致 |
| block.note_block.guitar | note/guitar | 4c237fdb2e | ✅ 一致 |
| block.note_block.harp | note/harp2 | 4a2b2aca57 | ✅ 一致 |
| block.note_block.hat | note/hat | db3b85662c | ✅ 一致 |
| block.note_block.iron_xylophone | note/iron_xylophone | f0c022a51c | ✅ 一致 |
| block.note_block.pling | note/pling | 774ae41e86 | ✅ 一致 |
| block.note_block.snare | note/snare | 2db5799d39 | ✅ 一致 |
| block.note_block.xylophone | note/xylobone | b97cfa83f2 | ✅ 一致 |

> 注：assetIndex 里另有遗留 `note/bass.ogg`(e28d844995)、`note/harp.ogg`(46244605b8)——
> 是 1.9 之前的旧版录音，现代版已改用 `bassattack` / `harp2`，仅作兼容保留。

### 1.2 26.2（16 经典 + 4 铜管；imitate 拟态早已存在）
**16 个经典声音与 1.21.11 逐字节完全一致**（Mojang 未在 26.x 重录经典音）；
新增 4 个铜管（我们的 `vanilla-262-trumpets/` 与官方 4/4 一致）。
6 个 `imitate.*` 事件（creeper/ender_dragon/piglin/skeleton/wither_skeleton/zombie）**1.21.11 已有**（sounds.json 实查，非 26.x 新增）。

**结论：[1.21.11/26.2 已验证] note 事件数 1.21.11 = 22（16 经典 + 6 拟态），26.1 起 +4 铜管 = 26。**

## 2. NBS Archive 命名映射（音频 NCC 验证，非按文件名猜）

NBS Studio 的文件名 ≠ 原版文件名。用全矩阵 NCC（`arc_off_matrix.py`）把 Archive 每个文件
对齐到官方文件，top-1 匹配即映射。

| NBS 名 | 原版事件 | 原版文件 | NCC | 判定 |
|---|---|---|---|---|
| banjo | banjo | note/banjo | 0.9990 | 同音频(重编码) |
| **bdrum** | **basedrum** | **note/bd** | 0.9998 | 同音频 |
| bell | bell | note/bell | 0.5878 | **不同录音** |
| bit | bit | note/bit | 0.9994 | 同音频 |
| **click** | **hat** | **note/hat** | 0.9904 | 同音频 |
| cow_bell | cow_bell | note/cow_bell | 0.9995 | 同音频 |
| **dbass** | **bass** | **note/bassattack** | 0.9987 | 同音频 |
| didgeridoo | didgeridoo | note/didgeridoo | 0.9995 | 同音频 |
| flute | flute | note/flute | 0.2978 | **不同录音** |
| guitar | guitar | note/guitar | 0.7356 | **不同录音** |
| **harp** | **harp** | **note/harp(遗留旧版,44.1k)** | 1.0000 | **= 1.9 前旧 harp，非 harp2** |
| icechime | chime | note/icechime | 0.0389 | **不同录音** |
| iron_xylophone | iron_xylophone | note/iron_xylophone | 0.9993 | 同音频 |
| pling | pling | note/pling | 0.9992 | 同音频 |
| **sdrum** | **snare** | **note/snare** | 0.9771 | 同音频 |
| trumpet ×4 | trumpet ×4 | note/trumpet* | 1.0000 | 同音频(=26.2) |
| **xylobone** | **xylophone** | **note/xylobone** | 0.7944 | **不同录音** |

**命名映射（NBS → 原版事件）**：
`bdrum→basedrum`、`dbass→bass`、`click→hat`、`sdrum→snare`、`xylobone→xylophone`、`harp→harp`、其余同名。

### 2.1 关键发现：Archive 不是纯「当前原版」
- **15/20 与当前官方音频一致**（仅重编码，字节不同但 NCC≥0.98）。
- **5/20 是不同（更旧）录音**：bell / flute / guitar / xylobone / icechime（NCC 0.04~0.79）。
- **harp 特例**：Archive 的 harp 与**遗留旧 `harp.ogg`（1.9 前，44.1k）NCC=1.0000**，而非现代 `harp2`（48k）。
  → NBS Studio 的 harp 用的是 1.9 之前的旧 harp 采样。
- 铜管 ×4 与 26.2 官方完全一致（NCC=1.0000）→ NBS 已同步 26.x 铜管，但经典音仍是旧集。

**推论**：NBS Archive 的「经典 16 音」是**混合集**（多数=当前原版、少数=1.9 前旧版），不是单一版本的原版。
→ **默认预设的「原版」应以 Mojang CDN 当前官方文件为准**（= 我们的 vanilla bank），而非 NBS Archive。
→ 若用户游戏里实际加载 NBS 这套声音作资源包，则 bell/flute/guitar/xylobone/icechime/harp 这 6 个
  乐器需用 NBS 版样本单独定参（它们与游戏内原版不同）。

## 3. 对我们既有的影响

1. **「原版基线」成立**：我们所有 `vanilla-*` 测试数据 = 真原版（字节验证），之前「原版 vs 用户」对比有效。
   用户担心的「测试数据是改版」**不成立于 vanilla bank**（成立的是 `user-bank/` = 用户自定义音色）。
2. **icechime「换回原版 + L0-only」决策**（Hy4 #18）仍成立——游戏内原版 icechime（=CDN）L0 优秀。
   但注意：若用户实际用 NBS 版 icechime（旧录音），需对 NBS 版样本重测。
3. **默认预设**：以 CDN 当前原版为基准（16 经典 + 4 铜管）；6 拟态（1.21.11 已有）默认 L1-only/直通。
4. **didgeridoo(用户) ripple 6.09 的真因**（50ms 窗复测，`didg_50ms_test2.py`）：
   attack=**3805ms** 慢起音，官方 metrics 度量窗全落在 attack 爬坡内 → 6.09dB 量的是 attack 不是 loop；
   **稳态区 loop 极干净**（10 周期窗 pp=0.11dB / 50ms 窗 pp=0.31dB / mod@0.84Hz=−77dBc / 无趋势）。
   → didgeridoo 的 L2 问题不是 loop 不稳，而是 **3.8s 起音太长**（音符盒触发后 3.8s 才到满电平）。

## 4. 复现
- `dl-vanilla-note.ps1 -Id 1.21.11 -OutDir … -BankDir …`（SHA1 校验 + 下载官方 ogg）
- `arc_off_ncc.py` / `arc_off_matrix.py`（NCC 命名映射）
- `didg_50ms_test2.py`（didgeridoo 50ms 窗复测）
- 官方 ogg 已落地：`_sources/vanilla-cdn-1.21.11/`、`_sources/vanilla-cdn-26.2/`
