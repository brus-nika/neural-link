package net.neurallink.client.config;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.gui.VisorDialog;
import net.neurallink.client.gui.VisorPanelScreen;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.registry.ModSounds;
import org.lwjgl.glfw.GLFW;

/** Settings in visor style. Edits a {@link ConfigDraft}; Esc with changes asks to save. */
public class VisorConfigScreen extends VisorPanelScreen implements ConfigRow.RowHost {
	private static final int MARGIN = 12;
	private static final int PANEL_MAX_WIDTH = 380;
	private static final int HEADER_HEIGHT = 26;
	private static final int FOOTER_HEIGHT = 30;
	private static final int INSET = 6;
	private static final int ROW_HEIGHT = ConfigRow.ROW_HEIGHT;
	private static final int ROW_GAP = 3;
	private static final int CONTROL_WIDTH = ConfigRow.CONTROL_WIDTH;
	private static final int BUTTON_HEIGHT = ConfigRow.BUTTON_HEIGHT;
	private static final int DIALOG_WIDTH = 248;
	private static final int DIALOG_HEIGHT = 84;
	private static final float HOVER_SPEED = 16.0F;
	private static final float SOUND_VOLUME = ConfigRow.SOUND_VOLUME;

	private final Screen parent;
	private final ConfigDraft draft = new ConfigDraft();
	private final boolean serverEditable = ConfigDraft.serverEditable();
	private final List<ConfigRow> rows = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final VisorDialog dialog = new VisorDialog(this, DIALOG_WIDTH, DIALOG_HEIGHT, (DIALOG_WIDTH - 30) / 2, BUTTON_HEIGHT, 10);
	private final BlacklistInput blacklist = BlacklistInput.mobs(draft, serverEditable, this::idAdded);
	private final BlacklistInput dimensions = BlacklistInput.dimensions(draft, serverEditable, this::idAdded);
	private double scroll;
	private ConfigRow dragging;
	private boolean confirmingExit;
	private boolean saved;
	private boolean leavingToCloth;
	private boolean sliderHintCounted;

	public VisorConfigScreen(Screen parent) {
		super(Text.translatable("config.neurallink.title"));
		this.parent = parent;
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clear();
		rows.add(new ConfigRow.Section(this, "config.neurallink.section.client", Text.translatable("config.neurallink.section.client.note")));
		rows.add(new ConfigRow.Slider(this, "overlay", true, () -> draft.overlayPercent, draft::setOverlay, 0, ConfigDraft.OVERLAY_MAX, 1, value -> Text.literal(value + "%")));
		rows.add(new ConfigRow.Toggle(this, "arrows", true, () -> draft.offscreenArrows, value -> draft.offscreenArrows = value));
		rows.add(new ConfigRow.Toggle(this, "mob_vision", true, () -> draft.mobVision, value -> draft.mobVision = value));
		rows.add(new ConfigRow.Toggle(this, "easter_eggs", true, () -> draft.easterEggs, value -> draft.easterEggs = value));
		rows.add(new ConfigRow.Slider(this, "body_sounds", true, () -> draft.bodySounds, value -> draft.bodySounds = value, 0, 100, 5, value -> Text.literal(value + "%")));
		rows.add(new ConfigRow.HintsRow(this));
		rows.add(new ConfigRow.Section(this, "config.neurallink.section.server",
				Text.translatable(serverEditable ? "config.neurallink.section.server.note" : "config.neurallink.section.server.locked")));
		rows.add(new ConfigRow.Toggle(this, "bosses", serverEditable, () -> draft.allowBosses, value -> draft.allowBosses = value));
		rows.add(new ConfigRow.Slider(this, "chips", serverEditable, () -> draft.maxChips, value -> draft.maxChips = value, 1, ConfigDraft.CHIPS_MAX, 1, value -> Text.literal(String.valueOf(value))));
		rows.add(new ConfigRow.Slider(this, "noise_start", serverEditable, () -> draft.noiseStart, draft::setNoiseStart, 0, ConfigDraft.NOISE_MAX - ConfigDraft.DISTANCE_STEP, ConfigDraft.DISTANCE_STEP, VisorConfigScreen::blocks));
		rows.add(new ConfigRow.Slider(this, "noise_max", serverEditable, () -> draft.noiseMax, draft::setNoiseMax, ConfigDraft.DISTANCE_STEP, ConfigDraft.NOISE_MAX, ConfigDraft.DISTANCE_STEP, VisorConfigScreen::blocks));
		rows.add(new ConfigRow.Toggle(this, "depth_noise", serverEditable, () -> draft.depthNoise, value -> draft.depthNoise = value));
		rows.add(new ConfigRow.Slider(this, "cooldown", serverEditable, () -> draft.connectCooldown, value -> draft.connectCooldown = value, 0, NeuralLinkConfig.MAX_CONNECT_COOLDOWN, 1,
				value -> Text.translatable("config.neurallink.seconds", value)));
		rows.add(new ConfigRow.Slider(this, "player_chips", serverEditable, () -> draft.playerChips, value -> draft.playerChips = value, 0, ConfigDraft.PLAYER_CHIPS_MAX, 1,
				value -> value == 0 ? Text.translatable("config.neurallink.player_chips.none") : Text.literal(String.valueOf(value))));
		rows.add(new ConfigRow.Toggle(this, "spawn_protection", serverEditable, () -> draft.spawnProtection, value -> draft.spawnProtection = value));
		rows.add(new ConfigRow.Toggle(this, "body_trance", serverEditable, () -> draft.bodyTrance, value -> draft.bodyTrance = value));
		rows.add(new ConfigRow.AddId(this, blacklist));
		draft.blacklist.forEach(id -> rows.add(new ConfigRow.IdRow(this, blacklist, id)));
		rows.add(new ConfigRow.AddId(this, dimensions));
		draft.dimensions.forEach(id -> rows.add(new ConfigRow.IdRow(this, dimensions, id)));
	}

	private static Text blocks(int value) {
		return Text.translatable("config.neurallink.blocks", value);
	}

	@Override
	public void rowsChanged() {
		rebuildRows();
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
	}

	/** the new row may be below the view, scroll just enough to show it. */
	private void idAdded(String id) {
		rebuildRows();
		int offset = 0;
		for (ConfigRow row : rows) {
			if (id.equals(row.key)) {
				int visible = listBottom() - listTop();
				scroll = MathHelper.clamp(Math.max(scroll, offset + row.height() - visible), 0, maxScroll());
				return;
			}
			offset += row.height() + ROW_GAP;
		}
	}

	@Override
	public boolean serverEditable() {
		return serverEditable;
	}

	/** center of a row's control for tests to click. */
	public int[] controlCenter(String key) {
		int offset = 0;
		for (ConfigRow row : rows) {
			if (key.equals(row.key)) {
				scroll = MathHelper.clamp(offset - ROW_HEIGHT, 0, maxScroll()); // row may be below the screen
				return new int[] {controlX() + CONTROL_WIDTH / 2, listTop() + offset - (int) scroll + row.height() / 2};
			}
			offset += row.height() + ROW_GAP;
		}
		throw new IllegalArgumentException(key);
	}

	public int[] buttonCenter(String key) {
		for (Button button : buttons) {
			if (key.equals(button.key)) {
				return new int[] {button.x + button.width / 2, button.y + BUTTON_HEIGHT / 2};
			}
		}
		throw new IllegalArgumentException(key);
	}

	@Override
	public ConfigDraft draft() {
		return draft;
	}

	public static boolean sliderHintDrawn() {
		return ConfigRow.sliderHintDrawn;
	}

	public boolean isConfirmingExit() {
		return confirmingExit;
	}

	public String idFieldText() {
		return blacklist.idField().getText();
	}

	public double scrollOffset() {
		return scroll;
	}

	public int maxScroll() {
		return Math.max(0, contentHeight() - (listBottom() - listTop()));
	}

	private int panelWidth() {
		return Math.min(PANEL_MAX_WIDTH, width - MARGIN * 2);
	}

	private int panelX() {
		return (width - panelWidth()) / 2;
	}

	private int listTop() {
		return MARGIN + HEADER_HEIGHT;
	}

	private int listBottom() {
		return height - MARGIN - FOOTER_HEIGHT;
	}

	@Override
	public int rowX() {
		return panelX() + INSET;
	}

	@Override
	public int rowWidth() {
		return panelWidth() - INSET * 2 - 4;
	}

	@Override
	public int controlX() {
		return rowX() + rowWidth() - CONTROL_WIDTH - INSET;
	}

	private int contentHeight() {
		return rows.stream().mapToInt(row -> row.height() + ROW_GAP).sum() - ROW_GAP;
	}

	private void layoutButtons() {
		buttons.clear();
		int y = height - MARGIN - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
		int left = panelX() + INSET;
		left = addButton("defaults", left, y, false);
		if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
			addButton("cloth", left + 4, y, false);
		}
		int right = panelX() + panelWidth() - INSET;
		right -= textRenderer.getWidth(Text.translatable("config.neurallink.button.done")) + 20;
		addButton("done", right, y, true);
		right -= textRenderer.getWidth(Text.translatable("config.neurallink.button.cancel")) + 24;
		addButton("cancel", right, y, false);
	}

	private int addButton(String key, int x, int y, boolean primary) {
		Text label = Text.translatable("config.neurallink.button." + key);
		Button button = new Button(key, label, x, y, textRenderer.getWidth(label) + 20, primary);
		buttons.add(button);
		return x + button.width;
	}

	@Override
	protected void init() {
		addSelectableChild(blacklist.idField(textRenderer));
		addSelectableChild(dimensions.idField(textRenderer));
		scroll = MathHelper.clamp(scroll, 0, maxScroll());
		layoutButtons();
	}

	private void finish(boolean save) {
		if (save) {
			draft.save();
			saved = true;
		} else {
			draft.cancel();
		}
		// The Cloth window has its own draft: returning here, Done would overwrite it.
		client.setScreen(leavingToCloth ? ClothConfigScreen.create(parent) : parent);
	}

	/** a death screen or disconnect skips finish, the overlay preview must not stick. */
	@Override
	public void removed() {
		if (!saved) {
			draft.cancel();
		}
	}

	@Override
	public void close() {
		if (draft.changed()) {
			confirmingExit = true;
			dragging = null;
		} else {
			finish(false);
		}
	}

	private void pressButton(String key) {
		HudSounds.play(ModSounds.CLICK, SOUND_VOLUME);
		switch (key) {
			case "done" -> finish(true);
			case "cancel" -> close();
			case "defaults" -> {
				draft.resetToDefaults();
				rebuildRows();
				scroll = MathHelper.clamp(scroll, 0, maxScroll());
			}
			case "cloth" -> {
				leavingToCloth = true;
				close();
			}
			default -> {
			}
		}
	}

	private ConfigRow rowAt(double mouseX, double mouseY) {
		if (!VisorTheme.inside(mouseX, mouseY, rowX(), listTop(), rowWidth(), listBottom() - listTop())) {
			return null;
		}
		int y = listTop() - (int) scroll;
		for (ConfigRow row : rows) {
			if (mouseY >= y && mouseY < y + row.height()) {
				return row;
			}
			y += row.height() + ROW_GAP;
		}
		return null;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (confirmingExit) {
			switch (keyCode) {
				case GLFW.GLFW_KEY_ESCAPE -> {
					confirmingExit = false;
					leavingToCloth = false;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> finish(true);
				default -> {
				}
			}
			return true;
		}
		for (BlacklistInput input : List.of(blacklist, dimensions)) {
			if (input.idField().isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
				input.addId();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		return confirmingExit || super.charTyped(chr, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		if (confirmingExit) {
			if (dialog.over(0, mouseX, mouseY)) {
				finish(false);
			} else if (dialog.over(1, mouseX, mouseY)) {
				finish(true);
			}
			return true;
		}
		for (Button footer : buttons) {
			if (footer.over(mouseX, mouseY)) {
				pressButton(footer.key);
				return true;
			}
		}
		ConfigRow row = rowAt(mouseX, mouseY);
		if (row != null && row.enabled() && row.click(mouseX)) {
			dragging = row;
			return true;
		}
		if (row != null && !row.enabled() && mouseX >= controlX()) {
			HudSounds.play(ModSounds.REFUSE, SOUND_VOLUME);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging != null) {
			dragging.drag(mouseX);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (confirmingExit) {
			return true;
		}
		ConfigRow row = rowAt(mouseX, mouseY);
		if (row != null && mouseX >= controlX() && row.enabled() && hasShiftDown() && row.scroll(vertical)) {
			if (!sliderHintCounted) { // one badge use per opened screen
				sliderHintCounted = true;
				ClientConfig.get().sliderHintUses++;
			}
			return true; // Shift + wheel steps a slider, plain wheel scrolls
		}
		scroll = MathHelper.clamp(scroll - vertical * ROW_HEIGHT, 0, maxScroll());
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ConfigRow.sliderHintDrawn = false;
		renderBackground(context, mouseX, mouseY, delta);
		float dt = frameDt(Util.getMeasuringTimeMs());
		pushOpenTransform(context, openProgress());
		int x = panelX();
		int w = panelWidth();
		int top = MARGIN;
		int panelHeight = height - MARGIN * 2;
		context.fill(x, top, x + w, top + panelHeight, VisorTheme.BG_SOLID);
		context.drawBorder(x, top, w, panelHeight, VisorTheme.ACCENT);
		context.drawText(textRenderer, title, x + INSET + 2, top + 9, VisorTheme.TEXT, false);
		Text version = Text.translatable("config.neurallink.subtitle");
		context.drawText(textRenderer, version, x + w - INSET - 2 - textRenderer.getWidth(version), top + 9, VisorTheme.TEXT_DIM, false);

		boolean interactive = !confirmingExit;
		int hoverX = interactive ? mouseX : -1;
		int hoverY = interactive ? mouseY : -1;
		context.enableScissor(x + 1, listTop(), x + w - 1, listBottom());
		int y = listTop() - (int) scroll;
		blacklist.idField().visible = false;
		dimensions.idField().visible = false;
		for (ConfigRow row : rows) {
			row.top = y;
			if (y + row.height() > listTop() && y < listBottom()) {
				boolean hovered = VisorTheme.inside(hoverX, hoverY, rowX(), y, rowWidth(), row.height()) && hoverY >= listTop() && hoverY < listBottom();
				row.hover = VisorTheme.approach(row.hover, hovered ? 1.0F : 0.0F, dt, HOVER_SPEED);
				row.render(context, hoverX, hoverY, delta, dt);
			}
			y += row.height() + ROW_GAP;
		}
		context.disableScissor();
		drawScrollbar(context, panelX() + panelWidth() - 5, listTop(), listBottom(), scroll, maxScroll(), 16);
		buttons.forEach(button -> button.render(context, hoverX, hoverY));
		if (confirmingExit) {
			renderDialog(context, mouseX, mouseY);
		}
		context.getMatrices().pop();
	}

	public int[] dialogButtonCenter(int button) {
		return dialog.buttonCenter(button);
	}

	private void renderDialog(DrawContext context, int mouseX, int mouseY) {
		dialog.pushFrame(context, 300.0F, VisorTheme.withAlpha(VisorTheme.BG, 0.7F), VisorTheme.PANEL);
		int y = dialog.y();
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("config.neurallink.exit.title"), width / 2, y + 14, VisorTheme.TEXT);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("config.neurallink.exit.hint"), width / 2, y + 30, VisorTheme.TEXT_DIM);
		for (int button = 0; button < 2; button++) {
			int bx = dialog.buttonX(button);
			boolean hovered = dialog.over(button, mouseX, mouseY);
			context.fill(bx, dialog.buttonY(), bx + dialog.buttonWidth(), dialog.buttonY() + BUTTON_HEIGHT, hovered ? VisorTheme.ACCENT : VisorTheme.PANEL_HOVER);
			context.drawBorder(bx, dialog.buttonY(), dialog.buttonWidth(), BUTTON_HEIGHT, button == 1 ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT);
			Text label = Text.translatable(button == 1 ? "config.neurallink.exit.save" : "config.neurallink.exit.discard");
			context.drawCenteredTextWithShadow(textRenderer, label, bx + dialog.buttonWidth() / 2, dialog.buttonY() + 5, VisorTheme.TEXT);
		}
		context.getMatrices().pop();
	}

	private final class Button {
		final String key;
		final Text label;
		final int x;
		final int y;
		final int width;
		final boolean primary;

		Button(String key, Text label, int x, int y, int width, boolean primary) {
			this.key = key;
			this.label = label;
			this.x = x;
			this.y = y;
			this.width = width;
			this.primary = primary;
		}

		boolean over(double mouseX, double mouseY) {
			return VisorTheme.inside(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
		}

		void render(DrawContext context, int mouseX, int mouseY) {
			context.fill(x, y, x + width, y + BUTTON_HEIGHT, over(mouseX, mouseY) ? VisorTheme.ACCENT : primary ? VisorTheme.PANEL_HOVER : VisorTheme.PANEL);
			context.drawBorder(x, y, width, BUTTON_HEIGHT, primary ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT);
			context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2, y + 5, VisorTheme.TEXT);
		}
	}
}
