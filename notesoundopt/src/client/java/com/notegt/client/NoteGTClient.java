package com.notegt.client;

import com.notegt.NoteGTMod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint for NoteGT. All sound-engine logic (L0 routing table,
 * L1 voice merge state machine, L2 sustain) lives on the client side.
 *
 * M2a ③: 运行时 DSP 挂点（note/* 缓冲 L0 重整形）+ 临时 dev 触发器。
 */
public class NoteGTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NoteGTMod.LOGGER.info("NoteGT client initialized (M2a③ 运行时 DSP 挂点: note/* 缓冲 L0 重整形).");
		// 临时：进世界自动播放 16 件 note 事件，触发懒加载挂点 + 缓冲转储（发布前移除）
		ClientTickEvents.END_CLIENT_TICK.register(DspDevTrigger::onClientTick);
	}
}
