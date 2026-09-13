package com.notegt.dsp;

/**
 * L2 循环点检测（Java 移植，与 _sources/loopdetect-run/loopdetect.py v5 逐行对应）。
 *
 * <p>判据（v5，AGENTS #26）：seamErrDeg ≤10 PASS / 10–25 WARN_SEAM / >25 FAIL；
 * fadeDipDb <−1.0 FAIL；loop 长度门 120ms；长音 ripple <2.0dB 且循环率调制 <−40dBc 才 PASS。
 *
 * <p>运行时用途（M4）：对原始 note 缓冲（保留的未整形副本）跑 detect() →
 * loop 缓冲 + 验收 verdict；verdict 不过则该件乐器 L2 关闭（回落 L0 重触发）。
 *
 * <p>移植核对：{@code LoopDetectorTest} 用 16 件原版样本的 Python 参考做字段级交叉核对。
 * seam_ratio_lf（butter/filtfilt）为 advisory，不进 verdict，Java 版暂不实现（Python 离线出）。
 */
public final class LoopDetector {

    private LoopDetector() {
    }

    /** 检测参数（与 v5 全库 CLI 默认一致：ncc 0.95、span 0.5、xfade 5ms、fmin 30Hz）。 */
    public record Params(double snrMinDb, double nccMin, double spanCycles, double xfadeSec,
                         boolean subharm, double fminHz, double dynRangeDb) {
        public static final Params DEFAULTS = new Params(20.0, 0.95, 0.5, 0.005, true, 30.0, 40.0);
    }

    /** 检测失败（对应 Python 返回 (None, reason)）。 */
    public static final class Rejected extends RuntimeException {
        public final String reason;

        Rejected(String reason) {
            super(reason);
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "REJECT(" + reason + ")";
        }
    }

    /** 检测成功。字段名对齐 Python JSON（交叉核对用）。 */
    public static final class Result {
        public final double f0;
        public final int subharmMult;
        public final int a;            // attackEnd / loopStart（样本）
        public final int L;            // loopSamples
        public final int k;            // periods
        public final double ncc;
        public final double loopMs;
        public final double rateHz;
        public final double loopRippleDb;
        public final double longRippleDb;
        public final double modDbc;
        public final double seamRatio;
        public final double seamErrDeg;   // NaN = 未测到
        public final double fadeNcc;
        public final double fadeDipDb;
        public final double periodErrDeg;
        public final double xfadeSec;     // keep-best 后的实际 xfade
        public final float[] loop;        // 可播放 loop 缓冲（env 归一 + xfade）
        public final String verdict;      // PASS / WARN_SEAM / WARN / FAIL

        Result(double f0, int subharmMult, int a, int L, int k, double ncc, double loopMs,
               double rateHz, double loopRippleDb, double longRippleDb, double modDbc,
               double seamRatio, double seamErrDeg, double fadeNcc, double fadeDipDb,
               double periodErrDeg, double xfadeSec, float[] loop, String verdict) {
            this.f0 = f0;
            this.subharmMult = subharmMult;
            this.a = a;
            this.L = L;
            this.k = k;
            this.ncc = ncc;
            this.loopMs = loopMs;
            this.rateHz = rateHz;
            this.loopRippleDb = loopRippleDb;
            this.longRippleDb = longRippleDb;
            this.modDbc = modDbc;
            this.seamRatio = seamRatio;
            this.seamErrDeg = seamErrDeg;
            this.fadeNcc = fadeNcc;
            this.fadeDipDb = fadeDipDb;
            this.periodErrDeg = periodErrDeg;
            this.xfadeSec = xfadeSec;
            this.loop = loop;
            this.verdict = verdict;
        }

        /** v5 判据下可启用 L2 延音。 */
        public boolean l2Usable() {
            return verdict.equals("PASS") || verdict.equals("WARN_SEAM");
        }
    }

    /**
     * 主入口：预处理 + f0 + 循环搜索 + xfade keep-best + 度量 + verdict。
     *
     * @param x  原始单声道样本（float32，文件原生采样率）
     * @param sr 采样率
     * @param p  参数
     */
    public static Result detect(float[] x, int sr, Params p) {
        double[] xx = preprocess(x);
        double[] f0m = estF0Full(xx, sr, p.subharm(), p.fminHz());
        double f0 = f0m[0], mult = f0m[1];
        if (f0 <= 0) {
            throw new Rejected("基频估计失败（噪声类/打击乐音色）");
        }

        double[] e = renv(xx, 0.030, sr);
        double eMax = 0;
        int ipk = 0;
        for (int i = 0; i < e.length; i++) {
            if (e[i] > eMax) {
                eMax = e[i];
                ipk = i;
            }
        }
        double thr = eMax * Math.pow(10.0, -p.dynRangeDb() / 20.0);
        Double nfp = nflPlateau(xx, e, 0.25, 0.80);
        if (nfp != null) {
            thr = Math.max(thr, nfp * Math.pow(10.0, p.snrMinDb() / 20.0));
        }
        int u1 = -1;
        for (int i = 0; i < e.length; i++) {
            if (e[i] > thr) {
                u1 = i;
            }
        }
        if (u1 < 0) {
            throw new Rejected("无可用区（信噪比不足或样本过短）");
        }

        double P = sr / f0;
        int aLo = ipk + (int) (2 * P);
        int aHi = Math.min(u1 - (int) (0.10 * sr), xx.length - (int) (0.10 * sr));
        if (aHi <= aLo) {
            throw new Rejected("起音后的可用区过短");
        }

        double medE = L0Shaper.median(e, 0, e.length);
        double[] xf = new double[xx.length];
        for (int i = 0; i < xx.length; i++) {
            xf[i] = xx[i] / Math.max(e[i], 1e-12) * medE;
        }

        int ws = (int) (0.060 * sr);
        Best best = null;
        int step = Math.max(1, (int) (0.005 * sr));
        for (int a = aLo; a < Math.max(aHi, aLo + 1); a += step) {
            int kmax = (int) (Math.min(u1 - a, 1.2 * sr) / P);
            if (kmax < 5) {
                continue;
            }
            Best got = null;
            for (int k = kmax; k >= 5; k--) {
                double li = k * P;
                if (a + 2 * li + ws > xx.length) {
                    continue;
                }
                int span = (int) (p.spanCycles() * P);
                for (int dl = -span; dl <= span; dl++) {
                    int l = (int) Math.rint(li) + dl; // Python int(round(Li))，银行家舍入
                    if (l < 10 || a + 2 * l + ws > xx.length) {
                        continue;
                    }
                    double c = ncc(slice(xf, a, a + ws), slice(xf, a + l, a + l + ws));
                    if (c >= p.nccMin() && (got == null || c > got.ncc)) {
                        got = new Best(a, l, k, c);
                    }
                }
                if (got != null) {
                    break;
            }
            }
            if (got != null && (best == null || got.L > best.L)) {
                best = got;
            }
        }
        if (best == null) {
            throw new Rejected("未找到满足相关度阈值的循环长度（可能非谐性过强）");
        }
        if (best.L < 0.080 * sr) {
            throw new Rejected("循环长度过短（<80ms），周期性可闻");
        }

        // build_loop_diag + keep-best 自适应淡化（仅在真正改善时替换）
        LoopBuild lb = buildLoopDiag(xx, best.a, best.L, f0, 0.030, p.xfadeSec(), sr);
        double[] loop = lb.loop;
        double seamErr = lb.seamErrDeg;
        double fadeNcc = lb.fadeNcc;
        double fadeDip = lb.fadeDipDb;
        double periodErr = lb.periodErrDeg;
        double xfadeUsed = p.xfadeSec();
        double bestSm = seamRatio(loop, sr);
        double[] upgrades = {0.010, 0.020, 0.040};
        for (double xfSec : upgrades) {
            if (bestSm < 2.0) {
                break;
            }
            LoopBuild alt = buildLoopDiag(xx, best.a, best.L, f0, 0.030, xfSec, sr);
            if (alt.loop.length != loop.length) {
                continue;
            }
            double sm = seamRatio(alt.loop, sr);
            if (sm < bestSm - 0.05) {
                loop = alt.loop;
                seamErr = alt.seamErrDeg;
                fadeNcc = alt.fadeNcc;
                fadeDip = alt.fadeDipDb;
                periodErr = alt.periodErrDeg;
                xfadeUsed = xfSec;
                bestSm = sm;
            }
        }

        double rate = sr / (double) best.L;
        double[] m = metrics(xx, best.a, loop, best.k, f0, rate, 1.0, sr);
        String verdict = verdict(m[1], m[2], best.L, sr, seamErr, fadeDip);

        float[] loopF = new float[loop.length];
        for (int i = 0; i < loop.length; i++) {
            loopF[i] = (float) loop[i];
        }
        return new Result(f0, (int) mult, best.a, best.L, best.k, best.ncc,
                best.L / (double) sr * 1000.0, rate, m[0], m[1], m[2], bestSm,
                seamErr, fadeNcc, fadeDip, periodErr, xfadeUsed, loopF, verdict);
    }

    private record Best(int a, int L, int k, double ncc) {
    }

    // ─────────────────────── 预处理与 f0（对应 preprocess / est_f0*） ───────────────────────

    /** 单声道 + 去 DC + 峰值归一。 */
    public static double[] preprocess(float[] x) {
        double[] xx = new double[x.length];
        double mean = 0;
        for (float v : x) {
            mean += v;
        }
        mean /= Math.max(x.length, 1);
        double m = 0;
        for (int i = 0; i < x.length; i++) {
            xx[i] = x[i] - mean;
            m = Math.max(m, Math.abs(xx[i]));
        }
        if (m > 0) {
            for (int i = 0; i < x.length; i++) {
                xx[i] /= m;
            }
        }
        return xx;
    }

    /** RMS 包络（归一 hanning 窗卷积，odd 窗）。 */
    public static double[] renv(double[] x, double wSec, int sr) {
        int w = (int) (wSec * sr);
        w = (w % 2 == 0) ? w + 1 : w;
        w = Math.max(w, 3);
        double[] win = L0Shaper.hanning(w);
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
        double[] c = L0Shaper.convSame(x2, wn);
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            out[i] = Math.sqrt(c[i] + 1e-24);
        }
        return out;
    }

    public static double ncc(double[] u, double[] v) {
        double uu = 0, vv = 0;
        for (int i = 0; i < u.length; i++) {
            uu += u[i] * u[i];
        }
        for (int i = 0; i < v.length; i++) {
            vv += v[i] * v[i];
        }
        double du = Math.sqrt(uu), dv = Math.sqrt(vv);
        if (du < 1e-12 || dv < 1e-12) {
            return 0.0;
        }
        double s = 0;
        for (int i = 0; i < u.length; i++) {
            s += u[i] * v[i];
        }
        return s / (du * dv);
    }

    /** 抛物线插值 FFT 基频（t0=10ms, t1=210ms, pad=16）。返回 (f0, 1)。 */
    public static double[] estF0Full(double[] x, int sr, boolean subharm, double fmin) {
        double f = estF0(x, sr, fmin);
        if (!subharm || f <= 0) {
            return new double[]{f, 1};
        }
        double[][] spec = specFull(x, sr);
        double[] s = spec[0];
        double[] fr = spec[1];
        int bandLo = 0, bandHi = -1;
        for (int i = 0; i < fr.length; i++) {
            if (fr[i] > f * 0.8 && fr[i] < f * 1.2) {
                if (bandHi < 0) {
                    bandLo = i;
                }
                bandHi = i;
            }
        }
        if (bandHi < 0) {
            return new double[]{f, 1};
        }
        double ref = 0;
        for (int i = bandLo; i <= bandHi; i++) {
            ref = Math.max(ref, s[i]);
        }
        for (int mult : new int[]{2, 3}) {
            double c = f / mult;
            if (c < 35.0) {
                continue;
            }
            int[] pk = peakNear(s, fr, c, 0.03);
            if (pk == null) {
                continue;
            }
            double cf = fr[pk[0]], cv = s[pk[0]];
            if (cv <= 0 || cv < ref * Math.pow(10.0, -20.0 / 20.0)) {
                continue;
            }
            double floor = 0;
            int n = 0;
            double[] nbVals = new double[fr.length];
            for (int i = 0; i < fr.length; i++) {
                if (fr[i] >= c * 0.6 && fr[i] <= c * 1.6) {
                    nbVals[n++] = s[i];
                }
            }
            if (n > 0) {
                floor = L0Shaper.median(nbVals, 0, n);
            }
            if (floor > 0 && cv < floor * Math.pow(10.0, 6.0 / 20.0)) {
                continue;
            }
            return new double[]{cf, mult};
        }
        return new double[]{f, 1};
    }

    private static double estF0(double[] x, int sr, double fmin) {
        int i0s = (int) (0.010 * sr);
        int i1s = (int) (0.210 * sr);
        int len = i1s - i0s;
        if (len < 256) {
            i1s = (int) (Math.min(0.5, x.length / (double) sr * 0.9) * sr);
            len = i1s - i0s;
        }
        if (len < 64) {
            return 0.0;
        }
        double[] seg = slice(x, i0s, i1s);
        double mean = 0;
        for (double v : seg) {
            mean += v;
        }
        mean /= seg.length;
        double[] win = L0Shaper.hanning(seg.length);
        double[] wseg = new double[seg.length];
        for (int i = 0; i < seg.length; i++) {
            wseg[i] = (seg[i] - mean) * win[i];
        }
        double[] s = FFT.rfftAmplitude(wseg, wseg.length * 16);
        double[] fr = freqGrid(s.length, sr);
        int i0 = -1;
        double bestS = 0;
        for (int i = 0; i < s.length; i++) {
            if (fr[i] > fmin && fr[i] < 2000.0 && s[i] > bestS) {
                bestS = s[i];
                i0 = i;
            }
        }
        if (i0 < 1 || i0 + 1 >= s.length) {
            return i0 > 0 ? fr[i0] : 0.0;
        }
        double a = Math.log(s[i0 - 1] + 1e-30);
        double b = Math.log(s[i0] + 1e-30);
        double c = Math.log(s[i0 + 1] + 1e-30);
        double d = 0.5 * (a - c) / (a - 2 * b + c + 1e-30);
        return fr[i0] + d * (fr[1] - fr[0]);
    }

    private static double[][] specFull(double[] x, int sr) {
        double mean = 0;
        for (double v : x) {
            mean += v;
        }
        mean /= x.length;
        double[] seg = new double[x.length];
        double[] win = L0Shaper.hanning(x.length);
        for (int i = 0; i < x.length; i++) {
            seg[i] = (x[i] - mean) * win[i];
        }
        double[] s = FFT.rfftAmplitude(seg, x.length * 4);
        return new double[][]{s, freqGrid(s.length, sr)};
    }

    /** 返回峰所在 bin 索引（|±tol| 相对容差内最大幅值），无则 null。 */
    private static int[] peakNear(double[] s, double[] fr, double f, double tol) {
        int idx = -1;
        double bestS = 0;
        for (int i = 0; i < fr.length; i++) {
            if (fr[i] >= f * (1 - tol) && fr[i] <= f * (1 + tol) && s[i] > bestS) {
                bestS = s[i];
                idx = i;
            }
        }
        return idx < 0 ? null : new int[]{idx};
    }

    private static double[] freqGrid(int bins, int sr) {
        int N = (bins - 1) * 2;
        double[] fr = new double[bins];
        for (int k = 0; k < bins; k++) {
            fr[k] = (double) k * sr / N;
        }
        return fr;
    }

    // ─────────────────────── 噪声底 / 可用区（nfl_est / nfl_plateau） ───────────────────────

    private static Double nflPlateau(double[] x, double[] e, double tail, double flatRatio) {
        int n = e.length;
        int t0 = (int) (n * (1 - tail));
        int segLen = n - t0;
        if (segLen < 64) {
            return null;
        }
        int q = Math.max(segLen / 4, 8);
        double headMed = L0Shaper.median(e, t0, t0 + q);
        double tailMed = L0Shaper.median(e, t0 + segLen - q, t0 + segLen);
        if (headMed <= 0) {
            return null;
        }
        if (tailMed / headMed >= flatRatio) {
            return tailMed; // 走平 → 噪声底
        }
        return null; // 仍在衰减 → 是信号
    }

    // ─────────────────────── 接缝/淡化度量（seam_err / fade_quality / perr / seam_ratio） ───────────────────────

    private static double nccShift(double[] u, double[] v, int lag) {
        int n = u.length;
        int aaOff, bbOff, m;
        if (lag >= 0) {
            aaOff = lag;
            bbOff = 0;
            m = n - lag;
        } else {
            aaOff = 0;
            bbOff = -lag;
            m = n + lag;
        }
        m = Math.min(m, v.length - bbOff);
        if (m < 16) {
            return -2.0;
        }
        double[] aa = new double[m], bb = new double[m];
        System.arraycopy(u, aaOff, aa, 0, m);
        System.arraycopy(v, bbOff, bb, 0, m);
        return ncc(aa, bb);
    }

    /** 接缝相位误差（周期）。NaN = 峰值在搜索边缘（二次谐波歧义等）。返回 |τ|/P。 */
    public static double seamErrCycles(double[] head, double[] tail, double P) {
        if (P <= 0) {
            return Double.NaN;
        }
        int maxlag = Math.max((int) (P / 2), 8);
        int nlags = 2 * maxlag + 1;
        double[] c = new double[nlags];
        for (int i = 0; i < nlags; i++) {
            c[i] = nccShift(head, tail, i - maxlag);
        }
        int i = argmax(c);
        if (i <= 0 || i >= nlags - 1) {
            return Double.NaN;
        }
        double a = c[i - 1], b = c[i], cc = c[i + 1];
        double d = 0.5 * (a - cc) / (a - 2 * b + cc + 1e-30);
        if (Math.abs(d) > 1) {
            d = 0.0;
        }
        double tau = (i - maxlag) + d;
        return Math.abs(tau) / P;
    }

    private record FadeQual(double ncc, double dipDb) {
    }

    private static FadeQual fadeQuality(double[] head, double[] tail) {
        double nv = ncc(head, tail);
        double rh = rms(head), rt = rms(tail);
        double[] m = new double[head.length];
        for (int i = 0; i < head.length; i++) {
            m[i] = 0.5 * (head[i] + tail[i]);
        }
        double rm = rms(m);
        double rSteady = Math.max(0.5 * (rh + rt), 1e-12);
        double dip = 20.0 * Math.log10(Math.max(rm, 1e-12) / rSteady);
        return new FadeQual(nv, dip);
    }

    public static double periodErrDeg(double f0, int l, int sr) {
        if (f0 <= 0 || l <= 0) {
            return Double.NaN;
        }
        double frac = (f0 * l / (double) sr) % 1.0;
        frac = Math.min(frac, 1.0 - frac);
        return frac * 360.0;
    }

    /** 接缝跳变 / 端点局部平均样本间跳变。 */
    public static double seamRatio(double[] lo, int sr) {
        int w = Math.max((int) (5.0 / 1000 * sr), 32);
        w = Math.min(w, lo.length / 2);
        double[] local = new double[2 * (w - 1)];
        for (int i = 0; i < w - 1; i++) {
            local[i] = Math.abs(lo[i + 1] - lo[i]);
            local[w - 1 + i] = Math.abs(lo[lo.length - w + i + 1] - lo[lo.length - w + i]);
        }
        double mean = 0;
        for (double v : local) {
            mean += v;
        }
        mean /= local.length;
        return Math.abs(lo[0] - lo[lo.length - 1]) / Math.max(mean, 1e-12);
    }

    // ─────────────────────── loop 构建与渲染（build_loop_diag / render / metrics） ───────────────────────

    private static final class LoopBuild {
        double[] loop;
        double seamErrDeg, fadeNcc, fadeDipDb, periodErrDeg;
    }

    private static LoopBuild buildLoopDiag(double[] x, int a, int L, double f0, double fwin, double xfade, int sr) {
        int xs = (int) Math.rint(xfade * sr); // Python round() = 银行家舍入（0.005*44100=220.5 → 220）
        double[] e = renv(x, fwin, sr);
        int need = a + L + xs;
        if (need > x.length) {
            xs = Math.max(0, x.length - a - L);
            need = a + L + xs;
        }
        double[] seg = slice(x, a, need);
        double[] ef = new double[seg.length];
        double[] efTmp = new double[seg.length];
        for (int i = 0; i < seg.length; i++) {
            ef[i] = e[a + i];
            efTmp[i] = Math.max(ef[i], 1e-12);
        }
        double medEf = L0Shaper.median(efTmp, 0, efTmp.length);
        double[] src = new double[seg.length];
        for (int i = 0; i < seg.length; i++) {
            src[i] = seg[i] / efTmp[i] * medEf;
        }
        double[] lo = new double[L];
        System.arraycopy(src, 0, lo, 0, L);
        if (xs > 0) {
            for (int i = 0; i < xs; i++) {
                double r = xs == 1 ? 1.0 : (double) i / (xs - 1); // np.linspace(0,1,Xs)
                lo[i] = src[i] * r + src[L + i] * (1 - r);
            }
        }
        LoopBuild lb = new LoopBuild();
        lb.loop = lo;
        double P = f0 > 0 ? sr / f0 : 0.0;
        int wd = Math.max(xs, (int) (0.004 * sr));
        wd = Math.min(wd, Math.max(src.length - L, 0));
        if (wd >= 64 && P > 0) {
            double[] head = slice(src, 0, wd);
            double[] tail = slice(src, L, L + wd);
            FadeQual fq = fadeQuality(head, tail);
            double sec = seamErrCycles(head, tail, P);
            lb.fadeNcc = fq.ncc();
            lb.fadeDipDb = fq.dipDb();
            lb.seamErrDeg = Double.isNaN(sec) ? Double.NaN : sec * 360.0;
        } else {
            lb.fadeNcc = Double.NaN;
            lb.fadeDipDb = Double.NaN;
            lb.seamErrDeg = Double.NaN;
        }
        lb.periodErrDeg = periodErrDeg(f0, L, sr);
        return lb;
    }

    /** attack(原样) → crossfade → loop×N → release（度量用渲染，total 秒）。 */
    private static double[] render(double[] x, int a, double[] loop, double total, int sr) {
        int f = (int) (0.035 * sr);
        int n = Math.max((int) (total * sr), a + f + loop.length + (int) (0.10 * sr) + 1);
        double[] out = new double[n];
        System.arraycopy(x, 0, out, 0, Math.min(a, n));
        double[] ex = renv(x, 0.030, sr);
        double[] el = renv(loop, 0.030, sr);
        double lv = ex[a] / Math.max(L0Shaper.median(el, 0, el.length), 1e-12);
        for (int i = 0; i < f && a + i < n; i++) {
            double u = f == 1 ? 1.0 : (double) i / (f - 1);
            out[a + i] = x[a + i] * (1 - u) + loop[i] * lv * u;
        }
        int p = a + f;
        while (p < n) {
            int seg = Math.min(loop.length, n - p);
            for (int i = 0; i < seg; i++) {
                out[p + i] = loop[i] * lv;
            }
            p += seg;
        }
        int rf = (int) (0.10 * sr);
        for (int i = 0; i < rf; i++) {
            double u = rf == 1 ? 0.0 : (double) i / (rf - 1);
            double s = Math.sin(Math.PI / 2 * (1 - u));
            out[n - rf + i] *= s * s;
        }
        return out;
    }

    private static double rms(double[] x) {
        double s = 0;
        for (double v : x) {
            s += v * v;
        }
        return Math.sqrt(s / Math.max(x.length, 1));
    }

    /** 返回 [loop_pp, long_pp, mod_dbc, seam_ratio]。 */
    private static double[] metrics(double[] x, int a, double[] loop, int k, double f0, double rate,
                                    double total, int sr) {
        // 循环内逐周期起伏
        int npp = loop.length / k;
        double[] r = new double[k];
        for (int i = 0; i < k; i++) {
            double s = 0;
            for (int j = 0; j < npp; j++) {
                double v = loop[i * npp + j];
                s += v * v;
            }
            r[i] = Math.sqrt(s / npp);
        }
        double rMax = 0, rMin = Double.POSITIVE_INFINITY;
        for (double v : r) {
            rMax = Math.max(rMax, v);
            rMin = Math.min(rMin, v);
        }
        double loopPp = 20 * Math.log10(rMax / Math.max(rMin, 1e-12));

        // 长音包络起伏 + 循环率调制
        double[] y = render(x, a, loop, total, sr);
        double w = 10.0 / f0; // 度量窗 = 10 周期
        int win = (int) (w * sr);
        int nw = y.length / win;
        double[] rr = new double[nw];
        for (int i = 0; i < nw; i++) {
            double s = 0;
            for (int j = 0; j < win; j++) {
                double v = y[i * win + j];
                s += v * v;
            }
            rr[i] = Math.sqrt(s / win);
        }
        int t0 = (int) (0.2 / w), t1 = (int) (0.8 / w);
        double[] rrT = slice(rr, t0, t1);
        double max = 0, min = Double.POSITIVE_INFINITY;
        for (double v : rrT) {
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        double longPp = 20 * Math.log10(max / Math.max(min, 1e-12));

        double dc = 0;
        for (double v : rrT) {
            dc += v;
        }
        dc /= rrT.length;
        double[] mod = new double[rrT.length];
        double[] winH = L0Shaper.hanning(rrT.length);
        for (int i = 0; i < rrT.length; i++) {
            mod[i] = (rrT[i] - dc) * winH[i];
        }
        double[] sp = FFT.dftAmplitude(mod);
        int nBins = sp.length;
        int N = (nBins - 1) * 2;
        int iRate = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < nBins; i++) {
            double fr = (double) i * (1.0 / (N * w)); // rfftfreq(N, d=w)
            double d = Math.abs(fr - rate);
            if (d < bestDist) {
                bestDist = d;
                iRate = i;
            }
        }
        double modDbc = 20 * Math.log10((2 * sp[iRate] / rrT.length) / Math.max(dc, 1e-12) + 1e-12);

        return new double[]{loopPp, longPp, modDbc, seamRatio(loop, sr)};
    }

    /** v5 verdict（对应 _verdict 的活动段）。 */
    private static String verdict(double lg, double md, int l, int sr, double sed, double fdip) {
        if (!Double.isNaN(sed) && sed > 25.0) {
            return "FAIL";
        }
        if (!Double.isNaN(fdip) && fdip < -1.0) {
            return "FAIL";
        }
        if (l < 0.120 * sr) {
            return "FAIL";
        }
        if (!Double.isNaN(sed) && sed > 10.0) {
            return "WARN_SEAM";
        }
        if (lg < 2.0 && md < -40) {
            return "PASS";
        }
        if (lg < 4.0) {
            return "WARN";
        }
        return "FAIL";
    }

    private static int argmax(double[] a) {
        int i = 0;
        for (int j = 1; j < a.length; j++) {
            if (a[j] > a[i]) {
                i = j;
            }
        }
        return i;
    }

    private static double[] slice(double[] a, int from, int to) {
        int len = Math.min(to, a.length) - from;
        double[] out = new double[Math.max(len, 0)];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }
}
