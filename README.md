# NoteGT — Minecraft 音符盒发声优化

> Fabric 模组（1.21.11 → 26.x，纯客户端）：修复音符盒乐谱快速重触发时的 voice 叠加浑浊。
> 逐乐器样本整形（L0）+ voice 合并状态机（L1）+ 循环延音（L2），数据驱动路由 + 降级阶梯。

**状态：alpha（M0 工程骨架）** — 详见 `notesoundopt/README.md`。

## 仓库布局

| 路径 | 内容 |
|---|---|
| [`notesoundopt/`](notesoundopt/) | Fabric 模组工程（gradle + Mojang 官方 mappings；`.\gradlew.bat build` / `runClient`） |
| [`docs/`](docs/README.md) | 中文解析文档 01-10：声音引擎反编译、混音方案、版本兼容策略、L0 首验报告、Hy4 声学问询记录、证据清单、模组开发 skill 笔记 |
| [`_sources/`](<file:./_sources>) | 原始证据（jar/反编译、Mojang CDN 原版声音、sounds.json、loopdetect 工具链与结果） |
| [`_hy4/`](<file:./_hy4>) | 声学技术搭档 Hy4 的往返材料（逐轮 prompt 存档） |
| [`AGENTS.md`](AGENTS.md) | 项目宪法：编码纪律（八荣八耻）、事实标注规范、Hy4 桥梁协议 |

## 背景（30 秒版）

音符盒每 tick（50ms）重触发同一音符时，原版**叠加**新的 OpenAL source：
长音 = 20Hz 锯齿叠加 + 相位起伏，且占满 247 槽通道池就丢音。
本项目先在**离线样本层**验证优化（Python 工具链，`tools/` + `_sources/loopdetect-run/`），
再落 **Fabric 客户端模组**（L1 混音合并 + 运行时路由），声学判据与 Hy4 侧逐轮对齐
（`docs/08-Hy4-prompt.md`）。

## 开发

```bash
cd notesoundopt
.\gradlew.bat build       # 需要 JDK 21；首次构建自动下载 MC 1.21.11 + mappings
.\gradlew.bat runClient   # dev 客户端手测
```

GitHub Actions 在每次 push/PR 自动构建（`.github/workflows/build.yml`）。

## 许可

CC0-1.0。
