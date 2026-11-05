package net.neurallink.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipLogic;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.gui.VisorTheme;

/** Red visor layer: grid, vignette, scanlines. Under the HUD, hidden by F1. */
public final class VisorOverlay {
	private static final Identifier VIGNETTE = NeuralLink.id("textures/gui/visor/vignette.png");
	private static final Identifier GRID = NeuralLink.id("textures/gui/visor/grid.png");
	private static final Identifier SCANLINES = NeuralLink.id("textures/gui/visor/scanlines.png");
	private static final float FADE_SPEED = 4.0F;
	private static final float SCROLL_PER_MS = 0.004F;
	private static final long RAINBOW_CYCLE_MS = 4000;
	private static final int RAINBOW_SEGMENTS = 24;
	private static final float RAINBOW_WASH = 0.12F;
	private static final float RAINBOW_GRID = 0.6F;
	private static final float RAINBOW_SCAN = 0.22F;
	/** texture measures: grid.png is 16 px, scanlines.png lights 2 rows in 8. */
	private static final int GRID_CELL = 16;
	private static final int SCAN_PERIOD = 8;

	private static float fade;
	private static long lastFrame;
	/** last frame's sweep position, -1 outside it. */
	private static int lastRevealed = -1;
	private static int lastTint = VisorTheme.WHITE;

	private VisorOverlay() {
	}

	/** For tests: last frame's share of configured strength. */
	public static float fade() {
		return fade;
	}

	/**
	 * jeb_ host: the usual grid and scanlines in rainbow colours over a light rainbow wash sliding down. Deliberately
	 * bright, ignoring overlay strength.
	 */
	private static void rainbow(DrawContext context, int width, int height, long now, float scroll) {
		float shift = (float) (now % RAINBOW_CYCLE_MS) / RAINBOW_CYCLE_MS;
		for (int band = 0; band < RAINBOW_SEGMENTS; band++) {
			int top = height * band / RAINBOW_SEGMENTS;
			int bottom = height * (band + 1) / RAINBOW_SEGMENTS;
			context.fillGradient(0, top, width, bottom, rainbowAt(top, height, shift, RAINBOW_WASH * fade), rainbowAt(bottom, height, shift, RAINBOW_WASH * fade));
		}
		// Grid: 16-pixel cells drifting up like the red one.
		int gridOffset = MathHelper.floorMod((int) (scroll * 0.5F), GRID_CELL);
		for (int y = GRID_CELL - gridOffset; y < height; y += GRID_CELL) {
			context.fill(0, y, width, y + 1, rainbowAt(y, height, shift, RAINBOW_GRID * fade));
		}
		for (int x = 0; x < width; x += GRID_CELL) { // vertical lines: gradients between fixed rows
			for (int band = 0; band < RAINBOW_SEGMENTS; band++) {
				int top = height * band / RAINBOW_SEGMENTS;
				int bottom = height * (band + 1) / RAINBOW_SEGMENTS;
				context.fillGradient(x, top, x + 1, bottom, rainbowAt(top, height, shift, RAINBOW_GRID * fade), rainbowAt(bottom, height, shift, RAINBOW_GRID * fade));
			}
		}
		// scanlines: 2 rows in 8 drifting down like the red ones.
		int scanOffset = MathHelper.floorMod((int) scroll, SCAN_PERIOD);
		for (int y = scanOffset - SCAN_PERIOD; y < height; y += SCAN_PERIOD) {
			context.fill(0, Math.max(0, y), width, Math.max(0, y + 2), rainbowAt(Math.max(0, y), height, shift, RAINBOW_SCAN * fade));
		}
		lastTint = rainbowAt(0, height, shift, 1.0F);
	}

	/** Rainbow colour at this row now; frame corners use it. */
	static int rainbowColour(int y, int height, long now) {
		return rainbowAt(y, height, (float) (now % RAINBOW_CYCLE_MS) / RAINBOW_CYCLE_MS, 1.0F);
	}

	/** one rainbow per screen height, moving down with shift. */
	private static int rainbowAt(int y, int height, float shift, float alpha) {
		float hue = (float) y / height - shift;
		return VisorTheme.withAlpha(MathHelper.hsvToRgb(hue - MathHelper.floor(hue), 0.85F, 1.0F) | 0xFF000000, alpha);
	}

	/** For tests: last overlay tint, white unless jeb_ colours it. */
	public static int tint() {
		return lastTint;
	}

	public static boolean worn(MinecraftClient client) {
		return client.player != null && !ChipLogic.wornHelmet(client.player).isEmpty();
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		long now = Util.getMeasuringTimeMs();
		float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		boolean booting = VisorBoot.active() && worn(client); // helmet removed between ticks stops the sweep now
		if (VisorBoot.waiting()) {
			fade = 0.0F; // boot reveals it once the screen closes
		} else if (booting) {
			fade = VisorBoot.overlayLevel(now); // boot drives the overlay
		} else {
			if (lastRevealed >= 0) {
				fade = 0.0F; // cut mid-sweep: the unrevealed part mustn't flash in on fade-out
			}
			fade = VisorTheme.approach(Math.min(fade, 1.0F), worn(client) ? 1.0F : 0.0F, dt, FADE_SPEED);
		}
		int revealed = booting ? VisorBoot.revealed(height, now) : -1;
		lastRevealed = revealed;
		if (fade < 0.01F || revealed == 0) {
			return;
		}
		if (revealed > 0) {
			context.enableScissor(0, 0, width, revealed); // only what the sweep passed
		}
		float strength = ClientConfig.get().overlayStrength * fade;
		float scroll = now * SCROLL_PER_MS;
		if (EasterEggs.rainbow(client)) {
			rainbow(context, width, height, now, scroll);
		} else {
			lastTint = VisorTheme.WHITE;
			context.drawTexture(RenderLayer::getGuiTextured, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 128, 72, 128, 72, VisorTheme.withAlpha(VisorTheme.WHITE, strength * 1.4F));
			context.drawTexture(RenderLayer::getGuiTextured, GRID, 0, 0, 0.0F, scroll * 0.5F, width, height, 16, 16, VisorTheme.withAlpha(VisorTheme.WHITE, strength));
			context.drawTexture(RenderLayer::getGuiTextured, SCANLINES, 0, 0, 0.0F, -scroll, width, height, 8, 8, VisorTheme.withAlpha(VisorTheme.WHITE, strength));
		}
		if (revealed > 0) {
			context.disableScissor();
		}
	}
}
