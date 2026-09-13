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

## 四、目录约定

```
NoteGT/
├── AGENTS.md                 # 本文件
├── docs/                     # 中文解析文档（交付物）
├── _sources/                 # 原始证据：反编译产物、sounds.json、wiki、jar（不入库可删）
│   ├── dec262/  dec262b/  dec262c/   # 26.2 命名类 CFR 反编译
│   ├── dec12111b/                           # 1.21.11 混淆类 CFR 反编译
│   ├── dec1218/                             # 1.21.8 混淆类
│   └── ...
├── _hy4/                     # Hy4 材料（导出 zip 解压区、loopdetect.py、对比图）
└── (未来) src/ 或独立 gradle 工程
```

## 五、环境与工具

- OS: Windows；工作区 `C:\00_Data\RGM\NoteGT`。
- JDK 21: `C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot\bin\java.exe`（反编译/构建共用）。
- CFR 0.152: `_sources\cfr.jar`。
- 本地 jar 缓存: `C:\Users\Yuxiao Lu\AppData\Local\Temp\mc12111_client.jar`（1.21.11 混淆）、`mc262_client.jar`（26.2 命名）、`_sources\mc1218_client.jar`（1.21.8 混淆）。
- 反编译产物缓存: `C:\Users\Yuxiao Lu\AppData\Local\Temp\cbv\`（x12111full / jar262full 已解压）。
- **真原版声音基准（SHA1 已验证，2026-08）**: `_sources\vanilla-cdn-1.21.11\`（18 文件含遗留 bass/harp）、`_sources\vanilla-cdn-26.2\`（22 文件含 4 铜管）；验证记录 `_sources\vanilla-baseline-verify.md`；下载脚本 `_sources\dl-vanilla-note.ps1`（piston-meta → assetIndex → CDN，官方 ogg 在 `minecraft/sounds/note/` 下，**非 block/note**）。
- 网络：Mojang CDN / meta.fabricmc.net / api.modrinth.com 可用（pwsh `Invoke-WebRequest`，需要时带 User-Agent）。

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
