package net.neurallink.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** loaded by Mod Menu, the dependency is absent without it. */
public final class NeuralLinkModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VisorConfigScreen::new;
	}
}
