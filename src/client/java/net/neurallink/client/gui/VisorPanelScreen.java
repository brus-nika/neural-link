package net.neurallink.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** shared by menu and settings: open animation, frame step, scrollbar. */
public abstract class VisorPanelScreen extends Screen {
	private static final float OPEN_MS = 180.0F;
	private static final float OPEN_SCALE = 0.96F;

	protected final long openedAt = Util.getMeasuringTimeMs();
	private long lastFrame = openedAt;

	protected VisorPanelScreen(Text title) {
		super(title);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, VisorTheme.withAlpha(VisorTheme.BG, openProgress()));
	}

	protected final boolean opening(long now) {
		return now - openedAt < OPEN_MS;
	}

	protected final float openProgress() {
		return VisorTheme.easeOut((float) (Util.getMeasuringTimeMs() - openedAt) / OPEN_MS);
	}

	/** seconds since last frame, capped so a stall can't overshoot animations. */
	protected final float frameDt(long now) {
		float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		return dt;
	}

	/** Panel grows from the center. Caller pops the matrix. */
	protected final void pushOpenTransform(DrawContext context, float open) {
		float scale = OPEN_SCALE + (1.0F - OPEN_SCALE) * open;
		context.getMatrices().push();
		context.getMatrices().translate(width / 2.0F, height / 2.0F, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(-width / 2.0F, -height / 2.0F, 0.0F);
	}

	protected final void drawScrollbar(DrawContext context, int x, int top, int bottom, double scroll, int maxScroll, int minThumb) {
		if (maxScroll > 0) {
			int track = bottom - top;
			int thumb = Math.max(minThumb, track * track / (track + maxScroll));
			int y = top + (int) ((track - thumb) * scroll / maxScroll);
			context.fill(x, y, x + 2, y + thumb, VisorTheme.ACCENT);
		}
	}
}
