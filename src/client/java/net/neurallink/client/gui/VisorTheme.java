package net.neurallink.client.gui;

import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

public final class VisorTheme {
	public static final int BG = 0xE01E070C;
	public static final int BG_SOLID = 0xFF1E070C;
	public static final int PANEL = 0xFF4A0F1C;
	public static final int PANEL_HOVER = 0xFF6E1423;
	public static final int ACCENT = 0xFFA4161A;
	public static final int HIGHLIGHT = 0xFFE5383B;
	public static final int TEXT = 0xFFF5D9D0;
	public static final int TEXT_DIM = 0xFFB58A86;

	public static final int WHITE = 0xFFFFFFFF;

	private VisorTheme() {
	}

	public static int withAlpha(int color, float alpha) {
		int base = color >>> 24;
		return ((int) (base * MathHelper.clamp(alpha, 0.0F, 1.0F)) << 24) | (color & 0xFFFFFF);
	}

	/** vanilla draws alpha under 4 opaque, so fades stop just above. */
	public static int textColor(int color, float alpha) {
		return withAlpha(color, Math.max(0.05F, alpha));
	}

	public static int mix(int from, int to, float amount) {
		return ColorHelper.lerp(MathHelper.clamp(amount, 0.0F, 1.0F), from, to);
	}

	/** Frame-rate independent smoothing. */
	public static float approach(float value, float target, float dt, float speed) {
		return value + (target - value) * (1.0F - (float) Math.exp(-dt * speed));
	}

	public static float easeOut(float t) {
		float clamped = MathHelper.clamp(t, 0.0F, 1.0F);
		return 1.0F - (1.0F - clamped) * (1.0F - clamped) * (1.0F - clamped);
	}

	/** hit test: right and bottom edges excluded. */
	public static boolean inside(double px, double py, int x, int y, int w, int h) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}
}
