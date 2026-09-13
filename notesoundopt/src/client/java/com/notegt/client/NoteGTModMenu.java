package com.notegt.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/**
 * ModMenu 集成（可选依赖：无 ModMenu 时本入口不加载，配置走键绑）。
 * [2026-09-13 javap 实测] ModMenu 17.0.0：entrypoints.modmenu → 无参构造实例 →
 * {@link #getModConfigScreenFactory()} → {@code ConfigScreenFactory#create(Screen parent)}
 * （Screen = 1.21.11 的 net.minecraft.client.gui.screens.Screen）。
 */
@Environment(EnvType.CLIENT)
public class NoteGTModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return NoteGTConfigScreen::create;
    }
}
