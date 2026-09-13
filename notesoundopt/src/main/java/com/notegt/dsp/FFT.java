package com.notegt.dsp;

/**
 * FFT 工具（LoopDetector 移植用，对应 numpy.fft.rfft 的幅度谱需求）。
 *
 * <ul>
 *   <li>{@link #rfftAmplitude(double[], int)}：零填充到 2 的幂 + 迭代 radix-2 Cooley-Tukey，
 *       对应 {@code np.fft.rfft(x, N)} 的 |X[k]|（N 为 2 的幂）；</li>
 *   <li>{@link #dftAmplitude(double[])}：任意长度精确 DFT（O(N²)），
 *       对应 {@code np.fft.rfft(x)}（N 非 2 的幂时，如 mod 度量窗）。</li>
 * </ul>
 * 频率栅格 fr[k] = k·sr/N（零填充）或 k/(n·d)（精确 DFT，采样间隔 d 秒）。
 */
public final class FFT {

    private FFT() {
    }

    /** 零填充到 ≥ len(x) 的最小 2 的幂，做 rfft，返回 |X[0..N/2]|。 */
    public static double[] rfftAmplitude(double[] x, int n) {
        int N = 1;
        while (N < n) {
            N <<= 1;
        }
        double[] re = new double[N];
        double[] im = new double[N];
        for (int i = 0; i < x.length && i < N; i++) {
            re[i] = x[i];
        }
        fftInPlace(re, im);
        double[] mag = new double[N / 2 + 1];
        for (int k = 0; k <= N / 2; k++) {
            mag[k] = Math.hypot(re[k], im[k]);
        }
        return mag;
    }

    /** 任意长度实序列的精确 DFT，返回 |X[0..N/2]|（O(N²)，N ≤ 数千足够快）。 */
    public static double[] dftAmplitude(double[] x) {
        int n = x.length;
        double[] mag = new double[n / 2 + 1];
        for (int k = 0; k <= n / 2; k++) {
            double sr = 0, si = 0;
            double ang = -2.0 * Math.PI * k / n;
            for (int i = 0; i < n; i++) {
                double t = ang * i;
                sr += x[i] * Math.cos(t);
                si += x[i] * Math.sin(t);
            }
            mag[k] = Math.hypot(sr, si);
        }
        return mag;
    }

    /** 原地迭代 radix-2 Cooley-Tukey（N 必须为 2 的幂）。 */
    public static void fftInPlace(double[] re, double[] im) {
        int N = re.length;
        // bit-reversal 置换
        for (int i = 1, j = 0; i < N; i++) {
            int bit = N >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j |= bit;
            if (i < j) {
                double tr = re[i];
                re[i] = re[j];
                re[j] = tr;
                double ti = im[i];
                im[i] = im[j];
                im[j] = ti;
            }
        }
        // 蝶形
        for (int len = 2; len <= N; len <<= 1) {
            double ang = -2.0 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < N; i += len) {
                double curR = 1.0, curI = 0.0;
                int half = len >> 1;
                for (int j = 0; j < half; j++) {
                    int u = i + j;
                    int v = i + j + half;
                    double ur = re[u], ui = im[u];
                    double vr = re[v] * curR - im[v] * curI;
                    double vi = re[v] * curI + im[v] * curR;
                    re[u] = ur + vr;
                    im[u] = ui + vi;
                    re[v] = ur - vr;
                    im[v] = ui - vi;
                    double nr = curR * wr - curI * wi;
                    curI = curR * wi + curI * wr;
                    curR = nr;
                }
            }
        }
    }
}
