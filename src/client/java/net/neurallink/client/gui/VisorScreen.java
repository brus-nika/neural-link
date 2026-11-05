package net.neurallink.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipCard;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.NeuralLinkClient;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.client.hud.VisorTransitions;
import net.neurallink.network.ChipListPayload;
import net.neurallink.network.ConnectPayload;
import net.neurallink.network.FavoriteChipPayload;
import net.neurallink.network.ForgetChipPayload;
import net.neurallink.registry.ModSounds;

/** visor menu, the host list. */
public class VisorScreen extends VisorPanelScreen {
	private static final int PANEL_WIDTH = 320;
	private static final int HEADER_HEIGHT = 26;
	private static final int CARD_HEIGHT = CardRenderer.CARD_HEIGHT;
	private static final int CARD_GAP = 4;
	private static final int CARD_INSET = 6;
	private static final int BOTTOM_ROW = CardRenderer.BOTTOM_ROW;
	private static final int TRASH_WIDTH = CardRenderer.TRASH_WIDTH;
	private static final int TRASH_HEIGHT = CardRenderer.TRASH_HEIGHT;
	private static final int TRASH_MARGIN = 2;
	private static final long CARD_STAGGER_MS = 45;
	private static final float CARD_APPEAR_MS = 160.0F;
	private static final int CARD_SLIDE = 10;
	private static final int CARD_HOVER_SHIFT = 2;
	private static final float CARD_MOVE_SPEED = 14.0F;
	private static final float CARD_HOVER_SPEED = 16.0F;
	private static final float GHOST_MS = 320.0F;
	private static final Identifier NOISE = NeuralLink.id("textures/gui/visor/noise.png");
	private static final int DISABLE_SIZE = CardRenderer.DISABLE_SIZE;
	private static final int STAR_SIZE = CardRenderer.STAR_SIZE;
	private static final int SORT_TOP = 5;
	private static final int SORT_HEIGHT = 13;
	public static final int DIALOG_WIDTH = DisableDialog.WIDTH;
	public static final int DIALOG_TITLE_WIDTH = DisableDialog.TITLE_WIDTH;
	public static final int DIALOG_TITLE_X = DisableDialog.TITLE_X;
	public static final int CARD_TEXT_X = CardRenderer.TEXT_X;

	private List<ChipCard> cards;
	private MenuSort sort = MenuSort.current();
	private int active;
	private int limit;
	private double scroll;
	private final Random random = Random.create();
	private final CardRenderer cardRenderer = new CardRenderer(this, random);
	private final DisableDialog dialog = new DisableDialog(this, cardRenderer);
	private final Map<UUID, CardAnimation> animations = new HashMap<>();
	private final List<Ghost> ghosts = new ArrayList<>();

	/** Keyed by chip id so list updates don't reset it. */
	private static final class CardAnimation {
		final long appearAt;
		float y = Float.NaN;
		float hover;

		CardAnimation(long appearAt) {
			this.appearAt = appearAt;
		}
	}

	private record Ghost(ChipCard card, float y, long removedAt) {
	}

	public VisorScreen(ChipListPayload list) {
		super(Text.translatable("menu.neurallink.title"));
		cards = List.of();
		update(list);
		HudSounds.play(ModSounds.MENU_OPEN, 0.6F);
	}

	public void update(ChipListPayload list) {
		long now = Util.getMeasuringTimeMs();
		for (ChipCard old : cards) {
			if (list.cards().stream().noneMatch(card -> card.chipId().equals(old.chipId()))) {
				CardAnimation animation = animations.remove(old.chipId());
				ghosts.add(new Ghost(old, animation != null && !Float.isNaN(animation.y) ? animation.y : 0.0F, now));
			}
		}
		cards = sort.sort(list.cards());
		active = list.active();
		limit = list.limit();
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
		dialog.update(cards);
	}

	@Override
	public void tick() {
		cardRenderer.tick();
	}

	public boolean isConfirming() {
		return dialog.isOpen();
	}

	public UUID confirmingChip() {
		return dialog.chip();
	}

	public int cardCount() {
		return cards.size();
	}

	public double scrollOffset() {
		return scroll;
	}

	public EntityType<?> previewType(int index) {
		return cardRenderer.previewType(cards.get(index).chipId());
	}

	public int previewCount() {
		return cardRenderer.previewCount();
	}

	public String cardTitle(int index) {
		return cardRenderer.cardName(cards.get(index), panelWidth() - 2 * CARD_INSET);
	}

	public int cardStatusLeft(int index) {
		return cardRenderer.statusLeft(cards.get(index), panelWidth() - 2 * CARD_INSET);
	}

	public String dialogTitle() {
		return dialog.title();
	}

	public ChipCard card(int index) {
		return cards.get(index);
	}

	/** card center in GUI coordinates for tests. */
	public int[] cardCenter(int index) {
		return new int[] {panelX() + PANEL_WIDTH / 2, cardY(index) + CARD_HEIGHT / 2};
	}

	public int[] trashCenter(int index) {
		return new int[] {trashX() + TRASH_WIDTH / 2, cardY(index) + BOTTOM_ROW - 1 + TRASH_HEIGHT / 2};
	}

	public int[] disableCenter(int index) {
		return new int[] {disableX() + DISABLE_SIZE / 2, disableY(index) + DISABLE_SIZE / 2};
	}

	public int[] starCenter(int index) {
		return new int[] {starX() + STAR_SIZE / 2, starY(index) + STAR_SIZE / 2};
	}

	public int[] sortCenter() {
		return new int[] {sortX() + sortWidth() / 2, panelY() + SORT_TOP + SORT_HEIGHT / 2};
	}

	public MenuSort sort() {
		return sort;
	}

	/** Under the host icon, far from the disable cross. */
	private int starX() {
		return panelX() + CARD_INSET + 10;
	}

	private int starY(int index) {
		return visualY(index) + 33;
	}

	private boolean overStar(int index, double mouseX, double mouseY) {
		return cards.get(index).status() != ChipCard.Status.LOST
				&& VisorTheme.inside(mouseX, mouseY, starX() - 1, starY(index) - 1, STAR_SIZE + 2, STAR_SIZE + 2);
	}

	private Text counterText() {
		return Text.literal(active + " / " + limit);
	}

	private int sortWidth() {
		return textRenderer.getWidth(sort.label()) + 8;
	}

	/** left of the header counter. */
	private int sortX() {
		return panelX() + panelWidth() - CARD_INSET - 2 - textRenderer.getWidth(counterText()) - 8 - sortWidth();
	}

	private boolean overSort(double mouseX, double mouseY) {
		return VisorTheme.inside(mouseX, mouseY, sortX(), panelY() + SORT_TOP, sortWidth(), SORT_HEIGHT);
	}

	private void cycleSort() {
		sort = sort.next();
		cards = sort.sort(cards);
		ClientConfig.get().menuSort = sort.name();
		ClientConfig.save();
	}

	/** Button 0 is cancel, 1 is disable. */
	public int[] dialogButtonCenter(int button) {
		return dialog.buttonCenter(button);
	}

	private int disableX() {
		return panelX() + panelWidth() - CARD_INSET - 6 - DISABLE_SIZE;
	}

	private int disableY(int index) {
		return visualY(index) + 16;
	}

	private boolean overDisable(int index, double mouseX, double mouseY) {
		return cards.get(index).status() != ChipCard.Status.LOST
				&& VisorTheme.inside(mouseX, mouseY, disableX(), disableY(index), DISABLE_SIZE, DISABLE_SIZE);
	}

	private int trashX() {
		return panelX() + panelWidth() - CARD_INSET - 6 - TRASH_WIDTH;
	}

	private boolean overTrash(int index, double mouseX, double mouseY) {
		int y = visualY(index) + BOTTOM_ROW - 1;
		return cards.get(index).status() == ChipCard.Status.LOST
				&& VisorTheme.inside(mouseX, mouseY, trashX() - TRASH_MARGIN, y - TRASH_MARGIN, TRASH_WIDTH + 2 * TRASH_MARGIN, TRASH_HEIGHT + 2 * TRASH_MARGIN);
	}

	private int panelWidth() {
		return Math.min(PANEL_WIDTH, width - 16);
	}

	private int panelHeight() {
		return Math.min(height - 16, HEADER_HEIGHT + 4 * (CARD_HEIGHT + CARD_GAP) + CARD_INSET);
	}

	private int panelX() {
		return (width - panelWidth()) / 2;
	}

	private int panelY() {
		return (height - panelHeight()) / 2;
	}

	private int listTop() {
		return panelY() + HEADER_HEIGHT;
	}

	private int listBottom() {
		return panelY() + panelHeight() - CARD_INSET;
	}

	private int cardY(int index) {
		return listTop() + index * (CARD_HEIGHT + CARD_GAP) - (int) scroll;
	}

	private int maxScroll() {
		return Math.max(0, cards.size() * (CARD_HEIGHT + CARD_GAP) - CARD_GAP - (listBottom() - listTop()));
	}

	/** where the card is drawn now; clicks follow the slide animation. */
	private int visualY(int index) {
		CardAnimation animation = animations.get(cards.get(index).chipId());
		return animation == null || Float.isNaN(animation.y) ? cardY(index) : Math.round(animation.y);
	}

	public int cardAt(double mouseX, double mouseY) {
		if (!VisorTheme.inside(mouseX, mouseY, panelX() + CARD_INSET, listTop(), panelWidth() - 2 * CARD_INSET, listBottom() - listTop())) {
			return -1;
		}
		for (int i = cards.size() - 1; i >= 0; i--) { // later cards drawn on top
			int y = visualY(i);
			if (mouseY >= y && mouseY < y + CARD_HEIGHT) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (dialog.isOpen()) {
			dialog.keyPressed(keyCode);
			return true; // Esc closes only the dialog
		}
		if (NeuralLinkClient.MENU_KEY.matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (dialog.isOpen()) {
			return true;
		}
		scroll = MathHelper.clamp(scroll - vertical * (CARD_HEIGHT + CARD_GAP) / 2.0, 0, maxScroll());
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (dialog.isOpen()) {
			dialog.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (button == 0 && overSort(mouseX, mouseY)) {
			HudSounds.play(ModSounds.CLICK, 0.6F);
			cycleSort();
			return true;
		}
		int index = cardAt(mouseX, mouseY);
		if (button != 0 || index < 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		ChipCard card = cards.get(index);
		if (overStar(index, mouseX, mouseY)) {
			HudSounds.play(ModSounds.CLICK, 0.6F);
			ClientPlayNetworking.send(new FavoriteChipPayload(card.chipId(), !card.favorite()));
			return true;
		}
		if (overDisable(index, mouseX, mouseY)) {
			HudSounds.play(ModSounds.CLICK, 0.6F);
			dialog.open(card);
			return true;
		}
		if (overTrash(index, mouseX, mouseY)) {
			HudSounds.play(ModSounds.CLICK, 0.6F);
			ClientPlayNetworking.send(new ForgetChipPayload(card.chipId()));
			return true;
		}
		if (!card.status().connectable()) {
			HudSounds.play(ModSounds.REFUSE, 0.6F);
			return true;
		}
		HudSounds.play(ModSounds.CLICK, 0.6F);
		VisorTransitions.beginLoading();
		ClientPlayNetworking.send(new ConnectPayload(card.chipId()));
		close();
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		long now = Util.getMeasuringTimeMs();
		float dt = frameDt(now);
		float open = openProgress();
		pushOpenTransform(context, open);
		int x = panelX();
		int y = panelY();
		int w = panelWidth();
		context.fill(x, y, x + w, y + panelHeight(), VisorTheme.withAlpha(VisorTheme.BG_SOLID, open));
		context.drawBorder(x, y, w, panelHeight(), VisorTheme.withAlpha(VisorTheme.ACCENT, open));
		context.drawText(textRenderer, title, x + CARD_INSET + 2, y + 9, VisorTheme.TEXT, false);
		Text counter = counterText();
		context.drawText(textRenderer, counter, x + w - CARD_INSET - 2 - textRenderer.getWidth(counter), y + 9, VisorTheme.TEXT_DIM, false);
		boolean sortHovered = !dialog.isOpen() && overSort(mouseX, mouseY);
		int sortX = sortX();
		context.fill(sortX, y + SORT_TOP, sortX + sortWidth(), y + SORT_TOP + SORT_HEIGHT, sortHovered ? VisorTheme.PANEL_HOVER : VisorTheme.PANEL);
		context.drawBorder(sortX, y + SORT_TOP, sortWidth(), SORT_HEIGHT, sortHovered ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT);
		context.drawText(textRenderer, sort.label(), sortX + 4, y + SORT_TOP + 3, sortHovered ? VisorTheme.TEXT : VisorTheme.TEXT_DIM, false);

		if (cards.isEmpty() && ghosts.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.neurallink.empty.title"), width / 2, height / 2 - 12, VisorTheme.TEXT);
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("menu.neurallink.empty.hint"), width / 2, height / 2 + 2, VisorTheme.TEXT_DIM);
		}
		int hovered = dialog.isOpen() ? -1 : cardAt(mouseX, mouseY);
		context.enableScissor(x + 1, listTop(), x + w - 1, listBottom());
		for (int i = 0; i < cards.size(); i++) {
			ChipCard card = cards.get(i);
			int index = i;
			CardAnimation animation = animations.computeIfAbsent(card.chipId(), id -> new CardAnimation(opening(now) ? openedAt + index * CARD_STAGGER_MS : now));
			float target = cardY(i);
			animation.y = Float.isNaN(animation.y) ? target : VisorTheme.approach(animation.y, target, dt, CARD_MOVE_SPEED);
			animation.hover = VisorTheme.approach(animation.hover, i == hovered ? 1.0F : 0.0F, dt, CARD_HOVER_SPEED);
			if (offList(Math.round(animation.y))) {
				continue; // scissor only clips pixels, an off-list model still costs a full draw
			}
			float appear = VisorTheme.easeOut((float) (now - animation.appearAt) / CARD_APPEAR_MS);
			int cardX = x + CARD_INSET + (int) ((appear - 1.0F) * CARD_SLIDE) + (int) (animation.hover * CARD_HOVER_SHIFT);
			cardRenderer.renderCard(context, card, cardX, Math.round(animation.y), w - 2 * CARD_INSET, animation.hover, appear);
			if (appear > 0.5F) {
				if (card.status() == ChipCard.Status.LOST) {
					cardRenderer.renderTrash(context, trashX(), Math.round(animation.y) + BOTTOM_ROW - 1, i == hovered && overTrash(i, mouseX, mouseY));
				} else {
					cardRenderer.renderDisableButton(context, disableX(), Math.round(animation.y) + 16, i == hovered && overDisable(i, mouseX, mouseY));
					cardRenderer.renderStar(context, starX(), Math.round(animation.y) + 33, card.favorite(), i == hovered && overStar(i, mouseX, mouseY));
				}
			}
		}
		renderGhosts(context, x + CARD_INSET, w - 2 * CARD_INSET, now);
		context.disableScissor();
		if (hovered >= 0 && overTrash(hovered, mouseX, mouseY)) {
			renderPlate(context, Text.translatable("menu.neurallink.forget"), mouseX, mouseY);
		} else if (hovered >= 0 && overDisable(hovered, mouseX, mouseY)) {
			renderPlate(context, Text.translatable("menu.neurallink.disable"), mouseX, mouseY);
		} else if (hovered >= 0 && overStar(hovered, mouseX, mouseY)) {
			renderPlate(context, Text.translatable(cards.get(hovered).favorite() ? "menu.neurallink.unstar" : "menu.neurallink.star"), mouseX, mouseY);
		}
		drawScrollbar(context, x + w - 4, listTop(), listBottom(), scroll, maxScroll(), 12);
		context.getMatrices().pop();
		if (dialog.isOpen()) {
			dialog.render(context, mouseX, mouseY);
		}
	}

	private boolean offList(int y) {
		return y + CARD_HEIGHT <= listTop() || y >= listBottom();
	}

	/** hint plate in our palette, vanilla tooltips are purple. */
	private void renderPlate(DrawContext context, Text text, int mouseX, int mouseY) {
		int w = textRenderer.getWidth(text) + 8;
		int h = textRenderer.fontHeight + 6;
		int x = MathHelper.clamp(mouseX + 8, 0, Math.max(0, width - w));
		int y = MathHelper.clamp(mouseY - h - 4, 0, Math.max(0, height - h));
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 400.0F); // above the cards' item icons
		context.fill(x, y, x + w, y + h, VisorTheme.PANEL);
		context.drawBorder(x, y, w, h, VisorTheme.ACCENT);
		context.drawText(textRenderer, text, x + 4, y + 4, VisorTheme.TEXT, false);
		context.getMatrices().pop();
	}

	/** A removed card fades out with noise as others slide in. */
	private void renderGhosts(DrawContext context, int x, int cardWidth, long now) {
		for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext();) {
			Ghost ghost = it.next();
			float t = (float) (now - ghost.removedAt()) / GHOST_MS;
			if (t >= 1.0F) {
				it.remove();
				continue;
			}
			int y = Math.round(ghost.y());
			if (offList(y)) {
				continue;
			}
			cardRenderer.renderCard(context, ghost.card(), x, y, cardWidth, 0.0F, 1.0F - t);
			context.drawTexture(RenderLayer::getGuiTextured, NOISE, x, y, random.nextInt(128), random.nextInt(128), cardWidth, CARD_HEIGHT, 128, 128, VisorTheme.withAlpha(VisorTheme.WHITE, 1.0F - t));
		}
	}
}
