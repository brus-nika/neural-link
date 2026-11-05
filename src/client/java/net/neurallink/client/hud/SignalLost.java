package net.neurallink.client.hud;

import java.util.Random;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.neurallink.NeuralLink;
import net.neurallink.client.gui.VisorTheme;
import org.slf4j.Logger;

/**
 * host gone: the last frame freezes, tears, greys out, folds to a line and a dot, then dead air until the shutters open on
 * the body. The loss packet comes before the camera reset, so the frame buffer still holds the host's view.
 */
final class SignalLost {
	static final long TOTAL_MS = 1700;
	private static final long FLASH_MS = 150;
	private static final long TEAR_MS = 600;
	private static final long FOLD_MS = 750;
	private static final long DOT_MS = 900;
	private static final long STUTTER_MS = 60;
	private static final float LABEL_SCALE = 3.0F;
	private static final Identifier FRAME = NeuralLink.id("signal_lost_frame");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Random RANDOM = new Random();

	private static long startedAt;
	private static NativeImageBackedTexture frame;
	private static int frameWidth;
	private static int frameHeight;
	private static Text hostName = Text.empty();
	private static long strips;
	private static long stripsAt;
	private static SoundInstance sound;

	private SignalLost() {
	}

	static void start(long now, Text lastHost) {
		startedAt = now;
		hostName = lastHost;
		release();
		MinecraftClient client = MinecraftClient.getInstance();
		try {
			NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
			frameWidth = image.getWidth();
			frameHeight = image.getHeight();
			frame = new NativeImageBackedTexture(image);
			client.getTextureManager().registerTexture(FRAME, frame);
		} catch (RuntimeException e) { // no frame: plays from black
			LOGGER.warn("Could not keep the last host frame", e);
			release();
		}
	}

	/** until its last frame is drawn, even overtime: it hands over to the shutters. */
	static boolean started() {
		return startedAt != 0;
	}

	static boolean active(long now) {
		return startedAt != 0 && now - startedAt <= TOTAL_MS;
	}

	/** A new connect cuts the loss sound so it won't play under the next search. */
	static void keepSound(SoundInstance instance) {
		sound = instance;
	}

	static void reset() {
		startedAt = 0;
		release();
		if (sound != null) {
			MinecraftClient.getInstance().getSoundManager().stop(sound);
			sound = null;
		}
	}

	/** for tests: loss sound still held for stopping. */
	static boolean holdsSound() {
		return sound != null;
	}

	/** For tests: loss play time, or -1. */
	static long elapsed(long now) {
		return active(now) ? now - startedAt : -1;
	}

	static boolean hasFrame() {
		return frame != null;
	}

	private static void release() {
		if (frame != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(FRAME);
			frame = null;
		}
	}

	/** false once the loss is over; the caller opens the shutters. */
	static boolean render(DrawContext context, TextRenderer text, int width, int height, long now) {
		long t = now - startedAt;
		if (t > TOTAL_MS) {
			startedAt = 0;
			release();
			return false;
		}
		context.fill(0, 0, width, height, 0xFF000000);
		if (t < TEAR_MS) {
			tear(context, width, height, t, now);
		} else if (t < FOLD_MS) {
			fold(context, width, height, (float) (t - TEAR_MS) / (FOLD_MS - TEAR_MS));
		} else if (t < DOT_MS) {
			dot(context, width, height, (float) (t - FOLD_MS) / (DOT_MS - FOLD_MS));
		} else {
			deadAir(context, text, width, height, t, now);
		}
		return true;
	}

	/** frozen frame in jittering strips with a red echo, colour draining. */
	private static void tear(DrawContext context, int width, int height, long t, long now) {
		float decay = (float) t / TEAR_MS;
		if (now - stripsAt >= STUTTER_MS || strips == 0) {
			stripsAt = now;
			strips = RANDOM.nextLong() | 1L;
		}
		Random cut = new Random(strips);
		int shift = (int) (4 + 28 * decay * decay);
		if (frame != null) {
			int y = 0;
			while (y < height) {
				int band = 2 + cut.nextInt(Math.max(3, (int) (height / (6 + 18 * decay))));
				int bottom = Math.min(height, y + band);
				int dx = cut.nextFloat() < 0.35F + 0.5F * decay ? cut.nextInt(shift * 2 + 1) - shift : 0;
				strip(context, dx, y, width, bottom, VisorTheme.WHITE);
				if (dx != 0) { // echo: same strip, red, a bit further
					strip(context, dx + Integer.signum(dx) * 3, y, width, bottom, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, 0.35F));
				}
				y = bottom;
			}
		}
		context.fill(0, 0, width, height, VisorTheme.withAlpha(VisorTheme.BG_SOLID, 0.15F + 0.55F * decay)); // colour drains
		VisorEffects.noise(context, 0, 0, width, height, 0.15F + 0.35F * decay); // light so the tearing still reads
		float flash = 1.0F - Math.min(1.0F, (float) t / FLASH_MS);
		if (flash > 0.0F) {
			context.fill(0, 0, width, height, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, 0.45F * flash));
		}
	}

	private static void strip(DrawContext context, int dx, int top, int width, int bottom, int color) {
		float v = (float) top * frameHeight / context.getScaledWindowHeight();
		int regionHeight = Math.max(1, Math.round((float) (bottom - top) * frameHeight / context.getScaledWindowHeight()));
		context.drawTexture(RenderLayer::getGuiTextured, FRAME, dx, top, 0.0F, v, width, bottom - top, frameWidth, regionHeight, frameWidth, frameHeight, color);
	}

	/** The picture squeezes to the middle row and brightens, like an old screen off. */
	private static void fold(DrawContext context, int width, int height, float t) {
		float squeeze = t * t; // slow first so the fold is seen, then snaps
		int half = Math.max(1, Math.round(height / 2.0F * (1.0F - squeeze)));
		int middle = height / 2;
		if (frame != null) {
			context.drawTexture(RenderLayer::getGuiTextured, FRAME, 0, middle - half, 0.0F, 0.0F, width, half * 2, frameWidth, frameHeight, frameWidth, frameHeight, VisorTheme.WHITE);
		}
		context.fill(0, middle - half, width, middle + half, VisorTheme.withAlpha(VisorTheme.TEXT, 0.25F + 0.65F * squeeze));
		context.fill(0, middle - 1, width, middle + 1, VisorTheme.withAlpha(VisorTheme.TEXT, 0.6F + 0.4F * squeeze));
	}

	/** line shrinks to a point that glows out. */
	private static void dot(DrawContext context, int width, int height, float t) {
		int middleX = width / 2;
		int middleY = height / 2;
		float shrink = VisorTheme.easeOut(Math.min(1.0F, t * 1.6F));
		int halfLine = Math.round(width / 2.0F * (1.0F - shrink));
		if (halfLine > 1) {
			context.fill(middleX - halfLine, middleY - 1, middleX + halfLine, middleY + 1, VisorTheme.TEXT);
		}
		float fade = 1.0F - MathHelper.clamp((t - 0.5F) * 2.0F, 0.0F, 1.0F);
		for (int ring = 3; ring >= 1; ring--) {
			int r = ring * 2;
			context.fill(middleX - r, middleY - r, middleX + r, middleY + r, VisorTheme.withAlpha(VisorTheme.TEXT, fade * 0.18F * (4 - ring)));
		}
		context.fill(middleX - 1, middleY - 1, middleX + 1, middleY + 1, VisorTheme.withAlpha(VisorTheme.TEXT, Math.max(0.05F, fade)));
	}

	/** Faint snow, the skipping label with red echo, last host below. */
	private static void deadAir(DrawContext context, TextRenderer text, int width, int height, long t, long now) {
		VisorEffects.noise(context, 0, 0, width, height, 0.08F);
		boolean skip = (now / 70) % 11 == 0; // an occasional dropped label frame
		int jitter = skip ? (int) ((now / 70) % 3) - 1 : 0;
		Text label = Text.translatable("hud.neurallink.lost");
		context.getMatrices().push();
		context.getMatrices().translate(width / 2.0F + jitter, height / 2.0F - 10.0F, 0.0F);
		context.getMatrices().scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
		context.drawCenteredTextWithShadow(text, label, 1, -4, VisorTheme.withAlpha(VisorTheme.ACCENT, skip ? 0.4F : 0.9F));
		if (!skip) {
			context.drawCenteredTextWithShadow(text, label, 0, -4, VisorTheme.HIGHLIGHT);
		}
		context.getMatrices().pop();
		float appear = MathHelper.clamp((float) (t - DOT_MS - 150) / 250.0F, 0.0F, 1.0F);
		if (appear > 0.0F && !hostName.getString().isEmpty()) {
			context.drawCenteredTextWithShadow(text, Text.translatable("hud.neurallink.lost.last", hostName), width / 2, height / 2 + 12, VisorTheme.textColor(VisorTheme.TEXT_DIM, appear));
		}
	}
}
