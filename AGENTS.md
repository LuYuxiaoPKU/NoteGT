# NoteGT — 项目约定与编码精神

> 本项目：**Minecraft 音符盒发声优化模组**（Fabric，目标 1.21.11 及以上至 26.x）。
> 本文件是项目的「宪法」：编码精神 + 工作约定 + 目录约定。任何代码/文档改动前先读本文件。

## 一、编码精神：八荣八耻（源自 Claude Code 全局行为准则）

每次工具调用和代码决策都必须对照以下 8 条原则自检。

1. **以瞎猜接口为耻，以认真查询为荣** — 不猜接口/API/函数签名，调用前查文档或源码确认参数和返回值。不相信训练数据中的记忆——相信当前代码和文档。
2. **以模糊执行为耻，以寻求确认为荣** — 不糊里糊涂干活，动手前先确认用户意图、需求边界。不确定时优先询问。
3. **以臆想业务为耻，以人类确认为荣** — 不臆想业务逻辑，先跟人类对齐需求并留痕（在对话或文件中可追溯）。
4. **以创造接口为耻，以复用现有为荣** — 不造新接口/新函数/新抽象，先搜索代码库中已有的实现。仅当确不能满足需求时才新增。
5. **以跳过验证为耻，以主动测试为荣** — 修改代码后主动跑测试或验证行为是否符合预期。
6. **以破坏架构为耻，以遵循规范为荣** — 不动架构红线。修改前先理解项目架构约定（检查本文件、README、已有代码模式）。
7. **以假装理解为耻，以诚实无知为荣** — 不确定、不清楚、或发现知识盲区时，坦白说"不确定"，然后查证。
8. **以盲目修改为耻，以谨慎重构为荣** — 不盲改。做最小变更，一次只做一个逻辑变更，检查是否引入副作用。

## 二、事实标注规范（本项目特有，极其重要）

声音机制跨版本有实际差异。**任何写入文档/代码注释的技术事实，必须标注版本来源**，使用以下等级：

- **[1.21.11 已验证]** — 已对 1.21.11 混淆 jar 反编译逐行核对（或 Mojang CDN 原始资源文件）。
- **[26.2 已验证]** — 已对 26.2 官方命名 jar 反编译核对。
- **[1.21.8 已验证]** — 1.21.8 javadoc / 反编译核对。
- **[wiki]** — minecraft.wiki 记载（可能与具体版本有滞后，标注抓取日期）。
- **[推断]** — 逻辑推论，未经源码验证，必须写明推断依据。

已知的版本分界（详见 `docs/05-版本差异与兼容策略.md`）：
- 1.21.8 与 1.21.11 引擎同构（已验证）；乐器映射一致（木=BASS、石=BASEDRUM、铜=HARP 默认）。
- 26.1 起：新增 TRUMPET ×4 乐器，铜块家族 → TRUMPET（常量 23→27）。
- **命名改名分界在 1.21.11**（`_sources/mc{1218,12111}-client-mappings.txt` Mojang 官方 mappings 实查 2026-08）：`ResourceLocation`→`Identifier`、`getLocation()`→`getIdentifier()` 在 **1.21.11** 完成（1.21.8/1.21.9/1.21.10 仍旧名）；主客户端类官方名 1.21.8 起即 `Minecraft`（`MinecraftClient` 是 YARN 名）。→ 1.21.11 与 26.x 命名完全一致，**目标区间无跨版本改名负担**（26.2 反编译核对一致）。

## 三、Hy4 桥梁协议

- 用户（Yuxiao Lu）与 **Hy4**（元宝 Hy4，红石音乐声学方向的技术搭档）之间有独立讨论渠道；Hy4 侧由**他的 AI 助手**执行数值模拟（numpy 合成谐波栈）。
- **重要区分**：Hy4 侧**从未反编译源码/运行游戏/加载资源包**——所有 dB 数字只在合成模型内成立，不得当作源码级验证结果写入验收文档；NOTE_VOLUME=3、通道池 247+8、48 格衰减等是**我方源码验证**结果。
- **项目记忆/历史决策若本仓库中查不到 → 不要瞎猜，打印一段可直接转发给 Hy4 的中文 prompt**（问题要具体、附上我方已验证的前提，让 Hy4 只需回答差异点）。
- Hy4 的结论以 `_hy4\` 目录下的材料 + 后续对话记录为准；写入文档时注明来源。
- Hy4 核心结论（含 2026-08 第二轮修正，详见 `docs/05` §6 / `docs/06`）：
  1. 1gt = **1 game tick = 50ms = 20Hz**（已确认，非巧合）。重触发叠加是主因（20Hz 锯齿 + 相位慢起伏），软起音无效。
  2. **撤回 −56.65 dBc**：原值用 Hilbert 瞬时幅度/均值归一；RMS 包络复现 = **−36.2 dBc**，且随机相位种子离散 ±4.8dB（−33.4~−38.2）。**不得用作验收线。**
  3. 验收改两级：**筛选线** = 20Hz 调制 ≤ −30dBc（约 3%）且包络峰峰值 ≤ 3dB（仅淘汰明显不合格）；**终审** = 对 naive 基线做强制选择盲测 A/B，报告检出率。低频乐器（bass/didgeridoo）20Hz 调制不可测（奈奎斯特边缘混叠）→ 改看**逐周期起伏**。
  4. **60ms+8ms 不通用**（185Hz 调的）：F#5+ 用 55ms+约1周期淡化（≈1.4ms）、F#4 用 55ms+≈2.7ms、**F#3（harp/pling）= 60ms+8ms（验证 1.93dB）**；**F#2（guitar）/F#1（bass/didgeridoo）L0 无效甚至更差（bass 13.75 vs 原始 10.82 dB）→ 必须 L1+L2**。**音域表已降级为初值（第三轮确认：它在合成模型上拟合的，真实样本不服从）；逐样本 `--len-sweep` 定参**，防过拟合：top-2 候选 A/B 听 + 达标前提下选最短平台。
  5. **W 统一 2gt + release-cancel（第三轮，取代旧「纯 L0 W=1gt」——旧对与 60ms 平台有 40ms 静音空隙，Hy4 自认内部矛盾）**：1gt 时开始淡出，若淡出期间来了匹配音符 → **取消淡出回到 HOLD**（短音仍 50ms + 拿 2gt 合并窗口）。取消淡出的电平跳变需 A/B 验证。
  5b. **打击乐规格（第三轮）**：不做 L0 平台整形（孤立 hit 的 pp 本身 64.6dB，pp 测的是起落不是叠加失真；20Hz 调制对打击乐=节奏本身）。**48ms 截尾 + 4ms 淡出、fade-in=0 保留 attack、不压平、峰值归一（文件时间，名义 p=1.0 零重叠）**；L1 对打击乐关闭、L2 不适用。
  6. **L1 合并 key 含位置 → 一个持续音必须来自同一音符盒**；多方块拼持续音的乐谱 L1 直接失效。需统计真实乐谱中「同乐器+同音高+相邻 1gt 来自同一坐标」的比例。
  7. L2 延音**优先循环点**（无限延音、内存仅一个 loop 段）；预渲染兜底 **4s**（非 8s），且预渲染后仍打循环点（attack+N×loop，attack 只播一次）。48k/16bit 单声道 = 96KB/s。
  8. **不要做 ×(48000/44100) 采样率换算**：新版 `loopdetect.py` 按文件原生率分析（原 44.1k 硬编码是缺陷）；正确做法 = 对最终要加载的缓冲做检测；也不建议统一重采样到 48k（AL_PITCH 本就在重采样，混合率无额外成本）。
  9. 26.1 TRUMPET×4：Hy4 侧无可靠知识，未覆盖；程序性结论 = 直接对 note/trumpet*.ogg 跑新版 loopdetect（铜管强起音/强非谐性有被拒风险）。
  10. 48 格网络截断对 Hy4 结论无影响（他从未做距离测量）；听感测试布点须 ≤48 格（超过 = 无声而非衰减）。
  11. **L0 样本改造的额外价值**：并发占用降约 1/4（250ms 尾 @1gt = 5 存活 source → 60ms 平台 = 1.2），在 247 槽限制下直接降低丢音率——值得单独测丢音率。
  12. **先改样本验证，再写模组。**
  13. **L0 首验完成（2026-08，docs/09）**：harp 实测 1.95pp/−36.8dBc ≈ 模型 1.93dB（管线确认）；60ms 全局最优；**音域参数表降级为初值**（trumpet_oxidized 需 75ms、guitar F#2 实测 60ms 过线）；打击乐按 5b 新规格处理。
  14. **.nbs 乐谱格式已解析（2026-08）**：Open Note Block Studio v5，`pynbs` 库可读写；音符=(tick,layer,instrument,key,vel,pan,pitch)，**不存坐标**，物理盒由 (layer,instrument) 唯一确定（布局推定对 L1 够用，Hy4 第三轮认可；确定性验证法 = 导出同 layer/instr/key 隔 1gt 两音符 .nbs，加载 schematic 读坐标）。20 乐谱统计见 docs/09 §7。
  15. **f0「二次谐波」是估计器 bug（第三轮根因）**：旧 `est_f0` 搜索下限 60Hz 排除了 F#1=46.25Hz。新版（`loopdetect.py` v2，2026-08）下限 30Hz + 子谐波纠正（`f0Source` 字段）+ `seamFundRatio` + 淡化升级阈值 3.0→2.0 + render 越界修复。**重跑裁决：dbass f0=138.69 f0Source=fundamental → 真 C#3 样本，无翻转无需处理**；banjo/didgeridoo/bassattack 的 92Hz 也是真基频（样本本身无强 46.25Hz 分音）→ 基频相位翻转问题整体消失，不用测听感。
  16. **铜管（trumpet×4）= 纯 L0 路线（第三轮拍板）**：放宽重试（NCC 0.90 + 40ms 交叉淡化）仍 8/8 全拒（用户 4 + 26.2 原版 4：2×「循环<80ms」2×「非谐性过强」）；**否决** 60-80ms 短循环（12-16Hz 循环率嗡鸣可闻）；只有 >1s 持续铜管音才用颗粒式伪循环（逐段交叉淡化，不依赖周期性）。
  17. **L0 与 L2 统一（第三轮）**：L0 的平台段 = L2 的 attack 段，资源包只建一份（attack=扫描定参的平台，loop=loopdetect 输出）；短音 = attack+release（不进循环），长音 = attack→淡化→loop→release。
  18. **icechime = L0-only + 换回原版（第三轮）**：用户版无削波（peak 仅 0.244）、噪声底干净（−57dBFS）→ FAIL（ripple 10.4dB/−16.3dBc）归因样本非谐性；原版 L2 也 FAIL 但 L0 优秀（1.39pp/−53.7dBc）→ 换回原版 + L0；用户版标为已知问题轨。
  19. **实测管线取代合成模型（第三轮确认）**：后续调参直接用 verify_l0.py 模拟；合成模型仅留 F#3 harp 合成用例做回归金丝雀 + 机制隔离。
  20. **seamFundRatio 废弃（第四轮纠错，两个独立缺陷）**：① filtfilt 默认 padlen=9 样本但该滤波器冲激响应数百样本 → 边缘瞬态未吸收，完美周期循环也给出 61-224 的比值（∝1/f0，量的是滤波器病态不是接缝）；② 低通把咔哒抹成斜坡 → 测不到跳变，半周期错位反而"更好"。改用两个新指标（`loopdetect.py` v3，`_sources/loopdetect-run/`）：**`seamClickDb`**（未滤波接缝跳变/信号 RMS，答"有没有咔哒"：≤−40dB 安全 / −30 边界 / ≥−25 可闻，masking 启发值仅用于排序）+ **`seamPhaseDeg`**（平铺 7 遍消除边缘瞬态后低通+Hilbert 相位，答"基频在接缝处连不连续"：<10° 好 / >45° 需修）。旧 `seamFundRatio` 保留但标 deprecated 仅向后兼容。
  21. **L2 验收固化为结果导向四条件（第四轮，与 NCC 阈值解耦）**：NCC 只是搜索参数不是质量标准。四条件：① loop 长度 ≥150ms（循环率 ≤6.7Hz）；② 循环内 ripple ≤2dB；③ 循环率调制 ≤−40dBc；④ 接缝 clickDb ≤−40 且 phaseDeg <10°。低频样本 20Hz 调制不可测 → 看逐周期 ripple。
  22. **guitar 长音走 0.90 L2 同意（第四轮）**：ripple 0.16dB / mod −54.6dBc / loop 285ms（循环率 3.5Hz，边带远细于 92Hz 临界带宽）全过；否决铜管短循环的理由（60-80ms=12-16Hz 边带+mod 差）对它不成立。需补测 pitch 0.5×/2.0×（变速后接缝可能暴露）。
  23. **打击乐 48ms 零重叠域确认（第四轮）**：按**文件时间、名义 p=1.0**（48ms 文件 p=1.0 播 48ms<50ms 零重叠；p=0.5 播 96ms 约 1 层重叠）。低八度不必也无法零重叠（一个乐器一份文件、pitch 全靠 AL_PITCH；要 24ms 会切掉一半 attack），打击乐宽带/噪声性无相干 20Hz 锯齿、重叠区是已淡出弱尾撞瞬态掩蔽极强，已远好于原版（0.4-0.5s 尾=8-10 层重叠）。维持 48ms+4ms 不压平峰值归一。
24. **L2 判据 v4（第五轮，loopdetect v4，SHA `BB1510EC…` 20576B）**：启用循环点交叉淡化后，接缝 click/phase 指标**结构性失效**——Hy4 注入误差对照证实 clickDb 从 −41.3 到 −16.7 乱跳且与注入误差无关、注入 180° 误差 phaseDeg 仍读 0.0°（我们 v3 移植版读出的 banjo/bassattack/didgeridoo phase≈0.0° 不构成"接缝干净"证据）。新判据三件套：`periodErrDeg`（循环长度偏离整数周期的角度，PASS ≤10°）、`fadeNcc`（淡化混合两段 NCC，PASS ≥0.95）、`fadeDipDb`（淡化中点电平塌陷，PASS ≥−1.0dB）；**loop 长度门限 150ms→120ms**（循环率 ≤8.3Hz）；ripple/mod 维持 <2.0dB 且 <−40dBc（≥4.0dB FAIL）。10° 档是工程折中（5°→ncc 0.982/dip −0.04，20°→0.777/−0.51）非听阈——A/B 时顺带验 10–15° 一带。clickDb/phaseDeg 降为 advisory 仅排序、不进 verdict。**取代 #21 的四条件为现行判据。**纪律：Hy4 自认上一版"给了指标却没先做注入验证"→ 门限一律按"未经听感验证"标注，最终以我方 A/B 为准。
25. **真原版基线已验证 + NBS 命名映射（2026-08）**：vanilla bank 与 Mojang CDN 1.21.11/26.2 官方 SHA1 **逐字节一致**（16+4，`_sources/vanilla-baseline-verify.md`）；**1.21.11 与 26.2 的 16 经典声音逐字节相同**（26.x 未重录）；note_block 事件数：1.21.11 = **22（16 经典 + 6 imitate 拟态，imitate 早已存在，非 26.x 新增）**，26.1 起 +4 铜管 = **26**（[1.21.11 已验证] sounds.json 实查）。NBS Archive 命名映射经 NCC 全矩阵确认：bdrum=basedrum(note/bd)、dbass=bass(note/bassattack)、click=hat、sdrum=snare、xylobone=xylophone；**harp = 1.9 前遗留旧 harp.ogg（NCC 1.0000，44.1k）而非现行 harp2**；bell/flute/guitar/xylobone/icechime 五件是**与现行官方不同的旧录音**（NCC 0.04~0.79）。→ **默认预设以 CDN 现行官方为基准**（非 NBS Archive）；用户游戏若加载 NBS 声音集，这 6 件需按 NBS 样本单独定参。didgeridoo(用户) 官方报 ripple 6.09 的真因 = **attack 3805ms 慢起音污染度量窗**，稳态 loop 极干净（10 周期窗 pp 0.11dB / 50ms 窗 0.31dB / mod@0.84Hz −77dBc / 无趋势）。
26. **L2 判据 v5（第六轮，loopdetect v5，SHA `06c2cdb5…` 23602B，2026-08）**：Hy4 注入对照证实 **perr 在长 loop 上测的是 f0 精度不是接缝**（噪声底 = 360·k·ε，k=280 时 ε=0.01% 即 10°；ε=0.1% 时 perr 在 δ=0 与 δ=0.02 间不可分辨，fadeNcc 可以）。**判据换血**：`seamErrDeg`（`seam_err_cycles` 互相关分数延迟直接量测接缝错位，对 f0 误差免疫）≤10 PASS / 10–25 **WARN_SEAM** / >25 FAIL；`periodErrDeg` 降级 advisory；`fadeNcc` 降为参考（窗口相关，δ=0.02 时 1ms→0.984 / 5ms→0.949）；`fadeDipDb` <−1.0 FAIL、120ms 门、ripple/mod 门维持。**v5 全库 41 文件：11 PASS / 2 WARN_SEAM / 6 FAIL / 22 REJECT**（v4 只有 1 PASS）——长 loop 的 f0 假象全部消失（bit-user 65.98°→seam 4.15 PASS 等）。v5 新字段：`sr`（原生采样率）、`fadeTrace`（淡化升级轨迹）、`--span-cycles`（搜索范围，默认 0.5，高 perr 长 loop 试 1.0–1.5）、`diagWindowSamples`（诊断窗 ≥4ms）。**Q4（v3/v4 差异）已定位**：guitar 原生 44.1k（无 SR 泄漏）；`--xfade 0.040` 精确复现 v3 数字（1.5/−54.57）→ 真因 = v3 keep-best 无条件保留最后候选（40ms）vs v4/v5 keep-best 保留 5ms；且真实样本上长 xfade 方向与合成预测相反（40ms 使 seam 4.88°→1.73°、lp 2.49→1.50，代价 mod −58.14→−54.57）。**didgeridoo(用户) 3.8s attack 真身 = 起始 breath burst（0–100ms，−4.5~−7dB）的 3.6s 衰减尾**（−7→−34.6dB）：非 lead-in 非 swell；loop（1190.9ms/seam 1.77°）干净，FAIL 是衰减段入窗（mod −23.0）→ 路线 A（250ms burst+level-match）/ B（安静 200ms+10ms 淡入）待 Hy4 第七轮裁决（试听 `ab/didgeridoo/didg-{A,B,C}.wav`）。**dbass(用户) lp 7.09 真因 = loop 内容内 ~85–95ms 的 ghost pluck 瞬态**（30ms 包络归一后仍 +7.09dB，2.1Hz 周期重复；起点按周期平移 0–15 期 pp 不变 6.07–7.09）→ 与接缝无关，待裁决（接受/换短 L/要新 take）。**查表退役**：fadeNcc 反查表 vs seamErr 直接量测双向偏差 0.18×–2.2×，路由表直接用 seamErrDeg。guitar(用户)@0.90：seam 4.88° PASS 但 Hy4 按查表倾向不通过（12.8°）→ A/B 裁决中（`ab/guitar/ngs-gtr-{A,B,C}.wav`：A=5ms xfade / B=硬循环 / C=40ms）。`_verdict` 有 return 后死代码（无行为影响，下版删）。
27. **默认预设范围拍板 + 原版 L0 定参（2026-09-13，用户）**：用户 bank（21 件）来源确认 = **(Ballad|谣) 资源包**（用户自改；NCC 全矩阵显示 NBS Archive 风格旧录音）；用户拍板**游戏内打谱先用官方原版 → 模组资源包只优化 CDN 原版 16 件**，(Ballad|谣) 数据留档移出范围。原版 16 声音事件映射 [1.21.11 已验证]（mcmeta-sounds 实查）：banjo/bd/bassattack/bell/bit/icechime/cow_bell/didgeridoo/flute/guitar/harp2/hat/iron_xylophone/pling/snare/xylobone（CDN 遗留 bass/harp 无事件引用）+ 6 imitate（entity 事件）。**原版 L0 逐样本定参完成**（verify-sweeps-vanilla/，粗扫→±10ms@2.5ms 精调→**120ms 网格边缘外推 125–160ms**；bank 映射 `tools/instruments-vanilla.json`）：**PASS 5**（harp2 62.5ms 1.21pp、icechime 52.5 1.07、**bell 150ms 2.13**（最优在粗网格外）、banjo 62.5 2.47、flute 112.5 2.76）/**NEAR 5**（iron_xylophone 65 3.13、cow_bell 55 3.60、guitar 155 3.88、pling 60 4.31、didgeridoo 62.5 4.80）/**WEAK 3**（xylobone 7.93、bit 9.49、bassattack 9.82——后两件 L2 PASS 接管长音）。**L2 延音乐器（原版）= bit/bell/bassattack/harp2/iron_xylophone/pling 共 6 件**；flute 长音 = L0 重触发（L2 seam 38.21° FAIL）、guitar = L0 重触发（L2 REJECT）、**didgeridoo = L2 循环（`--span-cycles 1.0` 重检：130.5ms/7.66Hz/seam 8.42°/lp 0.76 PASS；默认 0.5 span 的 108.9ms<120ms 是搜索范围伪像，2026-09-13 更正）**。同 reg 同参数下原版 pp 普遍高于 user bank（bit 3.9→9.5 等）→ 音域表/旧录音参数不可复用，逐样本定参纪律再验证。**A/B 初轮（用户，2026-09-13，对 Ballad|谣 样本，保留为听感校准）**：didgeridoo 路线 A（250ms burst+level-match）胜出、C（3.8s attack）~3s 后近不可闻；guitar 三版均不理想（循环内容含拨弦瞬态 → 每 285ms 重拨，「原始音不适合延长音」）→ 拨弦乐 L2 长音困境同 #16 铜管。
28. **参数分层 + 变体预烘（2026-09-13，用户拍板）**：游戏内可调性分三档——① **行为参数热调**（L0/L1/L2 分层开关、每件 L2 开关、L1 W/fade/release-cancel、延音上限、音量覆盖）：改完下一音符生效；② **样本参数档位热切（变体预烘）**：M2a builder 导出资源包时除调优值外，预烘「平台长度平坦区相邻档（与最优点 worst_pp 差 ≤2pp 才烘，含调优值共 ≤3 档）+ L2 六件 xfade 5ms/40ms 双档 + 16 件未整形原版（单件回原版开关）」，全部变体在同一资源包内常驻内存 → 游戏内下拉切换事件指向即生效，**无需 F3+T**（包体 +<1MB）；③ **任意连续值** = 运行时 DSP（Java 移植 l0shape 对原版解码缓冲重整形），列为 M4+ 高级模式，不进 M1。M1 配置三 Tab：①图层开关（含每件变体下拉）②L1 参数 ③高级（延音上限/音量/调试 overlay）。行为类热生效、变体切换热生效、换资源包/整套参数才需 F3+T。

## 四、目录约定

```
NoteGT/                              # = GitHub 仓库 LuYuxiaoPKU/NoteGT（main）
├── AGENTS.md                 # 本文件
├── README.md                 # 仓库门面
├── .github/workflows/build.yml  # CI：push/PR 构建 notesoundopt（JDK 21）
├── notesoundopt/             # Fabric 模组工程（gradle，Mojang 官方 mappings，mod id = notegt）
├── docs/                     # 中文解析文档（交付物）
├── tools/                    # Python 离线工具链（l0shape / verify_l0 / nbs_stat）
├── _sources/                 # 原始证据：反编译产物、sounds.json、wiki、jar（不入库可删）
│   ├── dec262/  dec262b/  dec262c/   # 26.2 命名类 CFR 反编译
│   ├── dec12111b/                           # 1.21.11 混淆类 CFR 反编译
│   ├── dec1218/                             # 1.21.8 混淆类
│   ├── loopdetect-run/                      # loopdetect 工具 + 结果 JSON + 试听 ab/
│   └── ...
└── _hy4/                     # Hy4 材料（导出 zip 解压区、loopdetect.py、对比图）
```

## 五、环境与工具

- OS: Windows；工作区 `C:\00_Data\RGM\NoteGT`。
- JDK 21: `C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot\bin\java.exe`（反编译/构建共用）。
- CFR 0.152: `_sources\cfr.jar`。
- 本地 jar 缓存: `C:\Users\Yuxiao Lu\AppData\Local\Temp\mc12111_client.jar`（1.21.11 混淆）、`mc262_client.jar`（26.2 命名）、`_sources\mc1218_client.jar`（1.21.8 混淆）。
- 反编译产物缓存: `C:\Users\Yuxiao Lu\AppData\Local\Temp\cbv\`（x12111full / jar262full 已解压）。
- **真原版声音基准（SHA1 已验证，2026-08）**: `_sources\vanilla-cdn-1.21.11\`（18 文件含遗留 bass/harp）、`_sources\vanilla-cdn-26.2\`（22 文件含 4 铜管）；验证记录 `_sources\vanilla-baseline-verify.md`；下载脚本 `_sources\dl-vanilla-note.ps1`（piston-meta → assetIndex → CDN，官方 ogg 在 `minecraft/sounds/note/` 下，**非 block/note**）。
- 网络：Mojang CDN / meta.fabricmc.net / api.modrinth.com 可用（pwsh `Invoke-WebRequest`，需要时带 User-Agent）。
- **Gradle 代理纪律（2026-09-13 CI 根因，重要）**：`notesoundopt/gradle.properties` 的 `org.gradle.jvmargs` **不得**写本地代理 `-Dhttp(s).proxyHost=127.0.0.1:7897`——GitHub runner 无此本地代理，daemon 全部 HTTP（插件解析/Mojang jar）走死代理，症状 = 插件"not found"（实为连接失败）+ "Failed download after 3 attempts"，极易误判为 CDN 问题。本地构建代理一律走环境变量 `$env:JAVA_TOOL_OPTIONS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897'`（Java 不读 WinINET 系统代理，必须显式给）。

## 六、工具链（1.21.11 目标，已查证 2026-07/08）

| 组件 | 版本 | 备注 |
|---|---|---|
| Minecraft | 1.21.11 | 目标下限；兼容上探 26.x |
| Java | 21 | |
| Fabric Loader | 0.19.5（1.21.11 最新稳定） | 0.19.x 线 |
| Mixin | 0.8.7 | sponge-mixin 0.17.4 |
| Mappings | **Mojang 官方**（1.21.9+ 时代 Fabric 文档默认） | Yarn `1.21.11+build.6` 亦可 |
| Fabric Loom | 1.17.x 稳定线（1.18.0-alpha.21 最新） | 按模板生成器推荐 |
| fabric-api | 0.141.6+1.21.11 | |

> **模组开发方法论（已学习并沉淀，2026-08）**：两个外部 skill（ModelScope `@majiayu000/minecraft-fabric-dev` + GitHub `chouzz/minecraft-mod-dev`）的消化见 `docs/10-模组开发Skill笔记.md`，原文存 `_sources/skills/`。
> 采纳的核心纪律：**① 写 Mixin 前查目标反编译+Fabric wiki，不凭记忆；② Mixin 交付前必须 Loom 构建 + dev 客户端触发验证；③ Mappings 维持 Mojang 官方（1.21.9+ 时代 Fabric 默认，intermediary 备用）；④ 声音逻辑严格 client-side（`ClientModInitializer`）；⑤ 跨版本迁移先抽数学/状态机逻辑、再重查 API 挂点；⑥ 配置用 Cloth/fabric-api Config（W/fade/plateau/releaseCancel）。**
> 工程骨架开工按 `docs/10` §3.1 结构 + §3.2 Mixin 验证流 + §3.3 的 26.x 兼容清单执行。

## 七、待办（长期）

1. **【项目末，用户要求 2026-08】备份已知 skill + 项目开发经验，做一份合并的 skill**：素材 = `_sources/skills/`（两个外部 skill 原文）+ `docs/01-10` 沉淀 + 本文件八荣八耻/事实标注/Hy4 协议 + 项目特有工具链（l0shape/verify_l0/loopdetect v4/nbs_stat 的工作流与坑）。产出形式：一份可复用的 `SKILL.md`（含 references/），覆盖「Minecraft 音符盒声音优化 + Fabric 模组开发」完整方法论。
