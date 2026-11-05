package net.neurallink.client.config;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.client.hud.KeyHints;
import net.neurallink.registry.ModSounds;

/** list row. key is the translation key tail for label and hint. */
abstract class ConfigRow {
	static final int ROW_HEIGHT = 34;
	static final int CONTROL_WIDTH = 112;
	static final int BUTTON_HEIGHT = 18;
	static final float SOUND_VOLUME = 0.6F;
	private static final int SECTION_HEIGHT = 20;
	private static final int ID_ROW_HEIGHT = 18;
	private static final int SWITCH_WIDTH = 26;
	private static final int SWITCH_HEIGHT = 12;
	private static final float SWITCH_SPEED = 18.0F;
	static final int SLIDER_HINT_USES = 3;
	/** For tests: last frame drew the Shift + wheel badge. */
	static boolean sliderHintDrawn;

	interface RowHost {
		TextRenderer getTextRenderer();

		int rowX();

		int rowWidth();

		int controlX();

		ConfigDraft draft();

		boolean serverEditable();

		void rowsChanged();
	}

	final RowHost host;
	final String key;
	final Text label;
	final Text hint;
	float hover;
	int top;

	ConfigRow(RowHost host, String key) {
		this.host = host;
		this.key = key;
		label = Text.translatable("config.neurallink." + key);
		hint = Text.translatable("config.neurallink." + key + ".hint");
	}

	ConfigRow(RowHost host, String key, Text label, Text hint) {
		this.host = host;
		this.key = key;
		this.label = label;
		this.hint = hint;
	}

	int rowX() {
		return host.rowX();
	}

	int rowWidth() {
		return host.rowWidth();
	}

	int controlX() {
		return host.controlX();
	}

	int height() {
		return ROW_HEIGHT;
	}

	boolean enabled() {
		return true;
	}

	boolean click(double mouseX) {
		return false;
	}

	void drag(double mouseX) {
	}

	boolean scroll(double vertical) {
		return false;
	}

	void render(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
		int x = rowX();
		int w = rowWidth();
		float alpha = enabled() ? 1.0F : 0.55F;
		context.fill(x, top, x + w, top + height(), VisorTheme.withAlpha(VisorTheme.mix(VisorTheme.PANEL, VisorTheme.PANEL_HOVER, hover), alpha));
		context.fill(x, top, x + 2, top + height(), VisorTheme.withAlpha(VisorTheme.mix(VisorTheme.ACCENT, VisorTheme.HIGHLIGHT, hover), alpha));
		context.drawText(host.getTextRenderer(), label, x + 8, top + 7, VisorTheme.withAlpha(VisorTheme.TEXT, alpha), false);
		context.drawText(host.getTextRenderer(), hint, x + 8, top + 20, VisorTheme.withAlpha(VisorTheme.TEXT_DIM, alpha), false);
		renderControl(context, mouseX, mouseY, delta, dt);
	}

	void renderControl(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
	}

	static final class Section extends ConfigRow {
		Section(RowHost host, String key, Text note) {
			super(host, key, Text.translatable(key), note);
		}

		@Override
		int height() {
			return SECTION_HEIGHT;
		}

		@Override
		void render(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			context.drawText(host.getTextRenderer(), label, rowX() + 2, top + 8, VisorTheme.HIGHLIGHT, false);
			context.drawText(host.getTextRenderer(), hint, rowX() + rowWidth() - host.getTextRenderer().getWidth(hint), top + 8, VisorTheme.TEXT_DIM, false);
		}
	}

	static final class Toggle extends ConfigRow {
		private final boolean editable;
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;
		private float knob;

		Toggle(RowHost host, String key, boolean editable, BooleanSupplier getter, Consumer<Boolean> setter) {
			super(host, key);
			this.editable = editable;
			this.getter = getter;
			this.setter = setter;
			knob = getter.getAsBoolean() ? 1.0F : 0.0F;
		}

		@Override
		boolean enabled() {
			return editable;
		}

		@Override
		boolean click(double mouseX) {
			if (mouseX < controlX()) {
				return false;
			}
			setter.accept(!getter.getAsBoolean());
			HudSounds.play(ModSounds.CLICK, SOUND_VOLUME);
			return true;
		}

		@Override
		void renderControl(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			boolean on = getter.getAsBoolean();
			knob = VisorTheme.approach(knob, on ? 1.0F : 0.0F, dt, SWITCH_SPEED);
			int x = controlX() + CONTROL_WIDTH - SWITCH_WIDTH;
			int y = top + (height() - SWITCH_HEIGHT) / 2;
			context.fill(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, VisorTheme.mix(VisorTheme.BG_SOLID, VisorTheme.HIGHLIGHT, knob));
			context.drawBorder(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, VisorTheme.ACCENT);
			int knobX = x + 1 + (int) (knob * (SWITCH_WIDTH - SWITCH_HEIGHT));
			context.fill(knobX, y + 1, knobX + SWITCH_HEIGHT - 2, y + SWITCH_HEIGHT - 1, VisorTheme.TEXT);
			Text state = Text.translatable(on ? "config.neurallink.on" : "config.neurallink.off");
			context.drawText(host.getTextRenderer(), state, x - 6 - host.getTextRenderer().getWidth(state), y + 2, on ? VisorTheme.TEXT : VisorTheme.TEXT_DIM, false);
		}
	}

	static final class Slider extends ConfigRow {
		private final boolean enabled;
		private final IntSupplier get;
		private final IntConsumer set;
		private final int min;
		private final int max;
		private final int step;
		private final IntFunction<Text> format;

		Slider(RowHost host, String key, boolean enabled, IntSupplier get, IntConsumer set, int min, int max, int step, IntFunction<Text> format) {
			super(host, key);
			this.enabled = enabled;
			this.get = get;
			this.set = set;
			this.min = min;
			this.max = max;
			this.step = step;
			this.format = format;
		}

		@Override
		boolean enabled() {
			return enabled;
		}

		@Override
		boolean click(double mouseX) {
			if (mouseX < controlX() - 2) {
				return false;
			}
			drag(mouseX);
			return true;
		}

		@Override
		void drag(double mouseX) {
			float part = MathHelper.clamp((float) (mouseX - controlX()) / CONTROL_WIDTH, 0.0F, 1.0F);
			set.accept(min + Math.round((max - min) * part / step) * step);
		}

		@Override
		boolean scroll(double vertical) {
			set.accept(MathHelper.clamp(get.getAsInt() + (int) Math.signum(vertical) * step, min, max));
			return true;
		}

		@Override
		void renderControl(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			int value = get.getAsInt();
			Text text = format.apply(value);
			context.drawText(host.getTextRenderer(), text, controlX() + CONTROL_WIDTH - host.getTextRenderer().getWidth(text), top + 7, enabled() ? VisorTheme.TEXT : VisorTheme.TEXT_DIM, false);
			int y = top + 22;
			float part = MathHelper.clamp((float) (value - min) / (max - min), 0.0F, 1.0F);
			int filled = (int) (CONTROL_WIDTH * part);
			context.fill(controlX(), y, controlX() + CONTROL_WIDTH, y + 3, VisorTheme.BG_SOLID);
			context.fill(controlX(), y, controlX() + filled, y + 3, VisorTheme.HIGHLIGHT);
			context.fill(controlX() + filled - 1, y - 3, controlX() + filled + 2, y + 6, VisorTheme.TEXT);
			if (enabled() && hover > 0.5F && ClientConfig.get().sliderHintUses < SLIDER_HINT_USES) {
				// plain wheel scrolls the list, the badge shows how to step the slider.
				int capWidth = KeyHints.keyCap(context, host.getTextRenderer(), Text.literal("Shift"), controlX(), top + 4);
				KeyHints.mouseWheel(context, controlX() + capWidth + 3, top + 4);
				sliderHintDrawn = true;
			}
		}
	}

	/** One-shot action, fires on save so it's undoable. */
	static final class HintsRow extends ConfigRow {
		HintsRow(RowHost host) {
			super(host, "hints");
		}

		@Override
		boolean click(double mouseX) {
			if (mouseX < controlX()) {
				return false;
			}
			host.draft().showHints = true;
			HudSounds.play(ModSounds.CLICK, SOUND_VOLUME);
			return true;
		}

		@Override
		void renderControl(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			int y = top + (height() - BUTTON_HEIGHT) / 2;
			boolean hovered = VisorTheme.inside(mouseX, mouseY, controlX(), y, CONTROL_WIDTH, BUTTON_HEIGHT);
			context.fill(controlX(), y, controlX() + CONTROL_WIDTH, y + BUTTON_HEIGHT, hovered ? VisorTheme.ACCENT : VisorTheme.BG_SOLID);
			context.drawBorder(controlX(), y, CONTROL_WIDTH, BUTTON_HEIGHT, VisorTheme.ACCENT);
			Text text = Text.translatable(host.draft().showHints ? "config.neurallink.hints.done" : "config.neurallink.hints.button");
			context.drawCenteredTextWithShadow(host.getTextRenderer(), text, controlX() + CONTROL_WIDTH / 2, y + 5, VisorTheme.TEXT);
		}
	}

	static final class AddId extends ConfigRow {
		private final BlacklistInput input;

		AddId(RowHost host, BlacklistInput input) {
			super(host, input.key());
			this.input = input;
		}

		@Override
		boolean enabled() {
			return host.serverEditable();
		}

		private int plusX() {
			return controlX() + CONTROL_WIDTH - BUTTON_HEIGHT;
		}

		@Override
		boolean click(double mouseX) {
			if (mouseX >= plusX()) {
				input.addId();
				return true;
			}
			return false;
		}

		@Override
		void renderControl(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			TextFieldWidget idField = input.idField();
			int y = top + (height() - BUTTON_HEIGHT) / 2;
			int width = CONTROL_WIDTH - BUTTON_HEIGHT - 2;
			context.fill(controlX(), y, controlX() + width, y + BUTTON_HEIGHT, VisorTheme.BG_SOLID);
			context.drawBorder(controlX(), y, width, BUTTON_HEIGHT, input.idRejected() ? VisorTheme.HIGHLIGHT : idField.isFocused() ? VisorTheme.TEXT_DIM : VisorTheme.ACCENT);
			idField.visible = true;
			idField.setX(controlX() + 4);
			idField.setY(y + 5);
			idField.setWidth(width - 8);
			idField.render(context, mouseX, mouseY, delta);
			if (idField.getText().isEmpty() && !idField.isFocused()) {
				context.drawText(host.getTextRenderer(), Text.translatable("config.neurallink." + input.key() + ".placeholder"), controlX() + 4, y + 5, VisorTheme.TEXT_DIM, false);
			}
			boolean hovered = VisorTheme.inside(mouseX, mouseY, plusX(), y, BUTTON_HEIGHT, BUTTON_HEIGHT);
			context.fill(plusX(), y, plusX() + BUTTON_HEIGHT, y + BUTTON_HEIGHT, hovered ? VisorTheme.ACCENT : VisorTheme.PANEL_HOVER);
			context.drawBorder(plusX(), y, BUTTON_HEIGHT, BUTTON_HEIGHT, VisorTheme.ACCENT);
			context.drawCenteredTextWithShadow(host.getTextRenderer(), Text.literal("+"), plusX() + BUTTON_HEIGHT / 2, y + 5, VisorTheme.TEXT);
		}
	}

	static final class IdRow extends ConfigRow {
		private final BlacklistInput input;
		private final String id;

		IdRow(RowHost host, BlacklistInput input, String id) {
			super(host, id, input.name(id), Text.literal(id));
			this.input = input;
			this.id = id;
		}

		@Override
		int height() {
			return ID_ROW_HEIGHT;
		}

		@Override
		boolean enabled() {
			return host.serverEditable();
		}

		private int crossX() {
			return rowX() + rowWidth() - 16;
		}

		@Override
		boolean click(double mouseX) {
			if (mouseX >= crossX()) {
				input.list().remove(id);
				host.rowsChanged();
				HudSounds.play(ModSounds.CLICK, SOUND_VOLUME);
				return true;
			}
			return false;
		}

		@Override
		void render(DrawContext context, int mouseX, int mouseY, float delta, float dt) {
			int x = rowX() + 10;
			context.fill(x, top, rowX() + rowWidth(), top + height(), VisorTheme.mix(VisorTheme.BG_SOLID, VisorTheme.PANEL, hover));
			context.drawText(host.getTextRenderer(), label, x + 6, top + 5, VisorTheme.TEXT, false);
			context.drawText(host.getTextRenderer(), hint, x + 12 + host.getTextRenderer().getWidth(label), top + 5, VisorTheme.TEXT_DIM, false);
			boolean hovered = VisorTheme.inside(mouseX, mouseY, crossX(), top, rowX() + rowWidth() - crossX(), height());
			context.drawText(host.getTextRenderer(), Text.literal("×"), crossX() + 5, top + 5, hovered ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT_DIM, false);
		}
	}
}
