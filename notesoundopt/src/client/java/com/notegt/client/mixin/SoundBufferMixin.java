package com.notegt.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.notegt.client.NoteDSPRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/**
 * 上传前兜底挂点 [1.21.11 已验证，Mojang mappings]：
 * {@code SoundBuffer.getAlBuffer()}（混淆 {@code fwl.a}）HEAD ——
 * 配置代际过期时在此 discard + 从原始重整形 + 换 data，本次上传即新参数结果
 * （热改参后首次重上传必经此路；正常路径为 no-op，仅一次 int 比较）。
 */
@Mixin(SoundBuffer.class)
public class SoundBufferMixin {

	@Inject(method = "getAlBuffer", at = @At("HEAD"))
	private void notegt$onBeforeUpload(CallbackInfoReturnable<OptionalInt> cir) {
		NoteDSPRuntime.onBeforeUpload((SoundBuffer) (Object) this);
	}
}
