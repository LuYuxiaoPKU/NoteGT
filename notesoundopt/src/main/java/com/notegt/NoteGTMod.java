package com.notegt;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NoteGT — Minecraft note-block sound-optimization mod.
 *
 * Main (common) entrypoint: kept intentionally minimal. All sound logic is
 * client-side (see {@link com.notegt.client.NoteGTClient}); this class exists
 * so fabric.mod.json can declare a common entrypoint and expose the logger/mod id.
 */
public class NoteGTMod implements ModInitializer {
	public static final String MOD_ID = "notegt";

	// Logger named after the mod id so it is clear who wrote the log line.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Some resources may still be uninitialized here; keep this a no-op + log.
		LOGGER.info("NoteGT loaded (common); sound logic is client-side only.");
	}
}
