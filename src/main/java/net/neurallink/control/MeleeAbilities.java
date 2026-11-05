package net.neurallink.control;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreakingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

/** Attack effects come from the mob's own tryAttack. */
final class MeleeAbilities {
	private static final int ATTACK_POSE_TICKS = 10;
	private static final double ROAR_RANGE = 4.0;
	private static final float ROAR_DAMAGE = 6.0F;
	private static final double ROAR_PUSH = 4.0;
	private static final int ROAR_PARTICLES = 40;

	static final Ability MELEE = context -> {
		if (context.state().frozen) {
			return false;
		}
		MobEntity mob = context.mob();
		mob.swingHand(Hand.MAIN_HAND);
		mob.setAttacking(true);
		context.state().attackPoseTicks = ATTACK_POSE_TICKS;
		LivingEntity target = AbilitySupport.aimed(context, 2.0 + mob.getWidth());
		if (target != null) {
			mob.tryAttack(context.world(), target);
		}
		return true;
	};

	/** only StingGoal reads hasStung and goals don't run in a session. */
	static final Ability BEE_STING = context -> !(context.mob() instanceof BeeEntity bee && bee.hasStung()) && MELEE.press(context);

	static final Ability CREEPER_FUSE = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof CreeperEntity creeper) || creeper.isIgnited()) {
				return false;
			}
			creeper.ignite();
			return true;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof CreeperEntity creeper && creeper.isIgnited();
		}
	};

	static final Ability GOAT_RAM = context -> {
		AbilitySupport.dash(context, 1.2);
		AbilitySupport.sound(context, SoundEvents.ENTITY_GOAT_PREPARE_RAM);
		AbilitySupport.strikeLater(context, 6, 3.0, false, target -> {
			target.takeKnockback(2.0, -context.look().x, -context.look().z);
			AbilitySupport.sound(context, SoundEvents.ENTITY_GOAT_RAM_IMPACT);
		});
		return true;
	};

	static final Ability GOAT_JUMP = context -> {
		if (!AbilitySupport.leap(context, 0.3, 0.8)) {
			return false;
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_GOAT_LONG_JUMP);
		return true;
	};

	/** Vanilla numbers without the stun; the push reaches players too. */
	static final Ability RAVAGER_ROAR = context -> {
		if (!(context.mob() instanceof RavagerEntity ravager)) {
			return false;
		}
		ServerWorld world = context.world();
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, ravager.getBoundingBox().expand(ROAR_RANGE), entity -> AbilitySupport.canTarget(context, entity))) {
			if (!(target instanceof IllagerEntity)) {
				target.damage(world, ravager.getDamageSources().mobAttack(ravager), ROAR_DAMAGE);
			}
			double x = target.getX() - ravager.getX();
			double z = target.getZ() - ravager.getZ();
			double distance = Math.max(x * x + z * z, 0.001);
			target.addVelocity(x / distance * ROAR_PUSH, 0.2, z / distance * ROAR_PUSH);
			target.velocityModified = true;
		}
		Vec3d center = ravager.getBoundingBox().getCenter();
		world.spawnParticles(ParticleTypes.POOF, center.x, center.y, center.z, ROAR_PARTICLES, 0.0, 0.0, 0.0, 0.2);
		ravager.emitGameEvent(GameEvent.ENTITY_ACTION);
		AbilitySupport.sound(context, SoundEvents.ENTITY_RAVAGER_ROAR);
		return true;
	};

	static final Ability POLAR_STAND = new ToggleAbility(context -> ((PolarBearEntity) context.mob()).setWarning(true), context -> ((PolarBearEntity) context.mob()).setWarning(false),
			(mob, state) -> mob instanceof PolarBearEntity bear && bear.isWarning());

	static final Ability PHANTOM_DIVE = context -> {
		context.state().glideSpeed = Movement.GLIDE_MAX_SPEED;
		AbilitySupport.strikeLater(context, 10, 4.0, true, target -> {});
		return true;
	};

	static final Ability SLIME_STRIKE = context -> {
		MobEntity mob = context.mob();
		if (!mob.isOnGround() || !(mob instanceof SlimeEntity slime)) {
			return false;
		}
		mob.jump();
		mob.addVelocity(context.look().multiply(0.3 + 0.08 * slime.getSize()));
		mob.velocityModified = true;
		context.state().schedule(6, () -> {
			for (LivingEntity target : context.world().getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(0.5), entity -> AbilitySupport.canTarget(context, entity))) {
				mob.tryAttack(context.world(), target);
			}
		});
		return true;
	};

	static final Ability CREAKING_FREEZE = new ToggleAbility(context -> AbilitySupport.freeze(context, true), context -> AbilitySupport.freeze(context, false), (mob, state) -> mob instanceof CreakingEntity && state.frozen);

	private MeleeAbilities() {
	}
}
