package com.notegt.dsp;

/**
 * 整体振幅（用户 2026-09-13 要求：每种音符盒声音一个总体振幅调节，AGENTS #31）。
 *
 * <p>语义：对**最终输出**（L0 整形后或 L0 关闭的原样 PCM）乘 gainPct/100。
 * 100 = 恒等（零拷贝）；量化钳制（±1.0 → 16bit）由回写侧 toPcm 负责。
 * 热生效：改值 → 从原始重整形（含本增益）→ 下一音符起新电平。
 */
public final class DspGain {

    private DspGain() {
    }

    /** @return 缩放后的副本；gainPct==100 时直接返回原数组（零拷贝）。 */
    public static float[] scale(float[] x, double gainPct) {
        if (gainPct == 100.0) {
            return x;
        }
        float g = (float) (gainPct / 100.0);
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = x[i] * g;
        }
        return out;
    }
}
