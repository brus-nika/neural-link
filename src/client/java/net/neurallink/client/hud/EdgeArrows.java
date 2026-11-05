package net.neurallink.client.hud;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.neurallink.client.ClientSession;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.network.HostsPayload;
import net.neurallink.network.HudStatePayload;
import org.joml.Vector4f;

/** Unframed hosts and body get an edge arrow. */
public final class EdgeArrows {
	private static final int ARROW_INSET = 18;
	private static final int ARROW_LIMIT = 8;
	/** about one label plate, closer arrows would stack labels. */
	private static final float ARROW_MERGE = 24.0F;
	private static final long ARROW_FADE_MS = 150;
	/** how hard a target behind pulls its arrow to the bottom edge. */
	private static final float BEHIND_PULL = 2.0F;
	private static final int AREA_PAD = 8;
	private static final int MAX_AREAS = 8;
	/** How far past a blocked span's middle before the arrow jumps to the other end. */
	private static final float SIDE_HOLD = 16.0F;
	private static final int OVERHEAD_LIFT = 10;
	/** chevron grid half size, 15 x 15 GUI pixels. */
	private static final int SPRITE_HALF = 7;
	private static final int SPRITE_SIZE = SPRITE_HALF * 2 + 1;
	private static final int DIRECTIONS = 16;
	private static final byte PIXEL_FAINT = 1;
	private static final byte PIXEL_FULL = 2;
	private static final byte PIXEL_EDGE = 3;
	/**
	 * Hand-drawn chevrons at 0, 22.5 and 45 degrees: f faint, r full, w white edge. Rotated pixel art turns to mush, so the
	 * other 13 come from quarter turns and a diagonal mirror.
	 */
	private static final String[][] DRAWN = {
			{
					"...............",
					"...............",
					".ff..rrw.......",
					"..ff..rrw......",
					"...ff..rrw.....",
					"....ff..rrw....",
					".....ff..rrw...",
					"......ff..rrw..",
					".....ff..rrw...",
					"....ff..rrw....",
					"...ff..rrw.....",
					"..ff..rrw......",
					".ff..rrw.......",
					"...............",
					"...............",
			},
			{
					"...............",
					"...ff..........",
					"....ff.........",
					"....ff.rrw.....",
					".....ff.rrw....",
					".....ff.rrw....",
					"......ff.rrw...",
					"....ffff.rrw...",
					"..ffff...rrrw..",
					".fff...rrrrww..",
					".f....rrrww....",
					"......rww......",
					"......w........",
					"...............",
					"...............",
			},
			{
					"...............",
					"......ff.......",
					"......ff.......",
					"......ff.......",
					"......ff.......",
					"......ff.rrw...",
					".fffffff.rrw...",
					".fffffff.rrw...",
					".........rrw...",
					".....rrrrrrw...",
					".....rrrrrrw...",
					".....wwwwwww...",
					"...............",
					"...............",
					"...............",
			},
	};
	private static final byte[][] SPRITES = buildSprites();
	private static final Vector4f TARGET = new Vector4f();
	private static final Map<Integer, Arrow> ARROWS = new HashMap<>();
	/** when the client last had each marked entity: just lost means gone, long lost means out of range. */
	private static final Map<Integer, Long> LAST_SEEN = new HashMap<>();
	/** Longer than the server takes to drop a dead host from the markers. */
	private static final long GONE_MS = 600L;
	private static final List<Group> GROUPS = new ArrayList<>();
	private static final Comparator<Arrow> BODY_THEN_NEAREST = Comparator.comparing((Arrow arrow) -> !arrow.body).thenComparingInt(arrow -> arrow.distance);
	private static List<HostsPayload.Marker> markers = List.of();

	private static final class Arrow {
		final int id;
		final boolean body;
		final long createdAt = Util.getMeasuringTimeMs();
		/** per blocked area: last pushed end, -1 or 1, 0 outside. */
		final int[] sides = new int[MAX_AREAS];
		float angle;
		float x;
		float y;
		boolean placed;
		/** Untracked target in view, the arrow hangs over it. */
		boolean overhead;
		int distance;
		Text label;
		int labelValue = -1;

		Arrow(int id, boolean body) {
			this.id = id;
			this.body = body;
		}
	}

	/** one drawn arrow. The nearest member anchors merging, the oldest is drawn so the arrow doesn't jump. */
	private record Group(Arrow anchor, List<Arrow> members) {
		Arrow lead() {
			Arrow oldest = anchor;
			for (Arrow member : members) {
				if (member.createdAt < oldest.createdAt) {
					oldest = member;
				}
			}
			return oldest;
		}

		boolean has(int id) {
			for (Arrow member : members) {
				if (member.id == id) {
					return true;
				}
			}
			return false;
		}
	}

	private EdgeArrows() {
	}

	static void onHosts(HostsPayload payload) {
		markers = payload.markers();
		LAST_SEEN.keySet().retainAll(payload.ids());
		ARROWS.keySet().retainAll(payload.ids());
	}

	static void clear() {
		markers = List.of();
		LAST_SEEN.clear();
		ARROWS.clear();
		GROUPS.clear();
	}

	static void reset() {
		ARROWS.clear();
		GROUPS.clear();
	}

	/** For tests: arrows drawn last frame, merged counted once. */
	public static int arrowCount() {
		return GROUPS.size();
	}

	/** for tests: this target's arrow position, or null. */
	public static float[] arrowPoint(int id) {
		for (Group group : GROUPS) {
			if (group.has(id)) {
				return new float[] {group.lead().x, group.lead().y};
			}
		}
		return null;
	}

	/** For tests: whether this target's arrow hangs over it. */
	public static boolean arrowOverhead(int id) {
		for (Group group : GROUPS) {
			if (group.has(id)) {
				return group.lead().overhead;
			}
		}
		return false;
	}

	/** for tests: label under this target's arrow. */
	public static String arrowLabel(int id) {
		for (Group group : GROUPS) {
			if (group.has(id) && group.lead().label != null) {
				return group.lead().label.getString();
			}
		}
		return "";
	}

	/** Arrows to unframed targets: off screen or out of tracking range. */
	static void render(DrawContext context, MinecraftClient client, Set<Integer> framed, float tickDelta, int width, int height, float dt, long now) {
		GROUPS.clear();
		List<int[]> areas = blockedAreas(width, height);
		List<Arrow> shown = new ArrayList<>();
		for (HostsPayload.Marker marker : markers) {
			int id = marker.id();
			if (id == ClientSession.mobId() || framed.contains(id)) {
				ARROWS.remove(id);
				continue;
			}
			Entity entity = client.world.getEntityById(id);
			if (entity != null) {
				LAST_SEEN.put(id, now);
			}
			Vec3d feet = entity != null ? entity.getLerpedPos(tickDelta) : Vec3d.ofBottomCenter(marker.pos());
			// the body gets an arrow only in a session. A just-lost entity is dead, its marker waits for the next packet;
			// one never seen or long lost is out of range, so the marker leads.
			if (marker.body() && !ClientSession.isActive() || entity == null && now - LAST_SEEN.getOrDefault(id, Long.MIN_VALUE / 2) < GONE_MS) {
				ARROWS.remove(id);
				continue;
			}
			Vec3d target = feet.add(0.0, entity != null ? entity.getHeight() / 2.0F : 0.9F, 0.0);
			Arrow arrow = ARROWS.computeIfAbsent(id, key -> new Arrow(key, marker.body()));
			aim(arrow, target, entity == null, width, height, dt);
			if (arrow.overhead) {
				pushOut(arrow, areas, width, height);
			} else {
				keepOff(arrow, areas, width, height);
			}
			arrow.distance = (int) feet.distanceTo(DetectionFrames.cameraPos);
			shown.add(arrow);
		}
		// Body first and never merges; the nearest host anchors its group.
		shown.sort(BODY_THEN_NEAREST);
		for (Arrow arrow : shown) {
			Group join = arrow.body ? null : hostGroup(arrow, GROUPS.size() >= ARROW_LIMIT);
			if (join != null) {
				join.members().add(arrow);
			} else {
				GROUPS.add(new Group(arrow, new ArrayList<>(List.of(arrow))));
			}
		}
		for (Group group : GROUPS) {
			drawArrow(context, client.textRenderer, group, width, height, now);
		}
	}

	/** a host group close enough to join, or the nearest once the limit is hit. */
	private static Group hostGroup(Arrow arrow, boolean full) {
		Group nearest = null;
		float best = Float.MAX_VALUE;
		for (Group group : GROUPS) {
			if (group.anchor().body) {
				continue;
			}
			float distance = (float) Math.hypot(group.anchor().x - arrow.x, group.anchor().y - arrow.y);
			if (distance < best) {
				best = distance;
				nearest = group;
			}
		}
		return best < ARROW_MERGE || full ? nearest : null;
	}

	/** Edge track toward the target; untracked targets in view get the arrow above them. */
	private static void aim(Arrow arrow, Vec3d target, boolean untracked, int width, int height, float dt) {
		Vec3d cameraPos = DetectionFrames.cameraPos;
		Vector4f clip = DetectionFrames.VIEW_PROJECTION.transform(TARGET.set((float) (target.x - cameraPos.x), (float) (target.y - cameraPos.y), (float) (target.z - cameraPos.z), 1.0F));
		float centerX = width / 2.0F;
		float centerY = height / 2.0F;
		if (untracked && clip.w > DetectionFrames.NEAR_W) {
			float screenX = (clip.x / clip.w + 1.0F) * centerX;
			float screenY = (1.0F - clip.y / clip.w) * centerY;
			if (screenX >= 0.0F && screenX <= width && screenY >= 0.0F && screenY <= height) {
				screenX = MathHelper.clamp(screenX, ARROW_INSET, width - ARROW_INSET);
				screenY = MathHelper.clamp(screenY, ARROW_INSET + OVERHEAD_LIFT, height - ARROW_INSET);
				if (!arrow.placed || !arrow.overhead) {
					arrow.x = screenX;
					arrow.y = screenY;
				}
				arrow.x = VisorTheme.approach(arrow.x, screenX, dt, DetectionFrames.SMOOTH_SPEED);
				arrow.y = VisorTheme.approach(arrow.y, screenY, dt, DetectionFrames.SMOOTH_SPEED);
				arrow.angle = 90.0F;
				arrow.overhead = true;
				arrow.placed = true;
				return;
			}
		}
		// direction from centre to the projected point, pulled down behind the camera so a target behind gets a bottom
		// arrow.
		float directionX = clip.x * centerX;
		float directionY = (-clip.y + Math.max(0.0F, -clip.w) * BEHIND_PULL) * centerY;
		float angle = Math.abs(directionX) + Math.abs(directionY) < 1.0E-4F ? 90.0F : (float) Math.toDegrees(Math.atan2(directionY, directionX));
		if (!arrow.placed || arrow.overhead) {
			arrow.angle = angle;
		} else { // smooth the angle, not the point, so the arrow follows the edge
			arrow.angle += MathHelper.wrapDegrees(angle - arrow.angle) * (1.0F - (float) Math.exp(-dt * DetectionFrames.SMOOTH_SPEED));
		}
		arrow.overhead = false;
		arrow.placed = true;
		float radians = arrow.angle * MathHelper.RADIANS_PER_DEGREE;
		float cos = MathHelper.cos(radians);
		float sin = MathHelper.sin(radians);
		float reach = Math.min((centerX - ARROW_INSET) / Math.max(1.0E-4F, Math.abs(cos)), (centerY - ARROW_INSET) / Math.max(1.0E-4F, Math.abs(sin)));
		arrow.x = centerX + cos * reach;
		arrow.y = centerY + sin * reach;
	}

	/** HUD blocks arrows avoid: x0, y0, x1, y1 each. */
	private static List<int[]> blockedAreas(int width, int height) {
		List<int[]> areas = new ArrayList<>();
		areas.add(new int[] {width - 56, 0, width, 28}); // implant counter
		if (ClientSession.isActive()) {
			areas.addAll(Arrays.asList(SessionHud.blockedAreas(width, height)));
			HudStatePayload state = SessionHud.state();
			if (state != null && !state.slots().isEmpty()) {
				areas.add(AbilityPanel.bounds(state.slots().size(), width, height));
			}
		} else {
			areas.add(new int[] {width / 2 - 120, height - 52, width / 2 + 120, height}); // hotbar, offhand and bars over it
		}
		return areas;
	}

	/**
	 * slides an arrow out of blocked areas to the nearer end, kept until the target passes the middle by a margin so it
	 * doesn't flip across the hotbar.
	 */
	private static void keepOff(Arrow arrow, List<int[]> areas, int width, int height) {
		boolean horizontal = arrow.y <= ARROW_INSET + 0.5F || arrow.y >= height - ARROW_INSET - 0.5F;
		float track = horizontal ? arrow.x : arrow.y; // where the target points before any push
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < Math.min(areas.size(), MAX_AREAS); i++) {
				int[] area = areas.get(i);
				float x0 = area[0] - AREA_PAD;
				float y0 = area[1] - AREA_PAD;
				float x1 = area[2] + AREA_PAD;
				float y1 = area[3] + AREA_PAD;
				if (arrow.x <= x0 || arrow.x >= x1 || arrow.y <= y0 || arrow.y >= y1) {
					if (pass == 0) {
						arrow.sides[i] = 0;
					}
					continue;
				}
				float low = horizontal ? x0 : y0;
				float high = horizontal ? x1 : y1;
				float max = (horizontal ? width : height) - ARROW_INSET;
				int side = arrow.sides[i];
				float middle = (low + high) / 2.0F;
				if (side == 0 || side < 0 && track > middle + SIDE_HOLD || side > 0 && track < middle - SIDE_HOLD) {
					side = track - low <= high - track ? -1 : 1;
				}
				if (side < 0 && low < ARROW_INSET) {
					side = 1;
				} else if (side > 0 && high > max) {
					side = -1;
				}
				float end = side < 0 ? low : high;
				if (end < ARROW_INSET || end > max) {
					continue; // area spans the whole edge, nowhere to go
				}
				arrow.sides[i] = side;
				if (horizontal) {
					arrow.x = end;
				} else {
					arrow.y = end;
				}
			}
		}
	}

	/** An arrow over an unseen target leaves a blocked area the shortest on-screen way. */
	private static void pushOut(Arrow arrow, List<int[]> areas, int width, int height) {
		for (int[] area : areas) {
			float x0 = area[0] - AREA_PAD;
			float y0 = area[1] - AREA_PAD;
			float x1 = area[2] + AREA_PAD;
			float y1 = area[3] + AREA_PAD + OVERHEAD_LIFT; // arrow drawn above its point
			if (arrow.x <= x0 || arrow.x >= x1 || arrow.y <= y0 || arrow.y >= y1) {
				continue;
			}
			float[][] exits = {{x0, arrow.y}, {x1, arrow.y}, {arrow.x, y0}, {arrow.x, y1}};
			float best = Float.MAX_VALUE;
			for (float[] exit : exits) {
				boolean onScreen = exit[0] >= ARROW_INSET && exit[0] <= width - ARROW_INSET && exit[1] >= ARROW_INSET + OVERHEAD_LIFT && exit[1] <= height - ARROW_INSET;
				float distance = Math.abs(exit[0] - arrow.x) + Math.abs(exit[1] - arrow.y);
				if (onScreen && distance < best) {
					best = distance;
					arrow.x = exit[0];
					arrow.y = exit[1];
				}
			}
		}
	}

	private static void drawArrow(DrawContext context, TextRenderer text, Group group, int width, int height, long now) {
		Arrow arrow = group.lead();
		float fade = Math.min(1.0F, (float) (now - arrow.createdAt) / ARROW_FADE_MS);
		int color = arrow.body ? VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, fade) : VisorTheme.withAlpha(VisorTheme.ACCENT, fade * DetectionFrames.pulse(now));
		int pointX = Math.round(arrow.x);
		int pointY = Math.round(arrow.overhead ? arrow.y - OVERHEAD_LIFT : arrow.y);
		chevrons(context, pointX, pointY, arrow.angle, color, VisorTheme.withAlpha(VisorTheme.WHITE, 0.7F * fade));

		int count = group.members().size();
		int value = count > 1 ? -count : arrow.distance;
		if (arrow.label == null || value != arrow.labelValue) {
			arrow.labelValue = value;
			arrow.label = value < 0 ? Text.literal("×" + -value) : Text.translatable("hud.neurallink.arrow", value);
		}
		int iconWidth = arrow.body ? 11 : 0;
		int labelWidth = text.getWidth(arrow.label) + 4 + iconWidth;
		float radians = arrow.angle * MathHelper.RADIANS_PER_DEGREE;
		float labelX = pointX - MathHelper.cos(radians) * (9.0F + labelWidth / 2.0F);
		float labelY = pointY - MathHelper.sin(radians) * 14.0F;
		int plateX = MathHelper.clamp(Math.round(labelX - labelWidth / 2.0F), 1, width - labelWidth - 1);
		int plateY = MathHelper.clamp(Math.round(labelY - 5.0F), 1, height - 11);
		context.fill(plateX, plateY, plateX + labelWidth, plateY + 10, VisorTheme.withAlpha(VisorTheme.BG, 0.9F * fade));
		context.fill(plateX, plateY + 9, plateX + labelWidth, plateY + 10, color);
		if (arrow.body) {
			context.drawTexture(RenderLayer::getGuiTextured, SessionHud.BODY, plateX + 1, plateY, 0.0F, 0.0F, 10, 10, 10, 10, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, fade));
		}
		// text below alpha 4 draws opaque, the first fade frame would flash.
		context.drawText(text, arrow.label, plateX + 2 + iconWidth, plateY + 1, VisorTheme.withAlpha(VisorTheme.TEXT, Math.max(0.05F, fade)), false);
	}

	/**
	 * Two chevrons like the frame corners: faint behind, full in front with a white edge. Snapped to GUI pixels in 16
	 * directions.
	 */
	private static void chevrons(DrawContext context, int x, int y, float angle, int color, int edge) {
		byte[] sprite = SPRITES[Math.floorMod(Math.round(angle * DIRECTIONS / 360.0F), DIRECTIONS)];
		int faint = VisorTheme.withAlpha(color, 0.45F);
		for (int row = 0; row < SPRITE_SIZE; row++) {
			int runStart = 0;
			byte kind = 0;
			for (int col = 0; col <= SPRITE_SIZE; col++) {
				byte pixel = col < SPRITE_SIZE ? sprite[row * SPRITE_SIZE + col] : 0;
				if (pixel == kind) {
					continue;
				}
				if (kind != 0) { // one fill per colour run
					int fill = kind == PIXEL_EDGE ? edge : kind == PIXEL_FULL ? color : faint;
					context.fill(x - SPRITE_HALF + runStart, y - SPRITE_HALF + row, x - SPRITE_HALF + col, y - SPRITE_HALF + row + 1, fill);
				}
				kind = pixel;
				runStart = col;
			}
		}
	}

	/** all 16 directions clockwise from right, built from three drawn ones. */
	private static byte[][] buildSprites() {
		byte[][] quarter = {parse(DRAWN[0]), parse(DRAWN[1]), parse(DRAWN[2]), null};
		quarter[3] = mirror(quarter[1]); // diagonal mirror turns 22.5 degrees into 67.5
		byte[][] sprites = new byte[DIRECTIONS][];
		for (int direction = 0; direction < DIRECTIONS; direction++) {
			byte[] sprite = quarter[direction % 4];
			for (int turn = 0; turn < direction / 4; turn++) {
				sprite = turn(sprite);
			}
			sprites[direction] = sprite;
		}
		return sprites;
	}

	private static byte[] parse(String[] rows) {
		byte[] sprite = new byte[SPRITE_SIZE * SPRITE_SIZE];
		for (int row = 0; row < SPRITE_SIZE; row++) {
			for (int col = 0; col < SPRITE_SIZE; col++) {
				sprite[row * SPRITE_SIZE + col] = switch (rows[row].charAt(col)) {
					case 'f' -> PIXEL_FAINT;
					case 'r' -> PIXEL_FULL;
					case 'w' -> PIXEL_EDGE;
					default -> 0;
				};
			}
		}
		return sprite;
	}

	/** quarter turn clockwise: right becomes down. */
	private static byte[] turn(byte[] sprite) {
		byte[] turned = new byte[sprite.length];
		for (int row = 0; row < SPRITE_SIZE; row++) {
			for (int col = 0; col < SPRITE_SIZE; col++) {
				turned[col * SPRITE_SIZE + SPRITE_SIZE - 1 - row] = sprite[row * SPRITE_SIZE + col];
			}
		}
		return turned;
	}

	/** Mirror across the down-right diagonal: a becomes 90 - a. */
	private static byte[] mirror(byte[] sprite) {
		byte[] mirrored = new byte[sprite.length];
		for (int row = 0; row < SPRITE_SIZE; row++) {
			for (int col = 0; col < SPRITE_SIZE; col++) {
				mirrored[col * SPRITE_SIZE + row] = sprite[row * SPRITE_SIZE + col];
			}
		}
		return mirrored;
	}
}
