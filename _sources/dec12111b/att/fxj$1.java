/*
 * Decompiled with CFR 0.152.
 */
import com.mojang.blaze3d.textures.FilterMode;

static class fxj.1 {
    static final /* synthetic */ int[] a;

    static {
        a = new int[FilterMode.values().length];
        try {
            fxj.1.a[FilterMode.NEAREST.ordinal()] = 1;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            // empty catch block
        }
        try {
            fxj.1.a[FilterMode.LINEAR.ordinal()] = 2;
        }
        catch (NoSuchFieldError noSuchFieldError) {
            // empty catch block
        }
    }
}
