package net.neurallink.client.hud;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.neurallink.client.NeuralLinkClient;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.control.ControlState;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.VisorEventPayload;
import net.neurallink.registry.ModSounds;
import org.lwjgl.glfw.GLFW;

public final class AbilityPanel {
	private static final float ICON_SCALE = 1.5F;
	private static final float ENTITY_ICON_SCALE = 40.0F;
	private static final int ENTITY_ICON_AGE = 6;
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final Map<Identifier, Entity> ICON_ENTITIES = new HashMap<>();
	private static final Map<Identifier, ItemStack> ICON_STACKS = new HashMap<>();
	private static final Map<String, Text> KEY_LABELS = new HashMap<>();
	private static final int ICON_SIZE = 24;
	private static final int ICON_GAP = 7;
	private static final long FLASH_MS = 280;
	private static final long SHAKE_MS = 320;
	private static final long PANEL_MS = 320;

	private static final long[] USED_AT = new long[ControlState.SLOTS];
	private static final long[] REJECTED_AT = new long[ControlState.SLOTS];
	private static final long[] READY_AT = new long[ControlState.SLOTS];
	private static final int[] LAST_LEFT = new int[ControlState.SLOTS];

	private static long panelAt;

	private AbilityPanel() {
	}

	static void reset() {
		panelAt = Util.getMeasuringTimeMs();
		for (int slot = 0; slot < ControlState.SLOTS; slot++) {
			LAST_LEFT[slot] = 0;
			USED_AT[slot] = 0;
			REJECTED_AT[slot] = 0;
			READY_AT[slot] = 0;
		}
	}

	public static boolean[][] iconMask(Identifier icon) {
		return IconMask.of(icon).pixels;
	}

	public static int[][] iconRows(Identifier icon) {
		return IconMask.of(icon).rows;
	}

	public static int[] iconOutline(Identifier icon) {
		return IconMask.of(icon).outline;
	}

	public static void clearIcons() {
		ICON_ENTITIES.clear();
	}

	public static int iconEntityCount() {
		return ICON_ENTITIES.size();
	}

	public static long readyAt(int slot) {
		return READY_AT[slot];
	}

	static void onState(HudStatePayload payload) {
		for (HudStatePayload.Slot slot : payload.slots()) {
			if (LAST_LEFT[slot.index()] > 0 && slot.cooldownLeft() == 0) {
				READY_AT[slot.index()] = Util.getMeasuringTimeMs();
				HudSounds.play(ModSounds.ABILITY_READY, 0.35F);
			}
			LAST_LEFT[slot.index()] = slot.cooldownLeft();
		}
	}

	static void onEvent(VisorEventPayload.Kind kind, int slot) {
		long now = Util.getMeasuringTimeMs();
		switch (kind) {
			case SLOT_USED -> USED_AT[slot] = now;
			case SLOT_REJECTED -> {
				REJECTED_AT[slot] = now;
				HudSounds.play(ModSounds.REFUSE, 0.5F);
			}
			case HOST_SWITCH -> {
				panelAt = now;
				Arrays.fill(LAST_LEFT, 0);
			}
			default -> {
			}
		}
	}

	/** Panel at rest: x0, y0, x1, y1. Edge arrows avoid it. */
	static int[] bounds(int count, int width, int height) {
		int total = count * ICON_SIZE + (count - 1) * ICON_GAP;
		int y = (height - total) / 2;
		return new int[] {width - ICON_SIZE - SessionHud.MARGIN - 8, y, width, y + total};
	}

	static void render(DrawContext context, MinecraftClient client, RenderTickCounter counter, HudStatePayload state, int width, int height, long now) {
		int[] rest = bounds(state.slots().size(), width, height);
		float slide = (1.0F - VisorTheme.easeOut((float) (now - panelAt) / PANEL_MS)) * (ICON_SIZE + SessionHud.MARGIN);
		int x = rest[0] + (int) slide;
		int y = rest[1];
		for (HudStatePayload.Slot slot : state.slots()) {
			renderCell(context, client, slot, x, y, now, counter.getTickDelta(false));
			y += ICON_SIZE + ICON_GAP;
		}
	}

	private static void renderCell(DrawContext context, MinecraftClient client, HudStatePayload.Slot slot, int baseX, int y, long now, float tickDelta) {
		long sinceReject = now - REJECTED_AT[slot.index()];
		int x = baseX + (sinceReject < SHAKE_MS ? (int) (MathHelper.sin(sinceReject * 0.07F) * 2.5F * (1.0F - (float) sinceReject / SHAKE_MS)) : 0);
		context.getMatrices().push();
		context.getMatrices().translate((float) x, (float) y, 0.0F);
		context.getMatrices().scale(ICON_SCALE, ICON_SCALE, 1.0F);
		renderIcon(context, slot.icon());
		context.getMatrices().translate(0.0F, 0.0F, 200.0F); // above the item icon
		IconMask mask = IconMask.of(slot.icon());
		if (slot.cooldownLeft() > 0 && slot.cooldownTotal() > 0) {
			float left = Math.max(0.0F, slot.cooldownLeft() - tickDelta) / slot.cooldownTotal();
			fillCooldown(context, mask, MathHelper.ceil(IconMask.SIZE * left));
		}
		int outline = outlineColor(slot, now);
		if ((outline >>> 24) != 0) {
			drawOutline(context, mask, outline);
		}
		context.getMatrices().pop();

		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 200.0F);
		if (slot.cooldownLeft() > 20) {
			Text seconds = Text.literal(Integer.toString(MathHelper.ceil(slot.cooldownLeft() / 20.0F)));
			context.drawCenteredTextWithShadow(client.textRenderer, seconds, x + ICON_SIZE / 2, y + 8, VisorTheme.TEXT);
		}
		Text key = keyLabel(slot.index());
		context.getMatrices().translate(x + ICON_SIZE + 1.0F, y + ICON_SIZE - 5.0F, 0.0F);
		context.getMatrices().scale(0.6F, 0.6F, 1.0F);
		context.drawTextWithShadow(client.textRenderer, key, -client.textRenderer.getWidth(key), 0, VisorTheme.TEXT);
		context.getMatrices().pop();
	}

	private static void fillCooldown(DrawContext context, IconMask mask, int rows) {
		int dark = VisorTheme.withAlpha(VisorTheme.BG, 0.8F);
		for (int y = 0; y < rows; y++) {
			int[] runs = mask.rows[y];
			for (int i = 0; i < runs.length; i += 2) {
				context.fill(runs[i], y, runs[i + 1], y + 1, dark);
			}
		}
	}

	private static void drawOutline(DrawContext context, IconMask mask, int color) {
		int[] pixels = mask.outline;
		for (int i = 0; i < pixels.length; i += 2) {
			context.fill(pixels[i], pixels[i + 1], pixels[i] + 1, pixels[i + 1] + 1, color);
		}
	}

	/** transparent means no outline for an idle icon. */
	private static int outlineColor(HudStatePayload.Slot slot, long now) {
		if (slot.active()) {
			return VisorTheme.WHITE;
		}
		float used = 1.0F - Math.min(1.0F, (float) (now - USED_AT[slot.index()]) / FLASH_MS);
		if (used > 0.0F) {
			return VisorTheme.withAlpha(VisorTheme.WHITE, used);
		}
		float ready = 1.0F - Math.min(1.0F, (float) (now - READY_AT[slot.index()]) / (FLASH_MS * 2));
		return VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, ready);
	}

	private static void renderIcon(DrawContext context, Identifier icon) {
		if (Registries.ITEM.containsId(icon)) {
			context.drawItem(ICON_STACKS.computeIfAbsent(icon, id -> new ItemStack(Registries.ITEM.get(id))), 0, 0);
		} else {
			renderEntityIcon(context, icon);
		}
	}

	/** An entity icon is its 3D model, still since its age never advances. */
	private static void renderEntityIcon(DrawContext context, Identifier icon) {
		MinecraftClient client = MinecraftClient.getInstance();
		Entity entity = ICON_ENTITIES.get(icon);
		if (entity == null || entity.getWorld() != client.world) {
			entity = Registries.ENTITY_TYPE.get(icon).create(client.world, SpawnReason.LOAD);
			if (entity != null) {
				entity.age = ENTITY_ICON_AGE; // projectile spins with age, this one shows it angled
			}
			ICON_ENTITIES.put(icon, entity);
		}
		if (entity == null) {
			return;
		}
		Entity shown = entity;
		EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
		context.getMatrices().push();
		context.getMatrices().translate(IconMask.SIZE / 2.0F, IconMask.SIZE / 2.0F, 100.0F);
		context.getMatrices().scale(ENTITY_ICON_SCALE, ENTITY_ICON_SCALE, -ENTITY_ICON_SCALE);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
		context.draw();
		DiffuseLighting.method_34742();
		dispatcher.setRenderShadows(false);
		context.draw(vertices -> dispatcher.render(shown, 0.0, -0.15, 0.0, 0.0F, context.getMatrices(), vertices, FULL_BRIGHT));
		dispatcher.setRenderShadows(true);
		context.draw();
		DiffuseLighting.enableGuiDepthLighting();
		context.getMatrices().pop();
	}

	private static Text keyLabel(int slot) {
		MinecraftClient client = MinecraftClient.getInstance();
		KeyBinding binding = switch (slot) {
			case 0 -> client.options.attackKey;
			case 1 -> NeuralLinkClient.SLOT_2_KEY;
			default -> NeuralLinkClient.SLOT_3_KEY;
		};
		return KEY_LABELS.computeIfAbsent(binding.getBoundKeyTranslationKey(), id -> buildKeyLabel(binding));
	}

	private static Text buildKeyLabel(KeyBinding binding) {
		InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
		if (key.getCategory() == InputUtil.Type.MOUSE && key.getCode() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return Text.translatable("hud.neurallink.key_lmb");
		}
		if (key.getCategory() == InputUtil.Type.MOUSE && key.getCode() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			return Text.translatable("hud.neurallink.key_rmb");
		}
		return shortKeyName(binding);
	}

	/** key letter ignores layout so R doesn't show as Cyrillic. Modifiers drop their side to fit the badge. */
	public static Text shortKeyName(KeyBinding binding) {
		InputUtil.Key bound = KeyBindingHelper.getBoundKeyOf(binding);
		if (bound.getCategory() == InputUtil.Type.KEYSYM) {
			switch (bound.getCode()) {
				case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> {
					return Text.literal("Shift");
				}
				case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> {
					return Text.literal("Ctrl");
				}
				case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> {
					return Text.literal("Alt");
				}
				case GLFW.GLFW_KEY_SPACE -> {
					return Text.translatable("hud.neurallink.key_space");
				}
				default -> {
				}
			}
		}
		String id = bound.getTranslationKey();
		String last = id.substring(id.lastIndexOf('.') + 1);
		return last.length() == 1 ? Text.literal(last.toUpperCase(Locale.ROOT)) : binding.getBoundKeyLocalizedText();
	}
}
