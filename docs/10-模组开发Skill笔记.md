# 10 · 模组开发 Skill 学习笔记（两个外部 skill 的消化与 NoteGT 适用性）

> 来源（原文存档在 `_sources/skills/`）：
> 1. **ModelScope `@majiayu000/minecraft-fabric-dev`**（MCP 三件套：minecraft-dev-mcp / fabric-docs-mcp / baritone-docs-mcp）
> 2. **GitHub `chouzz/minecraft-mod-dev`**（Claude Code skill，NeoForge/Fabric + JEI/AE2/Create 生态）
> 抓取 2026-08。本文是消化后的**行动清单**，不重复原文（原文逐字存 `_sources/skills/`）。

## 1. 两个 skill 的共性纪律（直接采纳进本项目）

| 纪律 | 出处 | NoteGT 落地 |
|---|---|---|
| **不靠记忆查接口**：提到具体版本/加载器就现查官方文档（wiki.fabricmc.net / docs.neoforged.net），Registry 名、包处理逻辑变更频繁 | 两个都强调（chouzz「DO NOT rely on internal knowledge」） | 我们 AGENTS.md 已有「事实标注」制度，补一条：写 Mixin/调 API 前查 Fabric wiki 对应版本页，不凭训练数据记忆 |
| **Mixin 必须验证后再说完成**：注入点存在、签名精确匹配、优先级/冲突 | ModelScope（analyze_mixin 工作流） | 我们没有 MCP 的 analyze_mixin，**验证 = Loom 构建 + 启动 dev 客户端看 Mixin 报错**；每个 Mixin 交付前必须构建通过 + 游戏内触发一次 |
| **先读目标源码再写 Mixin** | ModelScope 工作流 | 已有惯例（01-05 全链路反编译），保持：L1 写 Mixin 前重读 `iqo`（SoundEngine）当前版本的 play/stop 反编译 |
| **Access Widener 有固定语法**（`accessWidener v2 named` + accessible/extendable/mutable） | ModelScope | L3（EFX）需要：`Channel.source`（private）等字段 → 提前准备好 `.accesswidener` |
| **环境自检**：先 grep gradle.properties 的 minecraft/fabric 版本与依赖再动手 | chouzz mod-env-check.sh | 工程骨架建好后，改版本前先跑环境检查 |
| **迁移 = 抽逻辑不抽 API**：先提取数学/业务逻辑，再套新范式 | chouzz migration-guide | 从 L0 模拟→真实模组、1.21.11→26.x 跨版本时同法：状态机/包络数学不变，API 挂点重查 |

## 2. 与我们已有决策的对照（确认 / 修正）

| 议题 | skill 说法 | 我们现状 | 结论 |
|---|---|---|---|
| **Mappings 选择** | ModelScope：legacy Fabric 用 yarn「PREFERRED」；chouzz：「always 官方 mapped names」 | AGENTS.md 已定 **Mojang 官方 mappings**（1.21.9+ 时代 Fabric 文档默认） | **维持**。两个 skill 的分歧本质是 legacy vs 现代；ModelScope 自己也写了 1.21.11 后官方发布走向去混淆，yarn 仅为一致性。我们目标 1.21.11→26.x 全在「官方名可读」区间，Mojang mappings 是对的；**intermediary 作为跨版本稳定 ID 保留备用**（26.x 兼容层可能用得上） |
| **去混淆过渡** | ModelScope：1.21.11 后的实验快照起官方 jar 带可读名 | 26.2 jar 已确认官方命名 | 一致；docs/05 已记录。26.x 兼容时「official = 可读」会省掉 remap |
| **客户端逻辑隔离** | chouzz：`@OnlyIn(Dist.CLIENT)` 严格在客户端类 | L1/L3 全是客户端声音逻辑 | 采纳：入口用 `ClientModInitializer`，不建服务端入口 |
| **DataGen 优于手改 JSON** | 两个都提 | 我们的「资源包」（整形 ogg）是二进制资产，无 JSON 生成需求；sounds.json 是手写的 1 个文件 | 不适用（除非未来加自定义音效事件） |
| **Data Components / NBT** | 1.20.5+ 用 DataComponents | 我们的 SoundInstance 自定义类不用 NBT 存储（状态在内存） | 不适用 |
| **Networking** | CustomPacketPayload + PayloadRegistrar | L1 纯客户端声音合并，**不需要网络包**（音符盒事件本身是服务端广播 block event，我们不新增通信） | 不适用（若未来要「静音模式同步」再说） |
| **Inter-mod（JEI/AE2/Create）** | 生态优先 | 声音优化模组没有 recipe/storage 面 | 不适用；**Cloth Config 适用**（W/fade/plateau/release-cancel 开关做成可配置，用 Cloth Config 或 fabric-api 的 Config 方案） |
| **runGameTestServer / runData** | 集成测试工具 | 声音引擎测试需要客户端 + 音频设备 + 方块触发 → **用 `./gradlew runClient` dev 客户端**（游戏内按 docs/06 测试清单手测 + 我们自己的 verify 脚本） | runGameTestServer 可用于不碰声音的回归（如 mixin 注册不炸） |

## 3. 对 NoteGT 开发直接有用的具体清单

### 3.1 工程骨架（Fabric，1.21.11）
```
notesoundopt/
├── gradle.properties            # minecraft_version=1.21.11, loader 0.19.5, fabric-api 0.141.6+1.21.11, mappings=官方
├── build.gradle(.kts)           # fabric-loom 1.17.x；accessWidener 文件路径配置
└── src/main/
    ├── java/com/notegt/
    │   ├── NoteGTMod.java        # (无服务端入口；客户端走 client)
    │   ├── client/
    │   │   └── NoteGTClient.java # ClientModInitializer
    │   ├── mixin/
    │   │   ├── SoundEngineMixin.java   # L1 主挂点（iqo.play/stop）
    │   │   └── ...
    │   └── config/NoteGTConfig.java    # Cloth/fabric 配置：enabled/w/fade/plateau/releaseCancel
    ├── resources/
    │   ├── fabric.mod.json        # entrypoints: client；mixins: notegt.mixins.json；accessWidenerPath
    │   ├── notegt.mixins.json
    │   └── notegt.accesswidener   # L3 用：Channel.source 等 private 字段
    └── (sounds 资源包是独立产物，不进模组 jar——或做内置资源包 fallback，待定)
```

### 3.2 Mixin 验证工作流（替代 MCP analyze_mixin）
1. 读目标反编译（`_sources/dec12111b/` 或 26.2 命名 jar）确认注入点 + 签名；
2. 写 Mixin；
3. `./gradlew build` 通过；
4. `./gradlew runClient` 启动 → 触发音符盒 → 看 log 有无 `@Mixin target not found` / `mixin apply failed`；
5. 交付前对照 docs/06 §7 测试清单。

### 3.3 26.x 兼容检查清单（chouzz 1.26.1 checklist 裁剪版，对齐 docs/05）
- [ ] mappings 用官方（1.21.11 起与 26.x 命名一致——改名 `ResourceLocation`→`Identifier`、`getLocation()`→`getIdentifier()` 在 **1.21.11** 完成，主类官方名 1.21.8 起即 `Minecraft`；docs/05 §1 更正 2026-08）
- [ ] TRUMPET×4 乐器枚举 + 铜块家族映射（docs/05 已有）
- [ ] Loom/Loader 版本升级后重新 remap 构建
- [ ] grep 已弃用 API（`grep -R "getSound\|SoundEvent" --include='*.java'`）
- [ ] 严格 client-side：声音逻辑不进 common

### 3.4 环境自检（建骨架后）
```powershell
# 等效 mod-env-check.sh（Windows 版）
Select-String -Path gradle.properties -Pattern 'minecraft_version|fabric_version|loader_version|mod_version'
Select-String -Path build.gradle* -Pattern 'fabric-loom|fabric-loader|fabric-api'
```

## 4. 不采纳 / 降权的（避免 skill 带偏）

| 项 | 原因 |
|---|---|
| ModelScope「Fabric 必须 yarn」的强表述 | 1.21.9+ 时代 Fabric 官方文档默认 Mojang mappings；我们的反编译证据链（01-05）全部建立在官方名 + 混淆名对照上，换 yarn 要重建 |
| MCP 工具链（minecraft-dev-mcp 等） | 本环境无 MCP 挂载；等效能力已有（CFR 反编译 + 本地 jar 缓存 + web_fetch wiki），不为此改流程 |
| Baritone docs | 与声音模组无关 |
| JEI/AE2/Create 集成 | 无 recipe/库存/机械面 |
| TileEntity/NET 迁移范式 | 从 1.12 迁移用的，我们从零写 Fabric，只需其「现代范式」部分（Data Components/BlockEntity/CustomPacketPayload 概念了解即可） |

## 5. 一句话结论

两个 skill 的**方法论**（现查文档、Mixin 先验证、AW 语法、client 隔离、环境自检、迁移抽逻辑）全部采纳进 NoteGT 工作流；**工具链**（MCP/yarn/DataGen）按我们已有环境替换为 CFR+Loom+Mojang mappings+runClient；**生态集成**（JEI/AE2/Create）与本声音模组无关。工程骨架开工时直接按 §3.1 结构 + §3.2 验证流执行。
