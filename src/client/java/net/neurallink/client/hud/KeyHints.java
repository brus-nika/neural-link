package net.neurallink.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.neurallink.client.gui.VisorTheme;

/** key badges and glyphs drawn with fills, hints without text. */
public final class KeyHints {
	public static final int HEIGHT = 13;
	private static final Text ELLIPSIS = Text.literal("...");

	private KeyHints() {
	}

	/** Returns the badge width. */
	public static int keyCap(DrawContext context, TextRenderer text, Text key, int x, int y) {
		int size = Math.max(HEIGHT, text.getWidth(key) + 6);
		context.fill(x, y, x + size, y + HEIGHT, VisorTheme.withAlpha(VisorTheme.PANEL, 0.9F));
		context.drawBorder(x, y, size, HEIGHT, VisorTheme.ACCENT);
		// text width has an empty right column; without it the letter centers.
		context.drawTextWithShadow(text, key, x + (size - text.getWidth(key) + 2) / 2, y + 3, VisorTheme.TEXT);
		return size;
	}

	/** Right arrow, 8 px, centered on the badge row. */
	public static void arrowRight(DrawContext context, int x, int y) {
		context.fill(x, y + 6, x + 8, y + 7, VisorTheme.TEXT_DIM);
		for (int i = 0; i < 3; i++) {
			context.fill(x + i, y + 6 - i, x + i + 1, y + 7 + i, VisorTheme.TEXT_DIM);
		}
	}

	/** vertical arrow 5 px wide. Returns its width. */
	public static int arrowVertical(DrawContext context, int x, int y, boolean up) {
		context.fill(x + 2, y + 3, x + 3, y + 11, VisorTheme.TEXT_DIM);
		for (int i = 0; i < 3; i++) {
			int row = up ? y + 3 + i : y + 10 - i;
			context.fill(x + 2 - i, row, x + 3 + i, row + 1, VisorTheme.TEXT_DIM);
		}
		return 5;
	}

	/** Mouse with lit wheel. Returns its width. */
	public static int mouseWheel(DrawContext context, int x, int y) {
		context.drawBorder(x, y + 1, 7, 11, VisorTheme.TEXT_DIM);
		context.fill(x + 3, y + 2, x + 4, y + 5, VisorTheme.HIGHLIGHT);
		return 7;
	}

	/** cuts text to width with "..." keeping each part's style and colours, no space before the dots. */
	public static OrderedText fit(TextRenderer text, Text value, int room) {
		if (text.getWidth(value) <= room) {
			return value.asOrderedText();
		}
		List<Text> parts = new ArrayList<>();
		text.trimToWidth(value, Math.max(0, room - text.getWidth(ELLIPSIS))).visit((style, segment) -> {
			parts.add(Text.literal(segment).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		while (!parts.isEmpty()) {
			Text last = parts.removeLast();
			String kept = last.getString().stripTrailing();
			if (!kept.isEmpty()) {
				parts.add(Text.literal(kept).setStyle(last.getStyle()));
				break;
			}
		}
		MutableText cut = Text.empty();
		parts.forEach(cut::append);
		return OrderedText.concat(cut.asOrderedText(), ELLIPSIS.asOrderedText());
	}
}
