package com.notegt.dsp;

import com.notegt.dsp.L0Shaper.ShapeParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2a 交叉核对（守 Hy4 #12）：Java L0Shaper 输出 vs Python l0shape 参考输出，
 * 16 件原版样本 max|diff| 必须 &lt;= 1e-4（float32 舍入量级）。
 *
 * <p>夹具：src/test/resources/dsp/{&lt;name&gt;.wav, &lt;name&gt;.ref, fixtures.json}
 * （tools/gen_dsp_fixtures.py 生成；改定参后需重跑）。
 */
class L0ShaperTest {

    private static final double MAX_DIFF = 1e-4;

    private record Fixture(String name, int rate, double lenMs, double fadeInMs, double fadeOutMs, boolean flatten, int nOut) {
    }

    private static Fixture loadFixture(String name) throws Exception {
        String json = readAll("/dsp/fixtures.json");
        // fixtures.json 由 gen_dsp_fixtures.py 生成（indent=1），定位本条目对象后逐字段解析
        int keyIdx = json.indexOf("\"name\": \"" + name + "\"");
        assertTrue(keyIdx >= 0, "fixtures.json 缺条目: " + name);
        int start = json.lastIndexOf('{', keyIdx);
        int end = json.indexOf('}', keyIdx);
        String row = json.substring(start, end + 1);
        int rate = (int) numField(row, "rate");
        double lenMs = numField(row, "len_ms");
        double fadeInMs = numField(row, "fade_in_ms");
        double fadeOutMs = numField(row, "fade_out_ms");
        boolean flatten = row.contains("\"flatten\": true");
        int nOut = (int) numField(row, "n_out");
        return new Fixture(name, (int) rate, lenMs, fadeInMs, fadeOutMs, flatten, nOut);
    }

    private static double numField(String row, String key) {
        String k = "\"" + key + "\": ";
        int i = row.indexOf(k);
        assertTrue(i >= 0, "字段缺失: " + key + " in " + row);
        int j = i + k.length();
        int e = j;
        while (e < row.length() && (Character.isDigit(row.charAt(e)) || row.charAt(e) == '.' || row.charAt(e) == '-')) {
            e++;
        }
        return Double.parseDouble(row.substring(j, e));
    }

    private static String readAll(String path) throws Exception {
        try (InputStream in = L0ShaperTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "夹具缺失: " + path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            in.transferTo(bo);
            return bo.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(String path) throws Exception {
        try (InputStream in = L0ShaperTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "夹具缺失: " + path);
            return in.readAllBytes();
        }
    }

    /** 32-bit float 单声道 WAV（javax.sound.sampled）。 */
    private static float[] readWav32f(String path) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(readBytes(path)))) {
            AudioFormat fmt = ais.getFormat();
            assertEquals(AudioFormat.Encoding.PCM_FLOAT, fmt.getEncoding(), "夹具必须是 32-bit float WAV");
            assertEquals(32, fmt.getSampleSizeInBits(), "夹具必须是 32-bit float WAV");
            assertEquals(1, fmt.getChannels(), "夹具必须是单声道");
            byte[] raw = ais.readAllBytes();
            ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            float[] x = new float[raw.length / 4];
            for (int i = 0; i < x.length; i++) {
                x[i] = bb.getFloat();
            }
            return x;
        }
    }

    private static float[] readRef(String path) throws Exception {
        byte[] raw = readBytes(path);
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] x = new float[raw.length / 4];
        for (int i = 0; i < x.length; i++) {
            x[i] = bb.getFloat();
        }
        return x;
    }

    @Test
    void crossCheckAll16() throws Exception {
        String json = readAll("/dsp/fixtures.json");
        List<String> names = new ArrayList<>();
        int i = 0;
        while ((i = json.indexOf("\"name\":", i)) >= 0) {
            int q1 = json.indexOf('"', i + 8);
            int q2 = json.indexOf('"', q1 + 1);
            names.add(json.substring(q1 + 1, q2));
            i = q2;
        }
        assertEquals(16, names.size(), "夹具必须是 16 件");

        StringBuilder report = new StringBuilder();
        double worst = 0;
        for (String name : names) {
            Fixture f = loadFixture(name);
            float[] in = readWav32f("/dsp/" + name + ".wav");
            float[] ref = readRef("/dsp/" + name + ".ref");
            ShapeParams p = new ShapeParams(f.lenMs(), f.fadeInMs(), f.fadeOutMs(), f.flatten());
            L0Shaper.Result r = L0Shaper.shape(in, f.rate(), p);
            assertEquals(f.nOut, r.samples.length, name + ": 输出长度");
            double maxDiff = 0;
            int at = -1;
            for (int k = 0; k < ref.length; k++) {
                double d = Math.abs(r.samples[k] - ref[k]);
                if (d > maxDiff) {
                    maxDiff = d;
                    at = k;
                }
            }
            worst = Math.max(worst, maxDiff);
            report.append(String.format("  %-16s len=%6.1fms fade=%4.1f/%4.1f flat=%d  maxDiff=%.2e @%d%n",
                    name, f.lenMs(), f.fadeInMs(), f.fadeOutMs(), f.flatten() ? 1 : 0, maxDiff, at));
            assertTrue(maxDiff <= MAX_DIFF, name + ": max|diff|=" + maxDiff + " > " + MAX_DIFF);
        }
        System.out.println("[L0ShaperTest] Java vs Python 交叉核对（16 件原版）:\n" + report
                + String.format("worst = %.2e (容差 %.0e)%n", worst, MAX_DIFF));
    }

    @Test
    void percussionSpec() throws Exception {
        // 打击乐：fade-in=0 保 attack、4ms 淡出、不压平 → 首样本应保留 attack 电平（非 0）
        for (String name : List.of("bd", "hat", "snare")) {
            Fixture f = loadFixture(name);
            float[] in = readWav32f("/dsp/" + name + ".wav");
            L0Shaper.Result r = L0Shaper.shape(in, f.rate(), ShapeParams.PERCUSSION);
            assertEquals(0, r.fadeIn, name + ": 打击乐 fade-in 必须为 0");
            float firstIn = in[0];
            float firstOut = r.samples[0];
            // 首样本只经过归一（无淡入压零）：|out[0]| ≈ 0.95*|in[0]|/peak，量级不变
            assertTrue(Math.abs(firstOut) > 0.01 * Math.abs(in[0]) / peakOf(in) * 0.5,
                    name + ": attack 被改动（out[0]=" + firstOut + " in[0]=" + firstIn + "）");
        }
    }

    private static double peakOf(float[] x) {
        double p = 0;
        for (float v : x) {
            p = Math.max(p, Math.abs(v));
        }
        return p;
    }
}
