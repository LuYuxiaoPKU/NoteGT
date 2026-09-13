package com.notegt.client.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code SoundBufferLibrary} 私有字段访问器（供 {@code getCompleteBuffer} 的 @Overwrite 重实现用）。
 * [1.21.11 已验证] 字段：{@code resourceManager}(ResourceProvider, 混淆 a) / {@code cache}(Map, 混淆 b)。
 */
@Mixin(SoundBufferLibrary.class)
public interface SoundBufferLibraryAccessor {

	@Accessor("cache")
	Map<Identifier, CompletableFuture<SoundBuffer>> notegt$cache();

	@Accessor("resourceManager")
	ResourceProvider notegt$resourceManager();
}