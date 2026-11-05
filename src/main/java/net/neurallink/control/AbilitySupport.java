package net.neurallink.control;

import java.util.function.Consumer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.neurallink.entity.BodyEntity;

final class AbilitySupport {
	private static final double AIM_CONE_COS = Math.cos(Math.toRadians(8.0));

	private AbilitySupport() {
	}

	static void launch(AbilityContext context, ProjectileEntity projectile, ItemStack ammo, float speed) {
		projectile.setPosition(context.eyes().add(context.look()));
		projectile.setVelocity(context.look().x, context.look().y, context.look().z, speed, 1.0F);
		context.world().spawnEntity(projectile);
		projectile.triggerProjectileSpawned(context.world(), ammo);
	}

	static void sound(AbilityContext context, SoundEvent sound) {
		MobEntity mob = context.mob();
		context.world().playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, mob.getSoundCategory(), 1.0F, 1.0F);
	}

	static boolean leap(AbilityContext context, double forward, double up) {
		if (!context.mob().isOnGround()) {
			return false;
		}
		context.mob().addVelocity(context.look().multiply(forward).add(0.0, up, 0.0));
		context.mob().velocityModified = true;
		return true;
	}

	/** Nearest to the crosshair in a narrow cone, so moving targets need no exact aim. */
	static LivingEntity lookedAt(AbilityContext context, double range) {
		MobEntity mob = context.mob();
		Vec3d start = context.eyes();
		Vec3d look = context.look();
		LivingEntity best = null;
		double bestDot = AIM_CONE_COS;
		for (LivingEntity candidate : context.world().getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(range), entity -> canTarget(context, entity))) {
			Vec3d toTarget = candidate.getBoundingBox().getCenter().subtract(start);
			double dot = toTarget.normalize().dotProduct(look);
			if (dot > bestDot && toTarget.lengthSquared() <= range * range && mob.canSee(candidate)) {
				bestDot = dot;
				best = candidate;
			}
		}
		return best;
	}

	static boolean canTarget(AbilityContext context, Entity entity) {
		return entity instanceof LivingEntity && entity.isAlive() && entity != context.mob() && !entity.isSpectator()
				&& !(entity instanceof BodyEntity body && body.getOwner().filter(context.operator().getUuid()::equals).isPresent());
	}

	/** exact aim stopped by blocks; the operator's body can be hit. */
	static LivingEntity aimed(AbilityContext context, double range) {
		Vec3d start = context.eyes();
		Vec3d end = start.add(context.look().multiply(range));
		MobEntity mob = context.mob();
		HitResult blocks = context.world().raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mob));
		if (blocks.getType() != HitResult.Type.MISS) {
			end = blocks.getPos();
		}
		Box box = mob.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityCollision(context.world(), mob, start, end, box,
				entity -> entity instanceof LivingEntity && entity.isAlive() && entity != mob && !entity.isSpectator());
		return hit == null ? null : (LivingEntity) hit.getEntity();
	}

	static void freeze(AbilityContext context, boolean frozen) {
		context.state().frozen = frozen;
		context.state().shielded = frozen;
	}

	static void dash(AbilityContext context, double speed) {
		context.mob().setVelocity(context.look().multiply(speed));
		context.mob().velocityModified = true;
	}

	/** Target picked when the blow lands, not on press. */
	static void strikeLater(AbilityContext context, int delay, double range, boolean swing, Consumer<LivingEntity> after) {
		MobEntity mob = context.mob();
		context.state().schedule(delay, () -> {
			LivingEntity target = aimed(context, range);
			if (target != null) {
				if (swing) {
					mob.swingHand(Hand.MAIN_HAND);
				}
				mob.tryAttack(context.world(), target);
				after.accept(target);
			}
		});
	}

	static boolean griefing(AbilityContext context) {
		return context.world().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
	}

	static BlockHitResult raycast(AbilityContext context, double range, RaycastContext.ShapeType shape) {
		Vec3d start = context.eyes();
		return context.world().raycast(new RaycastContext(start, start.add(context.look().multiply(range)), shape, RaycastContext.FluidHandling.NONE, context.mob()));
	}
}
