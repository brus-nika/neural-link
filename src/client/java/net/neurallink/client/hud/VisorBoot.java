package net.neurallink.client.hud;

import java.util.UUID;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.neurallink.chip.ChipLogic;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.registry.ModComponents;
import net.neurallink.registry.ModSounds;

/**
 * visor boot when the helmet goes on: a top sweep reveals the overlay, status lines type out, then it settles; frames,
 * arrows and counter wait. A new player entity gets its helmet a few ticks late, which doesn't count as putting it on.
 */
public final class VisorBoot {
	private static final long BOOT_MS = 1000;
	private static final long SWEEP_MS = 450;
	/** After the status lines: sweep, lines, flare. */
	private static final long FLARE_AT = 830;
	private static final long FLARE_MS = 70;
	private static final long LINES_OUT_MS = 850;
	private static final long CHAR_MS = 22;
	private static final long[] LINE_AT = {120, 320, 560};
	private static final long BAR_FROM = 450;
	private static final long BAR_TO = 800;
	private static final int BAR_SEGMENTS = 5;
	private static final int SETTLE_TICKS = 20;
	private static final int TEXT_X = 10;
	private static final int TEXT_Y = 10;
	private static final int LINE_HEIGHT = 11;

	private static ClientPlayerEntity lastPlayer;
	/** last tick's helmet id: a different helmet swapped in one click has other implants and boots too. */
	private static UUID wornId;
	private static boolean wasWorn;
	private static int settleTicks;
	/** Put on behind a screen: boot waits until the player can see it. */
	private static boolean pending;
	private static long startedAt = -1;

	private VisorBoot() {
	}

	public static void tick(MinecraftClient client) {
		boolean worn = VisorOverlay.worn(client);
		UUID id = worn ? ChipLogic.wornHelmet(client.player).get(ModComponents.HELMET_ID) : null;
		if (client.player != lastPlayer) {
			lastPlayer = client.player;
			settleTicks = SETTLE_TICKS;
			startedAt = -1;
			pending = false;
		}
		if (settleTicks > 0) {
			settleTicks--;
		} else if (worn && (!wasWorn || wornId != null && !wornId.equals(id))) {
			// the id arrives with the first bound chip, so no id to an id is the same helmet; anything else is a swap. A
			// fresh id-less helmet has no implants, so not booting for it is fine.
			pending = true;
			startedAt = -1;
		}
		if (!worn) {
			pending = false;
			startedAt = -1; // taken off mid-boot: just fades out
		}
		if (pending && client.currentScreen == null) {
			pending = false;
			startedAt = Util.getMeasuringTimeMs();
			HudSounds.play(ModSounds.VISOR_BOOT, 0.6F);
		}
		wasWorn = worn;
		wornId = id;
	}

	public static void reset() {
		lastPlayer = null;
		wornId = null;
		wasWorn = false;
		pending = false;
		startedAt = -1;
	}

	public static boolean active() {
		return startedAt >= 0 && Util.getMeasuringTimeMs() - startedAt < BOOT_MS;
	}

	/** helmet on, boot waiting for a screen to close; no visor yet. */
	public static boolean waiting() {
		return pending;
	}

	/** For tests: boot play time, or -1. */
	public static long elapsedMs() {
		return active() ? Util.getMeasuringTimeMs() - startedAt : -1;
	}

	private static long elapsed(long now) {
		return now - startedAt;
	}

	/** how far down the overlay is revealed, -1 after the sweep. */
	static int revealed(int height, long now) {
		if (!active() || elapsed(now) >= SWEEP_MS) {
			return -1;
		}
		return MathHelper.ceil(height * sweep(now));
	}

	private static float sweep(long now) {
		float t = (float) elapsed(now) / SWEEP_MS;
		return t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2) / 2.0F;
	}

	/** Boot overlay factor: dim, a flare, then normal strength. */
	static float overlayLevel(long now) {
		long t = elapsed(now);
		if (t < FLARE_AT) {
			return 0.55F;
		}
		if (t < FLARE_AT + FLARE_MS) {
			return MathHelper.lerp((float) (t - FLARE_AT) / FLARE_MS, 0.55F, 1.8F);
		}
		return MathHelper.lerp(VisorTheme.easeOut((float) (t - FLARE_AT - FLARE_MS) / (BOOT_MS - FLARE_AT - FLARE_MS)), 1.8F, 1.0F);
	}

	static void render(DrawContext context, TextRenderer text, int width, int height, long now, int active, int limit) {
		if (!active()) {
			return;
		}
		long t = elapsed(now);
		int y = revealed(height, now);
		if (y >= 0) {
			context.fillGradient(0, Math.max(0, y - 28), width, y, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, 0.0F), VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, 0.28F));
			VisorEffects.noise(context, 0, y - 5, width, 10, 0.35F);
			context.fill(0, y - 1, width, y + 1, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, 0.9F));
			context.fill(0, y, width, y + 1, VisorTheme.withAlpha(VisorTheme.TEXT, 0.55F));
		}

		float alpha = 1.0F - MathHelper.clamp((float) (t - LINES_OUT_MS) / (BOOT_MS - LINES_OUT_MS), 0.0F, 1.0F);
		Text implants = limit > 0 ? Text.translatable("hud.neurallink.boot.implants", active, limit) : Text.translatable("hud.neurallink.boot.implants_wait");
		Text[] lines = {Text.translatable("hud.neurallink.boot.visor"), Text.translatable("hud.neurallink.boot.calibrate"), implants};
		int[] colors = {VisorTheme.HIGHLIGHT, VisorTheme.TEXT, VisorTheme.TEXT_DIM};
		for (int i = 0; i < lines.length; i++) {
			if (t < LINE_AT[i]) {
				break;
			}
			String full = lines[i].getString();
			int shown = (int) Math.min(full.length(), (t - LINE_AT[i]) / CHAR_MS + 1);
			int lineY = TEXT_Y + i * LINE_HEIGHT;
			int color = VisorTheme.textColor(colors[i], alpha);
			context.drawTextWithShadow(text, Text.literal(full.substring(0, shown)), TEXT_X, lineY, color);
			int end = TEXT_X + text.getWidth(full.substring(0, shown));
			if (shown < full.length()) {
				context.fill(end + 1, lineY, end + 5, lineY + 8, color); // typing cursor
			} else if (i == 1) {
				calibration(context, end + 4, lineY + 2, t, alpha);
			}
		}
	}

	private static void calibration(DrawContext context, int x, int y, long t, float alpha) {
		float done = MathHelper.clamp((float) (t - BAR_FROM) / (BAR_TO - BAR_FROM), 0.0F, 1.0F);
		int lit = MathHelper.floor(done * BAR_SEGMENTS + 0.001F);
		for (int i = 0; i < BAR_SEGMENTS; i++) {
			int color = i < lit ? VisorTheme.HIGHLIGHT : VisorTheme.withAlpha(VisorTheme.PANEL, 0.9F);
			context.fill(x + i * 5, y, x + i * 5 + 4, y + 4, VisorTheme.withAlpha(color, alpha));
		}
	}
}
