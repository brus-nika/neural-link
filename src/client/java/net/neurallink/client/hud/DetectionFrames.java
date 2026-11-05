package net.neurallink.client.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.neurallink.client.ClientConfig;
import net.neurallink.client.ClientSession;
import net.neurallink.client.gui.HostWheelScreen;
import net.neurallink.client.gui.VisorScreen;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.entity.BodyEntity;
import net.neurallink.network.HostsPayload;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Detection frames around the player's hosts: a 2D HUD layer, not glow, so Iris shaders ignore it and it shows through
 * blocks.
 */
public final class DetectionFrames {
	static final float SMOOTH_SPEED = 18.0F;
	private static final int MIN_SIZE = 16;
	private static final int CORNER = 5;
	private static final long FLASH_MS = 600;
	private static final float PULSE_PER_MS = 0.004F;
	static final float NEAR_W = 0.05F;

	static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	// scratch space for project, render thread only.
	private static final float[] RANGE = new float[4];
	private static final Vector4f[] POINTS = {new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};
	private static final Vector4f CROSSING = new Vector4f();
	private static final Map<Integer, Frame> FRAMES = new HashMap<>();
	private static List<Integer> hosts = List.of();
	static Vec3d cameraPos = Vec3d.ZERO;
	private static long lastFrame;
	private static final Set<Integer> FRAMED = new HashSet<>();
	/** World the hosts list belongs to; after a portal old ids mean nothing until resent. */
	private static Object hostsWorld;

	private static final class Frame {
		float x0;
		float y0;
		float x1;
		float y1;
		boolean placed;
		final long createdAt = Util.getMeasuringTimeMs();
		Text name;
		int distance;
		Text label;
	}

	private DetectionFrames() {
	}

	public static void onHosts(HostsPayload payload) {
		hosts = payload.ids();
		EdgeArrows.onHosts(payload);
		hostsWorld = MinecraftClient.getInstance().world;
		FRAMES.keySet().retainAll(hosts);
	}

	public static void clear() {
		hosts = List.of();
		hostsWorld = null; // would keep the last session's world reachable
		FRAMES.clear();
		EdgeArrows.clear();
	}

	public static int frameCount() {
		return FRAMES.size();
	}

	/** whole periods keep the sine table index from saturating after hours of uptime. */
	public static float pulse(long now) {
		return 0.75F + 0.25F * MathHelper.sin((now % 1571L) * PULSE_PER_MS);
	}

	public static boolean framePlaced(int id) {
		Frame frame = FRAMES.get(id);
		return frame != null && frame.placed;
	}

	public static String frameLabel(int id) {
		Frame frame = FRAMES.get(id);
		return frame == null || frame.label == null ? "" : frame.label.getString();
	}

	/** Frame matrices exist only during world rendering. */
	public static void captureMatrices(WorldRenderContext context) {
		VIEW_PROJECTION.set(context.projectionMatrix()).mul(context.positionMatrix());
		cameraPos = context.camera().getPos();
	}

	public static void render(DrawContext context, RenderTickCounter counter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || !VisorOverlay.worn(client) || VisorBoot.active() || VisorBoot.waiting()) { // after boot the frames come in with their flash
			FRAMES.clear();
			EdgeArrows.reset();
			return;
		}
		long now = Util.getMeasuringTimeMs();
		float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		float tickDelta = counter.getTickDelta(false);
		int width = context.getScaledWindowWidth();
		int height = context.getScaledWindowHeight();
		FRAMED.clear();
		if (client.world != hostsWorld) {
			FRAMES.clear();
			EdgeArrows.reset();
			return;
		}
		for (int id : hosts) {
			Entity mob = client.world.getEntityById(id);
			if (mob == null || id == ClientSession.mobId()) {
				Frame stale = FRAMES.get(id);
				if (stale != null) {
					stale.placed = false;
				}
				continue;
			}
			float[] bounds = project(mob, tickDelta, width, height);
			if (bounds == null) {
				FRAMES.remove(id);
				continue;
			}
			Frame frame = FRAMES.computeIfAbsent(id, key -> new Frame());
			if (!frame.placed) {
				frame.x0 = bounds[0];
				frame.y0 = bounds[1];
				frame.x1 = bounds[2];
				frame.y1 = bounds[3];
				frame.placed = true;
			}
			frame.x0 = VisorTheme.approach(frame.x0, bounds[0], dt, SMOOTH_SPEED);
			frame.y0 = VisorTheme.approach(frame.y0, bounds[1], dt, SMOOTH_SPEED);
			frame.x1 = VisorTheme.approach(frame.x1, bounds[2], dt, SMOOTH_SPEED);
			frame.y1 = VisorTheme.approach(frame.y1, bounds[3], dt, SMOOTH_SPEED);
			draw(context, client.textRenderer, frame, mob, now);
			FRAMED.add(id);
		}
		// wheel and menu cover the middle and bottom, arrows would clutter their hints.
		if (ClientConfig.get().offscreenArrows && !(client.currentScreen instanceof HostWheelScreen || client.currentScreen instanceof VisorScreen)) {
			EdgeArrows.render(context, client, FRAMED, tickDelta, width, height, dt, now);
		} else {
			EdgeArrows.reset();
		}
	}

	/** Hitbox screen bounds, null if behind the camera or off screen. */
	private static float[] project(Entity mob, float tickDelta, int width, int height) {
		Vec3d pos = mob.getLerpedPos(tickDelta);
		Box box = mob.getBoundingBox().offset(pos.subtract(mob.getPos()));
		float[] range = RANGE;
		range[0] = Float.MAX_VALUE;
		range[1] = Float.MAX_VALUE;
		range[2] = -Float.MAX_VALUE;
		range[3] = -Float.MAX_VALUE;
		Vector4f[] points = POINTS;
		boolean clipped = false;
		for (int corner = 0; corner < 8; corner++) {
			double x = ((corner & 1) == 0 ? box.minX : box.maxX) - cameraPos.x;
			double y = ((corner & 2) == 0 ? box.minY : box.maxY) - cameraPos.y;
			double z = ((corner & 4) == 0 ? box.minZ : box.maxZ) - cameraPos.z;
			VIEW_PROJECTION.transform(points[corner].set((float) x, (float) y, (float) z, 1.0F));
			if (points[corner].w > NEAR_W) {
				include(range, points[corner], width, height);
			} else {
				clipped = true;
			}
		}
		if (clipped) {
			clipEdges(range, points, width, height);
		}
		float minX = range[0];
		float minY = range[1];
		float maxX = range[2];
		float maxY = range[3];
		if (maxX < 0 || maxY < 0 || minX > width || minY > height) {
			return null;
		}
		if (clipped) {
			minX = Math.max(minX, 0.0F);
			minY = Math.max(minY, 0.0F);
			maxX = Math.min(maxX, width);
			maxY = Math.min(maxY, height);
		}
		float centerX = (minX + maxX) / 2.0F;
		float centerY = (minY + maxY) / 2.0F;
		float halfWidth = Math.max(MIN_SIZE / 2.0F, (maxX - minX) / 2.0F);
		float halfHeight = Math.max(MIN_SIZE / 2.0F, (maxY - minY) / 2.0F);
		return new float[] {centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight};
	}

	/** skipped corners understate bounds, so near-plane crossings add their points. */
	private static void clipEdges(float[] range, Vector4f[] points, int width, int height) {
		for (int corner = 0; corner < 8; corner++) {
			for (int axis = 1; axis < 8; axis <<= 1) {
				Vector4f from = points[corner];
				Vector4f to = points[corner | axis];
				if ((corner & axis) == 0 && (from.w > NEAR_W) != (to.w > NEAR_W)) {
					include(range, CROSSING.set(from).lerp(to, (NEAR_W - from.w) / (to.w - from.w)), width, height);
				}
			}
		}
	}

	private static void include(float[] range, Vector4f point, int width, int height) {
		float screenX = (point.x / point.w + 1.0F) * 0.5F * width;
		float screenY = (1.0F - point.y / point.w) * 0.5F * height;
		range[0] = Math.min(range[0], screenX);
		range[1] = Math.min(range[1], screenY);
		range[2] = Math.max(range[2], screenX);
		range[3] = Math.max(range[3], screenY);
	}

	private static void draw(DrawContext context, TextRenderer text, Frame frame, Entity mob, long now) {
		int x0 = MathHelper.floor(frame.x0);
		int y0 = MathHelper.floor(frame.y0);
		int x1 = MathHelper.ceil(frame.x1);
		int y1 = MathHelper.ceil(frame.y1);
		float pulse = pulse(now);
		float flash = 1.0F - Math.min(1.0F, (float) (now - frame.createdAt) / FLASH_MS);
		int red = VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, pulse);
		int line = VisorTheme.withAlpha(VisorTheme.WHITE, 0.25F + 0.2F * pulse + flash * 0.5F);
		if (flash > 0.0F) {
			context.fill(x0, y0, x1, y1, VisorTheme.withAlpha(VisorTheme.HIGHLIGHT, flash * 0.35F));
		}
		context.fill(x0 + CORNER, y0, x1 - CORNER, y0 + 1, line);
		context.fill(x0 + CORNER, y1 - 1, x1 - CORNER, y1, line);
		context.fill(x0, y0 + CORNER, x0 + 1, y1 - CORNER, line);
		context.fill(x1 - 1, y0 + CORNER, x1, y1 - CORNER, line);
		corner(context, x0, y0, 1, 1, red);
		corner(context, x1, y0, -1, 1, red);
		corner(context, x0, y1, 1, -1, red);
		corner(context, x1, y1, -1, -1, red);

		int distance = (int) mob.getPos().distanceTo(cameraPos);
		Text name = mob instanceof BodyEntity ? mob.getDisplayName() : mob.getType().getName();
		if (frame.label == null || distance != frame.distance || !name.equals(frame.name)) {
			frame.name = name;
			frame.distance = distance;
			frame.label = Text.translatable("hud.neurallink.frame", name, distance);
		}
		Text label = frame.label;
		int labelWidth = text.getWidth(label) + 4;
		int plateX = x0 + 1;
		int plateY = Math.max(0, y0 - 10);
		context.fill(plateX - 1, plateY, plateX + labelWidth, plateY + 10, VisorTheme.withAlpha(VisorTheme.BG, 0.9F));
		context.fill(plateX - 1, plateY + 9, plateX + labelWidth, plateY + 10, red);
		context.drawText(text, label, plateX + 1, plateY + 1, VisorTheme.TEXT, false);
	}

	private static void corner(DrawContext context, int x, int y, int directionX, int directionY, int color) {
		int thickness = 2;
		int endX = x + directionX * CORNER;
		int endY = y + directionY * CORNER;
		context.fill(Math.min(x, endX), Math.min(y, y + directionY * thickness), Math.max(x, endX), Math.max(y, y + directionY * thickness), color);
		context.fill(Math.min(x, x + directionX * thickness), Math.min(y, endY), Math.max(x, x + directionX * thickness), Math.max(y, endY), color);
	}
}
