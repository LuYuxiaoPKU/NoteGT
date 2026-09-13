package com.notegt.client;

import com.notegt.config.DspSettings;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * M1 配置界面（Cloth Config 2.x）。
 *
 * <p>API [2026-09-13 javap 实测]：bundled cloth-config 21.11.153+fabric 为全新 2.x 架构
 * （me.shedaniel.clothconfig2，非旧 me.earlydove.clothconfig）：
 * {@code ConfigBuilder.create()} + {@code entryBuilder().start*()} 手工构建、无自动注册。
 *
 * <p>1.21.11 命名 [1.21.11 已验证]：Text → network.chat.FormattedText（组件接口 = Component，
 * 工厂 Component.literal/translatable）、gui.screen → gui.screens、KeyBinding → KeyMapping、
 * InputUtil → blaze3d.platform.InputConstants。
 *
 * <p>布局（AGENTS #28/#31，用户 2026-09-13 拍板范围）：
 * ① 总开关（L0/L1/L2 总开关 + 延音上限）② 乐器（16 件 × {L0/L1/L2 开关, 平台长度 40–200ms,
 * 振幅 0–200%}）③ L1 参数（M3 生效）④ 高级（每件淡入/淡出/压平）。
 * 全部改动 = 落盘 + 热重整形（下一音符生效）。
 */
public final class NoteGTConfigScreen {

    private NoteGTConfigScreen() {
    }

    /** @param parent 返回 Screen 的父屏幕（ModMenu 传入 ModMenu 屏；键绑打开传 null） */
    public static Screen create(Screen parent) {
        DspSettings s = NoteGTConfig.SETTINGS;
        ConfigBuilder builder = ConfigBuilder.create().setTitle(t("NoteGT 声音优化"));
        if (parent != null) {
            builder.setParentScreen(parent);
        }
        ConfigEntryBuilder eb = builder.entryBuilder();

        // ---- ① 总开关 ----
        ConfigCategory master = builder.getOrCreateCategory(t("总开关"));
        master.addEntry(eb.startTextDescription(t("L0 当前生效（M2a 运行时 DSP）；L1 于 M3、L2 于 M4 生效——参数先行保存。")).build());
        master.addEntry(eb.startBooleanToggle(t("L0 样本整形（全局）"), s.l0Master)
                .setTooltip(t("关闭 = 全部乐器回原版样本（A/B 对照基线）"))
                .setSaveConsumer(v -> {
                    s.l0Master = v;
                    NoteGTConfig.applyAll();
                }).build());
        master.addEntry(eb.startBooleanToggle(t("L1 voice 合并（M3 生效）"), s.l1Master)
                .setSaveConsumer(v -> {
                    s.l1Master = v;
                    NoteGTConfig.applyAll();
                }).build());
        master.addEntry(eb.startBooleanToggle(t("L2 循环延音（M4 生效）"), s.l2Master)
                .setSaveConsumer(v -> {
                    s.l2Master = v;
                    NoteGTConfig.applyAll();
                }).build());
        master.addEntry(eb.startIntSlider(t("延音上限 (ms, M4 生效)"), s.sustainLimitMs, 1000, 10000)
                .setTooltip(t("长音最多重触发/预渲染时长；Hy4 规范 4s 兜底"))
                .setSaveConsumer(v -> {
                    s.sustainLimitMs = v;
                    NoteGTConfig.applyAll();
                }).build());

        // ---- ② 乐器（16 件） ----
        ConfigCategory instrCat = builder.getOrCreateCategory(t("乐器"));
        instrCat.addEntry(eb.startTextDescription(t("L0 关闭 = 该件回原版样本。长度/振幅热生效（下一音符）。")).build());
        for (Map.Entry<String, DspSettings.Instrument> e : s.instruments.entrySet()) {
            final String key = e.getKey();
            DspSettings.Instrument in = e.getValue();
            SubCategoryBuilder sub = eb.startSubCategory(t(displayName(key)));
            sub.add(eb.startBooleanToggle(t("L0 整形"), in.l0)
                    .setTooltip(t("关闭 = 该乐器回原版样本"))
                    .setSaveConsumer(v -> {
                        in.l0 = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startBooleanToggle(t("L1 合并（M3 生效）"), in.l1)
                    .setSaveConsumer(v -> {
                        in.l1 = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startBooleanToggle(t("L2 延音（M4 生效）"), in.l2)
                    .setSaveConsumer(v -> {
                        in.l2 = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startDoubleField(t("平台长度 (ms)"), in.lenMs)
                    .setMin(40.0).setMax(200.0)
                    .setTooltip(t("L0 平台：短=更干净的重触发、尾更短；长=尾更自然、叠音指标变差（A/B 试听调）"))
                    .setSaveConsumer(v -> {
                        in.lenMs = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startDoubleField(t("振幅 (%)"), in.gainPct)
                    .setMin(0.0).setMax(200.0)
                    .setTooltip(t("该乐器总体音量；100 = 默认电平（L0 关闭时同样生效）"))
                    .setSaveConsumer(v -> {
                        in.gainPct = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            instrCat.addEntry(sub.build());
        }

        // ---- ③ L1 参数（M3 生效） ----
        ConfigCategory l1 = builder.getOrCreateCategory(t("L1 参数（M3 生效）"));
        l1.addEntry(eb.startTextDescription(t("合并窗口：1gt 起开始淡出，窗口内来匹配音符 → 取消淡出回 HOLD（Hy4 #5）。")).build());
        l1.addEntry(eb.startDoubleField(t("合并窗口 W (ms)"), s.l1WindowMs)
                .setMin(50.0).setMax(300.0)
                .setSaveConsumer(v -> {
                    s.l1WindowMs = v;
                    NoteGTConfig.applyAll();
                }).build());
        l1.addEntry(eb.startBooleanToggle(t("release-cancel（取消淡出）"), s.l1ReleaseCancel)
                .setSaveConsumer(v -> {
                    s.l1ReleaseCancel = v;
                    NoteGTConfig.applyAll();
                }).build());

        // ---- ④ 高级（每件淡化/压平） ----
        ConfigCategory adv = builder.getOrCreateCategory(t("高级（淡化/压平）"));
        for (Map.Entry<String, DspSettings.Instrument> e : s.instruments.entrySet()) {
            final String key = e.getKey();
            DspSettings.Instrument in = e.getValue();
            SubCategoryBuilder sub = eb.startSubCategory(t(displayName(key)));
            sub.add(eb.startDoubleField(t("淡入 (ms)"), in.fadeInMs)
                    .setMin(0.0).setMax(50.0)
                    .setSaveConsumer(v -> {
                        in.fadeInMs = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startDoubleField(t("淡出 (ms)"), in.fadeOutMs)
                    .setMin(0.0).setMax(50.0)
                    .setSaveConsumer(v -> {
                        in.fadeOutMs = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            sub.add(eb.startBooleanToggle(t("压平自然衰减"), in.flatten)
                    .setTooltip(t("开启 = 中段包络压平（限幅缓变）；打击乐默认关"))
                    .setSaveConsumer(v -> {
                        in.flatten = v;
                        NoteGTConfig.applyInstrument(key);
                    }).build());
            adv.addEntry(sub.build());
        }

        return builder.build();
    }

    private static Component t(String literal) {
        return Component.literal(literal);
    }

    /** 文件名片段 → 中文显示名。 */
    private static String displayName(String name) {
        return switch (name) {
            case "banjo" -> "班卓 banjo";
            case "bassattack" -> "低音 bassattack";
            case "bd" -> "底鼓 bd";
            case "bell" -> "钟 bell";
            case "bit" -> "八音盒 bit";
            case "cow_bell" -> "牛铃 cow_bell";
            case "didgeridoo" -> "迪吉里杜管 didgeridoo";
            case "flute" -> "笛 flute";
            case "guitar" -> "吉他 guitar";
            case "harp2" -> "竖琴 harp2";
            case "hat" -> "踩镲 hat";
            case "icechime" -> "冰铃 icechime";
            case "iron_xylophone" -> "铁木琴 iron_xylophone";
            case "pling" -> "叮 pling";
            case "snare" -> "军鼓 snare";
            case "xylobone" -> "木木琴 xylobone";
            default -> name;
        };
    }
}
