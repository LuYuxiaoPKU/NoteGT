package com.notegt.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.notegt.NoteGTMod;
import com.notegt.client.mixin.SoundBufferAccess;
import com.notegt.config.DspSettings;
import com.notegt.dsp.DspGain;
import com.notegt.dsp.L0Shaper;
import com.notegt.dsp.NoteInstruments;
import net.minecraft.resources.Identifier;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 运行时 DSP 挂点（M2a ③，AGENTS #29）：对 note/* 缓冲在懒上传窗口内做 L0 重整形。
 *
 * <p>架构 [1.21.11 已验证]：
 * <ul>
 *   <li>PCM 在 {@code SoundBuffer.data}（@Nullable ByteBuffer，懒上传后置 null）；
 *       上传发生在播放路径的 {@code getAlBuffer()}（alGenBuffers+alBufferData 后置 data=null）。</li>
 *   <li><b>接管点 = {@code SoundBufferLibrary.getCompleteBuffer} 的 {@code @Overwrite}</b>：
 *       委托原方法（computeIfAbsent 解码）后，把返回 future 包一层 {@code thenApply} ——
 *       缓冲对象刚由解码器创建、路径已知、data 必可读（尚未上传），且接管回调成为原 future
 *       的依赖节点、播放方回调挂在包装 future 上 → 结构上保证「首整形先于首次上传」，无竞态。
 *       （whenComplete 方案已废：future 在 hook 返回后、播放方 thenAccept 注册前完成的窗口内，
 *       上传回调可先于 our 回调执行 → 读 data=null。实测 bd 组命中此竞态。
 *       @Redirect/@Invoker 方案亦废：见 SoundBufferLibraryMixin 注释。）</li>
 *   <li><b>上传前兜底 = {@code SoundBuffer.getAlBuffer()} HEAD</b>：配置代际过期则
 *       discard + 从原始重整形 + 换 data → 本次上传即新参数结果。</li>
 *   <li>缓冲对象经 {@code SoundBufferLibrary.cache}（key = 文件路径 Identifier）共享；
 *       资源重载走 {@code clear()}（AL 丢弃 + 缓存清空）→ 新缓冲对象 → 自动重新接管。</li>
 *   <li>解码器输出 16-bit 有符号小端（OggSoundDecoder），{@code AudioFormat} final 不可换；
 *       声道数可变（原版 CDN 全 mono；NBS 风格资源包如 (Ballad|谣) 为 stereo）
 *       → stereo 做平均降混、回写时双声道复制，DSP 恒为单声道数学。</li>
 * </ul>
 *
 * <p>热改参：原始 PCM 常驻 {@link State#original} → 改参 = 全部已接管缓冲
 * discardAlBuffer + 从原始重整形换入（下一音符起新音色，AGENTS #28「下一音符生效」）。
 * 量化差 ≤0.5 LSB（16-bit 回写）；交叉核对的位级一致性在 float32 管线内成立（AGENTS #30）。
 */
public final class NoteDSPRuntime {

	/** SoundBuffer 对象 → 状态（原始 PCM 常驻）。Weak：资源重载后 GC 自动回收。 */
	private static final Map<SoundBuffer, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	/** 当前配置（M1：NoteGTConfig.SETTINGS；改值 → bump → 过期缓冲重整形）。 */
	private static DspSettings cfg() {
		return NoteGTConfig.SETTINGS;
	}

	/** 开发验证：首整形时把 原始/整形 缓冲转储为 float32 WAV（离线核对用，正式发布前移除）。 */
	static final boolean DEV_DUMP = true;
	static final String DEV_DUMP_DIR = "C:\\00_Data\\RGM\\NoteGT\\_sources\\dsp-dump";

	private NoteDSPRuntime() {
	}

	private static final class State {
		final float[] original;
		final AudioFormat format;
		final String instrument;
		/** 上次应用时的配置代际（-1 = 未应用）。 */
		volatile int lastAppliedGen = -1;
		volatile boolean l0Applied;
		volatile boolean dumped;

		State(float[] original, AudioFormat format, String instrument) {
			this.original = original;
			this.format = format;
			this.instrument = instrument;
		}
	}

	/**
	 * 挂点回调（{@code getCompleteBuffer} @Overwrite 的 thenApply 包装内）：
	 * 缓冲对象刚创建、路径已知。仅 note/* 且有名定参的样本被接管；首整形在此完成
	 * （data 必可读，无上传竞态）。每次播放都会回调，已有状态的缓冲为廉价 no-op。
	 */
	public static void onBufferCreated(Identifier path, SoundBuffer buf) {
		String pathStr = path == null ? null : path.getPath();
		NoteInstruments.Params p = NoteInstruments.byNotePath(pathStr);
		if (p == null || buf == null) {
			return;
		}
		String instrument = NoteInstruments.instrumentName(pathStr);
		try {
			State st;
			synchronized (STATES) {
				if (STATES.containsKey(buf)) {
					return;
				}
				SoundBufferAccess acc = (SoundBufferAccess) buf;
				st = new State(readPcm(acc), acc.notegt$format(), instrument);
				STATES.put(buf, st);
				applyConfig(st, acc);
			}
			NoteGTMod.LOGGER.info("NoteGT DSP: {} 已接管（{} 样本 {} → L0 {}，thread={}）",
					instrument, st.original.length, channelDesc(st.format),
					l0On(instrument) ? "整形" : "原样", Thread.currentThread().getName());
		} catch (Throwable t) {
			NoteGTMod.LOGGER.error("NoteGT DSP dev: {} 接管异常", instrument, t);
		}
	}

	/**
	 * Mixin 入口（{@code SoundBuffer.getAlBuffer()} HEAD）：上传前兜底。
	 * 代际过期（配置已改但缓冲未重整形，例如改参后未触发显式重整形、或首次上传路径变化）
	 * → discard 旧 AL + 从原始重整形 + 换 data，本次上传即新参数结果。
	 */
	public static void onBeforeUpload(SoundBuffer buf) {
		State st;
		synchronized (STATES) {
			st = STATES.get(buf);
		}
		if (st == null || st.lastAppliedGen == cfg().gen()) {
			return;
		}
		try {
			SoundBufferAccess acc = (SoundBufferAccess) buf;
			synchronized (STATES) {
				applyConfig(st, acc);
			}
		} catch (Throwable t) {
			NoteGTMod.LOGGER.error("NoteGT DSP dev: {} 上传前重整形异常", st.instrument, t);
		}
	}

	/** Mixin 入口：{@code SoundBufferLibrary.clear()}（资源重载 / F3+T）→ 状态随旧缓冲释放。 */
	public static void onLibraryClear() {
		synchronized (STATES) {
			int n = STATES.size();
			STATES.clear();
			if (n > 0) {
				NoteGTMod.LOGGER.info("NoteGT DSP: 资源重载，释放 {} 个缓冲状态（新缓冲自动重接管）", n);
			}
		}
	}

	/** 配置变更（全局：总开关/延音上限/L1 参数）→ 全部已接管缓冲重整形（= onInstrumentChanged(null)）。 */
	public static void onConfigChanged() {
		onInstrumentChanged(null);
	}

	/**
	 * 单件（或全局）配置变更 → 从原始 PCM 重整形换入（热生效，下一音符起新音色）。
	 * @param instrument 乐器名；null = 全部已接管缓冲。
	 */
	public static void onInstrumentChanged(String instrument) {
		cfg().bump();
		synchronized (STATES) {
			for (Map.Entry<SoundBuffer, State> e : STATES.entrySet()) {
				if (instrument != null && !e.getValue().instrument.equals(instrument)) {
					continue;
				}
				try {
					applyConfig(e.getValue(), (SoundBufferAccess) e.getKey());
				} catch (Throwable t) {
					NoteGTMod.LOGGER.error("NoteGT DSP dev: {} 重整形异常", e.getValue().instrument, t);
				}
			}
		}
		NoteGTMod.LOGGER.info("NoteGT DSP: 参数热重整形完成（{}，影响 {} 件）",
				instrument == null ? "全局" : instrument, STATES.size());
	}

	private static boolean l0On(String instrument) {
		DspSettings.Instrument in = cfg().instrument(instrument);
		return cfg().l0Master && in != null && in.l0;
	}

	/**
	 * 按当前配置把（重）整形结果换入 SoundBuffer（调用方持有 STATES 锁）。
	 * 换入 = discardAlBuffer（丢已上传 AL 缓冲）+ 换 data + hasAlBuffer=false → 下次 getAlBuffer 重上传。
	 */
	private static void applyConfig(State st, SoundBufferAccess acc) {
		int g = cfg().gen();
		if (st.lastAppliedGen == g) {
			return;
		}
		DspSettings.Instrument is = cfg().instrument(st.instrument);
		float[] out;
		if (l0On(st.instrument) && is != null) {
			out = L0Shaper.shape(st.original, (int) st.format.getSampleRate(),
					new L0Shaper.ShapeParams(is.lenMs, is.fadeInMs, is.fadeOutMs, is.flatten)).samples;
			st.l0Applied = true;
		} else {
			out = st.original.clone();
			st.l0Applied = false;
		}
		// 整体振幅（M1：0–200%，100 = 恒等零拷贝；对整形与原版原样输出都生效）
		out = DspGain.scale(out, is == null ? 100.0 : is.gainPct);
		acc.notegt$discardAlBuffer();
		acc.notegt$setHasAlBuffer(false);
		acc.notegt$setData(toPcm(out, st.format.getChannels()));
		st.lastAppliedGen = g;
		dumpIfDev(st, out);
	}

	/** DEV_DUMP：转储 原始（MC 解码 16bit→float32 单声道）与 整形输出（16bit 量化前）float32 WAV。 */
	private static void dumpIfDev(State st, float[] shaped) {
		if (!DEV_DUMP || st.dumped) {
			return;
		}
		try {
			int rate = (int) st.format.getSampleRate();
			writeF32Wav(DEV_DUMP_DIR + "\\" + st.instrument + "-original.f32.wav", st.original, rate);
			writeF32Wav(DEV_DUMP_DIR + "\\" + st.instrument + "-shaped.f32.wav", shaped, rate);
			st.dumped = true;
			NoteGTMod.LOGGER.info("NoteGT DSP dev: {} 已转储 original/shaped → {}", st.instrument, DEV_DUMP_DIR);
		} catch (Exception e) {
			NoteGTMod.LOGGER.warn("NoteGT DSP dev: 转储失败（{}）", st.instrument, e.toString());
		}
	}

	private static String channelDesc(AudioFormat fmt) {
		return fmt.getChannels() > 1 ? fmt.getChannels() + "ch" : "mono";
	}

	/**
	 * 16-bit 有符号小端 PCM → float32 单声道（±1.0 = 满刻度）。
	 * mono 直读；stereo 平均降混（DSP 恒单声道）。duplicate 不动原 buffer 位置。
	 */
	private static float[] readPcm(SoundBufferAccess acc) {
		ByteBuffer bb = acc.notegt$data();
		if (bb == null) {
			throw new IllegalStateException("SoundBuffer.data 已为 null（AL 已上传）——DSP 接管晚于首播放");
		}
		AudioFormat fmt = acc.notegt$format();
		if (fmt.getSampleSizeInBits() != 16) {
			throw new IllegalStateException("期望 16-bit PCM，实际 " + fmt);
		}
		int ch = fmt.getChannels();
		if (ch < 1 || ch > 2) {
			throw new IllegalStateException("期望 1/2 声道，实际 " + fmt);
		}
		ByteBuffer src = bb.duplicate();
		src.order(ByteOrder.LITTLE_ENDIAN);
		int frames = src.remaining() / (2 * ch);
		float[] x = new float[frames];
		if (ch == 1) {
			for (int i = 0; i < frames; i++) {
				x[i] = src.getShort() / 32768f;
			}
		} else {
			for (int i = 0; i < frames; i++) {
				short l = src.getShort();
				short r = src.getShort();
				x[i] = (l + r) / 2.0f / 32768f;
			}
		}
		return x;
	}

	/** float32 单声道 → 16-bit 有符号小端 direct buffer（stereo 格式则双声道复制；AL alBufferData 可直读）。 */
	private static ByteBuffer toPcm(float[] x, int channels) {
		ByteBuffer bb = ByteBuffer.allocateDirect(x.length * 2 * channels);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		for (float v : x) {
			float c = Math.max(-1f, Math.min(1f, v));
			short s = (short) Math.round(c * 32767f);
			bb.putShort(s);
			if (channels > 1) {
				bb.putShort(s);
			}
		}
		bb.flip();
		return bb;
	}

	/** 调试/测试用：返回指定乐器的已接管状态（无则 null）。 */
	public static State stateFor(String instrument) {
		synchronized (STATES) {
			for (State st : STATES.values()) {
				if (st.instrument.equals(instrument)) {
					return st;
				}
			}
		}
		return null;
	}

	/** 极简 float32 单声道 WAV（RIFF/IEEE float，format=3）。 */
	static void writeF32Wav(String path, float[] x, int rate) throws java.io.IOException {
		int n = x.length;
		java.io.File f = new java.io.File(path);
		if (f.getParentFile() != null) {
			f.getParentFile().mkdirs();
		}
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(
				new java.io.BufferedOutputStream(new java.io.FileOutputStream(f)))) {
			java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
			h.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			h.putInt(36 + 4 * n);
			h.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			h.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			h.putInt(16);
			h.putShort((short) 3);
			h.putShort((short) 1);
			h.putInt(rate);
			h.putInt(rate * 4);
			h.putShort((short) 4);
			h.putShort((short) 32);
			h.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			h.putInt(4 * n);
			out.write(h.array());
			for (float v : x) {
				// WAV float 数据必须 little-endian（DataOutputStream.writeInt 是大端，勿用）
				int bits = Float.floatToRawIntBits(v);
				out.writeByte(bits & 0xFF);
				out.writeByte((bits >> 8) & 0xFF);
				out.writeByte((bits >> 16) & 0xFF);
				out.writeByte((bits >> 24) & 0xFF);
			}
		}
	}
}
