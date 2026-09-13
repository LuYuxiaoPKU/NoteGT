package com.notegt.dsp;

import java.util.Map;

/**
 * 原版 16 件 note 样本的 L0 定参表（CDN 现行官方，SHA1 已验证，AGENTS #25/#27）。
 *
 * <p>参数 = verify-sweeps-vanilla/ 逐样本扫描定参（docs/09 §11.10），
 * 与 {@code tools/gen_dsp_fixtures.py} 的 TUNED 表同源——交叉核对夹具即按此表生成。
 * 打击乐 = Hy4 5b 规格（48ms 截尾 + 4ms 淡出、fade-in=0、不压平）。
 *
 * <p>按 note 文件路径名匹配（任意命名空间：用户资源包若替换 note/* 文件同样生效，
 * 参数为原版定参——用户包样本需单独定参时由 M1 配置覆盖）。
 * 未知名称（如 26.x 铜管）→ {@code null} = 不整形（M5 补参）。
 */
public final class NoteInstruments {

    /** L0 整形参数（文件时间，ms）。 */
    public record Params(String name, double lenMs, double fadeInMs, double fadeOutMs, boolean flatten) {
        public static final Params PERCUSSION = new Params("percussion", 48.0, 0.0, 4.0, false);
    }

    private static final Map<String, Params> VANILLA_16 = Map.ofEntries(
            Map.entry("banjo", new Params("banjo", 62.5, 8.0, 8.0, true)),
            Map.entry("bassattack", new Params("bassattack", 62.5, 8.0, 8.0, true)),
            Map.entry("bd", Params.PERCUSSION),
            Map.entry("bell", new Params("bell", 150.0, 1.4, 1.4, true)),
            Map.entry("bit", new Params("bit", 57.5, 8.0, 8.0, true)),
            Map.entry("cow_bell", new Params("cow_bell", 55.0, 2.7, 2.7, true)),
            Map.entry("didgeridoo", new Params("didgeridoo", 62.5, 8.0, 8.0, true)),
            Map.entry("flute", new Params("flute", 112.5, 2.7, 2.7, true)),
            Map.entry("guitar", new Params("guitar", 155.0, 8.0, 8.0, true)),
            Map.entry("harp2", new Params("harp2", 62.5, 8.0, 8.0, true)),
            Map.entry("hat", Params.PERCUSSION),
            Map.entry("icechime", new Params("icechime", 52.5, 1.4, 1.4, true)),
            Map.entry("iron_xylophone", new Params("iron_xylophone", 65.0, 8.0, 8.0, true)),
            Map.entry("pling", new Params("pling", 60.0, 8.0, 8.0, true)),
            Map.entry("snare", Params.PERCUSSION),
            Map.entry("xylobone", new Params("xylobone", 52.5, 1.4, 1.4, true)));

    private NoteInstruments() {
    }

    /**
     * 按 note 文件路径匹配。
     * [1.21.11 已验证] {@code Sound.getPath()} = FileToIdConverter("sounds",".ogg") 输出
     * = "sounds/note/&lt;name&gt;.ogg"（sounds.json 条目 "note/harp2" + 前缀）；
     * 兼容裸 "note/&lt;name&gt;.ogg" 形态。
     */
    public static Params byNotePath(String path) {
        if (path == null || !path.endsWith(".ogg")) {
            return null;
        }
        String p = path.startsWith("sounds/") ? path.substring("sounds/".length()) : path;
        if (!p.startsWith("note/")) {
            return null;
        }
        String name = p.substring("note/".length(), p.length() - ".ogg".length());
        return VANILLA_16.get(name);
    }

    /** 由乐器名还原 getPath() 形态（sounds/note/<name>.ogg）。 */
    public static String notePath(String name) {
        return "sounds/note/" + name + ".ogg";
    }

    /** 从 getPath() 形态提取乐器文件名片段（bd/hat/snare/…）。 */
    public static String instrumentName(String path) {
        String p = path.startsWith("sounds/") ? path.substring("sounds/".length()) : path;
        return p.substring("note/".length(), p.length() - ".ogg".length());
    }

    /** 打击乐判定（L1 关闭 / L2 不适用，Hy4 5b）。 */
    public static boolean isPercussion(String name) {
        return "bd".equals(name) || "hat".equals(name) || "snare".equals(name);
    }

    /** L2 延音乐器（原版，v5 判据 + didgeridoo span-1.0 定参，AGENTS #27）。M4 用。 */
    public static boolean isL2Sustain(String name) {
        return switch (name) {
            case "bit", "bell", "bassattack", "harp2", "iron_xylophone", "pling", "didgeridoo" -> true;
            default -> false;
        };
    }
}
