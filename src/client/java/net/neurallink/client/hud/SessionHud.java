package net.neurallink.client.hud;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.neurallink.NeuralLink;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.ClientSession;
import net.neurallink.client.NeuralLinkClient;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.VisorEventPayload;

/** session HUD: visor frame, name, HP, signal, key hints. Values ease in. */
public final class SessionHud {
	private static final Identifier CORNER = NeuralLink.id("textures/gui/hud/corner.png");
	private static final Identifier CORNER_WHITE = NeuralLink.id("textures/gui/hud/corner_white.png");
	private static final Identifier BAR = NeuralLink.id("textures/gui/hud/bar.png");
	static final Identifier BODY =NeuralLink.id("textures/gui/hud/body.png");
	static final int MARGIN = 6;
	private static final int BAR_WIDTH = 64;
	private static final int SIGNAL_BARS = 4;
	private static final float VALUE_SPEED = 9.0F;
	private static final long BODY_ICON_MS = 1600;
	private static final int HINT_SESSIONS = 3;
	private static final int NAME_MAX_WIDTH = 160;

	private static HudStatePayload state;
	private static boolean verticalHintShown;
	private static int nameWidth;
	private static int lastCornerTint = VisorTheme.WHITE;
	private static float shownHealth = -1.0F;
	private static float shownSignal = 100.0F;
	private static long bodyHitAt;
	private static long lastFrame;

	private SessionHud() {
	}

	public static void reset() {
		state = null;
		shownHealth = -1.0F;
		shownSignal = 100.0F;
		bodyHitAt = 0;
		AbilityPanel.reset();
	}

	/** icon silhouettes come from atlas sprites, replaced on resource reload. */
	public static void registerReloadListener() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return NeuralLink.id("icon_masks");
			}

			@Override
			public void reload(ResourceManager manager) {
				IconMask.clear();
			}
		});
	}

	public static int shownHp() {
		return MathHelper.ceil(shownHealth);
	}

	public static HudStatePayload state() {
		return state;
	}

	public static void onState(HudStatePayload payload) {
		AbilityPanel.onState(payload);
		state = payload;
	}

	public static void onEvent(VisorEventPayload.Kind kind, int slot) {
		if (kind == VisorEventPayload.Kind.BODY_HIT) {
			bodyHitAt = Util.getMeasuringTimeMs();
		} else {
			AbilityPanel.onEvent(kind, slot);
		}
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity mob = client.getCameraEntity();
		if (!ClientSession.isActive() || mob == client.player || mob == null) {
			verticalHintShown = false;
			return;
		}
		long now = Util.getMeasuringTimeMs();
		float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		renderFrame(context, width, height);
		renderStats(context, client.textRenderer, mob, width, dt, now);
		if (state != null) {
			AbilityPanel.render(context, client, counter, state, width, height, now);
		}
		int hintY = height - MARGIN - 21;
		if (ClientConfig.get().exitHintUses < HINT_SESSIONS) {
			renderExitHint(context, client.textRenderer, hintY);
			hintY -= KeyHints.HEIGHT + 4;
		}
		verticalHintShown = state != null && state.vertical() && ClientConfig.get().verticalHintUses < HINT_SESSIONS;
		if (verticalHintShown) {
			renderVerticalHint(context, client, hintY);
		}
	}

	/** For tests: what the last frame drew. */
	public static boolean verticalHintShown() {
		return verticalHintShown;
	}

	public static int nameWidth() {
		return nameWidth;
	}

	/** Info block and key hint areas (x0, y0, x1, y1) that edge arrows avoid. */
	static int[][] blockedAreas(int width, int height) {
		int statsRight = MARGIN + 8 + Math.max(nameWidth, BAR_WIDTH + 44);
		int hintsTop = height - MARGIN - 21 - KeyHints.HEIGHT - 4;
		return new int[][] {{0, 0, statsRight, MARGIN + 47}, {0, hintsTop, MARGIN + 100, height}};
	}

	private static void renderFrame(DrawContext context, int width, int height) {
		// jeb_ host: corners go grey and take the rainbow band colour under them.
		boolean rainbow = EasterEggs.rainbow(MinecraftClient.getInstance());
		long now = Util.getMeasuringTimeMs();
		corner(context, MARGIN, MARGIN, 0.0F, rainbow, height, now);
		corner(context, width - MARGIN, MARGIN, 90.0F, rainbow, height, now);
		corner(context, width - MARGIN, height - MARGIN, 180.0F, rainbow, height, now);
		corner(context, MARGIN, height - MARGIN, 270.0F, rainbow, height, now);
	}

	/** Rotated not mirrored: a mirrored quad faces away and isn't drawn. */
	private static void corner(DrawContext context, int x, int y, float degrees, boolean rainbow, int height, long now) {
		context.getMatrices().push();
		context.getMatrices().translate((float) x, (float) y, 0.0F);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
		if (rainbow) {
			lastCornerTint = VisorOverlay.rainbowColour(y, height, now);
			context.drawTexture(RenderLayer::getGuiTextured, CORNER_WHITE, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, lastCornerTint);
		} else {
			lastCornerTint = VisorTheme.WHITE;
			context.drawTexture(RenderLayer::getGuiTextured, CORNER, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
		}
		context.getMatrices().pop();
	}

	/** for tests: last corner tint, white unless jeb_ colours it. */
	public static int cornerTint() {
		return lastCornerTint;
	}

	private static void renderStats(DrawContext context, TextRenderer text, Entity mob, int width, float dt, long now) {
		int x = MARGIN + 8;
		int y = MARGIN + 7;
		// a long name tag is cut, the type always stays.
		Text type = mob.hasCustomName() ? Text.literal(" · ").append(mob.getType().getName()) : Text.empty();
		OrderedText name = KeyHints.fit(text, mob.getDisplayName(), Math.min(NAME_MAX_WIDTH, width / 2 - x) - text.getWidth(type));
		context.drawTextWithShadow(text, name, x, y, VisorTheme.TEXT);
		context.drawTextWithShadow(text, type, x + text.getWidth(name), y, VisorTheme.TEXT_DIM);
		nameWidth = text.getWidth(name) + text.getWidth(type);
		if (state == null) {
			return;
		}
		shownHealth = shownHealth < 0.0F ? state.health() : VisorTheme.approach(shownHealth, state.health(), dt, VALUE_SPEED);
		// The approach stalls just above target and ceil would show one point too many.
		if (Math.abs(shownHealth - state.health()) < 0.05F) {
			shownHealth = state.health();
		}
		shownSignal = VisorTheme.approach(shownSignal, state.signal(), dt, VALUE_SPEED);
		int barY = y + 11;
		context.drawTexture(RenderLayer::getGuiTextured, BAR, x, barY, 0.0F, 0.0F, BAR_WIDTH, 8, BAR_WIDTH, 8);
		float health = state.maxHealth() > 0.0F ? MathHelper.clamp(shownHealth / state.maxHealth(), 0.0F, 1.0F) : 0.0F;
		context.fill(x + 1, barY + 1, x + 1 + (int) ((BAR_WIDTH - 2) * health), barY + 7, VisorTheme.HIGHLIGHT);
		context.fill(x + 1, barY + 1, x + 1 + (int) ((BAR_WIDTH - 2) * health), barY + 2, VisorTheme.withAlpha(VisorTheme.WHITE, 0.35F));
		Text hp = Text.literal(shownHp() + " / " + MathHelper.ceil(state.maxHealth()));
		context.drawTextWithShadow(text, hp, x + BAR_WIDTH + 4, barY, VisorTheme.TEXT_DIM);

		int signalY = barY + 18;
		int lit = MathHelper.ceil(shownSignal * SIGNAL_BARS / 100.0F);
		for (int i = 0; i < SIGNAL_BARS; i++) {
			int barX = x + i * 3;
			context.fill(barX, signalY - 2 - 2 * i, barX + 2, signalY, i < lit ? VisorTheme.HIGHLIGHT : VisorTheme.withAlpha(VisorTheme.BG, 0.8F));
		}
		float bodyHit = 1.0F - Math.min(1.0F, (float) (now - bodyHitAt) / BODY_ICON_MS);
		if (bodyHit > 0.0F) {
			float blink = blink(now);
			context.drawTexture(RenderLayer::getGuiTextured, BODY, x + 18, signalY - 10, 0.0F, 0.0F, 10, 10, 10, 10, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, bodyHit * (0.5F + 0.5F * blink)));
		}
	}

	/** whole periods keep the sine table index from saturating after hours of uptime. */
	public static float blink(long now) {
		return 0.5F + 0.5F * MathHelper.sin((now % 314L) * 0.02F);
	}

	private static void renderExitHint(DrawContext context, TextRenderer text, int y) {
		int x = MARGIN + 8;
		int size = KeyHints.keyCap(context, text, AbilityPanel.shortKeyName(NeuralLinkClient.EXIT_KEY), x, y);
		KeyHints.arrowRight(context, x + size + 3, y); // the arrow alone says this key leaves the mob
	}

	/** Space and Shift arrows, only when the host really moves vertically. */
	private static void renderVerticalHint(DrawContext context, MinecraftClient client, int y) {
		int x = MARGIN + 8;
		x += KeyHints.keyCap(context, client.textRenderer, AbilityPanel.shortKeyName(client.options.jumpKey), x, y) + 2;
		x += KeyHints.arrowVertical(context, x, y, true) + 6;
		x += KeyHints.keyCap(context, client.textRenderer, AbilityPanel.shortKeyName(client.options.sneakKey), x, y) + 2;
		KeyHints.arrowVertical(context, x, y, false);
	}
}
