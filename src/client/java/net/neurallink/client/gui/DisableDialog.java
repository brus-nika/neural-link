package net.neurallink.client.gui;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.neurallink.chip.ChipCard;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.network.DisableChipPayload;
import net.neurallink.registry.ModSounds;
import org.lwjgl.glfw.GLFW;

final class DisableDialog {
	static final int WIDTH = 248;
	static final int TITLE_WIDTH = WIDTH - 42;
	static final int TITLE_X = 32;
	private static final int HEIGHT = 108;
	private static final int BUTTON_WIDTH = 110;
	private static final int BUTTON_HEIGHT = 20;
	private static final int FOCUS_CANCEL = 0;
	private static final int FOCUS_DISABLE = 1;

	private final Screen screen;
	private final CardRenderer cardRenderer;
	private final VisorDialog frame;
	private ChipCard confirming;
	private int confirmFocus = FOCUS_CANCEL;

	DisableDialog(Screen screen, CardRenderer cardRenderer) {
		this.screen = screen;
		this.cardRenderer = cardRenderer;
		frame = new VisorDialog(screen, WIDTH, HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT, 8);
	}

	boolean isOpen() {
		return confirming != null;
	}

	UUID chip() {
		return confirming == null ? null : confirming.chipId();
	}

	void open(ChipCard card) {
		confirming = card;
		confirmFocus = FOCUS_CANCEL;
	}

	/** The card may be gone or lost, then nothing to confirm. */
	void update(List<ChipCard> cards) {
		if (confirming != null) {
			UUID confirmed = confirming.chipId();
			confirming = cards.stream().filter(card -> card.chipId().equals(confirmed) && card.status() != ChipCard.Status.LOST).findFirst().orElse(null);
		}
	}

	String title() {
		String type = Registries.ENTITY_TYPE.get(confirming.entityType()).getName().getString();
		return screen.getTextRenderer().trimToWidth(confirming.name().isEmpty() ? type : type + " · " + confirming.name(), TITLE_WIDTH);
	}

	int[] buttonCenter(int button) {
		return frame.buttonCenter(button);
	}

	private void confirmDisable() {
		HudSounds.play(ModSounds.LOST, 0.5F);
		ClientPlayNetworking.send(new DisableChipPayload(confirming.chipId()));
		confirming = null;
	}

	void keyPressed(int keyCode) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE -> confirming = null;
			case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> confirmFocus = FOCUS_CANCEL + FOCUS_DISABLE - confirmFocus;
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
				if (confirmFocus == FOCUS_DISABLE) {
					confirmDisable();
				} else {
					confirming = null;
				}
			}
			default -> {
			}
		}
	}

	void mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && frame.over(FOCUS_DISABLE, mouseX, mouseY)) {
			confirmDisable();
		} else if (button == 0 && frame.over(FOCUS_CANCEL, mouseX, mouseY)) {
			confirming = null;
		}
	}

	void render(DrawContext context, int mouseX, int mouseY) {
		TextRenderer textRenderer = screen.getTextRenderer();
		frame.pushFrame(context, 400.0F, VisorTheme.BG, VisorTheme.BG_SOLID); // above the cards' item icons
		int x = frame.x();
		int y = frame.y();
		EntityType<?> type = Registries.ENTITY_TYPE.get(confirming.entityType());
		cardRenderer.renderIcon(context, confirming, type, x + 18, y + 17);
		context.drawText(textRenderer, title(), x + TITLE_X, y + 13, VisorTheme.TEXT, false);
		int line = y + 34;
		for (OrderedText text : textRenderer.wrapLines(Text.translatable("menu.neurallink.disable.warning"), WIDTH - 20)) {
			context.drawText(textRenderer, text, x + 10, line, VisorTheme.TEXT_DIM, false);
			line += 11;
		}
		renderButton(context, FOCUS_CANCEL, Text.translatable("menu.neurallink.disable.cancel"), mouseX, mouseY);
		renderButton(context, FOCUS_DISABLE, Text.translatable("menu.neurallink.disable.confirm"), mouseX, mouseY);
		context.getMatrices().pop();
	}

	private void renderButton(DrawContext context, int button, Text label, int mouseX, int mouseY) {
		int x = frame.buttonX(button);
		int y = frame.buttonY();
		boolean active = button == confirmFocus || frame.over(button, mouseX, mouseY);
		context.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, active ? VisorTheme.PANEL_HOVER : VisorTheme.PANEL);
		context.drawBorder(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, button == confirmFocus ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT);
		context.drawCenteredTextWithShadow(screen.getTextRenderer(), label, x + BUTTON_WIDTH / 2, y + 6, button == FOCUS_DISABLE ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT);
	}
}
