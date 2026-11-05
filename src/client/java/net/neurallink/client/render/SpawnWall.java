package net.neurallink.client.render;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.neurallink.client.ClientSession;
import net.neurallink.client.gui.VisorTheme;
import net.neurallink.client.hud.HudSounds;
import net.neurallink.network.SpawnZonePayload;
import net.neurallink.registry.ModSounds;
import org.joml.Matrix4f;

/**
 * spawn zone border as the host sees it: flickering pixels and a scan line on a glowing ground line, lit only near the host
 * and cleared near the eye. Pressing in sends a wave with a flash and thud. Plain quads; under Iris they use gbuffers_basic.
 */
public final class SpawnWall {
	/** Fade-out distance from the hitbox, in blocks. */
	private static final float RADIUS = 4.5F;
	/** offset from block faces to avoid flicker. */
	private static final double LIFT = 0.005;
	/** hitbox this close presses the side. */
	private static final double CONTACT = 0.08;
	/** Drive share needed to press a side, so sliding along isn't a bump. */
	private static final double PRESS = 0.2;
	private static final long RIPPLE_MS = 1100L;
	private static final long RIPPLE_EVERY_MS = 650L;
	private static final long FLASH_MS = 260L;
	private static final double RIPPLE_SPEED = 3.2;
	private static final double RIPPLE_WIDTH = 0.45;
	/** gap between thuds while pressing. */
	private static final long SOUND_EVERY_MS = 1400L;
	private static final double NEAR_CLEAR = 0.35;
	private static final double NEAR_FULL = 1.6;

	private static final double PIXEL = 0.0625;
	private static final double PIXEL_GAP = 0.12;
	private static final float NOISE_FPS = 12.0F;
	/** Scan line sweeps per second. */
	private static final float SCAN_SPEED = 0.45F;
	private static final double SCAN_HEIGHT = 0.35;

	private static final double LINE_STEP = 0.0625;
	private static final double LINE_HEIGHT = 0.045;
	/** line thickening under a wave. */
	private static final double LINE_SWELL = 0.06;
	private static final double GLOW_HEIGHT = 0.45;
	private static final double GLOW_SWELL = 0.35;
	/** How far below the feet the ground line searches. */
	private static final int GROUND_SEARCH = 6;
	/** glow bands: shader packs colour triangles by one corner, turning one quad into a wedge. */
	private static final int GLOW_BANDS = 8;

	/** Translucent, no depth write: depth would cut rain, clouds and (Fabulous) water and glass behind the wall. */
	private static final RenderLayer LAYER = RenderLayer.of("neurallink_spawn_wall", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS, 1536, false, true,
			RenderLayer.MultiPhaseParameters.builder()
					.program(RenderPhase.POSITION_COLOR_PROGRAM)
					.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
					.cull(RenderPhase.DISABLE_CULLING)
					.writeMaskState(RenderPhase.COLOR_MASK)
					.build(false));

	/**
	 * own buffers, drawn at once: under Iris entity buffers batch and draw(layer) does nothing, so the wall would land under
	 * terrain next frame.
	 */
	private static VertexConsumerProvider.Immediate buffers;
	private static SpawnZonePayload zone = SpawnZonePayload.NONE;
	private static final List<Ripple> RIPPLES = new ArrayList<>();
	private static boolean touching;
	private static long contactAt;
	private static long lastRipple;
	private static long lastSound;
	/** For tests: waves and thuds started, for pace. */
	private static int wavesStarted;
	private static int bumps;

	private SpawnWall() {
	}

	/** a wave on one side from the press point. */
	private record Ripple(boolean acrossX, double plane, double along, double y, long start) {
	}

	public static void onZone(SpawnZonePayload payload) {
		zone = payload;
	}

	public static void clear() {
		zone = SpawnZonePayload.NONE;
		RIPPLES.clear();
		touching = false;
	}

	/** for tests: what the server last said. */
	public static SpawnZonePayload zone() {
		return zone;
	}

	/** For tests: host pressing the wall now. */
	public static boolean touching() {
		return touching;
	}

	/** for tests: waves started; diff over a press gives pace. */
	public static int wavesStarted() {
		return wavesStarted;
	}

	/** For tests: thuds played. */
	public static int bumps() {
		return bumps;
	}

	/** for tests: waves running now. */
	public static int ripples() {
		return RIPPLES.size();
	}

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!zone.present() || !ClientSession.isActive() || client.world == null || context.matrixStack() == null) {
			return;
		}
		Entity host = client.world.getEntityById(ClientSession.mobId());
		if (host == null) {
			return;
		}
		long now = Util.getMeasuringTimeMs();
		Vec3d pos = host.getLerpedPos(context.tickCounter().getTickDelta(false));
		Host at = new Host(pos.add(0.0, host.getHeight() / 2.0, 0.0), pos.y, host.getWidth() / 2.0);
		int minX = zone.minX();
		int minZ = zone.minZ();
		int maxX = zone.maxX() + 1;
		int maxZ = zone.maxZ() + 1;
		Side[] sides = {
				new Side(true, minX - LIFT, minZ, maxZ, -1),
				new Side(true, maxX + LIFT, minZ, maxZ, 1),
				new Side(false, minZ - LIFT, minX, maxX, -1),
				new Side(false, maxZ + LIFT, minX, maxX, 1)};
		trackContact(at, ClientSession.moveIntent(host), sides, now);
		RIPPLES.removeIf(ripple -> now - ripple.start > RIPPLE_MS);
		if (buffers == null) {
			buffers = VertexConsumerProvider.immediate(new BufferAllocator(1 << 18));
		}
		VertexConsumer buffer = buffers.getBuffer(LAYER);
		Matrix4f matrix = context.matrixStack().peek().getPositionMatrix();
		Vec3d camera = context.camera().getPos();
		float flash = touching ? 1.0F - MathHelper.clamp((now - contactAt) / (float) FLASH_MS, 0.0F, 1.0F) : 0.0F;
		for (Side side : sides) {
			if (side.gap(at) > RADIUS) {
				continue;
			}
			Field field = new Field(side, at, now, flash);
			Draw draw = new Draw(buffer, matrix, camera, side);
			noise(draw, field, now);
			groundLine(draw, field, client.world);
		}
		client.getFramebuffer().beginWrite(false); // Iris uses the pack program only with the main target bound
		buffers.draw(LAYER);
	}

	/**
	 * Pressing starts a wave, flash and thud, then waves periodically. The server parks a stopped host right at the wall, so
	 * drive into the side is required too.
	 */
	private static void trackContact(Host at, Vec3d intent, Side[] sides, long now) {
		Side pressed = null;
		for (Side side : sides) {
			double along = side.acrossX ? at.center.z : at.center.x;
			double into = -side.outward * (side.acrossX ? intent.x : intent.z);
			if (side.gap(at) < CONTACT && into > PRESS && along > side.from - at.halfWidth && along < side.to + at.halfWidth) {
				pressed = side;
			}
		}
		if (pressed == null) {
			touching = false;
			return;
		}
		double along = pressed.acrossX ? at.center.z : at.center.x;
		if (!touching) {
			touching = true;
			contactAt = now;
			addRipple(pressed, along, at.center.y, now);
		} else if (now - lastRipple > RIPPLE_EVERY_MS) {
			addRipple(pressed, along, at.center.y, now);
		}
		if (now - lastSound > SOUND_EVERY_MS) {
			lastSound = now;
			bumps++;
			HudSounds.play(ModSounds.WALL_BUMP, 0.5F);
		}
	}

	private static void addRipple(Side side, double along, double y, long now) {
		lastRipple = now;
		wavesStarted++;
		RIPPLES.add(new Ripple(side.acrossX, side.plane, along, y, now));
	}

	/** flickering palette pixels, a rising scan line, waves lighting them. */
	private static void noise(Draw draw, Field field, long now) {
		float time = now / 1000.0F;
		int frame = (int) (time * NOISE_FPS);
		double scan = field.centerY - RADIUS + frac(time * SCAN_SPEED) * RADIUS * 2.0;
		// Light is 0 past RADIUS so only a disc of the side can be lit.
		double lit = (RADIUS + field.halfWidth) * (RADIUS + field.halfWidth) - field.across * field.across;
		if (lit <= 0.0) {
			return;
		}
		double reach = Math.min(RADIUS, Math.sqrt(lit) + PIXEL);
		int iStart = MathHelper.floor(Math.max(draw.side.from, field.along - reach) / PIXEL);
		int iEnd = MathHelper.ceil(Math.min(draw.side.to, field.along + reach) / PIXEL) - 1;
		int jStart = MathHelper.floor((field.centerY - reach) / PIXEL);
		int jEnd = MathHelper.ceil((field.centerY + reach) / PIXEL);
		double inset = PIXEL * PIXEL_GAP;
		for (int i = iStart; i <= iEnd; i++) {
			for (int j = jStart; j <= jEnd; j++) {
				double a = i * PIXEL;
				double y = j * PIXEL;
				if (field.light(a + PIXEL / 2.0, y + PIXEL / 2.0) <= 0.02F) {
					continue;
				}
				float n = hash(i * 31 + frame, j * 17 - frame);
				float sweep = (float) Math.max(0.0, 1.0 - Math.abs(y - scan) / SCAN_HEIGHT);
				float alpha = 0.12F + 0.6F * n * n * n + 0.5F * sweep;
				int color = n > 0.93F ? VisorTheme.TEXT : n > 0.55F ? VisorTheme.HIGHLIGHT : VisorTheme.ACCENT;
				draw.rect(a + inset, y + inset, a + PIXEL - inset, y + PIXEL - inset, field, alpha, color, VisorTheme.TEXT);
			}
		}
	}

	/** ground contact: bright line with soft glow, swelling under waves. */
	private static void groundLine(Draw draw, Field field, World world) {
		int start = MathHelper.floor(Math.max(draw.side.from, field.along - RADIUS) / LINE_STEP);
		int end = MathHelper.ceil(Math.min(draw.side.to, field.along + RADIUS) / LINE_STEP) - 1;
		int column = Integer.MIN_VALUE;
		double ground = field.feetY;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = start; i <= end; i++) {
			double a0 = i * LINE_STEP;
			double a1 = a0 + LINE_STEP;
			int block = MathHelper.floor(a0 + LINE_STEP / 2.0);
			if (block != column) { // one ground per block column
				column = block;
				ground = ground(world, draw.side, a0 + LINE_STEP / 2.0, field.feetY, pos);
			}
			float heat = field.heat(a0 + LINE_STEP / 2.0, ground);
			draw.gradient(a0, ground, a1, ground + GLOW_HEIGHT + GLOW_SWELL * heat, field, 0.55F + 0.45F * heat, 0.0F, VisorTheme.HIGHLIGHT);
			draw.rect(a0, ground, a1, ground + LINE_HEIGHT + LINE_SWELL * heat, field, 0.95F, VisorTheme.HIGHLIGHT, VisorTheme.TEXT);
		}
	}

	/**
	 * Ground top just outside the side near host height, following slopes. Climbs only from solid blocks so a cave roof
	 * isn't taken for ground.
	 */
	private static double ground(World world, Side side, double along, double feetY, BlockPos.Mutable pos) {
		double outside = side.plane + side.outward * 0.5;
		int top = MathHelper.floor(feetY) + 1;
		if (solid(world, pos.set(side.acrossX ? outside : along, top, side.acrossX ? along : outside))) {
			for (int y = top + 1; y <= top + GROUND_SEARCH; y++) {
				if (!solid(world, pos.setY(y))) {
					return y;
				}
			}
			return feetY;
		}
		for (int y = top - 1; y >= top - GROUND_SEARCH; y--) {
			if (solid(world, pos.setY(y))) {
				return y + 1.0;
			}
		}
		return feetY;
	}

	private static boolean solid(World world, BlockPos pos) {
		return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
	}

	/** host middle, feet, half width. */
	private record Host(Vec3d center, double feetY, double halfWidth) {
	}

	/** Zone side: plane across x (or z), {@code from} to {@code to} along the other axis; outward is -1 or 1. */
	private record Side(boolean acrossX, double plane, int from, int to, int outward) {
		double gap(Host at) {
			double across = acrossX ? at.center.x : at.center.z;
			return Math.abs(plane - across) - at.halfWidth;
		}
	}

	/**
	 * per-point light on a side: bright at the host, dark at RADIUS, brighter under waves and the contact flash. Waves also
	 * set colour heat.
	 */
	private static final class Field {
		final double across;
		final double along;
		final double centerY;
		final double feetY;
		final double halfWidth;
		final List<Ripple> ripples = new ArrayList<>();
		final long now;
		final float flash;

		Field(Side side, Host at, long now, float flash) {
			this.across = side.plane - (side.acrossX ? at.center.x : at.center.z);
			this.along = side.acrossX ? at.center.z : at.center.x;
			this.centerY = at.center.y;
			this.feetY = at.feetY;
			this.halfWidth = at.halfWidth;
			this.now = now;
			this.flash = flash;
			for (Ripple ripple : RIPPLES) {
				if (ripple.acrossX == side.acrossX && ripple.plane == side.plane) {
					ripples.add(ripple);
				}
			}
		}

		float light(double a, double y) {
			return light(a, y, heat(a, y));
		}

		/** With heat known, since a quad needs both per corner. */
		float light(double a, double y, float heat) {
			double dAlong = a - along;
			double dY = y - centerY;
			double distance = Math.sqrt(across * across + dAlong * dAlong + dY * dY) - halfWidth;
			float base = (float) MathHelper.clamp(1.0 - distance / RADIUS, 0.0, 1.0);
			base = base * base * (3.0F - 2.0F * base);
			return Math.min(1.0F, base * (1.0F + flash) + heat * base);
		}

		/** 0 to 1: wave light at this point. */
		float heat(double a, double y) {
			float heat = 0.0F;
			for (Ripple ripple : ripples) {
				float age = (now - ripple.start) / (float) RIPPLE_MS;
				double radius = age * RIPPLE_MS / 1000.0 * RIPPLE_SPEED;
				double d = Math.sqrt((a - ripple.along) * (a - ripple.along) + (y - ripple.y) * (y - ripple.y));
				double ring = (d - radius) / RIPPLE_WIDTH;
				heat += (float) Math.exp(-ring * ring) * (1.0F - age);
			}
			return Math.min(1.0F, heat + flash);
		}
	}

	/** quads on one side, positioned along and up. */
	private record Draw(VertexConsumer buffer, Matrix4f matrix, Vec3d camera, Side side) {
		/** Per-corner light; colour goes cold to hot under a wave. */
		void rect(double a0, double y0, double a1, double y1, Field field, float alpha, int cold, int hot) {
			float h0 = field.heat(a0, y0);
			float h1 = field.heat(a1, y0);
			float h2 = field.heat(a1, y1);
			float h3 = field.heat(a0, y1);
			float l0 = field.light(a0, y0, h0);
			float l1 = field.light(a1, y0, h1);
			float l2 = field.light(a1, y1, h2);
			float l3 = field.light(a0, y1, h3);
			if (l0 + l1 + l2 + l3 <= 0.0F) {
				return;
			}
			vertex(a0, y0, color(cold, hot, h0, alpha * l0));
			vertex(a1, y0, color(cold, hot, h1, alpha * l1));
			vertex(a1, y1, color(cold, hot, h2, alpha * l2));
			vertex(a0, y1, color(cold, hot, h3, alpha * l3));
		}

		/** bottom to top alpha in bands; smooth without shaders. */
		void gradient(double a0, double y0, double a1, double y1, Field field, float bottom, float top, int color) {
			float lb0 = field.light(a0, y0) * bottom;
			float lb1 = field.light(a1, y0) * bottom;
			float lt1 = field.light(a1, y1) * top;
			float lt0 = field.light(a0, y1) * top;
			if (lb0 + lb1 + lt1 + lt0 <= 0.0F) {
				return;
			}
			for (int k = 0; k < GLOW_BANDS; k++) {
				float t0 = k / (float) GLOW_BANDS;
				float t1 = (k + 1) / (float) GLOW_BANDS;
				double yb = MathHelper.lerp(t0, y0, y1);
				double yt = MathHelper.lerp(t1, y0, y1);
				vertex(a0, yb, VisorTheme.withAlpha(color, MathHelper.lerp(t0, lb0, lt0)));
				vertex(a1, yb, VisorTheme.withAlpha(color, MathHelper.lerp(t0, lb1, lt1)));
				vertex(a1, yt, VisorTheme.withAlpha(color, MathHelper.lerp(t1, lb1, lt1)));
				vertex(a0, yt, VisorTheme.withAlpha(color, MathHelper.lerp(t1, lb0, lt0)));
			}
		}

		/** Clears near the eye, clipped at the zone corner. */
		private void vertex(double a, double y, int color) {
			double clamped = MathHelper.clamp(a, side.from, side.to);
			float dx = (float) ((side.acrossX ? side.plane : clamped) - camera.x);
			float dy = (float) (y - camera.y);
			float dz = (float) ((side.acrossX ? clamped : side.plane) - camera.z);
			float near = (float) MathHelper.clamp((Math.sqrt(dx * dx + dy * dy + dz * dz) - NEAR_CLEAR) / (NEAR_FULL - NEAR_CLEAR), 0.0, 1.0);
			near = near * near * (3.0F - 2.0F * near);
			buffer.vertex(matrix, dx, dy, dz).color((int) ((color >>> 24) * near) << 24 | color & 0xFFFFFF);
		}

		private static int color(int cold, int hot, float heat, float alpha) {
			return VisorTheme.withAlpha(VisorTheme.mix(cold, hot, heat), alpha);
		}
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	private static float frac(float value) {
		return value - MathHelper.floor(value);
	}
}
