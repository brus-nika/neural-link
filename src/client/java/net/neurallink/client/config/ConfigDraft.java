package net.neurallink.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.MathHelper;
import net.neurallink.client.BodySoundPlayer;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.ClientSession;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.network.ConfigPayload;

/** settings draft both screens edit, saved only on save. */
public final class ConfigDraft {
	public static final int OVERLAY_MAX = 50;
	public static final int CHIPS_MAX = 32;
	public static final int DISTANCE_STEP = 16;
	public static final int NOISE_MAX = 16384;
	public static final int PLAYER_CHIPS_MAX = 64;

	public boolean allowBosses;
	public int maxChips;
	public int noiseStart;
	public int noiseMax;
	public List<String> blacklist;
	public boolean depthNoise;
	public int connectCooldown;
	public int playerChips;
	public boolean spawnProtection;
	public List<String> dimensions;
	public boolean bodyTrance;
	public int overlayPercent;
	public boolean showHints;
	public boolean offscreenArrows;
	public boolean mobVision;
	public boolean easterEggs;
	public int bodySounds;

	private final float savedOverlay = ClientConfig.get().overlayStrength;
	private final int savedSliderHintUses = ClientConfig.get().sliderHintUses;
	private final String savedState;

	public ConfigDraft() {
		if (MinecraftClient.getInstance().world == null) {
			NeuralLinkConfig.load(); // from the main menu show the file, not stale memory
		}
		allowBosses = NeuralLinkConfig.allowBosses;
		maxChips = NeuralLinkConfig.maxChipsPerHelmet;
		noiseStart = NeuralLinkConfig.noiseStartDistance;
		noiseMax = NeuralLinkConfig.noiseMaxDistance;
		blacklist = new ArrayList<>(NeuralLinkConfig.entityBlacklist);
		depthNoise = NeuralLinkConfig.depthNoise;
		connectCooldown = NeuralLinkConfig.connectCooldownSeconds;
		playerChips = NeuralLinkConfig.maxChipsPerPlayer;
		spawnProtection = NeuralLinkConfig.spawnProtection;
		dimensions = new ArrayList<>(NeuralLinkConfig.dimensionBlacklist);
		bodyTrance = NeuralLinkConfig.bodyTrance;
		overlayPercent = Math.round(savedOverlay * 100.0F);
		offscreenArrows = ClientConfig.get().offscreenArrows;
		mobVision = ClientConfig.get().mobVision;
		easterEggs = ClientConfig.get().easterEggs;
		bodySounds = ClientConfig.get().bodySoundPercent;
		savedState = state();
	}

	/** On a foreign server values come from it and only its owner may change them. */
	public static boolean serverEditable() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null || client.isIntegratedServerRunning();
	}

	private String state() {
		return allowBosses + "|" + maxChips + "|" + noiseStart + "|" + noiseMax + "|" + blacklist + "|" + depthNoise + "|" + connectCooldown + "|" + playerChips
				+ "|" + spawnProtection + "|" + dimensions + "|" + bodyTrance + "|" + overlayPercent + "|" + showHints + "|" + offscreenArrows + "|" + mobVision + "|" + easterEggs + "|" + bodySounds;
	}

	public boolean changed() {
		return !Objects.equals(state(), savedState);
	}

	/** applied at once to preview overlay strength. */
	public void setOverlay(int percent) {
		overlayPercent = MathHelper.clamp(percent, 0, OVERLAY_MAX);
		ClientConfig.get().overlayStrength = overlayPercent / 100.0F;
	}

	public void setNoiseStart(int distance) {
		noiseStart = MathHelper.clamp(distance, 0, NOISE_MAX - DISTANCE_STEP);
		noiseMax = Math.max(noiseMax, noiseStart + DISTANCE_STEP);
	}

	public void setNoiseMax(int distance) {
		noiseMax = MathHelper.clamp(distance, DISTANCE_STEP, NOISE_MAX);
		noiseStart = Math.min(noiseStart, noiseMax - DISTANCE_STEP);
	}

	public void resetToDefaults() {
		setOverlay(ClientConfig.DEFAULT_OVERLAY_PERCENT);
		showHints = false;
		offscreenArrows = true;
		mobVision = true;
		easterEggs = true;
		bodySounds = BodySoundPlayer.DEFAULT_PERCENT;
		if (serverEditable()) {
			allowBosses = false;
			maxChips = NeuralLinkConfig.DEFAULT_MAX_CHIPS;
			noiseStart = NeuralLinkConfig.DEFAULT_NOISE_START;
			noiseMax = NeuralLinkConfig.DEFAULT_NOISE_MAX;
			blacklist = new ArrayList<>();
			depthNoise = true;
			connectCooldown = NeuralLinkConfig.DEFAULT_CONNECT_COOLDOWN;
			playerChips = 0;
			spawnProtection = true;
			dimensions = new ArrayList<>();
			bodyTrance = false;
		}
	}

	/** Drops the edits. Slider badge use still counts, it's about the window. */
	public void cancel() {
		ClientConfig.get().overlayStrength = savedOverlay;
		if (ClientConfig.get().sliderHintUses != savedSliderHintUses) {
			ClientConfig.save();
		}
	}

	public void save() {
		ClientConfig config = ClientConfig.get();
		config.overlayStrength = overlayPercent / 100.0F;
		config.offscreenArrows = offscreenArrows;
		boolean visionChanged = config.mobVision != mobVision;
		config.mobVision = mobVision;
		config.easterEggs = easterEggs;
		config.bodySoundPercent = bodySounds;
		if (visionChanged) {
			ClientSession.applyMobVision(MinecraftClient.getInstance()); // seen at once without leaving the session
		}
		if (showHints) {
			config.exitHintUses = 0;
			config.verticalHintUses = 0;
			config.sliderHintUses = 0;
		}
		ClientConfig.save();
		if (serverEditable()) {
			NeuralLinkConfig.update(new ConfigPayload(allowBosses, maxChips, noiseStart, noiseMax, List.copyOf(blacklist), depthNoise,
					connectCooldown, playerChips, spawnProtection, List.copyOf(dimensions), bodyTrance));
			IntegratedServer server = MinecraftClient.getInstance().getServer();
			if (server != null) {
				server.execute(() -> server.getPlayerManager().getPlayerList().forEach(NeuralLinkConfig::sync));
			}
		}
	}
}
