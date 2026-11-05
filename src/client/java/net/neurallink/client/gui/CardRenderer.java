package net.neurallink.client.gui;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipCard;

/** draws host cards, holds the menu's mob models. */
final class CardRenderer {
	static final int CARD_HEIGHT = 44;
	static final int TEXT_X = 28;
	static final int BOTTOM_ROW = 31;
	static final int TRASH_WIDTH = 9;
	static final int TRASH_HEIGHT = 10;
	static final int DISABLE_SIZE = 11;
	private static final int HP_BAR_WIDTH = 72;
	private static final int SIGNAL_BARS = 4;
	private static final int SIGNAL_WIDTH = SIGNAL_BARS * 3 - 1;
	private static final int PREVIEW_SIZE = 18;
	private static final int DISABLE_ICON = 5;
	private static final int CARD_RIGHT = 6;
	static final int STAR_SIZE = 7;
	/** Filled runs per row, [from, to) or two of them. */
	private static final int[][] STAR = {{3, 4}, {3, 4}, {0, 7}, {1, 6}, {2, 5}, {1, 3, 4, 6}, {0, 2, 5, 7}};
	private static final Map<ChipCard.Status, Text> STATUS_TEXTS = new EnumMap<>(ChipCard.Status.class);
	private static final Map<ChipCard.Status, Identifier> STATUS_ICONS = new EnumMap<>(ChipCard.Status.class);

	static {
		for (ChipCard.Status status : ChipCard.Status.values()) {
			String name = status.name().toLowerCase(Locale.ROOT);
			STATUS_TEXTS.put(status, Text.translatable("menu.neurallink.status." + name));
			STATUS_ICONS.put(status, NeuralLink.id("textures/gui/menu/status_" + name + ".png"));
		}
	}

	private final Screen screen;
	private final Random random;
	private final Map<UUID, MobPreview> previews = new HashMap<>();
	private final Set<EntityType<?>> noPreview = new HashSet<>();

	CardRenderer(Screen screen, Random random) {
		this.screen = screen;
		this.random = random;
	}

	void tick() {
		previews.values().forEach(preview -> preview.tick(random));
	}

	/** host type in the card preview, null if no model. */
	EntityType<?> previewType(UUID chipId) {
		MobPreview preview = previews.get(chipId);
		return preview == null ? null : preview.type();
	}

	int previewCount() {
		return previews.size();
	}

	/** Left edge of the status mark relative to the card. */
	int statusLeft(ChipCard card, int cardWidth) {
		return cardWidth - CARD_RIGHT - screen.getTextRenderer().getWidth(statusText(card)) - 7;
	}

	void renderIcon(DrawContext context, ChipCard card, EntityType<?> type, int centerX, int centerY) {
		MobPreview preview = previews.compute(card.chipId(), (id, old) -> old != null && old.type() == type ? old : createPreview(type));
		if (preview != null) {
			preview.hold(card.held());
			if (preview.render(context, centerX, centerY, PREVIEW_SIZE)) {
				return;
			}
			previews.remove(card.chipId());
		}
		context.drawItem(new ItemStack(Items.NAME_TAG), centerX - 8, centerY - 8);
	}

	/** a type without a model stays one: the map can't store misses and this runs every frame. */
	private MobPreview createPreview(EntityType<?> type) {
		if (noPreview.contains(type)) {
			return null;
		}
		MobPreview preview = MobPreview.create(type, random);
		if (preview == null) {
			noPreview.add(type);
		}
		return preview;
	}

	private static Text displayName(ChipCard card, EntityType<?> type) {
		return card.name().isEmpty() ? type.getName() : Text.literal(card.name());
	}

	static Text statusText(ChipCard card) {
		return STATUS_TEXTS.get(card.status());
	}

	/** Room between icon and status mark; anvil names reach 50 chars. */
	String cardName(ChipCard card, int cardWidth) {
		TextRenderer textRenderer = screen.getTextRenderer();
		int width = cardWidth - TEXT_X - CARD_RIGHT - textRenderer.getWidth(statusText(card)) - 7 - 4;
		return textRenderer.trimToWidth(displayName(card, Registries.ENTITY_TYPE.get(card.entityType())).getString(), width);
	}

	void renderCard(DrawContext context, ChipCard card, int x, int y, int w, float hover, float alpha) {
		TextRenderer textRenderer = screen.getTextRenderer();
		boolean enabled = card.status().connectable();
		float lit = enabled ? hover : 0.0F;
		context.fill(x, y, x + w, y + CARD_HEIGHT, VisorTheme.withAlpha(VisorTheme.mix(VisorTheme.PANEL, VisorTheme.PANEL_HOVER, lit), alpha));
		context.drawBorder(x, y, w, CARD_HEIGHT, VisorTheme.withAlpha(VisorTheme.mix(VisorTheme.ACCENT, VisorTheme.HIGHLIGHT, lit), alpha));

		EntityType<?> type = Registries.ENTITY_TYPE.get(card.entityType());
		if (alpha > 0.5F) {
			renderIcon(context, card, type, x + 13, y + CARD_HEIGHT / 2);
		}
		int text = x + TEXT_X;
		int right = x + w - CARD_RIGHT;
		context.drawText(textRenderer, cardName(card, w), text, y + 5, VisorTheme.textColor(enabled ? VisorTheme.TEXT : VisorTheme.TEXT_DIM, alpha), false);
		Text status = statusText(card);
		int statusColor = card.status() == ChipCard.Status.ONLINE ? VisorTheme.TEXT : card.status() == ChipCard.Status.OUT_OF_RANGE ? VisorTheme.TEXT_DIM : VisorTheme.HIGHLIGHT;
		int statusX = right - textRenderer.getWidth(status);
		context.drawText(textRenderer, status, statusX, y + 5, VisorTheme.textColor(statusColor, alpha), false);
		context.drawTexture(RenderLayer::getGuiTextured, STATUS_ICONS.get(card.status()), statusX - 7, y + 6, 0.0F, 0.0F, 4, 4, 4, 4, VisorTheme.withAlpha(VisorTheme.WHITE, alpha));

		int barY = y + 17;
		context.fill(text, barY, text + HP_BAR_WIDTH, barY + 4, VisorTheme.withAlpha(VisorTheme.BG_SOLID, alpha));
		float health = card.maxHealth() > 0 ? MathHelper.clamp(card.health() / card.maxHealth(), 0.0F, 1.0F) : 0.0F;
		context.fill(text, barY, text + (int) (HP_BAR_WIDTH * health), barY + 4, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, alpha));
		Text hp = Text.literal(MathHelper.ceil(card.health()) + " / " + MathHelper.ceil(card.maxHealth()));
		context.drawText(textRenderer, hp, text + HP_BAR_WIDTH + 5, barY - 2, VisorTheme.textColor(VisorTheme.TEXT_DIM, alpha), false);

		Text coords = Text.literal(card.pos().getX() + "  " + card.pos().getY() + "  " + card.pos().getZ());
		context.drawText(textRenderer, coords, text, y + BOTTOM_ROW, VisorTheme.textColor(VisorTheme.TEXT_DIM, alpha), false);

		// bottom row left of distance so the signal crowds nothing.
		int signalRight = right;
		if (enabled) {
			Text distance = Text.translatable("menu.neurallink.distance", card.distance());
			context.drawText(textRenderer, distance, right - textRenderer.getWidth(distance), y + BOTTOM_ROW, VisorTheme.textColor(VisorTheme.TEXT_DIM, alpha), false);
			signalRight -= textRenderer.getWidth(distance) + 6;
		} else if (card.status() == ChipCard.Status.LOST) {
			signalRight -= TRASH_WIDTH + 6;
		}
		renderSignal(context, card.signal(), signalRight, y + BOTTOM_ROW + 8, alpha);
	}

	void renderDisableButton(DrawContext context, int left, int y, boolean hovered) {
		int x = left + (DISABLE_SIZE - DISABLE_ICON) / 2;
		int top = y + (DISABLE_SIZE - DISABLE_ICON) / 2;
		int color = hovered ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT_DIM;
		for (int i = 0; i < DISABLE_ICON; i++) {
			context.fill(x + i, top + i, x + i + 1, top + i + 1, color);
			context.fill(x + DISABLE_ICON - 1 - i, top + i, x + DISABLE_ICON - i, top + i + 1, color);
		}
	}

	/** seven pixels wide: filled when starred, faint when not. */
	void renderStar(DrawContext context, int x, int y, boolean starred, boolean hovered) {
		int color = hovered ? VisorTheme.TEXT : starred ? VisorTheme.HIGHLIGHT : VisorTheme.withAlpha(VisorTheme.TEXT_DIM, 0.6F);
		for (int row = 0; row < STAR.length; row++) {
			context.fill(x + STAR[row][0], y + row, x + STAR[row][1], y + row + 1, color);
			if (STAR[row].length > 2) {
				context.fill(x + STAR[row][2], y + row, x + STAR[row][3], y + row + 1, color);
			}
		}
	}

	void renderTrash(DrawContext context, int x, int y, boolean hovered) {
		int color = hovered ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT_DIM;
		context.fill(x + 3, y, x + 6, y + 1, color);
		context.fill(x, y + 1, x + TRASH_WIDTH, y + 2, color);
		context.fill(x + 1, y + 3, x + 8, y + TRASH_HEIGHT, color);
		context.fill(x + 3, y + 4, x + 4, y + TRASH_HEIGHT - 1, VisorTheme.PANEL);
		context.fill(x + 5, y + 4, x + 6, y + TRASH_HEIGHT - 1, VisorTheme.PANEL);
	}

	private void renderSignal(DrawContext context, int signal, int right, int bottom, float alpha) {
		int lit = MathHelper.ceil(signal * SIGNAL_BARS / 100.0F);
		for (int i = 0; i < SIGNAL_BARS; i++) {
			int x = right - SIGNAL_WIDTH + i * 3;
			context.fill(x, bottom - 2 - 2 * i, x + 2, bottom, VisorTheme.withAlpha(i < lit ? VisorTheme.HIGHLIGHT : VisorTheme.BG_SOLID, alpha));
		}
	}
}
