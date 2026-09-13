package com.notegt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.notegt.dsp.NoteInstruments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M1 配置模型（乐器种类级，AGENTS #28/#31）：全局开关 + 16 件 × {l0,l1,l2,平台长度,淡化,压平,振幅}。
 *
 * <p>生效状态：L0 全部参数<b>当前生效</b>（M2a③ 运行时 DSP）；L1 参数 M3 生效、L2 参数 M4 生效
 * ——先行持久化，界面标注。
 *
 * <p>默认值 = CDN 原版定参表（{@link NoteInstruments#table()}）+ 路由表开关
 * （打击乐 L1 关 / L2 = isL2Sustain 七件 / 振幅 100%）。
 *
 * <p>持久化：{@code config/notegt.json}（gson JsonObject 手工映射——缺键取默认、解析失败整体回默认，
 * 永不阻断启动）。热生效语义由调用方实现（改字段 → bump → 运行时从原始 PCM 重整形，下一音符生效）。
 */
public final class DspSettings {

    /** 单乐器配置。字段 volatile：UI 线程写、音频路径读（代际同步兜底，见 NoteDSPRuntime）。 */
    public static final class Instrument {
        public volatile boolean l0;
        public volatile boolean l1;
        public volatile boolean l2;
        /** L0 平台长度 ms（文件时间）。滑条 40–200。 */
        public volatile double lenMs;
        public volatile double fadeInMs;
        public volatile double fadeOutMs;
        public volatile boolean flatten;
        /** 整体振幅 %（0–200，100 = 默认电平；对整形与原版原样输出都生效）。 */
        public volatile double gainPct = 100.0;

        Instrument() {
        }

        Instrument(NoteInstruments.Params p, boolean l1, boolean l2) {
            this.l0 = true;
            this.l1 = l1;
            this.l2 = l2;
            this.lenMs = p.lenMs();
            this.fadeInMs = p.fadeInMs();
            this.fadeOutMs = p.fadeOutMs();
            this.flatten = p.flatten();
        }
    }

    // ---- 全局（L1/L2 为 M3/M4 预留，当前仅持久化） ----
    public volatile boolean l0Master = true;
    public volatile boolean l1Master = true;
    public volatile boolean l2Master = true;
    /** L2 延音上限 ms（M4，预渲染兜底 4s，Hy4 #7）。 */
    public volatile int sustainLimitMs = 4000;
    /** L1 合并窗口 W ms（M3，2gt = 100ms，Hy4 #5）。 */
    public volatile double l1WindowMs = 100.0;
    /** L1 release-cancel（M3，Hy4 #5）。 */
    public volatile boolean l1ReleaseCancel = true;

    /** 16 件（LinkedHashMap 保 UI 顺序 = 定参表声明序）。26.x 铜管（M5）加入表后自动出现。 */
    public final Map<String, Instrument> instruments;

    private transient java.util.concurrent.atomic.AtomicInteger generation;

    private DspSettings() {
        this.instruments = new LinkedHashMap<>();
        this.generation = new java.util.concurrent.atomic.AtomicInteger();
    }

    /** 配置代际：任何改动 bump 后，运行时对过期缓冲重整形（AGENTS #28 热生效契约）。 */
    public int gen() {
        return generation().get();
    }

    public void bump() {
        generation().incrementAndGet();
    }

    private java.util.concurrent.atomic.AtomicInteger generation() {
        if (generation == null) { // gson 不经构造器时兜底（当前手工映射不会触发，防御）
            generation = new java.util.concurrent.atomic.AtomicInteger();
        }
        return generation;
    }

    /** 按名取配置；未知名（26.x 铜管未入表）→ null = 不接管、原版原样。 */
    public Instrument instrument(String name) {
        return instruments.get(name);
    }

    /** 默认配置 = 定参表 + 路由表开关（AGENTS #28）。 */
    public static DspSettings defaults() {
        DspSettings s = new DspSettings();
        for (Map.Entry<String, NoteInstruments.Params> e : NoteInstruments.table().entrySet()) {
            String name = e.getKey();
            s.instruments.put(name, new Instrument(e.getValue(),
                    !NoteInstruments.isPercussion(name), NoteInstruments.isL2Sustain(name)));
        }
        return s;
    }

    // ---------- JSON 持久化（config/notegt.json） ----------

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("l0Master", l0Master);
        o.addProperty("l1Master", l1Master);
        o.addProperty("l2Master", l2Master);
        o.addProperty("sustainLimitMs", sustainLimitMs);
        o.addProperty("l1WindowMs", l1WindowMs);
        o.addProperty("l1ReleaseCancel", l1ReleaseCancel);
        JsonObject insts = new JsonObject();
        for (Map.Entry<String, Instrument> e : instruments.entrySet()) {
            Instrument in = e.getValue();
            JsonObject io = new JsonObject();
            io.addProperty("l0", in.l0);
            io.addProperty("l1", in.l1);
            io.addProperty("l2", in.l2);
            io.addProperty("lenMs", in.lenMs);
            io.addProperty("fadeInMs", in.fadeInMs);
            io.addProperty("fadeOutMs", in.fadeOutMs);
            io.addProperty("flatten", in.flatten);
            io.addProperty("gainPct", in.gainPct);
            insts.add(e.getKey(), io);
        }
        o.add("instruments", insts);
        return o;
    }

    /** 从 JsonObject 装载到默认实例之上：缺键保留默认、类型错回默认（宽松前向兼容）。 */
    public static DspSettings fromJson(JsonObject o) {
        DspSettings s = defaults();
        if (o == null) {
            return s;
        }
        s.l0Master = bool(o, "l0Master", s.l0Master);
        s.l1Master = bool(o, "l1Master", s.l1Master);
        s.l2Master = bool(o, "l2Master", s.l2Master);
        s.sustainLimitMs = intOf(o, "sustainLimitMs", s.sustainLimitMs);
        s.l1WindowMs = dbl(o, "l1WindowMs", s.l1WindowMs);
        s.l1ReleaseCancel = bool(o, "l1ReleaseCancel", s.l1ReleaseCancel);
        JsonElement insts = o.get("instruments");
        if (insts != null && insts.isJsonObject()) {
            for (Map.Entry<String, Instrument> def : s.instruments.entrySet()) {
                JsonElement el = insts.getAsJsonObject().get(def.getKey());
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject io = el.getAsJsonObject();
                Instrument in = def.getValue();
                in.l0 = bool(io, "l0", in.l0);
                in.l1 = bool(io, "l1", in.l1);
                in.l2 = bool(io, "l2", in.l2);
                in.lenMs = dbl(io, "lenMs", in.lenMs);
                in.fadeInMs = dbl(io, "fadeInMs", in.fadeInMs);
                in.fadeOutMs = dbl(io, "fadeOutMs", in.fadeOutMs);
                in.flatten = bool(io, "flatten", in.flatten);
                in.gainPct = dbl(io, "gainPct", in.gainPct);
            }
        }
        return s;
    }

    /** 读取配置文件；不存在/解析失败 → 默认（不抛）。 */
    public static DspSettings load(Path path) {
        try {
            if (Files.exists(path)) {
                return fromJson(new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class));
            }
        } catch (Exception e) {
            System.err.println("[notegt] 配置解析失败，使用默认: " + e);
        }
        return defaults();
    }

    /** 落盘（pretty）。调用方自行 try/catch。 */
    public void save(Path path) throws IOException {
        Gson g = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, g.toJson(toJson()), StandardCharsets.UTF_8);
    }

    private static boolean bool(JsonObject o, String k, boolean d) {
        JsonElement e = o.get(k);
        return (e != null && e.isJsonPrimitive()) ? e.getAsBoolean() : d;
    }

    private static double dbl(JsonObject o, String k, double d) {
        JsonElement e = o.get(k);
        return (e != null && e.isJsonPrimitive()) ? e.getAsDouble() : d;
    }

    private static int intOf(JsonObject o, String k, int d) {
        JsonElement e = o.get(k);
        return (e != null && e.isJsonPrimitive()) ? e.getAsInt() : d;
    }
}
