package net.neurallink.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.neurallink.client.ClientSession;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.registry.ModSounds;

/** body/host transitions: search, entry, collapse, reveal, signal loss. */
public final class VisorTransitions {
	private static final long LOADING_MIN_MS = 700;
	private static final long SWITCH_MIN_MS = 300;
	private static final long LOADING_TIMEOUT_MS = 8000;
	private static final long ENTER_MS = 450;
	private static final long COLLAPSE_MS = 250;
	private static final long OPEN_MS = 280;
	/** Server's wait for a far host. */
	private static final long WAIT_MS = 5000;
	private static final int WAIT_BAR = 90;

	private static long loadingAt;
	/** shortest search start; the wheel's collapse covers the screen before it. */
	private static long minimumFrom;
	private static long enterAt;
	private static long collapseAt;
	private static long openAt;
	private static int switchFrom = -1;
	private static boolean switchConfirmed;
	/** Host in an unloaded chunk: the old host stays controlled, only a plaque searches. */
	private static boolean waiting;

	private VisorTransitions() {
	}

	static void reset() {
		loadingAt = 0;
		waiting = false;
		enterAt = 0;
		collapseAt = 0;
		openAt = 0;
		SignalLost.reset();
	}

	public static void beginLoading() {
		beginLoading(Util.getMeasuringTimeMs());
	}

	/** in a session this is a switch: waits for the host to change. */
	public static void beginLoading(long shownFrom) {
		SignalLost.reset(); // new connect cuts the loss so dead air won't cover the next host
		openAt = 0;
		loadingAt = Util.getMeasuringTimeMs();
		minimumFrom = Math.max(loadingAt, shownFrom);
		switchFrom = ClientSession.mobId();
		switchConfirmed = false;
		waiting = false;
		HudSounds.play(ModSounds.CONNECT, 0.7F);
	}

	/** Connect accepted; a host merely converted during a switch wait doesn't count. */
	static void onConnected() {
		switchConfirmed = true;
		if (waiting) {
			waiting = false;
			minimumFrom = Util.getMeasuringTimeMs(); // the transition still shows the search, as for a near host
		}
	}

	/** server loads the host's chunk; a switch returns control to the old host meanwhile. */
	static void onConnecting() {
		if (loadingAt != 0 && switchFrom != -1) {
			waiting = true;
		}
	}

	/** Search covers the view, host stands. For a far host only a plaque searches. */
	public static boolean coversView() {
		return loadingAt != 0 && !waiting;
	}

	/** for tests: switch waiting, old host still controlled. */
	public static boolean isWaiting() {
		return loadingAt != 0 && waiting;
	}

	public static void beginCollapse() {
		collapseAt = Util.getMeasuringTimeMs();
		HudSounds.play(ModSounds.DISCONNECT, 0.7F);
	}

	public static void onSessionStart() {
		SignalLost.reset();
		if (loadingAt == 0) {
			enterAt = Util.getMeasuringTimeMs();
		}
	}

	public static void onSessionEnd() {
		long now = Util.getMeasuringTimeMs();
		collapseAt = 0;
		enterAt = 0;
		loadingAt = 0; // a waiting switch dies with the session
		waiting = false;
		switchFrom = -1;
		if (!SignalLost.active(now)) {
			openAt = now;
		}
	}

	static void onSignalLost(long now) {
		// Loss arrives before the camera reset so the camera is still on the host.
		MinecraftClient client = MinecraftClient.getInstance();
		Entity camera = client.getCameraEntity();
		SignalLost.start(now, camera != null && camera != client.player ? camera.getDisplayName() : Text.empty());
		openAt = 0; // shutters wait for the loss to finish
	}

	/** for tests: signal loss play time, or -1. */
	public static long lostElapsedMs() {
		return SignalLost.elapsed(Util.getMeasuringTimeMs());
	}

	/** For tests: loss sound still held for stopping. */
	public static boolean lostHoldsSound() {
		return SignalLost.holdsSound();
	}

	/** for tests: the loss uses the host's last frame. */
	public static boolean lostHasFrame() {
		return SignalLost.hasFrame();
	}

	static void cancelLoading() {
		loadingAt = 0;
		waiting = false;
	}

	public static boolean isLoading() {
		return loadingAt != 0;
	}

	static void render(DrawContext context, TextRenderer text, int width, int height, long now) {
		if (loadingAt != 0 && now - loadingAt > LOADING_TIMEOUT_MS) {
			cancelLoading();
		}
		if (loadingAt != 0 && !waiting) {
			renderLoading(context, text, width, height, now);
			return;
		}
		if (enterAt != 0) {
			float t = (float) (now - enterAt) / ENTER_MS;
			if (t >= 1.0F) {
				enterAt = 0;
			} else {
				VisorEffects.noise(context, 0, 0, width, height, 1.0F - t);
			}
		}
		if (collapseAt != 0) {
			shutters(context, width, height, VisorTheme.easeOut((float) (now - collapseAt) / COLLAPSE_MS));
		} else if (openAt != 0) {
			float t = (float) (now - openAt) / OPEN_MS;
			if (t >= 1.0F) {
				openAt = 0;
			} else {
				shutters(context, width, height, 1.0F - VisorTheme.easeOut(t));
			}
		}
		if (SignalLost.started()) {
			if (!SignalLost.render(context, text, width, height, now)) {
				openAt = now;
				shutters(context, width, height, 1.0F); // this frame too or the body flashes between dead air and shutters
			}
		} else if (waiting && loadingAt != 0 && collapseAt == 0 && !MinecraftClient.getInstance().options.hudHidden) {
			renderWaiting(context, text, width, height, now);
		}
	}

	/** Under the crosshair, small enough to steer past. */
	private static void renderWaiting(DrawContext context, TextRenderer text, int width, int height, long now) {
		Text label = Text.translatable("hud.neurallink.searching");
		int panelWidth = Math.max(WAIT_BAR, text.getWidth(label)) + 12;
		int x = (width - panelWidth) / 2;
		int y = height / 2 + 14;
		context.fill(x, y, x + panelWidth, y + 22, VisorTheme.BG);
		context.drawBorder(x, y, panelWidth, 22, VisorTheme.ACCENT);
		context.drawCenteredTextWithShadow(text, label, width / 2, y + 4, VisorTheme.TEXT);
		int barX = (width - WAIT_BAR) / 2;
		float progress = Math.min(0.9F, (float) (now - loadingAt) / WAIT_MS);
		context.fill(barX, y + 15, barX + WAIT_BAR, y + 18, VisorTheme.PANEL);
		context.fill(barX, y + 15, barX + (int) (WAIT_BAR * progress), y + 18, VisorTheme.HIGHLIGHT);
	}

	private static void shutters(DrawContext context, int width, int height, float closed) {
		int half = (int) (height / 2.0F * closed);
		context.fill(0, 0, width, half, 0xFF000000);
		context.fill(0, height - half, width, height, 0xFF000000);
		if (closed > 0.05F) {
			int middle = height / 2;
			context.fill((int) (width * (1.0F - closed) / 2.0F), middle - 1, (int) (width * (1.0F + closed) / 2.0F), middle + 1, VisorTheme.HIGHLIGHT);
		}
	}

	private static void renderLoading(DrawContext context, TextRenderer text, int width, int height, long now) {
		boolean switching = switchFrom != -1;
		long minimum = switching ? SWITCH_MIN_MS : LOADING_MIN_MS;
		Entity camera = MinecraftClient.getInstance().getCameraEntity();
		// a switch ends on the server's yes plus the camera on the new host; a far one arrives later.
		boolean arrived = ClientSession.isActive() && ClientSession.mobId() != switchFrom
				&& (!switching || switchConfirmed && camera != null && camera.getId() == ClientSession.mobId());
		long shown = Math.max(0L, now - minimumFrom);
		if (arrived && shown >= minimum) {
			loadingAt = 0;
			enterAt = now;
			return;
		}
		context.fill(0, 0, width, height, VisorTheme.BG_SOLID);
		VisorEffects.noise(context, 0, 0, width, height, 0.25F);
		float progress = arrived ? (float) shown / minimum : Math.min(0.9F, (float) shown / minimum);
		int barWidth = 160;
		int x = (width - barWidth) / 2;
		int y = height / 2 + 6;
		context.drawCenteredTextWithShadow(text, Text.translatable("hud.neurallink.searching"), width / 2, y - 14, VisorTheme.TEXT);
		context.fill(x, y, x + barWidth, y + 4, VisorTheme.PANEL);
		context.fill(x, y, x + (int) (barWidth * progress), y + 4, VisorTheme.HIGHLIGHT);
		context.drawBorder(x - 1, y - 1, barWidth + 2, 6, VisorTheme.ACCENT);
	}

}
