package com.notegt.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.notegt.NoteGTMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for NoteGT. All sound-engine logic (L0 routing table,
 * L1 voice merge state machine, L2 sustain) lives on the client side.
 *
 * M2a ③: 运行时 DSP 挂点（note/* 缓冲 L0 重整形）+ 临时 dev 触发器。
 * M1: 配置（config/notegt.json + Cloth 界面 + 键绑 + ModMenu 入口）。
 *
 * <p>1.21.11 命名 [1.21.11 已验证]：KeyBinding → KeyMapping（Category = register(Identifier)）、
 * InputUtil → com.mojang.blaze3d.platform.InputConstants、边沿触发 wasPressed() → consumeClick()、
 * new Identifier(ns, path) → Identifier.fromNamespaceAndPath(ns, path)（构造器私有化）。
 */
public class NoteGTClient implements ClientModInitializer {

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("notegt", "category"));

	/** 打开配置界面（默认不占键，可在 选项→控制→NoteGT 里绑）。 */
	public static final KeyMapping OPEN_CONFIG = new KeyMapping(
			"key.notegt.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

	@Override
	public void onInitializeClient() {
		NoteGTMod.LOGGER.info("NoteGT client initialized (M2a③ 运行时 DSP 挂点 + M1 配置).");
		NoteGTConfig.init();
		KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// 临时：进世界自动播放 16 件 note 事件，触发懒加载挂点 + 缓冲转储（发布前移除）
			DspDevTrigger.onClientTick(client);
			if (OPEN_CONFIG.consumeClick()) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.screen == null) {
					mc.setScreen(NoteGTConfigScreen.create(null));
				}
			}
		});
	}
}
