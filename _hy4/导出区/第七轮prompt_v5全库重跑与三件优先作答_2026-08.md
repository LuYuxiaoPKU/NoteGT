# 第七轮 Hy4 Prompt — v5 全库重跑 + 三件优先问题全部作答（2026-08）

> 状态：待转发。v5（SHA `06c2cdb5…`，23602B）已落盘 `_sources/loopdetect-run/loopdetect.py`
> （v4 存档为 `loopdetect-v4-2026-08.py`）。41 文件全库已用 v5 重跑（`result-v5-{user,vanilla12111,vanilla262}.json`）。

第六轮收到，假设确认 + seamErr 判据换血已完整吸收。以下按你列的优先顺序逐件作答，最后附全库 v5 表和新问题。

---

## Q4（v3/v4 差异）——已定位：xfade 路径（keep-best 行为差异）

两个决定性复现都跑了：

1. **`sr` 字段**：guitar 原生采样率 = **44100** → 该文件无 SR 泄漏（44.1k 原身）。
2. **`--xfade 0.040` 复现**：`loopRippleDb = 1.50`、`modDbc = −54.57` —— **与 v3 数字完全一致**；
   默认 5ms xfade：`2.49 / −58.14`。

所以差异确实来自 xfade 路径，且机制比我们俩预想的更具体：**v3 的 keep-best 是"无条件保留最后一个
候选（40ms）"，v4/v5 的 keep-best 是"仅当 seamRatio 改善 ≥0.05 才替换"**。guitar 的 seamRatio=0.84
（<2.0），v4/v5 不升级 → 留 5ms；v3 留 40ms。归一化字段（f0/loopMs/ncc/periods）全同、样本域指标
不同，完全由这一处解释。

**方向与你的 δ=0.1% 预测相反**（你：5ms→0.60、40ms→1.11；实测此样本：5ms→2.49、40ms→1.50）——
真实样本的接缝错位（seamErr 4.88°）被长 xfade 抹平的效果占主导。附带数据：40ms 时 seamErr 4.88°→
**1.73°**、fadeNcc 0.8708→0.9154，但循环率调制 −58.14→−54.57（变差 3.6dB）。两者都 PASS。

**问**：builder 的淡化选择规则要不要加一条「seamErr 改善 ≥2° 时允许升级到长 xfade（代价是循环率调制
略变差）」？还是保持 seamRatio 门槛、以 A/B 裁决？

---

## 优先 ① didgeridoo 0–3.8s 电平曲线 —— 既非 lead-in 也非 swell：是「burst + 长衰减」

50ms 窗 RMS（preprocess 后，vs 峰值）：

```
t=0ms     -6.98 dB   <- 起音 burst（样本最响处）
t=50ms    -4.51 dB
t=100ms   -7.10 dB
t=250ms   -9.32 dB
t=800ms  -13.25 dB   <- 从 100ms 起单调衰减
t=1600ms -16.79 dB
t=2400ms -23.42 dB
t=3200ms -30.73 dB
≥3805ms  -34.61 dB   <- 稳态 drone（loop 区，seam 1.77°/lp 0.29 极干净）
```

即 "attack 3805ms" 的真身 = **起始 breath burst 的 3.6s 衰减尾**（−7→−34.6dB，共 28dB）。
v5 报 FAIL 的是 `modDbc=−23.0`——衰减段进了 12s 度量窗。loop 本身（1190.9ms，k=109）没有任何问题。

按你的两条分支都不完全命中：前几百 ms 不接近噪声底（是 burst 不是 lead-in），也不是"从低往高爬"的
swell（是从高往低衰）。我们渲染了 3 个 12s 试听版（`_sources/loopdetect-run/ab/didgeridoo/`）：

- **didg-A**：保留 burst（attack=x[0:250ms]）+ render 自带 level-match（`lv` 把 drone 抬到 burst
  电平，约 25dB），35ms crossfade 过渡；
- **didg-B**：你建议的无 swell 变体（attack=loop 起点前 200ms + 10ms fade-in，drone 原始电平）；
- **didg-C**：v5 as-is（3.8s attack + loop，对照"不断变响/变响又变暗"的问题音）。

**问**：
1. A/B 哪条是推荐路线？A 的 level-match 后 drone 比原始高 ~25dB（16bit 下峰值仍在 0.9 归一内），
   进游戏经 NOTE_VOLUME=3 后有没有削顶/听感风险？
2. 若 A 可行，builder 里 didgeridoo 的 L2 attack 段 = 250ms burst 还是保留更长（如 500ms）？

---

## 优先 ② dbass lp 7.09 —— 已定位：loop 内的瞬态（ghost pluck），与接缝无关

- loop 区 [62.2, 537.7]ms，k=66，f0=138.69Hz（P=7.20ms）。
- **loop 区 ~85–95ms 处（≈第 4 期）有一个短瞬态**，30ms 包络归一后仍比局部电平高 **+7.09dB**
  （per-period pp 官方 lp=7.09 与逐期复算 7.09 完全吻合）。
- loop 区整体另有 ~4dB 慢衰减（−8.3→−12.4dB / 475ms），这部分被包络归一吸收，不是问题。
- 对照实验：**loop 起点按周期平移 0–15 期，pp 保持 6.07–7.09dB**（瞬态在内容里，起点移动只把它
  挪到窗内另一期，逃不掉）；`--span-cycles 1.0` 找到同一个 loop。
- 结论：录音里的"ghost pluck"（二次击弦/鬼音），以 475ms 周期重复 ≈ **2.1Hz 的 6–7dB 周期起伏**，
  落在低音掩蔽区，但与接缝完全无关（seam 11.1° 是另一件事，WARN_SEAM 档）。

**问**（三选一或给组合）：
1. 接受：2.1Hz/6–7dB 对 bass 可闻性如何？A/B 能否兜住？
2. 换短 L：找不含 85–95ms 的 L（比如从 100ms 后起算一个整数周期的子窗），代价是 loop 率变高（>8.3Hz
   风险）——是否可行、阈值多少？
3. 向用户要更干净的 dbass take（这是用户自录样本）。

---

## 优先 ③ guitar A/B —— 12.8°（你的查表）vs 4.88°（v5 直接量测）矛盾，待用户听

- v5 直接量测：`seamErrDeg = 4.88°`（默认 5ms xfade）→ **PASS**；`seamErrDeg(40ms) = 1.73°`。
- 你第六轮按 fadeNcc 0.87 反查表给 ~12.8° → "倾向不通过"。两个数差 2.6 倍——查表是窗口相关的近似，
  直接量测才是本样本的真值，但**最终以 A/B 为准**这点我们没意见。
- 已渲染 3 版 12s 试听（`_sources/loopdetect-run/ab/guitar/`）：
  - `ngs-gtr-A.wav` = 5ms xfade（v5 默认，seam 4.88°）
  - `ngs-gtr-B.wav` = **硬循环（loop 区零 xfade）**——隔离 seam 可闻性的对照组
  - `ngs-gtr-C.wav` = 40ms xfade（seam 1.73°，mod 变差 3.6dB 的代价版）
- 用户听后回报强制选择结果。
- **问**：在等 A/B 期间，guitar 的路由表先标 PASS（4.88°）还是 WARN 挂起？

---

## v5 全库重跑（41 文件）：11 PASS / 2 WARN_SEAM / 6 FAIL / 22 REJECT

| 文件 | seam° | perr°(advisory) | lp | mod | 裁决 | 变化（vs v4） |
|---|---|---|---|---|---|---|
| user/bit | **4.15** | 65.98 | 0.30 | −60.1 | **PASS** | v4 FAIL（perr 假象消失） |
| user/bell | 1.46 | 19.62 | 0.57 | −95.2 | **PASS** | v4 FAIL（fadeNcc 门取消） |
| user/banjo | 5.10 | 20.97 | 0.59 | −47.9 | **PASS** | v4 FAIL（Q2 候选转正） |
| user/flute | 0.81 | 48.34 | 0.46 | −69.6 | **PASS** | v4 FAIL（f0 假象） |
| user/pling | 5.46 | 1.77 | 0.80 | −62.5 | **PASS** | v4 FAIL（fadeNcc 门取消） |
| user/harp | **11.36** | 94.06 | 1.08 | −66.2 | **WARN_SEAM** | v4 FAIL → A/B |
| user/dbass | **11.10** | 18.62 | **7.09** | −43.2 | **WARN_SEAM** | 见优先 ② |
| user/didgeridoo | 1.77 | 57.89 | 0.29 | **−23.0** | FAIL | 衰减段入窗（见优先 ①） |
| user/icechime | 2.08 | 54.98 | 0.43 | **−16.3** | FAIL | 已知问题轨（#18 不变） |
| user/iron_xylophone | None | 20.72 | 0.37 | −33.3 | FAIL | 86.6ms <120ms 门 |
| van12111/bassattack | **4.82** | 10.58 | 0.55 | −57.0 | **PASS** | v4 边缘 10.58° → 转正 |
| van12111/bell | 1.59 | 2.74 | 1.44 | −61.6 | **PASS** | v4 FAIL（fadeNcc 门取消） |
| van12111/bit | 0.51 | 4.85 | 0.63 | −54.6 | **PASS** | 保持（唯一 v4 PASS） |
| van12111/harp2 | **1.70** | 21.24 | 1.08 | −56.0 | **PASS** | v4 WARN（f0 假象消失） |
| van12111/iron_xylophone | 7.66 | 25.81 | 0.61 | −55.2 | **PASS** | v4 FAIL（f0 假象消失） |
| van12111/pling | 1.89 | 1.54 | 0.81 | −60.8 | **PASS** | v4 FAIL（fadeNcc 门取消） |
| van12111/didgeridoo | 0.25 | 27.54 | 1.02 | −68.6 | FAIL | 108.9ms <120ms 门（不变） |
| van12111/flute | **38.21** | 95.86 | **4.52** | −35.1 | FAIL | **真接缝问题**（你的 17.1° 偏低） |
| van12111/icechime | 4.23 | 110.41 | **4.71** | −34.3 | FAIL | lp 4.71≥4.0 门（seam 其实好） |
| 打击乐 ×6 + cow_bell×2 | — | — | — | — | REJECT | 预期（#5b：L1 off，L2 不适用） |
| trumpet ×8（user4+van262 4） | — | — | — | — | REJECT | 预期（#16：纯 L0 路线） |
| van12111/guitar, van12111/banjo, xylobone×2, snare-user, sdrum-user | — | — | — | — | REJECT | 循环过短/非谐性（不变） |

**查表 vs 直接量测的偏差汇总**（路由表以哪个为准请裁决）：

| 样本 | fadeNcc 反查° | seamErr 直接量测° | 差 |
|---|---|---|---|
| harp-user | 5.5 | 11.36 | 2.1× |
| dbass-user | 6.6 | 11.10 | 1.7× |
| pling-user/van | 10.3 | 5.46 / 1.89 | 0.53× / 0.18× |
| guitar-user | 12.8 | 4.88 | 0.38× |
| flute-van | 17.1 | 38.21 | 2.2× |
| icechime-van | 13.1 | 4.23 | 0.32× |

两个方向都有偏差，查表只适合粗筛；**建议路由表直接用 seamErrDeg，查表退役**。

---

## 小项

1. **`_verdict` 死代码**：`return 'FAIL'`（约 line 459）之后还有一段重复的 L<120ms/lg/md 块
   （line 460–466），不可达，无行为影响——下版顺手删。
2. **NBS harp 问题**（你说"建议问清楚，别默认按 CDN 现行版"）：我们已在问用户游戏内打谱用的
   声音集（NBS Archive 声音集 vs CDN 官方）。答复后单独同步，涉及 6 件旧录音样本
   （harp/bell/flute/guitar/xylobone/icechime）的定参基准切换。
3. **imitate ×6**：按 L1-only/不整形执行，无异议已落。
4. **guitar v5 默认 PASS 但 A/B 未做**——路由表暂标"PASS（待 A/B 确认）"，若你倾向挂起请说。

## 请按优先顺序回

1. dbass ghost pluck 裁决（接受 2.1Hz 起伏 / 换短 L / 要新 take）；
2. didgeridoo 路线（A burst+level-match vs B 安静淡入）+ level-match 的进游戏安全性；
3. guitar 4.88° 先 PASS 还是挂起（A/B 结果随后）；
4. builder 淡化选择规则（seamErr 改善 ≥2° 升级长 xfade 是否入规）。

全部原始数据：`result-v5-{user,vanilla12111,vanilla262}.json`、`q4-guitar-v5-{default,xf40}.json`、
`diag-hy4-r6.out`（电平曲线全文）、`ab/guitar/`、`ab/didgeridoo/`（试听 WAV）。
