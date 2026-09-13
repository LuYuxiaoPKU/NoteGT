package com.notegt.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.notegt.client.NoteDSPRuntime;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 运行时 DSP 主挂点 [1.21.11 已验证，Mojang mappings]：
 * <ul>
 *   <li>{@code getCompleteBuffer(Identifier)}（混淆 {@code iqn.a} / method_19743）：
 *       {@code @Overwrite} 重实现方法体（= 原 {@code cache.computeIfAbsent + supplyAsync 解码}，
 *       逐行对照反编译/映射），再把返回的 CompletableFuture 包一层 {@code thenApply}：
 *       接管回调成为原 future 的依赖节点，播放方回调挂在包装 future 上 → <b>结构上</b>保证
 *       「首整形先于首次上传」，无「future 先完成、thenAccept 立即执行」的竞态
 *       （whenComplete 方案在 20:45 实测命中 bd 组该竞态后废弃）。
 *       为何不用更轻的注入：① 此方法是单表达式尾返回，RETURN 注入点不可取消
 *       （setReturnValue 抛 CancellationException，实测）；② 本包混用的 mixin-extras 0.3.6
 *       （replaymod 内置 nested jar）对<b>任何</b> {@code @Redirect} 把数组型 {@code at} 强转
 *       AnnotationNode 崩溃（FactoryRedirectWrapperMixinTransformer 只认 at=@At("NEW") 工厂形态，
 *       21:12 实测 CCE）；③ @Invoker 委托到原方法名 + 同方法 @Overwrite = 互递归
 *       （21:25 实测 StackOverflow）。三关都只有 Overwrite+重实现能过。</li>
 *   <li>缓存 map 存的是原 future，每次查找都包一层新的，幂等。</li>
 *   <li>lambda 方法名（{@code lambda$getCompleteBuffer$0}→混淆 c）不经 Loom 重映射，跨环境不稳 → 不直接注入 lambda。</li>
 *   <li>{@code clear()}（混淆 {@code iqn.a()}，资源重载/F3+T）→ 释放 DSP 状态，
 *       新缓冲对象由上方挂点自动重新接管。</li>
 * </ul>
 */
@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {

	@Overwrite
	// 原方法体 [1.21.11 已验证]：
	//   return this.cache.computeIfAbsent(id, id -> CompletableFuture.supplyAsync(() -> {
	//       try (InputStream in = this.resourceManager.open(id)) {
	//           try (JOrbisAudioStream dec = new JOrbisAudioStream(in)) {
	//               return new SoundBuffer(dec.readAll(), dec.getFormat());
	//           }
	//       } catch (IOException e) { throw new CompletionException(e); }
	//   }, Util.nonCriticalIoPool()));
	public CompletableFuture<SoundBuffer> getCompleteBuffer(Identifier id) {
		SoundBufferLibraryAccessor self = (SoundBufferLibraryAccessor) (Object) this;
		CompletableFuture<SoundBuffer> ret = self.notegt$cache().computeIfAbsent(id, $$0 -> CompletableFuture.supplyAsync(() -> {
			try (InputStream in = self.notegt$resourceManager().open($$0)) {
				try (JOrbisAudioStream dec = new JOrbisAudioStream(in)) {
					return new SoundBuffer(dec.readAll(), dec.getFormat());
				}
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool()));
		return ret.thenApply(buf -> {
			NoteDSPRuntime.onBufferCreated(id, buf);
			return buf;
		});
	}

	@Inject(method = "clear", at = @At("TAIL"))
	private void notegt$onClear(CallbackInfo ci) {
		NoteDSPRuntime.onLibraryClear();
	}
}
