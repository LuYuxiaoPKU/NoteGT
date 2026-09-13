package com.notegt.client.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * M0 pipeline-proof Mixin: injects at the start of Minecraft.run().
 *
 * Verified facts behind the target (Mojang official mappings, 1.21.11):
 * - main client class is net.minecraft.client.Minecraft (obf: gfj);
 *   "MinecraftClient" is a YARN name, not the Mojang official name.
 * - net.minecraft.resources.Identifier (not ResourceLocation) since 1.21.11.
 *
 * This stub will be replaced/expanded in M1 with the real SoundEngine
 * hook (net.minecraft.client.sounds.SoundEngine, obf: iqo).
 */
@Mixin(Minecraft.class)
public class NoteGTClientMixin {
	@Inject(at = @At("HEAD"), method = "run")
	private void notegt$onRun(CallbackInfo info) {
		// M0: existence proof that the Mixin applies. Kept side-effect free.
	}
}
