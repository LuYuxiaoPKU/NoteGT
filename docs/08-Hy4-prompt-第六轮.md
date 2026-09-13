# 第六轮 prompt（2026-08，v4 官方版全库重跑回报 + 真原版基线验证 + 4 问）

## 0. 文件确认

- v4 已收到并回读核验：SHA `bb1510ec…`、20576B，与 `45b23246…` 不同，**这次是真换**。
- 逐函数核对：`period_err_deg` / `fade_quality` / `build_loop_diag` / `_verdict`（120ms 门限）/ JSON 新字段（periodErrDeg、fadeNcc、fadeDipDb、xfadeSamples）全部在位；`seam_click_db`/`seam_phase_deg` 确认未进（同意）。
- 你们的 keep-best（`sm < best_sm − 0.05` 才替换）与我们此前移植进 v3 的逻辑**逐行一致**（diff 核对）。我方 v3 移植版留底为 `loopdetect-v3port-2026-08.py`。

## 1. 你要的三件重跑（v4，全库 41 文件，`result-v4-*.json`）

### 1.1 banjo / bassattack / didgeridoo（你的 Q1）

| 样本 | loop | periodErrDeg | fadeNcc | fadeDipDb | lp / lg / mod |
|---|---|---|---|---|---|
| banjo（用户） | 303.8ms | 20.97° | 0.9813 | −0.04 | 0.59 / 1.66 / −47.9 |
| bassattack（原版，即 vanilla bass） | 141.3ms | **10.58°** | 0.9795 | −0.04 | 0.55 / 0.13 / −57.0 |
| dbass（用户，同上乐器） | 475.5ms | 18.62° | 0.9564 | −0.09 | **7.09** / 0.67 / −43.2 |
| didgeridoo（用户） | 1190.9ms | 57.89° | 0.9917 | −0.02 | 0.29 / **6.09** / −23.0 |

bassattack 超 10° 门限仅 0.58°，其余指标全优 → near-miss。

### 1.2 didgeridoo ripple 6.09：50ms 窗复测（你的 Q2）

**既不是音色固有噪声，也不是 20Hz 泵感——是度量窗被 attack 污染：**

- 用户版 didgeridoo 的 attack **a = 3805ms**（慢起音 swell）。官方 metrics 的度量窗（render 前 ~0.8s）**整段落在 attack 爬坡内** → lg 6.09 / mod −23 量的是 attack，不是 loop。
- **稳态区（attack 之后）实测：10 周期窗 pp = 0.11dB、50ms 窗 pp = 0.31dB、mod@0.84Hz = −77.1dBc、后 1/3 vs 前 1/3 = −0.01dB（无趋势）→ loop 本身极干净。**
- 真实代价 = 音符盒触发后 3.8s 才到满电平（L2 长音 = 3.8s attack + loop）。

### 1.3 guitar / bassattack / bit 的 fadeNcc 复测（你的 Q3）

- **guitar@0.90：fadeNcc = 0.8708（低于 0.95 门限）→ 第四轮"同意走 L2"现被 fade 门限卡住**（perr 7.55° / dip −0.29 / lg 0.14 / mod −58.1 均好，lp 2.49）。
- bassattack（原版）：0.9795（过）；bit（用户）：0.9761；bit（原版）：0.986（过）。

## 2. v4 全库总貌：41 文件中仅 1 个 PASS + 系统性矛盾模式

- **唯一 PASS = 原版 bit**（perr 4.85° / fadeNcc 0.986 / dip −0.03 / loop 140.5ms / lp 0.63 / mod −54.6）——恰好被 120ms 门限救回（v3 的 150ms 门限下它是 140.5ms 差 10ms 的 near-miss）。
- REJECT 17 个（打击乐/非谐/循环<80ms）与 v3 一致，无翻转。
- **此前所有 PASS/WARN 的旋律乐器在 v4 下全部 FAIL**，败因集中在新判据，且呈两种系统性模式：
  - **长 loop（≥0.75s，≥280 周期）→ perr 巨大但 fadeNcc 很好**：harp-user 94.06°/0.9686、bit-user 65.98°/0.9761、icechime-vanilla 110.41°/0.8886、flute-vanilla 95.86°/0.7917、harp2-vanilla 21.24°/0.9895、iron_xylophone-vanilla 25.81°/0.9720
  - **短 loop（~0.27s，~101 周期）→ perr 极小但 fadeNcc 略低**：pling-user 1.77°/0.9119、pling-vanilla 1.54°/0.9138
- 我们的假设（待你裁决）：perr = (f0·L/SR mod 1)·360° 依赖 f0 估计精度，误差放大 = δf0·L·360°。按 δf0 = perr/360/L 反推：harp-user ≈0.39Hz（0.1%）、bit-user ≈0.20Hz（0.05%）、pling-user ≈0.02Hz（0.005%）——**长 loop 的高 perr 疑似 f0 估计误差放大，而非循环段真偏离整数周期**；fadeNcc/fadeDip 才是淡化混合质量的直接量测。

## 3. 新完成：真原版基线验证（供你知悉）

- 我们的 vanilla 测试 bank 与 Mojang CDN 1.21.11/26.2 官方文件 **SHA1 逐字节一致**（16+4）→ 此前所有"原版基线"数字有效。
- **1.21.11 与 26.2 的 16 个经典声音逐字节相同**（26.x 未重录）；note 事件数：1.21.11 已有 **22**（16 经典 + 6 imitate 拟态，imitate 非 26.x 新增），26.1 起 +4 铜管 = **26**（拟态：creeper/ender_dragon/piglin/skeleton/wither_skeleton/zombie）。拟态是一次性怪声，我们默认按 L1-only/不整形处理，有异议请指出。
- NBS Studio（用户打谱工具）命名映射（NCC 全矩阵确认）：bdrum=basedrum(note/bd)、dbass=bass(note/bassattack)、click=hat、sdrum=snare、xylobone=xylophone；**NBS 的 harp = 1.9 前遗留旧 harp.ogg（NCC 1.0000，44.1k），不是现行 harp2**；bell/flute/guitar/xylobone/icechime 五件是与现行官方不同的旧录音（NCC 0.04~0.79）。我们默认预设以 CDN 现行官方为基准。

## 4. 待你回的 4 件事

1. **Q1（判据权重）**：长 loop 高 perr + 高 fadeNcc 的样本（harp/bit/iron_xylophone 等），你认为应该 FAIL（周期漂移真实存在）还是 perr 让位于 fadeNcc/fadeDip（直接量测）？若前者，请在**长 loop（≥0.75s、≥280 周期）**上补一组注入误差对照，验证 perr 在该长度尺度上仍能单调分离（你上一轮注入测试的 loop 若较短，放大效应结论可能不同）。若 perr 确实受 f0 精度限制，是否改为"按 L 归一"（perr/360/L = 等效 f0 误差 Hz）或直接降为 advisory？
2. **Q2（near-miss 集裁决）**：bassattack-vanilla（perr 10.58°，超 0.58°，其余全优）、pling-user（fadeNcc 0.9119 / perr 1.77°）、pling-vanilla（0.9138 / 1.54°）、guitar@0.90（fadeNcc 0.8708 / perr 7.55°）——哪些进 L2 候选、哪些 A/B 听定、哪些直接弃？（我们 A/B 排期里这四个都在"可试听"名单。）
3. **Q3（didgeridoo 3.8s attack）**：loop 本身干净（稳态 0.11dB/−77dBc），代价是 3.8s 慢起音。接受 L2（长音 3.8s 到满电平）？还是取稳态段硬切做 attack（可能有 click）？还是弃 L2 只留 L0/L1？
4. **Q4（QA）**：guitar@0.90 在 v3 移植版与你的 v4 下**找到的 loop 完全相同**（f0 368.2456 / 285.19ms / NCC 0.9141 / 105 周期 / attack 19.59ms 全一致），但 loopRippleDb 1.5→2.49、modDbc −54.57→−58.14，且 v4 `xfadeSamples=220`（5ms，无升级）。keep-best 代码我们 diff 过逐行一致——这个差异是否来自 v3 那版实际走了 xfade 升级而 v4 没走？请核对你们 v4 跑这个文件时的 seam sm 轨迹。
