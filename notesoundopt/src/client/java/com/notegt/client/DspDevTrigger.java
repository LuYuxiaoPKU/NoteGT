package com.notegt.client;

import com.notegt.NoteGTMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * 临时开发验证触发器（M2a ③）：进世界后自动播放 16 件 note 事件各一次。
 *
 * <p>用途 [1.21.11 已验证]：note 声音在 assets/sounds.json 无 preload 标志
 * → 缓冲在**首次播放**时才解码（getCompleteBuffer 懒加载）→ 本触发器保证
 * 16 件 note 缓冲全部走过 {@code SoundBufferLibraryMixin} 挂点，
 * 配合 {@link NoteDSPRuntime} 的 DEV_DUMP 转储做运行时链路离线核对。
 *
 * <p>正式发布前移除（或改为调试热键）。
 */
public final class DspDevTrigger {

	private static boolean done = false;
	private static int ticks = 0;
	// M1 临时：16 件播完后 100 tick 自动打开配置界面（冒烟测试；发布前移除）
	private static boolean configOpened = false;
	private static int configTicks = 0;

	/** 原版 16 件 note 事件（SoundEvents Holder.Reference 常量，Mojang mappings）。 */
	private static final List<Holder<net.minecraft.sounds.SoundEvent>> NOTE_16 = List.of(
			SoundEvents.NOTE_BLOCK_BANJO,
			SoundEvents.NOTE_BLOCK_BASEDRUM,
			SoundEvents.NOTE_BLOCK_BASS,
			SoundEvents.NOTE_BLOCK_BELL,
			SoundEvents.NOTE_BLOCK_BIT,
			SoundEvents.NOTE_BLOCK_CHIME,
			SoundEvents.NOTE_BLOCK_COW_BELL,
			SoundEvents.NOTE_BLOCK_DIDGERIDOO,
			SoundEvents.NOTE_BLOCK_FLUTE,
			SoundEvents.NOTE_BLOCK_GUITAR,
			SoundEvents.NOTE_BLOCK_HARP,
			SoundEvents.NOTE_BLOCK_HAT,
			SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE,
			SoundEvents.NOTE_BLOCK_PLING,
			SoundEvents.NOTE_BLOCK_SNARE,
			SoundEvents.NOTE_BLOCK_XYLOPHONE);

	private DspDevTrigger() {
	}

	/** ClientTickEvents.END_CLIENT_TICK 注册入口（客户端主线程）。 */
	public static void onClientTick(Minecraft mc) {
		if (done) {
			if (!configOpened && ++configTicks >= 100) {
				configOpened = true;
				NoteGTMod.LOGGER.info("NoteGT dev: 自动打开配置界面（冒烟测试）");
				mc.setScreen(NoteGTConfigScreen.create(null));
			}
			return;
		}
		// 启动后 400 tick（20s）触发：主菜单时声音引擎已初始化（菜单音乐走同一管线），
		// 无需进世界；UI 声音实例与块声音共用同一 SoundBufferLibrary 缓存路径。
		// （1.21.11 Minecraft 无 tick 计数 getter [已查映射]，自计。）
		if (++ticks < 400) {
			return;
		}
		done = true;
		SoundManager sm = mc.getSoundManager();
		for (Holder<net.minecraft.sounds.SoundEvent> h : NOTE_16) {
			sm.play(SimpleSoundInstance.forUI(h.value(), 1.0f));
		}
		NoteGTMod.LOGGER.info("NoteGT DSP dev: 已播放 16 件 note 事件（触发运行时 DSP 接管 + 转储）");
	}
}
