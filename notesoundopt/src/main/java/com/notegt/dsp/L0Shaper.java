package com.notegt.dsp;

/**
 * L0 样本整形（Java 移植，数学与 tools/l0shape.py 逐行对应）—— 运行时 DSP 核心。
 *
 * <p>规格（Hy4 第三/四轮，AGENTS #4/#5b）：
 * <ul>
 *   <li>melodic：前 lenMs 的样本 + 首尾 sin² 等功率淡化 + 中间压平自然衰减（RMS 包络除法，
 *       限幅 [0.5, 2.5] 防 pumping，17 点 hanning 平滑）；</li>
 *   <li>打击乐：48ms 截尾 + fade-in=0（保 attack）+ fade-out 4ms，<b>不压平</b>。</li>
 * </ul>
 * 输出：0.95 峰值归一的 float32（与 Python 管线一致）。
 *
 * <p>移植核对：{@code L0ShaperTest} 用 16 件原版样本的 Python 参考输出做 max|diff| 交叉核对
 * （守 Hy4 #12「先改样本验证，再写模组」—— Java 输出必须与已验证管线一致才算合格）。
 */
public final class L0Shaper {

    private static final double FLATTEN_MIN = 0.5;
    private static final double FLATTEN_MAX = 2.5;

    private L0Shaper() {
    }

    /** 整形参数（文件时间，ms）。 */
    public record ShapeParams(double lenMs, double fadeInMs, double fadeOutMs, boolean flatten) {
        public static ShapeParams melodic(double lenMs, double fadeMs) {
            return new ShapeParams(lenMs, fadeMs, fadeMs, true);
        }

        /** Hy4 5b 打击乐规格：48ms + 4ms 淡出、无淡入、不压平。 */
        public static final ShapeParams PERCUSSION = new ShapeParams(48.0, 0.0, 4.0, false);
    }

    /** 整形结果。 */
    public static final class Result {
        public final float[] samples;
        public final int n;
        public final int fadeIn;
        public final int fadeOut;
        public final double peakOut;

        Result(float[] samples, int n, int fadeIn, int fadeOut, double peakOut) {
            this.samples = samples;
            this.n = n;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
            this.peakOut = peakOut;
        }
    }

    /**
     * 整形主函数。
     *
     * @param x    原始单声道样本（float32，文件原生采样率）
     * @param rate 采样率（Hz）
     * @param p    参数
     * @return 0.95 峰值归一的 float32 输出
     */
    public static Result shape(float[] x, int rate, ShapeParams p) {
        int n = (int) Math.round(p.lenMs() / 1000.0 * rate);
        if (n >= x.length) {
            n = x.length; // 样本更短 → 整体使用，淡出贴尾
        }
        double[] seg = new double[n];
        for (int i = 0; i < n; i++) {
            seg[i] = x[i];
        }

        int fi = clamp((int) Math.round(p.fadeInMs() / 1000.0 * rate), 0, n / 4);
        int fo = clamp((int) Math.round(p.fadeOutMs() / 1000.0 * rate), 0, n / 4);

        double[] g = new double[n];
        java.util.Arrays.fill(g, 1.0);
        // 首段 sin² 淡入（等功率）
        if (fi > 1) {
            for (int i = 0; i < fi; i++) {
                double s = Math.sin(Math.PI * i / (2.0 * fi));
                g[i] = s * s;
            }
        }
        // 尾段 sin²（cos² 形状）淡出
        if (fo > 1) {
            for (int i = 0; i < fo; i++) {
                double s = Math.sin(Math.PI * (fo - i) / (2.0 * fo));
                g[n - fo + i] = s * s;
            }
        }

        // 中间压平自然衰减（限幅防 pumping/噪声放大），增益缓变
        if (p.flatten()) {
            double[] e = envRms(seg, Math.max((int) (0.005 * rate), 8));
            int midLen = n - fi - fo;
            if (midLen > 16) {
                double ref = median(e, fi, fi + midLen);
                if (ref > 1e-6) {
                    double[] gm = new double[midLen];
                    for (int i = 0; i < midLen; i++) {
                        gm[i] = clamp(ref / Math.max(e[fi + i], 1e-9), FLATTEN_MIN, FLATTEN_MAX);
                    }
                    double[] h = hanning(17);
                    double[] hs = new double[17];
                    double hsum = 0;
                    for (int i = 0; i < 17; i++) {
                        hsum += h[i];
                    }
                    for (int i = 0; i < 17; i++) {
                        hs[i] = h[i] / Math.max(hsum, 1e-9);
                    }
                    gm = convSame(gm, hs);
                    for (int i = 0; i < midLen; i++) {
                        gm[i] = clamp(gm[i], FLATTEN_MIN, FLATTEN_MAX);
                        g[fi + i] *= gm[i];
                    }
                }
            }
        }

        double[] out = new double[n];
        double mean = 0;
        for (int i = 0; i < n; i++) {
            out[i] = seg[i] * g[i];
            mean += out[i];
        }
        mean /= n;
        double peak = 0;
        for (int i = 0; i < n; i++) {
            out[i] -= mean; // 去 DC
            double a = Math.abs(out[i]);
            if (a > peak) {
                peak = a;
            }
        }
        double scale = 0.95 / Math.max(peak, 1e-12);
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = (float) (out[i] * scale);
        }
        return new Result(f, n, fi, fo, 0.95);
    }

    /** numpy.hanning(w)：对称窗，端点为 0。 */
    public static double[] hanning(int w) {
        double[] win = new double[w];
        for (int i = 0; i < w; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (w - 1));
        }
        return win;
    }

    /**
     * np.convolve(a, k, mode='same')：与 full 卷积居中对齐，越界视为 0。
     * 偶奇长度核均适用（偏移 = (M-1)//2）。
     */
    public static double[] convSame(double[] a, double[] k) {
        int off = (k.length - 1) / 2;
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            double s = 0;
            for (int j = 0; j < k.length; j++) {
                int src = i + off - j;
                if (src >= 0 && src < a.length) {
                    s += k[j] * a[src];
                }
            }
            out[i] = s;
        }
        return out;
    }

    /** l0shape.env_rms：归一 hanning 窗的卷积 RMS 包络（+1e-24 防零）。 */
    public static double[] envRms(double[] x, int w) {
        w = Math.max(w, 3);
        double[] win = hanning(w);
        double sum = 0;
        for (double v : win) {
            sum += v;
        }
        double[] wn = new double[w];
        for (int i = 0; i < w; i++) {
            wn[i] = win[i] / sum;
        }
        double[] x2 = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            x2[i] = x[i] * x[i];
        }
        double[] c = convSame(x2, wn);
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = Math.sqrt(c[i] + 1e-24);
        }
        return out;
    }

    /** 区间 [lo, hi) 的中位数（与 np.median 一致：偶数个取中间两值均值）。 */
    public static double median(double[] a, int lo, int hi) {
        int n = hi - lo;
        double[] t = new double[n];
        System.arraycopy(a, lo, t, 0, n);
        java.util.Arrays.sort(t);
        if (n % 2 == 1) {
            return t[n / 2];
        }
        return 0.5 * (t[n / 2 - 1] + t[n / 2]);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.min(Math.max(v, lo), hi);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.min(Math.max(v, lo), hi);
    }
}
