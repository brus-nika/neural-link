package net.neurallink.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.neurallink.NeuralLink;
import net.neurallink.chip.ChipCard;
import net.neurallink.client.NeuralLinkClient;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.client.hud.VisorTransitions;
import net.neurallink.network.ChipListPayload;
import net.neurallink.network.ConnectPayload;
import net.neurallink.registry.ModSounds;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Session host wheel: one sector per host, current on top. Hold G, point, release to switch; a tap leaves it open for a
 * click.
 */
public class HostWheelScreen extends VisorPanelScreen {
	private static final int INNER = 40;
	private static final int OUTER = 88;
	private static final Text NO_DISTANCE = Text.literal("—");
	private static final Text CURRENT = Text.translatable("menu.neurallink.status.current");
	private static final int RING_SIZE = 200;
	private static final float PUSH = 4.0F;
	private static final float DEAD_ZONE = INNER * 0.7F;
	private static final float ARC_STEP = MathHelper.PI / 48.0F;
	private static final long HOLD_MS = 200;
	private static final long STAGGER_MS = 22;
	private static final float APPEAR_MS = 140.0F;
	private static final float SELECT_MS = 170.0F;
	private static final float HOVER_SPEED = 18.0F;
	private static final int NAME_WIDTH = 56;
	private static final float ICON_ROOM = 24.0F;
	private static final float MARKS_Z = 200.0F;
	private static final float STATUS_SCALE = 0.75F;
	private static final int SIGNAL_BARS = 4;
	private static final Identifier RING = NeuralLink.id("textures/gui/wheel/ring.png");

	private List<ChipCard> cards = List.of();
	private final Random random = Random.create();
	private final CardRenderer cardRenderer = new CardRenderer(this, random);
	private final Map<UUID, Float> push = new HashMap<>();
	private boolean sticky;
	private boolean held;
	private int hovered = -1;
	private int selected = -1;
	private long selectedAt;

	/** held: G is down at open, so releasing it picks the hovered host. */
	public HostWheelScreen(ChipListPayload list, boolean held) {
		super(Text.translatable("menu.neurallink.wheel"));
		this.held = held;
		sticky = !held;
		update(list);
		HudSounds.play(ModSounds.WHEEL_OPEN, 0.6F);
	}

	/** order is fixed once so nothing moves under a still cursor; new hosts append, gone ones stay as lost. */
	public void update(ChipListPayload list) {
		if (selected >= 0) {
			return; // chosen sector must not change while the wheel closes
		}
		if (cards.isEmpty()) {
			cards = MenuSort.current().sort(list.cards());
			return;
		}
		Map<UUID, ChipCard> fresh = new LinkedHashMap<>();
		list.cards().forEach(card -> fresh.put(card.chipId(), card));
		List<ChipCard> next = new ArrayList<>();
		for (ChipCard old : cards) {
			ChipCard card = fresh.remove(old.chipId());
			next.add(card != null ? card : new ChipCard(old.chipId(), old.entityType(), old.name(), old.health(), old.maxHealth(), old.pos(), old.distance(), 0,
					ChipCard.Status.LOST, old.held(), false, false));
		}
		next.addAll(MenuSort.current().sort(List.copyOf(fresh.values())));
		cards = next;
	}

	public int sectorCount() {
		return cards.size();
	}

	public ChipCard card(int index) {
		return cards.get(index);
	}

	public int hovered() {
		return hovered;
	}

	public boolean isSticky() {
		return sticky;
	}

	/** sector middle in GUI coordinates for tests. */
	public int[] sectorCenter(int index) {
		float angle = index * MathHelper.TAU / cards.size();
		float radius = (INNER + OUTER) / 2.0F;
		return new int[] {Math.round(width / 2.0F + MathHelper.sin(angle) * radius), Math.round(height / 2.0F - MathHelper.cos(angle) * radius)};
	}

	/** Sector 0 on top, clockwise. Dead zone picks nothing. */
	public static int sectorAt(double dx, double dy, int count) {
		if (count == 0 || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			return -1;
		}
		double angle = Math.atan2(dx, -dy);
		if (angle < 0) {
			angle += Math.PI * 2;
		}
		double step = Math.PI * 2 / count;
		return (int) Math.floor((angle + step / 2) / step) % count;
	}

	private static boolean selectable(ChipCard card) {
		return card.status().connectable() && !card.current();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void tick() {
		cardRenderer.tick();
	}

	/** a press while G is held is key repeat. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (NeuralLinkClient.MENU_KEY.matchesKey(keyCode, scanCode)) {
			closeIfSticky();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Events, not polling: the binding state freezes under a screen and scancode keys can't be polled. */
	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (NeuralLinkClient.MENU_KEY.matchesKey(keyCode, scanCode)) {
			held = false;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (NeuralLinkClient.MENU_KEY.matchesMouse(button)) {
			closeIfSticky();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && selected < 0 && hovered >= 0) {
			select(hovered);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (NeuralLinkClient.MENU_KEY.matchesMouse(button)) {
			held = false;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void closeIfSticky() {
		if (sticky && selected < 0) {
			close();
		}
	}

	private boolean select(int index) {
		ChipCard card = cards.get(index);
		if (!selectable(card)) {
			HudSounds.play(ModSounds.REFUSE, 0.6F);
			return false;
		}
		selected = index;
		selectedAt = Util.getMeasuringTimeMs();
		HudSounds.play(ModSounds.WHEEL_SELECT, 0.6F);
		VisorTransitions.beginLoading(selectedAt + (long) SELECT_MS); // search shows once the wheel collapses
		ClientPlayNetworking.send(new ConnectPayload(card.chipId()));
		return true;
	}

	/** G released over a sector picks it; a quick tap in the middle leaves the wheel open. */
	private void followHeldKey(long now) {
		if (sticky || selected >= 0 || held) {
			return;
		}
		if (hovered >= 0) {
			if (!select(hovered)) {
				close();
			}
		} else if (now - openedAt < HOLD_MS) {
			sticky = true;
		} else {
			close();
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, VisorTheme.withAlpha(VisorTheme.BG_SOLID, 0.45F * openProgress()));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = Util.getMeasuringTimeMs();
		float dt = frameDt(now);
		float closing = selected < 0 ? 0.0F : VisorTheme.easeOut((now - selectedAt) / SELECT_MS);
		if (selected >= 0 && closing >= 1.0F) {
			close();
			return;
		}
		if (selected < 0) {
			int next = sectorAt(mouseX - width / 2.0, mouseY - height / 2.0, cards.size());
			if (next != hovered && next >= 0) {
				HudSounds.play(ModSounds.WHEEL_TICK, 0.35F);
			}
			hovered = next;
			followHeldKey(now);
		}
		renderBackground(context, mouseX, mouseY, delta);
		float open = openProgress();
		float alpha = open * (1.0F - closing);
		float scale = 0.7F + 0.3F * open - 0.12F * closing;
		context.getMatrices().push();
		context.getMatrices().translate(width / 2.0F, height / 2.0F, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawTexture(RenderLayer::getGuiTextured, RING, -RING_SIZE / 2, -RING_SIZE / 2, 0.0F, 0.0F, RING_SIZE, RING_SIZE, RING_SIZE, RING_SIZE, VisorTheme.withAlpha(VisorTheme.WHITE, alpha));
		arc(context, INNER, OUTER, 0.0F, MathHelper.TAU, VisorTheme.withAlpha(VisorTheme.BG_SOLID, alpha * 0.7F)); // gaps read as dark seams
		// flat shapes, then models, marks last and raised: models write depth and would hide later draws.
		Placement[] placements = new Placement[cards.size()];
		for (int i = 0; i < cards.size(); i++) {
			placements[i] = renderSector(context, i, now, dt, alpha, closing);
		}
		renderCenter(context, alpha);
		for (int i = 0; i < cards.size(); i++) {
			renderSectorIcon(context, i, placements[i]);
		}
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, MARKS_Z);
		for (int i = 0; i < cards.size(); i++) {
			renderSectorMarks(context, i, placements[i]);
		}
		renderHint(context, alpha);
		context.getMatrices().pop();
		context.getMatrices().pop();
	}

	/** Where a sector's icon and marks go this frame. */
	private record Placement(int x, int y, float outer, float middle, float alpha, float lift, float iconRoom) {
	}

	private void renderSectorIcon(DrawContext context, int index, Placement placement) {
		if (placement == null || placement.alpha() <= 0.5F) {
			return;
		}
		ChipCard card = cards.get(index);
		context.getMatrices().push();
		context.getMatrices().translate(placement.x(), placement.y(), 0.0F);
		float iconScale = Math.min(1.0F, placement.iconRoom() / ICON_ROOM) * (1.0F + 0.3F * placement.lift()); // many hosts make narrow sectors
		context.getMatrices().scale(iconScale, iconScale, 1.0F);
		cardRenderer.renderIcon(context, card, Registries.ENTITY_TYPE.get(card.entityType()), 0, 0);
		context.getMatrices().pop();
	}

	private void renderSectorMarks(DrawContext context, int index, Placement placement) {
		if (placement == null || placement.alpha() <= 0.5F) {
			return;
		}
		ChipCard card = cards.get(index);
		if (!card.status().connectable()) {
			cross(context, placement.x(), placement.y(), VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, placement.alpha()));
		}
		if (card.favorite()) {
			int starX = Math.round(MathHelper.sin(placement.middle()) * (placement.outer() - 7)) - CardRenderer.STAR_SIZE / 2;
			int starY = Math.round(-MathHelper.cos(placement.middle()) * (placement.outer() - 7)) - CardRenderer.STAR_SIZE / 2;
			cardRenderer.renderStar(context, starX, starY, true, false);
		}
	}

	private Placement renderSector(DrawContext context, int index, long now, float dt, float alpha, float closing) {
		ChipCard card = cards.get(index);
		float lift = VisorTheme.approach(push.getOrDefault(card.chipId(), 0.0F), index == hovered || index == selected ? 1.0F : 0.0F, dt, HOVER_SPEED);
		push.put(card.chipId(), lift);
		float appear = VisorTheme.easeOut((now - openedAt - index * STAGGER_MS) / APPEAR_MS);
		float sectorAlpha = alpha * appear;
		if (sectorAlpha <= 0.0F) {
			return null;
		}
		float step = MathHelper.TAU / cards.size();
		float gap = cards.size() == 1 ? 0.0F : Math.min(0.035F, step * 0.08F); // a lone sector has no neighbour, a gap would look like a slit
		float middle = index * step;
		float out = PUSH * lift;
		float grow = 0.82F + 0.18F * appear;
		float inner = (INNER + 2 + out) * grow;
		float outer = (OUTER + out) * grow;
		boolean reachable = card.status().connectable();
		int resting = VisorTheme.mix(VisorTheme.BG_SOLID, VisorTheme.PANEL, 0.8F);
		int fill = card.current() ? VisorTheme.mix(VisorTheme.BG_SOLID, VisorTheme.ACCENT, 0.4F)
				: reachable ? VisorTheme.mix(resting, VisorTheme.PANEL_HOVER, lift) : VisorTheme.BG_SOLID;
		if (index == selected) {
			fill = VisorTheme.mix(fill, VisorTheme.TEXT, 0.55F * (1.0F - closing));
		}
		float a0 = middle - step / 2 + gap;
		float a1 = middle + step / 2 - gap;
		arc(context, inner, outer, a0, a1, VisorTheme.withAlpha(fill, sectorAlpha * (reachable ? 0.92F : 0.75F)));
		// lighter rim band adds depth, brighter on hover.
		int band = VisorTheme.mix(VisorTheme.PANEL, VisorTheme.HIGHLIGHT, 0.15F + 0.35F * lift);
		arc(context, outer - 9.0F, outer - 1.5F, a0, a1, VisorTheme.withAlpha(band, sectorAlpha * (reachable ? 0.45F : 0.15F)));
		int edge = card.current() || index == hovered ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT;
		arc(context, outer - 1.5F, outer, a0, a1, VisorTheme.withAlpha(edge, sectorAlpha));
		arc(context, inner, inner + 1.0F, a0, a1, VisorTheme.withAlpha(VisorTheme.ACCENT, sectorAlpha * 0.8F));

		float radius = (inner + outer) / 2.0F;
		return new Placement(Math.round(MathHelper.sin(middle) * radius), Math.round(-MathHelper.cos(middle) * radius), outer, middle, sectorAlpha, lift, (a1 - a0) * radius);
	}

	/** Hovered or current host: name, distance, signal, model, status. */
	private void renderCenter(DrawContext context, float alpha) {
		arc(context, 0.0F, INNER - 3, 0.0F, MathHelper.TAU, VisorTheme.withAlpha(VisorTheme.BG_SOLID, alpha * 0.95F));
		ChipCard card = hovered >= 0 ? cards.get(hovered) : cards.stream().filter(ChipCard::current).findFirst().orElse(null);
		if (card == null) {
			return;
		}
		float health = card.maxHealth() > 0 ? MathHelper.clamp(card.health() / card.maxHealth(), 0.0F, 1.0F) : 0.0F;
		arc(context, INNER - 3, INNER - 1, 0.0F, MathHelper.TAU, VisorTheme.withAlpha(VisorTheme.PANEL, alpha));
		arc(context, INNER - 3, INNER - 1, 0.0F, MathHelper.TAU * health, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, alpha)); // HP runs around the rim
		if (alpha < 0.3F) {
			return;
		}
		// rows fit where the disk is wide; name and status fit the chord.
		boolean reachable = card.status().connectable();
		String name = textRenderer.trimToWidth(cardRenderer.cardName(card, Integer.MAX_VALUE / 2), NAME_WIDTH);
		context.drawCenteredTextWithShadow(textRenderer, name, 0, -23, VisorTheme.textColor(reachable ? VisorTheme.TEXT : VisorTheme.TEXT_DIM, alpha));

		// As in the menu, unreachable hosts show no distance: the server sends 0 from other worlds.
		Text distance = reachable ? Text.translatable("menu.neurallink.distance", card.distance()) : NO_DISTANCE;
		int rowWidth = textRenderer.getWidth(distance) + 4 + SIGNAL_BARS * 3 - 1;
		int left = -rowWidth / 2;
		context.drawText(textRenderer, distance, left, -12, VisorTheme.textColor(VisorTheme.TEXT_DIM, alpha), false);
		int lit = MathHelper.ceil(card.signal() * SIGNAL_BARS / 100.0F);
		int barsLeft = left + textRenderer.getWidth(distance) + 4;
		for (int i = 0; i < SIGNAL_BARS; i++) {
			int bx = barsLeft + i * 3;
			context.fill(bx, -5 - 2 * i, bx + 2, -3, VisorTheme.withAlpha(i < lit ? VisorTheme.HIGHLIGHT : VisorTheme.PANEL, alpha));
		}

		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 10.0F, 0.0F);
		cardRenderer.renderIcon(context, card, Registries.ENTITY_TYPE.get(card.entityType()), 0, 0);
		context.getMatrices().pop();

		Text status = card.current() ? CURRENT : CardRenderer.statusText(card);
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 22.0F, MARKS_Z); // over the model's depth
		context.getMatrices().scale(STATUS_SCALE, STATUS_SCALE, 1.0F);
		context.drawCenteredTextWithShadow(textRenderer, status, 0, 0, VisorTheme.textColor(card.current() ? VisorTheme.HIGHLIGHT : VisorTheme.TEXT_DIM, alpha));
		context.getMatrices().pop();
	}

	private void renderHint(DrawContext context, float alpha) {
		Text hint = Text.translatable(sticky ? "menu.neurallink.wheel.click" : "menu.neurallink.wheel.release");
		context.drawCenteredTextWithShadow(textRenderer, hint, 0, RING_SIZE / 2 + 4, VisorTheme.textColor(VisorTheme.TEXT_DIM, alpha * 0.9F));
	}

	private static void cross(DrawContext context, int x, int y, int color) {
		for (int i = -6; i <= 6; i++) {
			context.fill(x + i, y + i, x + i + 1, y + i + 1, color);
			context.fill(x + i, y - i, x + i + 1, y - i + 1, color);
		}
	}

	/**
	 * ring segment a0 to a1 (0 on top, clockwise) between two radii. Quads go in both windings since the GUI culls back
	 * faces.
	 */
	static void arc(DrawContext context, float r0, float r1, float a0, float a1, int color) {
		if ((color >>> 24) == 0 || a1 <= a0 || r1 <= r0) {
			return;
		}
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		int steps = Math.max(1, MathHelper.ceil((a1 - a0) / ARC_STEP));
		context.draw(provider -> {
			VertexConsumer buffer = provider.getBuffer(RenderLayer.getGui());
			for (int s = 0; s < steps; s++) {
				float b0 = a0 + (a1 - a0) * s / steps;
				float b1 = a0 + (a1 - a0) * (s + 1) / steps;
				float sin0 = MathHelper.sin(b0);
				float cos0 = MathHelper.cos(b0);
				float sin1 = MathHelper.sin(b1);
				float cos1 = MathHelper.cos(b1);
				float[] quad = {r1 * sin0, -r1 * cos0, r0 * sin0, -r0 * cos0, r0 * sin1, -r0 * cos1, r1 * sin1, -r1 * cos1};
				for (int v = 0; v < 4; v++) {
					buffer.vertex(matrix, quad[v * 2], quad[v * 2 + 1], 0.0F).color(color);
				}
				for (int v = 3; v >= 0; v--) {
					buffer.vertex(matrix, quad[v * 2], quad[v * 2 + 1], 0.0F).color(color);
				}
			}
		});
	}
}
