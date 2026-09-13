package com.notegt.client;

import com.notegt.NoteGTMod;
import com.notegt.config.DspSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * M1：配置文件（{@code config/notegt.json}）+ 设置 → 运行时热生效。
 *
 * <p>生命周期：{@link #init()} 在 client 初始化时读盘（不存在/损坏 → 定参默认）；
 * 界面改动即时 {@code save()} + 重整形（下一音符生效，AGENTS #28）。
 */
public final class NoteGTConfig {

    /** 运行时唯一配置源：界面写、NoteDSPRuntime 读（volatile 字段 + 代际同步）。 */
    public static volatile DspSettings SETTINGS = DspSettings.defaults();

    private NoteGTConfig() {
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("notegt.json");
    }

    public static void init() {
        Path p = configPath();
        DspSettings loaded = DspSettings.load(p);
        boolean fromFile = false;
        try {
            fromFile = java.nio.file.Files.exists(p);
        } catch (Exception ignored) {
        }
        SETTINGS = loaded;
        NoteGTMod.LOGGER.info("NoteGT config: {}（{} 件；L0 总开关={}，L1(M3)={}，L2(M4)={}）",
                fromFile ? "已加载 " + p : "默认（无配置文件）",
                loaded.instruments.size(), loaded.l0Master, loaded.l1Master, loaded.l2Master);
    }

    public static void save() {
        try {
            SETTINGS.save(configPath());
        } catch (Exception e) {
            NoteGTMod.LOGGER.error("NoteGT config: 保存失败 {}", configPath(), e);
        }
    }

    /** 全局改动（总开关/延音上限/L1 参数）→ 全部已接管缓冲重整形。 */
    public static void applyAll() {
        save();
        NoteDSPRuntime.onConfigChanged();
    }

    /** 单件改动（该件开关/长度/振幅/淡化）→ 只重整形该件（拖动滑条不卡其他件）。 */
    public static void applyInstrument(String instrument) {
        save();
        NoteDSPRuntime.onInstrumentChanged(instrument);
    }
}
