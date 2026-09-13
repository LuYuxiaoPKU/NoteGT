package com.notegt.client;

import com.notegt.NoteGTMod;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint for NoteGT. All sound-engine logic (L0 routing table,
 * L1 voice merge state machine, L2 sustain) lives on the client side.
 *
 * M0 milestone: entrypoint + Mixin pipeline proof only (see NoteGTClientMixin).
 */
public class NoteGTClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NoteGTMod.LOGGER.info("NoteGT client initialized (M0 skeleton).");
	}
}
