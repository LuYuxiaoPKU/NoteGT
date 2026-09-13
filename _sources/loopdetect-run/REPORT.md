# loopdetect.py 真实样本批处理报告（2026-08）

> 工具：`C:\Users\Yuxiao Lu\Downloads\loopdetect.py`（Hy4 侧新版，原生采样率自适应，48k 实测通过）
> 参数：默认（snr 20dB / ncc 0.95 / xfade 5ms / dynrange 40dB / pitch 0.5·1.0·2.0）+ `--total 12`（修复长起音 render 越界）
> 环境：Python 3.13.3 + numpy 2.5.3 + scipy 1.18.1 + soundfile 0.14.0
> 原始 JSON：`result-user.json` / `result-vanilla12111.json` / `result-vanilla262.json`（同目录）

## 1. 用户实际在用样本库（`Minecraft_Note_Block_Studio\data\sounds\*.ogg`，21 个）

**PASS 7 / WARN 1 / FAIL 2 / 被拒 11（通过率 33%）**

| 文件 | 判定 | f0 (Hz) | attack (ms) | loop (ms) | NCC | 长音起伏 (dB) | 循环率调制 (dBc) | 接缝 (×) |
|---|---|---|---|---|---|---|---|---|
| banjo | PASS | 92.35 | 289.4 | 303.8 | 0.9513 | 1.66 | −47.9 | 1.15 |
| bdrum | 拒：非谐性过强 | — | | | | | | |
| bell | PASS | 1482.3 | 54.8 | 792.0 | 0.9994 | 0.07 | **−95.2** | 1.10 |
| bit | PASS | 370.9 | 228.6 | 1200.4 | 0.9779 | 0.24 | −60.1 | 1.49 |
| click | 拒：f0 估计失败（打击乐） | — | | | | | | |
| cow_bell | 拒：非谐性过强 | — | | | | | | |
| dbass | PASS | **138.69** | 62.2 | 475.5 | 0.9501 | 0.67 | −43.2 | 0.87 |
| didgeridoo | **FAIL** | 91.66 | **3805** | 1190.9 | 0.9904 | **6.09** | **−23.0** | 0.18 |
| flute | PASS | 741.3 | 65.4 | 779.9 | 0.9996 | 0.12 | −69.6 | 0.39 |
| guitar | 拒：循环<80ms | — | | | | | | |
| harp | PASS | 370.2 | 20.0 | 759.8 | 0.9735 | 0.14 | −66.2 | 2.02 |
| icechime | **FAIL** | 1480.5 | 529.3 | 504.5 | 0.9510 | **10.42** | **−16.3** | 0.30 |
| iron_xylophone | WARN | **185.47** | **1039.7** | 86.6 | 0.9539 | 3.61 | −33.3 | 1.54 |
| pling | PASS | 370.1 | 16.0 | 272.9 | 0.9676 | 0.10 | −62.5 | 1.24 |
| sdrum | 拒：非谐性过强 | — | | | | | | |
| snare | 拒：非谐性过强 | — | | | | | | |
| trumpet | 拒：循环<80ms | — | | | | | | |
| trumpet_exposed | 拒：非谐性过强 | — | | | | | | |
| trumpet_oxidized | 拒：非谐性过强 | — | | | | | | |
| trumpet_weathered | 拒：循环<80ms | — | | | | | | |
| xylobone | 拒：非谐性过强 | — | | | | | | |

## 2. 原版 1.21.11 note/*.ogg（16 个，Mojang CDN 原始文件）

**PASS 7 / WARN 1 / FAIL 1 / 被拒 7（通过率 44%）**

| 文件 | 判定 | f0 (Hz) | attack (ms) | loop (ms) | NCC | 长音起伏 (dB) | 循环率调制 (dBc) | 接缝 (×) |
|---|---|---|---|---|---|---|---|---|
| banjo | 拒：循环<80ms | — | | | | | | |
| bassattack | PASS | 92.22 | 103.7 | 141.3 | 0.9532 | 0.13 | −57.0 | 1.48 |
| bd | 拒：f0 估计失败（打击乐） | — | | | | | | |
| bell | PASS | 1479.0 | 13.9 | 180.5 | 0.9599 | 1.08 | −61.6 | 0.83 |
| bit | PASS | 369.9 | 19.3 | 140.5 | 0.9575 | 0.15 | −54.6 | 1.20 |
| cow_bell | 拒：非谐性过强 | — | | | | | | |
| didgeridoo | PASS | 92.52 | 147.2 | 108.9 | 0.9526 | 0.69 | **−68.6** | 0.92 |
| flute | WARN | 740.1 | 65.2 | 243.6 | 0.9534 | 2.96 | −35.1 | 0.38 |
| guitar | 拒：循环<80ms | — | | | | | | |
| harp2 | PASS | 370.2 | 26.9 | 243.3 | 0.9796 | 0.22 | −56.0 | 0.75 |
| hat | 拒：f0 估计失败（打击乐） | — | | | | | | |
| icechime | FAIL | 1472.6 | 348.7 | 271.8 | 0.9507 | 11.66 | −34.3 | 0.10 |
| iron_xylophone | PASS | 369.9 | 57.9 | 232.3 | 0.9789 | 0.17 | −55.2 | 0.80 |
| pling | PASS | 370.1 | 16.0 | 283.7 | 0.9648 | 0.10 | −60.8 | 0.96 |
| snare | 拒：f0 估计失败（打击乐） | — | | | | | | |
| xylobone | 拒：循环<80ms | — | | | | | | |

## 3. 原版 26.2 trumpet*.ogg（4 个）

**全部被拒（4/4）**：trumpet / trumpet_weathered = 循环<80ms；trumpet_exposed / trumpet_oxidized = 非谐性过强。
（与 Hy4 侧预测一致：铜管强起音、强非谐性 → 拒绝类别。）

## 4. 观察与待 Hy4 确认项

1. **trumpet 4/4 全拒**（原版+用户副本一致）→ 参数表需补铜管行：L2 对铜管用什么替代（降 NCC 阈值/短循环/预渲染不循环）？
2. **低频并未被拒**：bassattack（92.22）/ didgeridoo 原版（92.52）/ dbass（138.69）/ banjo 用户（92.35）都 PASS，f0 锁在**第二次谐波**（F#1 基频 46.25 的 2 倍）。
   - dbass 138.69Hz 不在 F# 系列上（介于 F#2 92.5 与 F#3 185 之间，1.5×92.5），疑似锁定到强分音。
   - 循环长度 = 2f0 的整数周期 = 基频的**奇数**周期 → 接缝处基频相位翻转 180°。指标都正常（长音起伏 <1dB），请确认是否可闻 / 是否需要基频相位约束。
3. **用户 didgeridoo FAIL**（−23.0dBc，attack 3.8s）vs **原版 didgeridoo PASS**（−68.6dBc）——两份样本表现完全不同。
4. **icechime FAIL**（用户 −16.3dBc / 原版 −34.3dBc，长音起伏 10-12dB）：高频 chime 类非谐性最强，**F#5+ 走 L0（55ms+1周期淡化）在真实样本上可能是 WARN/FAIL**，需要复验。
5. 用户 iron_xylophone 与原版不同 take（f0 185.47 vs 369.9，attack 1.04s）→ 循环 86.6ms 贴着下限，WARN。
6. 打击乐（bd/hat/snare/click）f0 估计失败是**预期行为**——它们本来就没有可循环延音段，L2 不适用，L0/L1 照常。
7. 用户库已预置 26.1 的 4 个 trumpet 样本（与 26.1 snap7 新增乐器时间线吻合）。

## 5. 复现命令

```powershell
$py = 'C:\Users\Yuxiao Lu\AppData\Local\Programs\Python\Python313\python.exe'
$base = 'C:\00_Data\RGM\NoteGT\_sources\loopdetect-run'
& $py "$base\loopdetect.py" "$base\user-bank" --batch --json --total 12
# 样本收集: _sources\collect-samples.ps1（用户库复制 + Mojang CDN 下载）
```
