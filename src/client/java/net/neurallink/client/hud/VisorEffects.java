package net.neurallink.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipResult;
import net.neurallink.client.ClientSession;
import net.neurallink.client.gui.HostWheelScreen;
import net.neurallink.client.gui.VisorScreen;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.HostsPayload;
import net.neurallink.network.VisorEventPayload;
import net.neurallink.registry.ModSounds;

/** shaderless overlay above the HUD: noise, hit flashes, chip counter, toasts. */
public final class VisorEffects {
	private static final Identifier NOISE = NeuralLink.id("textures/gui/visor/noise.png");
	private static final Identifier VIGNETTE = NeuralLink.id("textures/gui/visor/vignette.png");
	private static final Identifier LINK = NeuralLink.id("textures/gui/hud/link.png");
	private static final long PULSE_MS = 350;
	private static final long TOAST_MS = 2200;
	private static final long LINK_MS = 1500;
	private static final long SHAKE_MS = 500;
	private static final long NOISE_FRAME_MS = 50;
	private static final float MAX_SIGNAL_NOISE = 0.5F;

	private static final Random RANDOM = Random.create();

	private static long pulseAt;
	private static float pulseStrength;
	private static long bodyPulseAt;
	private static long toastAt;
	private static Text toast;
	private static long linkAt;
	private static long shakeAt;
	private static int active;
	private static int limit;
	private static long noiseFrame;
	private static float noiseU;
	private static float noiseV;

	private VisorEffects() {
	}

	public static void reset() {
		VisorTransitions.reset();
		toast = null;
		active = 0;
		limit = 0;
	}

	public static void onEvent(VisorEventPayload.Kind kind) {
		long now = Util.getMeasuringTimeMs();
		switch (kind) {
			case MOB_HIT -> pulse(now, 0.55F);
			case HOST_SWITCH -> {
				pulse(now, 0.7F);
				HudSounds.play(ModSounds.SWITCH, 0.7F);
			}
			case BODY_HIT -> {
				bodyPulseAt = now;
				HudSounds.play(ModSounds.BODY_ALARM, 0.8F);
			}
			case SIGNAL_LOST -> {
				VisorTransitions.onSignalLost(now);
				SignalLost.keepSound(HudSounds.play(ModSounds.SIGNAL_LOST, 0.9F));
			}
			default -> {
			}
		}
	}

	static void pulse(long now, float strength) {
		pulseAt = now;
		pulseStrength = strength;
	}

	public static void onHosts(HostsPayload payload) {
		active = payload.active();
		limit = payload.limit();
	}

	/** Sound and image, not a hotbar line; text only when the reason is unclear. */
	public static void onChipResult(ChipResult result) {
		MinecraftClient client = MinecraftClient.getInstance();
		// no visor, no menu: an open one would refuse every second.
		if (result == ChipResult.NEED_HELMET && (client.currentScreen instanceof VisorScreen || client.currentScreen instanceof HostWheelScreen)) {
			client.setScreen(null);
		}
		long now = Util.getMeasuringTimeMs();
		if (result.isToast()) {
			HudSounds.play(ModSounds.REFUSE, 0.7F);
			toast = Text.translatable(result.translationKey());
			toastAt = now;
			VisorTransitions.cancelLoading();
			return;
		}
		switch (result) {
			case BOUND, REBOUND -> linkAt = now;
			case CONNECTED -> VisorTransitions.onConnected();
			case CONNECTING -> VisorTransitions.onConnecting();
			case INSERTED, EXTRACTED, DISABLED -> {
			}
			case LIMIT -> {
				shakeAt = now;
				HudSounds.play(ModSounds.REFUSE, 0.7F);
				VisorTransitions.cancelLoading();
			}
			case COOLDOWN -> { // sound only; the search screen closes
				HudSounds.play(ModSounds.REFUSE, 0.7F);
				VisorTransitions.cancelLoading();
			}
			case NOT_OWNER, INVALID_TARGET, NOT_PET_OWNER, ALREADY_CHIPPED, CHIP_IN_USE, ALREADY_BOUND -> HudSounds.play(ModSounds.REFUSE, 0.7F);
			default -> {
			}
		}
	}

	public static Text toast() {
		return toast;
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		long now = Util.getMeasuringTimeMs();
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 300.0F);
		if (now - noiseFrame > NOISE_FRAME_MS) {
			noiseFrame = now;
			noiseU = RANDOM.nextInt(128);
			noiseV = RANDOM.nextInt(128);
		}
		renderSessionNoise(context, width, height, now);
		renderBodyPulse(context, width, height, now);
		if (!client.options.hudHidden) {
			if (VisorOverlay.worn(client)) {
				if (VisorBoot.active()) { // boot lines show the implant count meanwhile
					VisorBoot.render(context, client.textRenderer, width, height, now, active, limit);
				} else if (!VisorBoot.waiting()) {
					renderCounter(context, client.textRenderer, width, now);
				}
			}
			renderToast(context, client.textRenderer, width, height, now);
			renderLink(context, width, now);
		}
		VisorTransitions.render(context, client.textRenderer, width, height, now);
		context.getMatrices().pop();
	}

	static void noise(DrawContext context, int x, int y, int width, int height, float alpha) {
		context.drawTexture(RenderLayer::getGuiTextured, NOISE, x, y, noiseU, noiseV, width, height, 128, 128, VisorTheme.withAlpha(VisorTheme.WHITE, alpha));
	}

	private static void renderSessionNoise(DrawContext context, int width, int height, long now) {
		float pulse = pulseStrength * (1.0F - Math.min(1.0F, (float) (now - pulseAt) / PULSE_MS));
		HudStatePayload state = SessionHud.state();
		float signal = ClientSession.isActive() && state != null ? (100 - state.signal()) / 100.0F * MAX_SIGNAL_NOISE : 0.0F;
		float alpha = Math.max(pulse, signal);
		if (alpha > 0.01F) {
			noise(context, 0, 0, width, height, alpha);
			if (pulse > 0.2F) {
				int tearY = RANDOM.nextInt(Math.max(1, height));
				context.fill(0, tearY, width, tearY + 2, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, pulse));
			}
		}
	}

	private static void renderBodyPulse(DrawContext context, int width, int height, long now) {
		float t = (float) (now - bodyPulseAt) / (PULSE_MS * 2);
		if (t < 1.0F) {
			context.drawTexture(RenderLayer::getGuiTextured, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 128, 72, 128, 72, VisorTheme.withAlpha(VisorTheme.WHITE, 1.0F - t));
		}
	}

	private static void renderCounter(DrawContext context, TextRenderer text, int width, long now) {
		if (limit == 0) {
			return;
		}
		long sinceShake = now - shakeAt;
		boolean shaking = sinceShake < SHAKE_MS;
		Text counter = Text.literal(active + "/" + limit);
		int x = width - text.getWidth(counter) - 14 + (shaking ? (int) (MathHelper.sin(sinceShake * 0.06F) * 2.5F) : 0);
		context.drawTextWithShadow(text, counter, x, 13, shaking ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT_DIM);
	}

	private static void renderToast(DrawContext context, TextRenderer text, int width, int height, long now) {
		if (toast == null) {
			return;
		}
		float t = (float) (now - toastAt) / TOAST_MS;
		if (t >= 1.0F) {
			toast = null;
			return;
		}
		float alpha = Math.min(1.0F, (1.0F - t) * 4.0F);
		int toastWidth = text.getWidth(toast) + 12;
		int x = (width - toastWidth) / 2;
		int y = height - 62;
		context.fill(x, y, x + toastWidth, y + 15, VisorTheme.withAlpha(VisorTheme.PANEL, alpha * 0.95F));
		context.drawBorder(x, y, toastWidth, 15, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, alpha));
		context.drawCenteredTextWithShadow(text, toast, width / 2, y + 4, VisorTheme.textColor(VisorTheme.TEXT, alpha));
	}

	private static void renderLink(DrawContext context, int width, long now) {
		float t = (float) (now - linkAt) / LINK_MS;
		if (t < 1.0F) {
			float pop = VisorTheme.easeOut(t * 4.0F);
			context.drawTexture(RenderLayer::getGuiTextured, LINK, width - 24, 24 - (int) (pop * 2.0F), 0.0F, 0.0F, 10, 10, 10, 10, VisorTheme.withAlpha(VisorTheme.WHITE, (1.0F - t) * 1.5F));
		}
	}
}
