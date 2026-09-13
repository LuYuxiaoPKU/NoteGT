package com.notegt.dsp;

import com.notegt.dsp.LoopDetector.Params;
import com.notegt.dsp.LoopDetector.Rejected;
import com.notegt.dsp.LoopDetector.Result;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2a 交叉核对：Java LoopDetector vs Python loopdetect v5 参考（loop-ref.json）。
 *
 * <p>参数 = 规范 v5 全库（ncc 0.95 / span 0.5）+ didgeridoo 定参（ncc 0.90 / span 1.0，
 * AGENTS #27），与 result-v5-vanilla12111 及 test-didgeridoo-span1 同源。
 * 整件 = L2 路由回归测试：9 件 OK（7 PASS / 2 FAIL 判据复核）+ 7 件 REJECT（原因一致）。
 */
class LoopDetectorTest {

    private static final Pattern KEY = Pattern.compile("^ \"([a-z0-9_]+)\"\\s*:\\s*\\{$", Pattern.MULTILINE);

    @Test
    void crossCheckAll16() throws Exception {
        String json = readAll("/dsp/loop-ref.json");
        List<String> names = new ArrayList<>();
        Matcher m = KEY.matcher(json);
        while (m.find()) {
            names.add(m.group(1));
        }
        assertEquals(16, names.size(), "loop-ref 必须是 16 件");

        StringBuilder report = new StringBuilder();
        int nOk = 0, nRej = 0;
        for (String name : names) {
            int ks = json.indexOf("\"" + name + "\"", 0);
            int brace = json.indexOf('{', ks);
            int close = json.indexOf("\n }", brace);
            if (close < 0) {
                close = json.indexOf('\n', json.indexOf("verdict", brace) > 0 ? json.indexOf("verdict", brace) : brace);
            }
            String block = json.substring(brace, close + 1);

            int rate = readWavRate(name);
            float[] in = readWav32f(name);
            Params p = new Params(20.0, dfield(block, "nccMin"), dfield(block, "span"),
                    0.005, true, 30.0, 40.0);

            boolean refOk = !block.contains("\"ok\": false");
            if (!refOk) {
                String refReason = sfield(block, "reason");
                Rejected rj = assertThrows(Rejected.class,
                        () -> LoopDetector.detect(in, rate, p),
                        name + ": 应 REJECT（参考: " + refReason + "）");
                assertEquals(refReason, rj.reason, name + ": REJECT 原因");
                nRej++;
                report.append(String.format("  %-16s REJECT 一致（%s）%n", name, refReason));
                continue;
            }

            Result r = LoopDetector.detect(in, rate, p);
            nOk++;
            StringBuilder line = new StringBuilder();
            checkExact(name, "a", dfield(block, "a"), r.a, line);
            checkExact(name, "L", dfield(block, "L"), r.L, line);
            checkExact(name, "k", dfield(block, "k"), r.k, line);
            checkExact(name, "subharmMult", dfield(block, "subharmMult"), r.subharmMult, line);
            checkClose(name, "f0", dfield(block, "f0"), r.f0, 0.01, line);
            checkClose(name, "ncc", dfield(block, "ncc"), r.ncc, 1e-3, line);
            checkClose(name, "loopMs", dfield(block, "loopMs"), r.loopMs, 0.01, line);
            checkClose(name, "rateHz", dfield(block, "rateHz"), r.rateHz, 0.01, line);
            checkClose(name, "loopRippleDb", dfield(block, "loopRippleDb"), r.loopRippleDb, 0.05, line);
            checkClose(name, "longRippleDb", dfield(block, "longRippleDb"), r.longRippleDb, 0.05, line);
            checkClose(name, "modDbc", dfield(block, "modDbc"), r.modDbc, 0.2, line);
            checkClose(name, "seamRatio", dfield(block, "seamRatio"), r.seamRatio, 0.02, line);
            checkClose(name, "seamErrDeg", dfield(block, "seamErrDeg"), r.seamErrDeg, 0.05, line);
            checkClose(name, "fadeNcc", dfield(block, "fadeNcc"), r.fadeNcc, 5e-3, line);
            checkClose(name, "fadeDipDb", dfield(block, "fadeDipDb"), r.fadeDipDb, 0.05, line);
            checkClose(name, "periodErrDeg", dfield(block, "periodErrDeg"), r.periodErrDeg, 0.05, line);
            checkClose(name, "xfadeMs", dfield(block, "xfadeMs"), r.xfadeSec * 1000.0, 0.5, line);
            assertEquals(sfield(block, "verdict"), r.verdict, name + ": verdict");
            assertTrue(line.length() == 0, name + ": 字段偏差 " + line);
            report.append(String.format("  %-16s a=%d L=%d k=%d f0=%.2f seam=%.2f lp=%.2f mod=%.2f  %s  %s%n",
                    name, r.a, r.L, r.k, r.f0, r.seamErrDeg, r.loopRippleDb, r.modDbc,
                    r.verdict, line.length() > 0 ? ("MISMATCH: " + line) : "OK"));
        }
        System.out.println("[LoopDetectorTest] Java vs Python loopdetect v5 交叉核对（16 件原版）:\n"
                + report + String.format("OK=%d REJECT=%d%n", nOk, nRej));
        assertEquals(9, nOk);
        assertEquals(7, nRej);
    }

    private static void checkExact(String name, String field, double ref, int got, StringBuilder line) {
        if (ref != got) {
            line.append(field).append("(").append((int) ref).append("!=").append(got).append(") ");
        }
    }

    private static void checkClose(String name, String field, double ref, double got, double tol, StringBuilder line) {
        boolean rN = Double.isNaN(ref), gN = Double.isNaN(got);
        if (rN != gN) {
            line.append(field).append("(NaN 不一致) ");
            return;
        }
        if (rN) {
            return;
        }
        double d = Math.abs(got - ref);
        if (d > tol) {
            line.append(field).append(String.format("(%.4g vs %.4g) ", ref, got));
        }
    }

    // ── 极简 JSON 字段提取（loop-ref.json 由 gen_dsp_fixtures.py 生成，格式固定） ──

    private static double dfield(String block, String key) {
        String k = "\"" + key + "\": ";
        int i = block.indexOf(k);
        assertTrue(i >= 0, "字段缺失: " + key + " in " + block);
        int j = i + k.length();
        int e = j;
        while (e < block.length()) {
            char c = block.charAt(e);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == 'e' || c == '+') {
                e++;
            } else {
                break;
            }
        }
        String v = block.substring(j, e);
        return v.equals("") || v.equals("NaN") ? Double.NaN : Double.parseDouble(v);
    }

    private static String sfield(String block, String key) {
        String k = "\"" + key + "\": \"";
        int i = block.indexOf(k);
        assertTrue(i >= 0, "字段缺失: " + key);
        int j = i + k.length();
        int e = block.indexOf('"', j);
        return block.substring(j, e);
    }

    // ── 32-bit float 单声道 WAV 读取（与 L0ShaperTest 相同夹具） ──

    private static String readAll(String path) throws Exception {
        try (InputStream in = LoopDetectorTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "夹具缺失: " + path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            in.transferTo(bo);
            return bo.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBytes(String path) throws Exception {
        try (InputStream in = LoopDetectorTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "夹具缺失: " + path);
            return in.readAllBytes();
        }
    }

    private static int readWavRate(String name) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(readBytes("/dsp/" + name + ".wav")))) {
            return (int) ais.getFormat().getSampleRate();
        }
    }

    private static float[] readWav32f(String name) throws Exception {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(readBytes("/dsp/" + name + ".wav")))) {
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
}
