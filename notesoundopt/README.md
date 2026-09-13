# NoteGT

> Minecraft 音符盒（note block）发声优化模组（Fabric，1.21.11 → 26.x，纯客户端）。
> 解决乐谱快速重触发时的 voice 叠加浑浊：逐乐器样本整形 + voice 合并 + 循环延音。

**状态：alpha（M0 工程骨架阶段）** — 构建/验证管线打通中，功能尚未进游戏。

## 为什么做这个

音符盒每 1 game tick（50ms = 20Hz）重触发同一音符时，原版行为是**叠加**新的
AL source 而不取消旧 voice：长音变成 20Hz 锯齿叠加 + 相位起伏，且每个未释放的
source 都占用 247 槽的通道池（占满即丢音）。NoteGT 在**客户端混音层**做三层优化：

- **L0 样本整形**（资源包）：按乐器/音域把样本改成「短平台 + 自然尾」，重触发叠加的失真直接变小（原版样本 60ms+8ms 平台在 F#3 验证 1.93dB）。
- **L1 voice 合并**（Mixin `SoundEngine`）：2gt 合并窗口 + release-cancel 状态机（IDLE→HOLD→RELEASING→RELEASE），同一音符盒的连续音符合并成一个持续 voice，通道占用降约 1/4。
- **L2 循环延音**：对通过离线检测（loopdetect v4 判据）的乐器用 attack+loop 无限延音，内存只占一个 loop 段。

逐乐器路由是**数据驱动**的（路由表 + 逐样本 override），用户替换声音包时按降级阶梯
自动回退（shaped+loop → full；shaped → L0+L1；raw drop-in → L1-only 兜底）。

## 特性

- 三层优化 L0/L1/L2，逐乐器路由表 + `overrides.yaml` 手工覆盖
- 降级阶梯：任意资源包（含随机音色）都能安全加载，最坏回退 L1
- 离线 builder 工具（Python：`l0shape` / `verify_l0` / `loopdetect v4` / `nbs_stat`）生成资源包 + 验收报告
- 游戏内热键 **Ctrl+Alt+N** 实时开关（A/B 听感对照工具），HUD overlay 显示 voice/池占用
- Cloth 配置 3 页（主开关/参数/逐乐器路由检视表 + 调试）
- 覆盖 1.21.11（22 音符事件）至 26.x（26 事件，含 26.1 新增 4 铜管）

## 快速开始（开发者）

```bash
# 需要 JDK 21
./gradlew build          # 产物 build/libs/notegt-0.1.0.jar
./gradlew runClient      # 启动 dev 客户端（游戏内放音符盒触发）
```

Windows: `.\gradlew.bat build`。构建会自动下载 Minecraft 1.21.11 + Mojang 官方 mappings
+ Loom 工具链（首次较慢）。

用户安装：把 `notegt-*.jar` + fabric-api 放进 mods 目录（模组默认开启，无需配置）。

## 仓库结构

```
notesoundopt/          # Fabric 模组工程（gradle，Mojang 官方 mappings）
docs/                  # 中文解析文档（01-10：引擎反编译/混音方案/版本兼容/L0 首验/Hy4 问询/证据清单/开发 skill 笔记）
_sources/              # 原始证据（jar/反编译/CDN 原版声音/loopdetect 工具链与结果 JSON；大体量部分 git 忽略）
_hy4/                  # 声学技术搭档 Hy4 的往返材料（导出区存档）
AGENTS.md              # 项目宪法：编码纪律 + 事实标注规范 + Hy4 桥梁协议
```

## 贡献

- 技术事实必须带版本标注（`[1.21.11 已验证]` / `[26.2 已验证]` / `[wiki]` / `[推断]`），见 `AGENTS.md` 第二节。
- 声学判据与 Hy4 侧的讨论记录见 `docs/08-Hy4-prompt.md`（逐轮 prompt + 答复沉淀）。

## 许可

CC0-1.0（与 fabric-example-mod 模板一致）。
