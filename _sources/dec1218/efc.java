/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  ayy
 *  ayz
 *  bda
 *  efc$a
 *  jl
 */
public final class efc
extends Enum<efc>
implements bda {
    public static final /* enum */ efc a = new efc("harp", (jl<ayy>)ayz.tk, efc$a.a);
    public static final /* enum */ efc b = new efc("basedrum", (jl<ayy>)ayz.te, efc$a.a);
    public static final /* enum */ efc c = new efc("snare", (jl<ayy>)ayz.tn, efc$a.a);
    public static final /* enum */ efc d = new efc("hat", (jl<ayy>)ayz.tl, efc$a.a);
    public static final /* enum */ efc e = new efc("bass", (jl<ayy>)ayz.tf, efc$a.a);
    public static final /* enum */ efc f = new efc("flute", (jl<ayy>)ayz.ti, efc$a.a);
    public static final /* enum */ efc g = new efc("bell", (jl<ayy>)ayz.tg, efc$a.a);
    public static final /* enum */ efc h = new efc("guitar", (jl<ayy>)ayz.tj, efc$a.a);
    public static final /* enum */ efc i = new efc("chime", (jl<ayy>)ayz.th, efc$a.a);
    public static final /* enum */ efc j = new efc("xylophone", (jl<ayy>)ayz.to, efc$a.a);
    public static final /* enum */ efc k = new efc("iron_xylophone", (jl<ayy>)ayz.tp, efc$a.a);
    public static final /* enum */ efc l = new efc("cow_bell", (jl<ayy>)ayz.tq, efc$a.a);
    public static final /* enum */ efc m = new efc("didgeridoo", (jl<ayy>)ayz.tr, efc$a.a);
    public static final /* enum */ efc n = new efc("bit", (jl<ayy>)ayz.ts, efc$a.a);
    public static final /* enum */ efc o = new efc("banjo", (jl<ayy>)ayz.tt, efc$a.a);
    public static final /* enum */ efc p = new efc("pling", (jl<ayy>)ayz.tm, efc$a.a);
    public static final /* enum */ efc q = new efc("zombie", (jl<ayy>)ayz.tu, efc$a.b);
    public static final /* enum */ efc r = new efc("skeleton", (jl<ayy>)ayz.tv, efc$a.b);
    public static final /* enum */ efc s = new efc("creeper", (jl<ayy>)ayz.tw, efc$a.b);
    public static final /* enum */ efc t = new efc("dragon", (jl<ayy>)ayz.tx, efc$a.b);
    public static final /* enum */ efc u = new efc("wither_skeleton", (jl<ayy>)ayz.ty, efc$a.b);
    public static final /* enum */ efc v = new efc("piglin", (jl<ayy>)ayz.tz, efc$a.b);
    public static final /* enum */ efc w = new efc("custom_head", (jl<ayy>)ayz.BU, efc$a.c);
    private final String x;
    private final jl<ayy> y;
    private final a z;
    private static final /* synthetic */ efc[] A;

    public static efc[] values() {
        return (efc[])A.clone();
    }

    public static efc valueOf(String $$0) {
        return Enum.valueOf(efc.class, $$0);
    }

    private efc(String $$0, jl<ayy> $$1, a $$2) {
        this.x = $$0;
        this.y = $$1;
        this.z = $$2;
    }

    public String c() {
        return this.x;
    }

    public jl<ayy> a() {
        return this.y;
    }

    public boolean b() {
        return this.z == efc$a.a;
    }

    public boolean d() {
        return this.z == efc$a.c;
    }

    public boolean e() {
        return this.z != efc$a.a;
    }

    private static /* synthetic */ efc[] f() {
        return new efc[]{a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v, w};
    }

    static {
        A = efc.f();
    }
}
