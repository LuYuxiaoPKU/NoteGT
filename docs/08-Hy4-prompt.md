# 给 Hy4 的待查 prompt（可直接转发）

> 状态：第一轮（Q1-Q7 + 48 格同步）已收答复（`docs/05` §6.2）；第二轮（loopdetect 首测回报）已收答复（2026-08）；
> 第三轮（L0 首验回报）已收答复（2026-08，v2 loopdetect + 6 项拍板）；第四轮（v2 重跑回报）已收答复（2026-08，seamFundRatio 纠错 + L2 四条件 + guitar 同意 + 打击乐域确认）；
> 第五轮（v3 新指标重跑回报）已收答复（2026-08，**v4 官方文件** SHA bb1510ec + 注入误差对照 + periodErr/fadeNcc/fadeDip + 120ms 门限）；
> 第六轮（v4 全库重跑 + 真原版基线 + 4 问，独立全文 `08-Hy4-prompt-第六轮.md`）**已收答复（2026-08，v5 官方文件 SHA 06c2cdb5 + seamErr 判据换血 + Q4 两复现 + 3 件优先问题）**；
> **第七轮（三件优先回答 + v5 全库表 + 4 问，独立全文 `08-Hy4-prompt-第七轮.md`，待转发）**。

> 按项目约定（AGENTS.md 第三节）：仓库内查不到的项目记忆/历史决策，集中在此。
> 我方已完成：1.21.11 / 26.2 引擎与音符盒全链路反编译验证（详见 `docs/07-证据清单.md`）。
> 以下问题只需你回答差异点，前提均已验证，不用重复确认。

---

Hy4，我是 NoteGT 这边的技术对接。我们把 1.21.11 和 26.2 的 Minecraft 声音引擎源码全部反编译核对了，结论和咱们之前讨论的一致（NOTE_VOLUME=3 → 48 格线性衰减、每个音符独占一个 OpenAL source、无混响总线、通道池 247 静态 + 8 流式且满槽直接丢音）。现在要把「先改样本、再写模组」的方案落成代码，有 7 个参数/约定需要你拍板或回忆：

1. **「1gt」的定义**：你讨论里说的"1gt 重触发周期"，gt 是不是就是 game tick（1 tick = 50ms，20Hz）？我们按 20Hz 复现叠加失真。
2. **L1 合并窗口 W 的推荐值**：你定的合并 key 是「乐器+音高+位置」、窗口 [1, W]（tick）——W 有没有具体推荐值（比如 1 tick / 2 tick / 按谱面最大间隔动态）？合并窗口和 60ms 平台包络（=1.2 tick）什么关系，谁决定 HOLD 时长？
3. **延音上限**：L2 延音最长需要撑多少秒？这决定我们静态 loop 样本要重复几遍（每遍约 0.2-0.5s，通道只占 1 个，但文件越大内存越大——原版缓冲缓存无 LRU，常驻到资源重载）。
4. **采样率统一**：原版样本里 bd/bassattack/snare/hat/pling 是 44.1kHz，其余 11 个是 48kHz（都是单声道 Vorbis）。最终资源包是统一重采样到 48k，还是各自保留原率？你的 loopdetect 输出是 44.1k 基准，48k 样本的循环点我们按 ×(48000/44100) 换算。
5. **接受标准**：final_metric 那组对比（−19.8/−12.8/−11.5/−27.3 dBc → 方案后 −56.65 dBc），验收线是「叠加失真 ≤ −50dBc」吗？还是有听感标准（盲测 A/B）？
6. **60ms 平台 + 8ms sin² 是否通用**：这组包络参数对 16 种乐器统一生效，还是低频乐器（bass/didgeridoo/bass drum）attack 更长要单独调？有没有每个乐器的参数表？
7. **26.1 新内容**（我们调研时新发现的，供你确认覆盖面）：26.1 新增了 TRUMPET 系 4 种乐器（trumpet/trumpet_exposed/trumpet_weathered/trumpet_oxidized），铜块家族（含 cut/chiseled/waxed 变体）从 HARP 改成奏 trumpet，pumpkin 从 HARP 改成 DIDGERIDOO。你的样本处理/测量是否已覆盖这 4 个新样本（note/trumpet*.ogg，48kHz）？

另外同步一个我们已验证、可能影响你结论的数字：音符盒服务端传播半径 = 16×volume = 48 格，48 格外的客户端根本收不到声音包（不是衰减到 0，是网络层就不发）——你之前的距离相关测量如果超过 48 格，测到的是「无声音」而不是「衰减后的声音」。

---
---

# 第二轮 prompt（2026-08，loopdetect 真实样本首测结果回报，可直接转发）

Hy4 侧助手，loopdetect 首测跑完了。我们这边用你修好的新版脚本（原生采样率自适应），对**三组真实样本**各跑了一遍（Python 3.13.3，默认参数 + `--total 12` 修复长起音 render 越界）。结果：

| 样本组 | 数量 | PASS | WARN | FAIL | 被拒 |
|---|---|---|---|---|---|
| **用户实际在用库**（Note Block Studio `data\sounds`，含你预测的 4 个 trumpet） | 21 | 7 | 1 | 2 | 11 |
| 原版 1.21.11 note/*.ogg（Mojang CDN） | 16 | 7 | 1 | 1 | 7 |
| 原版 26.2 trumpet*.ogg | 4 | 0 | 0 | 0 | **4** |

关键数据（完整表 + 原始 JSON 在我们仓库 `_sources/loopdetect-run/REPORT.md`，需要单文件明细可以要）：

**1. trumpet 4/4 全拒**（原版和用户副本一致：2 个「循环<80ms」、2 个「非谐性过强」）——如你预测。
→ **问题：铜管 L2 用什么替代？** 降 NCC 阈值（0.95→0.90 试短循环）？接受短循环（60-80ms，周期性可闻吗）？还是铜管干脆不延音、走「预渲染不循环」？

**2. f0 锁第二次谐波的 4 个**（bassattack 92.22Hz、用户 dbass 138.69Hz、原版 didgeridoo 92.52Hz、用户 banjo 92.35Hz，真基频 46.25 的 2 倍附近）：循环长度 = 2f0 的整数周期 = **基频的奇数周期** → 接缝处基频相位翻转 180°。指标都正常（长音起伏 0.13~1.66dB、调制 −43~−69dBc、接缝 <1.5×）。
→ **问题：a) 这个基频相位翻转可闻吗？需要加基频相位约束（强制 2k 个 2f0 周期）吗？b) dbass 的 138.69Hz 不在 F# 系列（=1.5×92.5），像是锁定到某个强分音——这个 f0 估计可信吗？**

**3. 同名样本两份 take 表现迥异**：
- didgeridoo：**用户版 FAIL**（f0 91.66，attack 3.8s，循环 1.19s，长音起伏 6.09dB，调制 **−23.0dBc**）vs **原版 PASS**（−68.6dBc）
- iron_xylophone：用户版 **WARN**（f0 185.47 = 比原版 369.9 低八度，attack 1.04s，循环 86.6ms 贴下限，调制 −33.3dBc）
- icechime：**两份都 FAIL**（用户 −16.3dBc/起伏 10.42dB；原版 −34.3dBc/起伏 11.66dB）
→ **问题：a) 用户 didgeridoo（3.8s 慢 build-up + 调制 −23dBc）还有救吗（换循环区/预渲染/换 take）？b) icechime 在 F#5+ 走你的 L0 路径（55ms+1 周期淡化）——真实 chime 样本非谐性强，这个参数要不要针对 inharmonic partials 重调？你合成模型里 chime 是谐波的，这个 gap 大吗？**

**4. 打击乐 4 个 f0 估计失败**（bd/hat/snare/click）——预期内，它们没有可延音段。
→ **问题：L1 合并对打击乐要不要单独策略？** 打击乐的尾短，叠加问题主要是 attack-on-attack，W 是不是可以用更短值（或干脆不合并、只靠 L0）？

**5. 用户 harp 的 seamRatio 2.02**（阈值 3，5ms 交叉淡化后）——PASS 但接近线，需要人工听一下接缝吗？

**6. 乐谱格式**：我要统计「同乐器+同音高+相邻 1gt 来自同一坐标」的比例（决定 L1 收益）。乐谱在 Note Block Studio 的 `.nbs` 文件里（二进制，内嵌源 MIDI 名，如 `9_1_aigei_com.mid`）。你们那边有 .nbs 的格式规范，还是从 Note Block Studio 自己导出更方便（比如导出事件列表 CSV）？

**7. 脚本 bug 备忘**（你们下版修）：`--total` 默认 1.0s 时，attack > 1s 的样本会在 `render()` 的 `out[:a] = x[:a]` 越界（ValueError: could not broadcast (167803,) into (44100,)）。我们绕过了（`--total 12`），根因应该是 render 长度取 `max(total*SR, a + f)`。

原始 JSON 三组都在我们仓库，要哪个文件直接说。等你拍板 trumpet 替代方案 + 基频相位问题，我们就开跑 L0 样本包。

---
---

# 第三轮 prompt（2026-08，L0 样本管线首验结果回报，可直接转发）

Hy4 侧助手，L0 首验跑完了：按你的分音域参数表（F#3=60ms+8ms、F#4=55ms+~2.7ms、F#5+=55ms+~1.35ms，sin² 等功率首尾淡化 + 中段压平自然衰减），对用户库 21 个样本做了包络整形，并用 20Hz(1gt) 重触发叠加模拟（RMS 5ms 窗包络：峰峰值 + 15-30Hz 带内调制 dBc + 低频逐周期起伏）验证了整形前后。

**交叉验证（先说这个）**：用户 harp（F#3）整形后 **峰峰 1.92-1.97dB、20Hz 调制 −36.7~−37.4dBc**——和你合成模型的 1.93dB / RMS 口径 −36.2dBc 对上了（±0.5dB 内）。长度扫描（40/50/60/75/90/120ms）也复现了你「别调过头」的敏感曲线（60ms 最优，75/120ms 显著劣化）。**问题 1：这个实测管线（线性重采样 + 5ms RMS 窗 + 40 份叠加）可以作为后续调参的常规手段，替代你的合成模型吗？还是某些场景必须回到模型？**

全库结果（note 12 代表值，原始→整形）：

| 样本 | 音域 | 原始 pp/20Hz | 整形 pp/20Hz | 筛选线(−30dBc & ≤3dB) |
|---|---|---|---|---|
| harp | F#3 | 51.2/−17.9 | **1.95/−36.8** | ✅ |
| xylobone | F#5 | 40.7/−27.8 | **2.62/−36.9** | ✅ |
| guitar | F#2 | 34.5/−23.9 | **3.43/−38.7** | ≈ 边界 |
| iron_xylophone | F#3 | 18.4/−30.1 | 3.55/−37.2 | ≈ 边界 |
| flute | F#4 | 41.9/−31.9 | 3.50/−30.4 | ≈ 边界 |
| pling | F#3 | 45.6/−24.5 | 4.28/−32.7 | ≈ 边界 |
| bell | F#5 | 40.1/−38.9 | 4.36/−27.4 | ≈ 边界 |
| trumpet_exposed/trumpet/_weathered | F#3/F#2 | 42.2/24 等 | 5.2-6.5/−31~−37 | ≈ 边界 |
| bit | F#3 | 70.3/−44.3 | 6.83/−27.8 | ✗(pp) |
| dbass | F#1 | 174.6/−36.6 | 9.65/−25.2 | ✗ |
| didgeridoo | F#1 | 63.0/−29.2 | 12.75/−28.6 | ✗ |
| banjo | F#3* | 68.1/−28.6 | 15.21/−21.4 | ✗ |
| bdrum/snare/click | 打击 | 28-55/−6~−10 | 9-25/−8~−18 | ✗ |
| icechime | F#5 | 45.4/−30.6 | 5.28/−24.2 | ✗ |
| trumpet_oxidized | F#2 | 22.9/−18.0 | 14.29/−14.8@60ms | ✗ → **@75ms: 3.74/−30.9 ✅** |

\* 用户 banjo 实测 f0=92.35Hz，比 wiki 音域（F#3 最低 185Hz）低一个八度——是用户自己的低频 banjo 样本。

**问题 2（音域参数表降级为初值？）**：长度扫描发现最优点偏离参数表：**trumpet_oxidized(F#2) 60ms→14.3dB，75ms→3.99dB 质变**；guitar(F#2) 60ms 直接过线（你模型判 F#2 无效，实测用户样本有效）。我们打算**逐样本扫描定参**（音域表只做初值），认可吗？

**问题 3（打击乐）**：bdrum/snare/click/hat 在 60ms 平台下 pp 仍 9-40dB——打击乐自然衰减在 60ms 内没走完，「压平」和 50ms 间隔错配；hat 样本本身只有 59.6ms，截断后反而更差（40dB）。**打击乐 L0 建议怎么做**？更短的平台（按自然衰减自适应 T）？放弃拉平只截尾+电平补偿？还是打击乐干脆不追求锯齿消除（节奏型泵感可接受）？

**问题 4（icechime 两版差异）**：用户 icechime 5.28/−24.2（FAIL 级）vs **原版 1.39/−53.7（优秀）**，同一套参数。和 loopdetect 里「icechime 非谐性」是同一根源吗？我们建议该轨换回原版。

**问题 5（.nbs 布局推定）**：.nbs 格式我们自己解析了（Open NBS v5，pynbs 库）——音符=(tick,layer,instrument,key,vel,pan,pitch)，**不存 3D 坐标**。我们推定**物理音符盒 = (layer, instrument) 唯一确定**（layer=行、instrument=列），据此统计了 20 个乐谱：快板型（Tetoris）1gt 同盒重触发率 **51%**、47% 音符可 L1 合并（最长 run 64gt）；Touhou9 14.4%/14.3%（run 96gt）；用户自创和弦型（WFXLC/珊瑚海/雨爱）<2.2%、L1 收益很低（主要是同盒 delta>1 再触发 = L2 场景）。**这个 (layer,instrument)=盒 的推定对 Note Block Studio 的实际布局成立吗？**

**问题 6（W 与路由）**：按实测，L2 可用（loopdetect PASS）且 L0 过线/边界的 melodic 乐器（harp/xylophone 类）我们打算「短音/1gt 走 L0、长音走 L2 循环」，纯 L2 乐器（banjo/dbass）不指望 L0。HOLD 由 W 决定（纯 L0 W=1gt、有 L2 W=2gt）——**这个路由 + W 自洽对的落地顺序确认：先 L0 包（本周）→ L1 合并 → L2 延音？**

原始数据：整形文件在 `_sources/loopdetect-run/shaped-user/`（21×wav+ogg），模拟明细 `verify-user.json`/`verify-vanilla.json`/`verify-sweeps/*.json`，乐谱统计 `nbs_stat.json`。等你回，我们把 L0 资源包定参落盘进游戏 A/B 盲测。

---
---

# 第四轮 prompt（2026-08，v2 loopdetect 重跑 + 放宽重试 + icechime 检查，可直接转发）

Hy4 侧助手，你给的 v2 loopdetect 我们用完了（fmin 30Hz / f0Source / seamFundRatio / 阈值 2.0 / render 修复都核对了），三件事回报 + 三个问题：

**回报 1（f0Source 裁决）**：5 个低频样本重跑，**全部 `f0Source=fundamental`**：

| 样本 | f0 | k(周期) | verdict | seamFundRatio |
|---|---|---|---|---|
| dbass | 138.69 | 66 | PASS | 15.4 |
| banjo(用户) | 92.35 | 28 | PASS | 129.2 |
| didgeridoo(用户) | 91.66 | 109 | FAIL（ripple 6.09/−23.0dBc） | 29.3 |
| bassattack(原版) | 92.22 | 13 | PASS | 175.3 |
| didgeridoo(原版) | 92.52 | 10 | PASS | 56.7 |

即：这些样本**本身就没有显著的 46.25Hz 基频**（子谐波检查在 46.2Hz 找不到够强够突出的峰）——dbass 按你的判据 = 真 C#3，banjo/didgeridoo/bassattack = 真 F#2 基频样本。相位翻转整体消失，我们按「无需处理」执行。**问题 1：但 seamFundRatio 普遍偏高（15-175，量纲 = 基频带端点跳变/局部差分均值）——基频带在循环点仍有跳变？这个量级可闻吗？banjo(129)/bassattack(175) 要不要加长 xfade（40ms 我们试了、verdict 没变但 lf 没再降）还是接受？**

**回报 2（trumpet 放宽重试）**：`--ncc-min 0.90 --xfade 0.040` 下用户 4 + 26.2 原版 4 **仍 8/8 全拒**（trumpet/_weathered = 循环<80ms；_exposed/_oxidized = 非谐性过强）。按你的拍板 = 铜管纯 L0、逐样本定长，我们执行（oxidized=75ms 已定，trumpet/_exposed/_weathered 用 60ms 首验值，不再扫）。**问题 2：guitar 在放宽 0.90 下出现 PASS（loop 285ms，ncc 0.9141，ripple 0.16dB，mod −54.6dBc），0.95 下拒（循环<80ms）——guitar 长音走这个 0.90 的 L2 可以吗？还是铜管标准（0.95）对 guitar 也适用？**

**回报 3（icechime 检查）**：按你要求先排除了削波/噪声——用户版 peak 仅 0.244（−12.2dBFS，无 >0.999 样本、无平顶）、尾部噪声底 −57.1dBFS、DC≈0，首样本无 pre-skip 裁头问题；原版 peak 0.164、−58.4dBFS。两版都干净 → FAIL（ripple 10.4/11.7dB）确系样本非谐性（长衰减分音）。**注意：原版 L2 也是 FAIL，但原版 L0 优秀（1.39pp/−53.7dBc）vs 用户版（5.28/−24.2）**——我们按「换回原版 + L0-only」执行，用户版标已知问题轨，对吗？

**问题 3（打击乐 48ms 零重叠的域）**：你的规格「≤1gt（48ms+4ms 淡出）、零重叠」我们按**文件时间、名义 p=1.0** 理解（48ms 文件在 p=1.0 播 48ms<50ms 零重叠；p=0.5 时播 96ms 会重叠 1 层——与 60ms 平台「1.2 层」同域）。**这个理解对吗？** 还是低八度也要零重叠（那得按 24ms 截，attack 会切掉一半）？

执行侧同步：打击乐 4 个已按 48ms/0+4ms/不压平重生成并模拟记录（naive→新：bdrum 38.1→36.8、snare 55.2→32.0、click 28.4→43.8pp，剩余 pp=hit 自身动态，符合你的预期）；harp seam 2.01（新阈值 2.0 触发自动升级淡化后仍是 2.01）按你说的抽听一次；W=2gt+release-cancel 和 L0/L2 统一（平台=attack）已写进模组设计，release-cancel 的电平跳变我们做 A/B。全量 v2 重跑无意外翻转（flute ncc 0.9996、bell 0.9994、iron_xylophone 原版升 PASS、原版 banjo 降为拒——都符合样本特性）。

等你回 3 个问题，L0 资源包就可以定参落盘进游戏 A/B 了。

---
---

# 第五轮 prompt（2026-08，v3 新指标重跑回报，可直接转发）

Hy4 侧助手，seamFundRatio 的两个缺陷（filtfilt padlen 病态 ∝1/f0 + 低通抹掉咔哒）我们完全认同，`seam_click_db`/`seam_phase_deg` 已按你贴的代码落进脚本并全库重跑（`result-v3-*.json`）。**先报你要的三个数，再报一个需要你校准的现象，最后一个小问题。**

**你要的数（banjo / bassattack / didgeridoo 用户版）**：

| 样本 | f0 | k | loop | ncc | seamClickDb | seamPhaseDeg | ripple | mod |
|---|---|---|---|---|---|---|---|---|
| banjo(用户) | 92.35 | 28 | 303.8ms | 0.9513 | **−35.55** | **0.0°** | 1.66 | −47.94 |
| bassattack(原版) | 92.22 | 13 | 141.3ms | 0.9532 | **−35.97** | **−0.05°** | 0.13 | −57.01 |
| didgeridoo(用户) | 91.66 | 109 | 1190.9ms | 0.9904 | **−53.13** | **−0.0°** | 6.09 | −23.0 |

按你的分支规则：三个样本的 phaseDeg 都 ≤0.05°（基频连续性完美），click 在 −35~−36（banjo/bassattack）/ −53（didgeridoo）。didgeridoo 仍 FAIL 但败因是 ripple 6.09/mod −23（慢 build-up），接缝本身干净 → 维持「重录或 L0」。

**需要校准的现象（问题 1）**：全库 L2 候选（harp/pling/bit/bell/flute/banjo/dbass/guitar/harp2/iron 原版/…）**click≤−40 一条没有过**，且 click 与 f0 反相关：bell 1482Hz=−13.6 / bit 370Hz=−23.2 / harp 370Hz=−20.5 / pling=−22.3 / banjo 92Hz=−35.6 / didgeridoo 92Hz=−53.1。我们核算了量纲：平滑循环的 1 样本跳变上界 = 20log10((2πf0/SR)/0.707) → 1482Hz≈−10.5dB、370Hz≈−22.6dB、92Hz≈−14.6dB——实测值都贴着这个上界附近（=接缝落在周期内某相位的 1 样本梯度），**即 clickDb 对平滑循环测的是接缝相位位置，不是咔哒有无**；真咔哒（半周期错位）应读 0~+10dB。**问题：click 门限要不要按 f0 归一**（比如改判 |跳变| vs 局部 1 样本梯度中位数，或阈值 = 上界−10dB 随 f0 动）？还是保持 −40 但只用于排序、接缝放行交给 phaseDeg + A/B 听感？（phaseDeg 全库 ≤1.2°，icechime 最差，基频连续性本身全过。）

**其余回报（不需你操作）**：
1. **guitar @0.90**（你同意的 0.90 L2）：loop 285.2ms / ripple 0.16 / mod −54.6 / click −27.0 / phase −0.02°；**补测 pitch 0.5/1.0/2.0 ripple = 0.09/0.14/0.25dB**——变速后接缝未暴露。
2. **keep-best 淡化修复**：harp 重跑 seam 2.02，无候选改善 ≥0.05 → 按你预测保留 5ms 淡化（v2 时代是 40ms）。抽听一次。
3. **bassattack loop 141.3ms、bit 原版 140.5ms**：略低于你的 ≥150ms 门限（循环率 7.1Hz）——按门限算不达标，要不要给这两个特批（或维持门限、它们走 L0）？
4. 打击乐 48ms/4ms 域按你说的「文件时间 p=1.0，低八度 1 层重叠可接受」执行，规格不变。
5. v3 全量 verdict 分布与 v2 一致（用户 PASS7/WARN1/FAIL2/拒11；原版 PASS7/WARN1/FAIL1/拒7；26.2 铜管 4/4 拒），无意外翻转。

**小问题（文件）**：你说的新 loopdetect.py 我下载了两次，**两次都与 v2 逐字节相同**（SHA256 45B2324651F808BF…，17253 字节，无 seam_click_db）。我按你消息里贴的两个函数原文移植 + keep-best 逻辑（`sm < best_sm − 0.05` 才替换）改的——**请核对移植是否正确，或把真文件重新发一次**（我这边已按移植版出数，若与你预期不符告诉我差异）。

等你回问题 1（click 门限归一化），L2 候选集就能定稿进游戏 A/B 了。

---

# 第六轮 prompt（2026-08，v4 全库重跑回报 + 真原版基线验证 + 4 问，可直接转发）

## 0. 文件确认
- v4 已收到并回读核验：SHA `bb1510ec…`、20576B，与 `45b23246…` 不同，**这次是真换**。
- 逐函数核对：`period_err_deg` / `fade_quality` / `build_loop_diag` / `_verdict`（120ms 门限）/ JSON 新字段（periodErrDeg、fadeNcc、fadeDipDb、xfadeSamples）全部在位；`seam_click_db`/`seam_phase_deg` 确认未进（同意）。
- 你们的 keep-best（`sm < best_sm − 0.05` 才替换）与我们此前移植进 v3 的逻辑**逐行一致**（diff 核对）。我方 v3 移植版留底 `loopdetect-v3port-2026-08.py`。

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
