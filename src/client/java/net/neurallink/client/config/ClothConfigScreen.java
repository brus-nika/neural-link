package net.neurallink.client.config;

import java.util.List;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.neurallink.NeuralLink;
import net.neurallink.client.BodySoundPlayer;
import net.neurallink.client.ClientConfig;
import net.neurallink.config.NeuralLinkConfig;

/** Same settings in Cloth Config, loaded only if that mod is present. */
final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	static Screen create(Screen parent) {
		ConfigDraft draft = new ConfigDraft();
		// setters move the other bound and Cloth saves every entry, so apply only edited values.
		int openedStart = draft.noiseStart;
		int openedMax = draft.noiseMax;
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("config.neurallink.title"))
				.setDefaultBackgroundTexture(NeuralLink.id("textures/gui/config_background.png"))
				.setTransparentBackground(false)
				.setSavingRunnable(draft::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory visor = builder.getOrCreateCategory(Text.translatable("config.neurallink.section.client"));
		visor.addEntry(entries.startIntSlider(Text.translatable("config.neurallink.overlay"), draft.overlayPercent, 0, ConfigDraft.OVERLAY_MAX)
				.setDefaultValue(ClientConfig.DEFAULT_OVERLAY_PERCENT)
				.setTextGetter(value -> Text.literal(value + "%"))
				.setTooltip(Text.translatable("config.neurallink.overlay.hint"))
				.setSaveConsumer(draft::setOverlay)
				.build());
		visor.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.arrows"), draft.offscreenArrows)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.neurallink.arrows.hint"))
				.setSaveConsumer(value -> draft.offscreenArrows = value)
				.build());
		visor.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.mob_vision"), draft.mobVision)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.neurallink.mob_vision.hint"))
				.setSaveConsumer(value -> draft.mobVision = value)
				.build());
		visor.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.easter_eggs"), draft.easterEggs)
				.setDefaultValue(true)
				.setTooltip(Text.translatable("config.neurallink.easter_eggs.hint"))
				.setSaveConsumer(value -> draft.easterEggs = value)
				.build());
		visor.addEntry(entries.startIntSlider(Text.translatable("config.neurallink.body_sounds"), draft.bodySounds, 0, 100)
				.setDefaultValue(BodySoundPlayer.DEFAULT_PERCENT)
				.setTextGetter(value -> Text.literal(value + "%"))
				.setTooltip(Text.translatable("config.neurallink.body_sounds.hint"))
				.setSaveConsumer(value -> draft.bodySounds = value)
				.build());
		visor.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.hints"), false)
				.setDefaultValue(false)
				.setTooltip(Text.translatable("config.neurallink.hints.hint"))
				.setSaveConsumer(value -> draft.showHints = value)
				.build());

		boolean editable = ConfigDraft.serverEditable();
		Requirement requirement = () -> editable;
		ConfigCategory server = builder.getOrCreateCategory(Text.translatable("config.neurallink.section.server"));
		server.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.bosses"), draft.allowBosses)
				.setDefaultValue(false)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.bosses.hint"))
				.setSaveConsumer(value -> draft.allowBosses = value)
				.build());
		server.addEntry(entries.startIntSlider(Text.translatable("config.neurallink.chips"), draft.maxChips, 1, ConfigDraft.CHIPS_MAX)
				.setDefaultValue(NeuralLinkConfig.DEFAULT_MAX_CHIPS)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.chips.hint"))
				.setSaveConsumer(value -> draft.maxChips = value)
				.build());
		IntFieldBuilder noiseStart = entries.startIntField(Text.translatable("config.neurallink.noise_start"), draft.noiseStart)
				.setDefaultValue(NeuralLinkConfig.DEFAULT_NOISE_START)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.noise_start.hint"))
				.setSaveConsumer(value -> {
					if (value != openedStart) {
						draft.setNoiseStart(value);
					}
				});
		IntFieldBuilder noiseMax = entries.startIntField(Text.translatable("config.neurallink.noise_max"), draft.noiseMax)
				.setDefaultValue(NeuralLinkConfig.DEFAULT_NOISE_MAX)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.noise_max.hint"))
				.setSaveConsumer(value -> {
					if (value != openedMax) {
						draft.setNoiseMax(value);
					}
				});
		if (editable) { // any entry error disables Cloth Save: a foreign server may send out-of-bounds values
			noiseStart.setMin(0).setMax(ConfigDraft.NOISE_MAX - ConfigDraft.DISTANCE_STEP);
			noiseMax.setMin(ConfigDraft.DISTANCE_STEP).setMax(ConfigDraft.NOISE_MAX);
		}
		server.addEntry(noiseStart.build());
		server.addEntry(noiseMax.build());
		server.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.depth_noise"), draft.depthNoise)
				.setDefaultValue(true)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.depth_noise.hint"))
				.setSaveConsumer(value -> draft.depthNoise = value)
				.build());
		server.addEntry(entries.startIntSlider(Text.translatable("config.neurallink.cooldown"), draft.connectCooldown, 0, NeuralLinkConfig.MAX_CONNECT_COOLDOWN)
				.setDefaultValue(NeuralLinkConfig.DEFAULT_CONNECT_COOLDOWN)
				.setRequirement(requirement)
				.setTextGetter(value -> Text.translatable("config.neurallink.seconds", value))
				.setTooltip(Text.translatable("config.neurallink.cooldown.hint"))
				.setSaveConsumer(value -> draft.connectCooldown = value)
				.build());
		server.addEntry(entries.startIntSlider(Text.translatable("config.neurallink.player_chips"), draft.playerChips, 0, ConfigDraft.PLAYER_CHIPS_MAX)
				.setDefaultValue(0)
				.setRequirement(requirement)
				.setTextGetter(value -> value == 0 ? Text.translatable("config.neurallink.player_chips.none") : Text.literal(String.valueOf(value)))
				.setTooltip(Text.translatable("config.neurallink.player_chips.hint"))
				.setSaveConsumer(value -> draft.playerChips = value)
				.build());
		server.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.spawn_protection"), draft.spawnProtection)
				.setDefaultValue(true)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.spawn_protection.hint"))
				.setSaveConsumer(value -> draft.spawnProtection = value)
				.build());
		server.addEntry(entries.startBooleanToggle(Text.translatable("config.neurallink.body_trance"), draft.bodyTrance)
				.setDefaultValue(false)
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.body_trance.hint"))
				.setSaveConsumer(value -> draft.bodyTrance = value)
				.build());
		server.addEntry(entries.startStrList(Text.translatable("config.neurallink.blacklist"), draft.blacklist)
				.setDefaultValue(List.of())
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.blacklist.hint"))
				.setSaveConsumer(value -> draft.blacklist = NeuralLinkConfig.normalizeIds(value))
				.build());
		server.addEntry(entries.startStrList(Text.translatable("config.neurallink.dimensions"), draft.dimensions)
				.setDefaultValue(List.of())
				.setRequirement(requirement)
				.setTooltip(Text.translatable("config.neurallink.dimensions.hint"))
				.setSaveConsumer(value -> draft.dimensions = NeuralLinkConfig.normalizeIds(value))
				.build());
		return builder.build();
	}
}
