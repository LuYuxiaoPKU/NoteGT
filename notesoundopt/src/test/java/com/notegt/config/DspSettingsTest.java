package com.notegt.config;

import com.google.gson.JsonObject;
import com.notegt.dsp.DspGain;
import com.notegt.dsp.NoteInstruments;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 配置模型测试（AGENTS #31）：默认值 = 定参表 + 路由表；JSON 往返/缺键宽松；增益语义。
 */
public class DspSettingsTest {

	@Test
	public void defaultsMatchTunedTable() {
		DspSettings s = DspSettings.defaults();
		Map<String, NoteInstruments.Params> table = NoteInstruments.table();
		assertEquals(16, s.instruments.size());
		for (Map.Entry<String, NoteInstruments.Params> e : table.entrySet()) {
			DspSettings.Instrument in = s.instrument(e.getKey());
			NoteInstruments.Params p = e.getValue();
			assertEquals(p.lenMs(), in.lenMs, e.getKey() + ".lenMs");
			assertEquals(p.fadeInMs(), in.fadeInMs, e.getKey() + ".fadeInMs");
			assertEquals(p.fadeOutMs(), in.fadeOutMs, e.getKey() + ".fadeOutMs");
			assertEquals(p.flatten(), in.flatten, e.getKey() + ".flatten");
			assertTrue(in.l0, e.getKey() + ".l0 默认开");
			assertEquals(!NoteInstruments.isPercussion(e.getKey()), in.l1, e.getKey() + ".l1 路由表");
			assertEquals(NoteInstruments.isL2Sustain(e.getKey()), in.l2, e.getKey() + ".l2 路由表");
			assertEquals(100.0, in.gainPct, e.getKey() + ".gainPct 默认 100");
		}
		assertTrue(s.l0Master && s.l1Master && s.l2Master, "全局总开关默认全开");
		assertEquals(4000, s.sustainLimitMs);
		assertEquals(100.0, s.l1WindowMs);
		assertTrue(s.l1ReleaseCancel);
	}

	@Test
	public void jsonRoundTrip() {
		DspSettings s = DspSettings.defaults();
		s.l0Master = false;
		s.sustainLimitMs = 6000;
		s.l1WindowMs = 150.0;
		s.l1ReleaseCancel = false;
		DspSettings.Instrument banjo = s.instrument("banjo");
		banjo.l0 = false;
		banjo.lenMs = 70.0;
		banjo.gainPct = 55.0;

		DspSettings back = DspSettings.fromJson(s.toJson());
		assertEquals(s.l0Master, back.l0Master);
		assertEquals(s.sustainLimitMs, back.sustainLimitMs);
		assertEquals(s.l1WindowMs, back.l1WindowMs);
		assertEquals(s.l1ReleaseCancel, back.l1ReleaseCancel);
		DspSettings.Instrument bb = back.instrument("banjo");
		assertEquals(banjo.l0, bb.l0);
		assertEquals(banjo.lenMs, bb.lenMs);
		assertEquals(banjo.gainPct, bb.gainPct);
		// 未改动的乐器保持默认
		DspSettings def = DspSettings.defaults();
		assertEquals(def.instrument("flute").lenMs, back.instrument("flute").lenMs);
		assertEquals(true, back.instrument("flute").l0);
	}

	@Test
	public void missingKeysKeepDefaults() {
		DspSettings back = DspSettings.fromJson(new JsonObject());
		DspSettings def = DspSettings.defaults();
		assertEquals(def.instrument("harp2").lenMs, back.instrument("harp2").lenMs);
		assertEquals(true, back.l0Master);
	}

	@Test
	public void loadMissingFileReturnsDefaults() {
		DspSettings s = DspSettings.load(Path.of("target/no-such-notegt-config.json"));
		assertEquals(DspSettings.defaults().instrument("guitar").lenMs, s.instrument("guitar").lenMs);
	}

	@Test
	public void gainIdentityAt100() {
		float[] x = {0.1f, -0.95f, 0f, 1f};
		assertSame(x, DspGain.scale(x, 100.0)); // 零拷贝
	}

	@Test
	public void gainScaling() {
		float[] x = {0.1f, -0.95f, 1f};
		float[] half = DspGain.scale(x, 50.0);
		float[] zero = DspGain.scale(x, 0.0);
		float[] doubleIt = DspGain.scale(x, 200.0);
		assertArrayEquals(new float[]{0.05f, -0.475f, 0.5f}, half, 1e-7f);
		assertArrayEquals(new float[]{0f, 0f, 0f}, zero, 0f);
		assertArrayEquals(new float[]{0.2f, -1.9f, 2.0f}, doubleIt, 1e-7f);
		// 原数组不被修改
		assertArrayEquals(new float[]{0.1f, -0.95f, 1f}, x, 0f);
	}
}
