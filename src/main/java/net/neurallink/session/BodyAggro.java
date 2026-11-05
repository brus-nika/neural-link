package net.neurallink.session;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neurallink.chip.ChipLogic;
import net.neurallink.entity.BodyEntity;

/**
 * Mobs target only players and drop spectators, so we hand the body their aggro: chasers switch to it and monsters notice it
 * like a player.
 */
final class BodyAggro {
	private static final double BODY_AGGRO_RANGE = 16.0;
	private static final double BODY_CHASE_RANGE = 48.0;

	private BodyAggro() {
	}

	static void attract(ControlSession session) {
		if (!session.record.gameMode().isSurvivalLike()) {
			return; // mobs ignore a creative player and his body stands in for him
		}
		BodyEntity body = session.body;
		ServerPlayerEntity player = session.player;
		for (MobEntity mob : body.getWorld().getEntitiesByClass(MobEntity.class, body.getBoundingBox().expand(BODY_CHASE_RANGE), candidate -> canHunt(candidate, session))) {
			boolean hunting = mob.getTarget() == player || mob.getTarget() == null && mob instanceof Angerable angry && player.getUuid().equals(angry.getAngryAt());
			boolean notices = mob instanceof Monster && !(mob instanceof Angerable) && mob.getTarget() == null && mob.squaredDistanceTo(body) <= BODY_AGGRO_RANGE * BODY_AGGRO_RANGE && mob.canSee(body);
			if (hunting || notices) {
				retarget(mob, body);
			}
		}
	}

	/** vexes of any of his hosts count, since after a switch their evoker isn't the host anymore. */
	private static boolean canHunt(MobEntity mob, ControlSession session) {
		return !(mob instanceof BodyEntity) && !SessionManager.isControlled(mob) && !ChipLogic.protects(mob, session.player)
				&& !(mob instanceof VexEntity vex && vex.getOwner() instanceof MobEntity owner && (owner == session.mob || ChipLogic.protects(owner, session.player)));
	}

	/** Brain mobs keep the target in memory, not the field. */
	private static void retarget(MobEntity mob, LivingEntity target) {
		mob.setTarget(target);
		mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, target); // no-op without a brain
	}

	static void release(ControlSession session) {
		BodyEntity body = session.body;
		body.getWorld().getEntitiesByClass(MobEntity.class, body.getBoundingBox().expand(BODY_CHASE_RANGE), mob -> mob.getTarget() == body)
				.forEach(mob -> retarget(mob, session.player));
	}
}
