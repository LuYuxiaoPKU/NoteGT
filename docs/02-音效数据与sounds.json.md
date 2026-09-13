# 02 · 音效数据体系与 sounds.json

> 证据：`_sources/vanilla-sounds-1.21.11.raw.json`、`_sources/vanilla-sounds-26.2.raw.json`
> （Mojang CDN 原始文件，哈希来自官方 asset index）；`_sources/dec262/net/minecraft/client/sounds/SoundManager.java` 等。

## 1. 三层对象模型

```
SoundEvent  (注册表条目, "block.note_block.harp")          ← /playsound、代码引用、网络同步都指它
   │  sounds.json 的一个 entry
   ▼
WeighedSoundEvents { List<Weighted<Sound>>, subtitle }     ← 运行时：加权文件集合 + 副标题
   │  按 weight 随机（每次播放抽一次）
   ▼
Sound { location(=ogg 文件), volume:SampledFloat, pitch:SampledFloat, weight,
        type: FILE|SOUND_EVENT, stream, preload, attenuationDistance }
```

- `SoundEvent` = `record(location, Optional<Float> fixedRange)` [26.2 已验证]
  - `getRange(volume) = fixedRange.orElse(volume > 1.0f ? 16.0f * volume : 16.0f)` —— **服务端**声音传播半径（决定哪些客户端收到 play 包）：默认 16，volume>1 时 16×volume。**与客户端衰减距离无关**。
- 内置事件常量类 `SoundEvents`（1.21.11 混淆名 bda；26.2 命名）：`NOTE_BLOCK_HARP`…`NOTE_BLOCK_PLING`、`NOTE_BLOCK_IMITATE_*`、`NOTE_BLOCK_TRUMPET*`（仅 26.x）。

## 2. sounds.json 字段全表（1.21.11 与 26.2 相同）

来源：SoundManager 解析代码 [26.2 已验证] + 官方文件实例。

```jsonc
"block.note_block.harp": {
  "sounds": [
    "note/harp2",                       // 字符串 = 文件引用（相对 assets/<ns>/sounds/，.ogg）
    {
      "name": "note/harp2",             // 或对象引用：
      "volume": 1.0,                    //  数值或 [min,max] 区间 → SampledFloat；缺省 1.0
      "pitch": 1.0,                     //  同上；缺省 1.0
      "weight": 1,                      //  缺省 1；0 = 不出现
      "stream": false,                  //  true = 流式播放（占 streaming 池）；缺省 false
      "preload": false,                 //  true = 重载时立即解码；缺省 false
      "attenuation_distance": 16        //  缺省 16（单位：格）
    }
  ],
  "subtitle": "subtitles.block.note_block.note"   // 副标题 key；缺省 = 按 source 的默认副标题
}
```

- **`"type": "event"`（SOUND_EVENT 型）**：
  ```jsonc
  "block.note_block.imitate.creeper": {
    "sounds": [{ "name": "entity.creeper.primed", "pitch": 0.5, "type": "event" }],
    "subtitle": "subtitles.entity.creeper.primed"
  }
  ```
  指向另一个**事件**（可跨资源包）。播放时：抽目标事件的文件，且外层 volume/pitch 以 `MultipliedFloats` **相乘**叠加。[26.2 已验证：SoundManager handleRegistration → MultipliedFloats]
- 资源包合并规则：同名事件默认**合并**（append sounds）；entry 带 `"replace": true` 则整条替换。[26.2 已验证]
- 校验：FILE 引用的文件必须存在于资源栈 `sounds/` 下，否则该文件被跳过（并 warn）；全部文件都不存在 → 事件无声音（resolve 时 EMPTY_SOUND，不发声不警告——**资源包改样本时注意路径**）。
- 数值区间语法：`"volume": [0.8, 1.2]` → `UniformFloat`（1.21.9+ 起文件音量/音高真正生效进 `AbstractSoundInstance.getVolume()/getPitch()` 的乘数采样 [1.21.8 javadoc 提及该行为]）。

## 3. 1.21.11 全部 note_block 事件（22 条，原始文件核对）[1.21.11 已验证]

| 事件 | 类型 | 文件 / 目标 | 特殊字段 |
|---|---|---|---|
| block.note_block.harp | file | note/harp2 | — |
| block.note_block.basedrum | file | note/bd | — |
| block.note_block.snare | file | note/snare | — |
| block.note_block.hat | file | note/hat | — |
| block.note_block.bass | file | note/bassattack | — |
| block.note_block.flute | file | note/flute | — |
| block.note_block.bell | file | note/bell | — |
| block.note_block.guitar | file | note/guitar | — |
| block.note_block.chime | file | **note/icechime** | —（文件名≠事件名） |
| block.note_block.xylophone | file | note/xylobone | — |
| block.note_block.iron_xylophone | file | note/iron_xylophone | — |
| block.note_block.cow_bell | file | note/cow_bell | — |
| block.note_block.didgeridoo | file | note/didgeridoo | — |
| block.note_block.bit | file | note/bit | — |
| block.note_block.banjo | file | note/banjo | — |
| block.note_block.pling | file | note/pling | — |
| block.note_block.imitate.creeper | **event** | entity.creeper.primed | **pitch 0.5** |
| block.note_block.imitate.ender_dragon | event | entity.ender_dragon.ambient | — |
| block.note_block.imitate.skeleton | event | entity.skeleton.ambient | — |
| block.note_block.imitate.wither_skeleton | event | entity.wither_skeleton.ambient | — |
| block.note_block.imitate.zombie | event | entity.zombie.ambient | — |
| block.note_block.imitate.piglin | event | entity.piglin.ambient | — |

26.2 额外 4 条 [26.2 已验证]：`block.note_block.trumpet{,_exposed,_oxidized,_weathered}` → `note/trumpet{,_exposed,_oxidized,_weathered}.ogg`。

**所有 note.* 文件 entry 都是裸字符串** → volume=1.0、pitch=1.0、weight=1、无 stream、无 preload、attenuation_distance=16（默认）。

## 4. 采样率实测（Mojang CDN 原始 ogg，Vorbis identification header）[1.21.11 已验证 = 26.2 相同]

| 文件 | 采样率 | 声道 |
|---|---|---|
| note/bd, note/bassattack, note/snare, note/hat, note/pling | **44100 Hz** | 单声道 |
| note/harp2, note/guitar, note/flute, note/bell, note/icechime, note/xylobone, note/iron_xylophone, note/cow_bell, note/didgeridoo, note/bit, note/banjo（+26.2 note/trumpet*） | **48000 Hz** | 单声道 |

- 解码链：`JOrbisAudioStream`（纯 Java Vorbis 解码，`nonCriticalIoPool` 异步）→ `SoundBuffer`（PCM + `AudioFormat`，**保留原采样率**）→ `alBufferData`；OpenAL Soft 播放时按设备率重采样。
- 对样本替换/循环点检测的影响：Hy4 的 `loopdetect.py` 按 44.1kHz 写，但多数音符样本是 **48kHz**——处理前必须按文件实际率换算循环点（`_hy4/导出区/loopdetect.py` 输出 attackEnd/loopStart/loopEnd）。

## 5. 分类（SoundSource）与音量链

`SoundSource`：`MASTER / MUSIC / RECORDS / WEATHER / BLOCKS / HOSTILE / NEUTRAL / PLAYERS / AMBIENT / VOICE / UI` [26.2 已验证]

最终增益 = `clamp(实例volume,0,1) × 设置滑条(该 source) × gainBySource(source)`（gain 默认 1.0，可被 `setSourceGain` 动态改）。
- 音符盒走 **RECORDS**（设置里叫 "Jukebox/Note Blocks" 滑条）[1.21.11 已验证：efi 触发处 bdb.c + wiki]。
- 唱机走 RECORDS（`forJukeboxSong` volume=4.0 → 衰减 64 格）；音乐走 MUSIC（relative，无衰减）；UI 走 UI（relative，NONE）。

## 6. 资源包替换样本的注意点（模组/资源包路线共用）

1. 路径必须 `assets/<ns>/sounds/...ogg`；事件名不变即可无代码替换。
2. 格式要求：**Ogg Vorbis**（JOrbis 解码）；WAV/MP3 不被引擎读（1.21.x 无其他解码器）[推断：SoundBufferLibrary 只 new JOrbisAudioStream/LoopingAudioStream，26.2 已验证]。
3. 采样率/位深自由（44.1k/48k 均可，单/立体声均可——但立体声会占 2 通道内存且原版全单声道）。
4. 想改"可闻距离"：在替换 entry 里加 `attenuation_distance`（注意它是 **乘以** 实例 volume 的基数：音符盒 volume=3 → 实际距离 = 3×该值）。
5. 想改副标题：entry 的 `subtitle`。
