package net.neurallink.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Flutterer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.neurallink.config.NeuralLinkConfig;
import net.neurallink.control.MobProfiles;

/**
 * Spawn protection walls off hosts like it blocks building: a host stops at the border, and one inside is pushed to the
 * nearest outside point. Zone rules are vanilla's.
 */
public final class SpawnZone {
	/** just past the border so a pushed host doesn't touch it again. */
	private static final double MARGIN = 0.01;
	/** Push result: nothing to do, moved (camera follows), or no room. */
	public enum Push { OUTSIDE, MOVED, STUCK }

	/** keeps the cut step off the border despite rounding. */
	private static final double EPSILON = 1.0E-7;
	/** How high a pushed host may look for room. */
	private static final int PUSH_CLIMB = 16;
	/** max push drop for fall-immune mobs; deeper is a void, not a fall. */
	private static final int IMMUNE_DROP = 64;
	/** Just under the feet, where a grounded box touches. */
	private static final double LANDING_PROBE = 0.05;

	/** protected blocks, inclusive, any height. */
	public record Box(int minX, int minZ, int maxX, int maxZ) {
		/** Hitbox of this half width overlaps the zone. */
		public boolean touches(double x, double z, double halfWidth) {
			return touches(x, z, halfWidth, halfWidth);
		}

		private boolean touches(double x, double z, double halfX, double halfZ) {
			return x + halfX > minX && x - halfX < maxX + 1 && z + halfZ > minZ && z - halfZ < maxZ + 1;
		}

		/** straight out each side, nearest first, in case one side is rock. */
		public List<Vec3d> exits(Vec3d pos, double halfWidth) {
			List<Vec3d> exits = new ArrayList<>(List.of(
					new Vec3d(minX - halfWidth - MARGIN, pos.y, pos.z),
					new Vec3d(maxX + 1 + halfWidth + MARGIN, pos.y, pos.z),
					new Vec3d(pos.x, pos.y, minZ - halfWidth - MARGIN),
					new Vec3d(pos.x, pos.y, maxZ + 1 + halfWidth + MARGIN)));
			exits.sort(Comparator.comparingDouble(exit -> exit.squaredDistanceTo(pos)));
			return exits;
		}

		public Vec3d stop(double x, double z, double halfWidth, Vec3d movement) {
			return stop(x, z, halfWidth, halfWidth, movement);
		}

		/**
		 * Cuts the step at the border per axis like vanilla, so the host slides along it. The whole path counts or fast
		 * hosts jump narrow zones. Hosts already inside move freely.
		 */
		public Vec3d stop(double x, double z, double halfX, double halfZ, Vec3d movement) {
			if (touches(x, z, halfX, halfZ)) {
				return movement;
			}
			double dx = movement.x;
			if (touches(x + dx / 2.0, z, halfX + Math.abs(dx) / 2.0, halfZ)) {
				dx = dx > 0.0 ? Math.max(0.0, minX - halfX - x - EPSILON) : Math.min(0.0, maxX + 1 + halfX - x + EPSILON);
			}
			double dz = movement.z;
			if (touches(x + dx, z + dz / 2.0, halfX, halfZ + Math.abs(dz) / 2.0)) {
				dz = dz > 0.0 ? Math.max(0.0, minZ - halfZ - z - EPSILON) : Math.min(0.0, maxZ + 1 + halfZ - z + EPSILON);
			}
			return new Vec3d(dx, movement.y, dz);
		}
	}

	/** zone source, vanilla by default. Test servers have no spawn protection so tests plug in their own. */
	public static Function<ServerPlayerEntity, Optional<Box>> source = SpawnZone::vanilla;

	private SpawnZone() {
	}

	/** The operator's zone in this world, if any. */
	public static Optional<Box> forOperator(ServerPlayerEntity player, World world) {
		if (!NeuralLinkConfig.spawnProtection || world.getRegistryKey() != World.OVERWORLD) {
			return Optional.empty();
		}
		return source.apply(player);
	}

	/** cuts a host's step at the zone, also for a vehicle carrying it. Runs for every move so it bails early. */
	public static Vec3d limit(Entity entity, Vec3d movement) {
		if (entity.getWorld().isClient || movement.x == 0.0 && movement.z == 0.0) {
			return movement;
		}
		Optional<ServerPlayerEntity> operator = SessionManager.operatorOf(entity);
		net.minecraft.util.math.Box held = entity.getBoundingBox();
		if (operator.isEmpty() && entity.hasPassengers()) {
			for (Entity passenger : entity.getPassengersDeep()) {
				operator = SessionManager.operatorOf(passenger);
				if (operator.isPresent()) {
					held = held.union(passenger.getBoundingBox()); // a host may sit off centre or stick out of a minecart
					break;
				}
			}
		}
		Optional<Box> zone = operator.flatMap(player -> forOperator(player, entity.getWorld()));
		if (zone.isEmpty()) {
			return movement;
		}
		Vec3d center = held.getCenter();
		Vec3d limited = zone.get().stop(center.x, center.z, held.getLengthX() / 2.0, held.getLengthZ() / 2.0, movement);
		if (limited.x != movement.x || limited.z != movement.z) {
			// Like a wall, speed into the border is dropped so fliers don't keep pressing.
			Vec3d velocity = entity.getVelocity();
			entity.setVelocity(limited.x != movement.x ? 0.0 : velocity.x, velocity.y, limited.z != movement.z ? 0.0 : velocity.z);
		}
		return limited;
	}

	/**
	 * Moves a host inside to the nearest outside point with room and a safe landing: up, then down, then other sides. Leaves
	 * any vehicle. STUCK if nothing nearby, so control can't continue.
	 */
	public static Push push(MobEntity mob, Box zone) {
		double halfWidth = mob.getWidth() / 2.0;
		if (!zone.touches(mob.getX(), mob.getZ(), halfWidth)) {
			return Push.OUTSIDE;
		}
		for (Vec3d exit : zone.exits(mob.getPos(), halfWidth)) {
			// unloaded chunks have no collision and would pass for open air.
			loadColumns(mob.getWorld(), mob.getBoundingBox().offset(exit.subtract(mob.getPos())));
			for (int step = 0; step <= PUSH_CLIMB * 2; step++) {
				int dy = step <= PUSH_CLIMB ? step : PUSH_CLIMB - step; // 0..16 up, then 1..16 down
				Vec3d target = exit.add(0.0, dy, 0.0);
				net.minecraft.util.math.Box box = mob.getBoundingBox().offset(target.subtract(mob.getPos()));
				if (box.minY < mob.getWorld().getBottomY()) {
					continue; // looks like room, but a flier under bedrock is stuck for good
				}
				if (mob.getWorld().isSpaceEmpty(mob, box) && safeLanding(mob, box)) {
					mob.stopRiding();
					mob.requestTeleport(target.x, target.y, target.z);
					mob.setVelocity(Vec3d.ZERO);
					mob.fallDistance = 0.0F; // a mid-fall host would carry its fall into the drop
					return Push.MOVED;
				}
			}
		}
		return Push.STUCK;
	}

	private static void loadColumns(World world, net.minecraft.util.math.Box box) {
		for (int cx = ChunkSectionPos.getSectionCoord(box.minX); cx <= ChunkSectionPos.getSectionCoord(box.maxX); cx++) {
			for (int cz = ChunkSectionPos.getSectionCoord(box.minZ); cz <= ChunkSectionPos.getSectionCoord(box.maxZ); cz++) {
				world.getChunk(cx, cz);
			}
		}
	}

	/**
	 * Landing from here won't kill the host. Lava below rules it out unless fire immune. Fliers stay up; others need ground
	 * or water within safe fall, or {@link #IMMUNE_DROP} if fall-immune.
	 */
	private static boolean safeLanding(MobEntity mob, net.minecraft.util.math.Box box) {
		World world = mob.getWorld();
		boolean burns = !mob.isFireImmune();
		if (burns && holds(world, box, FluidTags.LAVA)) {
			return false; // fluids don't collide, but it's lava
		}
		// profile too: on the connect tick the flying profile hasn't lifted it yet.
		if (mob.hasNoGravity() || mob instanceof FlyingEntity || mob instanceof Flutterer || MobProfiles.of(mob).movement().flies()) {
			return true;
		}
		// Short of the safe fall: float drift past it costs a heart.
		int drop = safeDrop(mob);
		for (int depth = 0; depth < drop; depth++) {
			net.minecraft.util.math.Box lower = box.offset(0.0, -depth - LANDING_PROBE, 0.0);
			boolean lava = holds(world, lower, FluidTags.LAVA);
			if (burns && lava) {
				return false;
			}
			if (lava || holds(world, lower, FluidTags.WATER) || !world.isSpaceEmpty(mob, lower)) {
				return true;
			}
		}
		return false;
	}

	/** fall height before damage. Not getSafeFallDistance, which with a target means how far the mob dares jump. */
	private static int safeDrop(MobEntity mob) {
		if (mob.getType().isIn(EntityTypeTags.FALL_DAMAGE_IMMUNE) || mob.getAttributeValue(EntityAttributes.FALL_DAMAGE_MULTIPLIER) <= 0.0) {
			return IMMUNE_DROP;
		}
		return Math.min(IMMUNE_DROP, MathHelper.ceil(mob.getAttributeValue(EntityAttributes.SAFE_FALL_DISTANCE)));
	}

	/** Any box block holds this fluid; wide hosts span columns. */
	private static boolean holds(World world, net.minecraft.util.math.Box box, TagKey<Fluid> fluid) {
		return BlockPos.stream(box.contract(1.0E-3)).anyMatch(pos -> world.getFluidState(pos).isIn(fluid));
	}

	private static Optional<Box> vanilla(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		ServerWorld overworld = server.getOverworld();
		BlockPos spawn = overworld.getSpawnPos();
		// asked at spawn so vanilla decides dimension, ops and radius.
		if (!server.isSpawnProtected(overworld, spawn, player)) {
			return Optional.empty();
		}
		int radius = server.getSpawnProtectionRadius();
		return Optional.of(new Box(spawn.getX() - radius, spawn.getZ() - radius, spawn.getX() + radius, spawn.getZ() + radius));
	}
}
