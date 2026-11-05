package net.neurallink.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** centered two-button dialog: geometry, hit tests, frame. */
public final class VisorDialog {
	private static final int BUTTON_INSET = 10;

	private final Screen screen;
	private final int width;
	private final int height;
	private final int buttonWidth;
	private final int buttonHeight;
	private final int buttonBottom;

	public VisorDialog(Screen screen, int width, int height, int buttonWidth, int buttonHeight, int buttonBottom) {
		this.screen = screen;
		this.width = width;
		this.height = height;
		this.buttonWidth = buttonWidth;
		this.buttonHeight = buttonHeight;
		this.buttonBottom = buttonBottom;
	}

	public int x() {
		return (screen.width - width) / 2;
	}

	public int y() {
		return (screen.height - height) / 2;
	}

	public int buttonX(int button) {
		return button == 0 ? x() + BUTTON_INSET : x() + width - BUTTON_INSET - buttonWidth;
	}

	public int buttonY() {
		return y() + height - buttonHeight - buttonBottom;
	}

	public int buttonWidth() {
		return buttonWidth;
	}

	public boolean over(int button, double mouseX, double mouseY) {
		return VisorTheme.inside(mouseX, mouseY, buttonX(button), buttonY(), buttonWidth, buttonHeight);
	}

	public int[] buttonCenter(int button) {
		return new int[] {buttonX(button) + buttonWidth / 2, buttonY() + buttonHeight / 2};
	}

	/** Dims the screen and draws the frame at layer z; caller pops the matrix. */
	public void pushFrame(DrawContext context, float z, int dim, int fill) {
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, z);
		context.fill(0, 0, screen.width, screen.height, dim);
		context.fill(x(), y(), x() + width, y() + height, fill);
		context.drawBorder(x(), y(), width, height, VisorTheme.HIGHLIGHT);
	}
}
