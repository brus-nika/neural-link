package net.neurallink.control;

import java.util.List;

import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.neurallink.mixin.WitherEntityAccessor;

/** vanilla numbers, behaviours built from vanilla projectiles and animations. */
final class BossAbilities {
	private static final int BOOM_CHARGE_TICKS = 34;
	private static final double BOOM_RANGE = 15.0;
	private static final float BOOM_DAMAGE = 10.0F;
	private static final int DIG_TICKS = 100;
	private static final int EMERGE_TICKS = 134;
	private static final double SKULL_MUZZLE = 2.0;
	private static final double RUSH_SPEED = 1.2;
	private static final int RUSH_TICKS = 6;
	private static final float RUSH_DAMAGE = 8.0F;
	private static final int RUSH_WITHER_TICKS = 200;
	private static final double FIREBALL_MUZZLE = 3.0;
	private static final double RAM_SPEED = 2.0;
	private static final double WING_RANGE = 10.0;
	private static final double WING_PUSH = 2.5;
	private static final double WING_LIFT = 0.8;
	private static final float WING_DAMAGE = 5.0F;
	private static final float WITHER_CHARGE_TICKS = 220.0F;
	private static final float WITHER_BLAST_POWER = 7.0F;


	/** Lands after the vanilla charge-up where the operator looks then. */
	static final Ability WARDEN_BOOM = context -> {
		if (context.state().frozen || !(context.mob() instanceof WardenEntity warden)) {
			return false;
		}
		context.world().sendEntityStatus(warden, EntityStatuses.SONIC_BOOM);
		warden.playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
		context.state().schedule(BOOM_CHARGE_TICKS, () -> {
			if (warden.isAlive() && !context.state().frozen) {
				boom(context, warden);
			}
		});
		return true;
	};

	/** vanilla removes a burrowed Warden, so it stays invisible and invulnerable instead. */
	static final Ability WARDEN_BURROW = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof WardenEntity warden)) {
				return false;
			}
			if (context.state().frozen) {
				warden.setInvisible(false);
				warden.setPose(EntityPose.EMERGING);
				warden.playSound(SoundEvents.ENTITY_WARDEN_EMERGE, 5.0F, 1.0F);
				context.state().schedule(EMERGE_TICKS, () -> stand(context, warden));
			} else {
				AbilitySupport.freeze(context, true);
				warden.setPose(EntityPose.DIGGING);
				warden.playSound(SoundEvents.ENTITY_WARDEN_DIG, 5.0F, 1.0F);
				context.state().schedule(DIG_TICKS, () -> warden.setInvisible(warden.isInPose(EntityPose.DIGGING)));
			}
			return true;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof WardenEntity && state.frozen;
		}

		@Override
		public void sessionEnded(AbilityContext context) {
			if (context.mob() instanceof WardenEntity warden && context.state().frozen) {
				warden.setInvisible(false);
				stand(context, warden);
			}
		}
	};

	static final Ability WITHER_BLACK_SKULL = context -> skull(context, false);

	static final Ability WITHER_BLUE_SKULL = context -> skull(context, true);

	/** The Wither has no attack attribute so damage is set here. */
	static final Ability WITHER_RUSH = context -> {
		MobEntity mob = context.mob();
		AbilitySupport.dash(context, RUSH_SPEED);
		AbilitySupport.sound(context, SoundEvents.ENTITY_WITHER_AMBIENT);
		context.state().schedule(RUSH_TICKS, () -> {
			for (LivingEntity target : targets(context, 1.0)) {
				target.damage(context.world(), mob.getDamageSources().mobAttack(mob), RUSH_DAMAGE);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, RUSH_WITHER_TICKS, 1), mob);
			}
		});
		return true;
	};

	static final Ability DRAGON_FIREBALL = context -> {
		if (!(context.mob() instanceof EnderDragonEntity dragon)) {
			return false;
		}
		Vec3d look = context.look();
		DragonFireballEntity fireball = new DragonFireballEntity(context.world(), dragon, look);
		fireball.setPosition(dragon.head.getPos().add(look.multiply(FIREBALL_MUZZLE)));
		context.world().spawnEntity(fireball);
		context.world().syncWorldEvent(null, WorldEvents.ENDER_DRAGON_SHOOTS, dragon.getBlockPos(), 0);
		return true;
	};

	/** head and neck hit everything they touch, like vanilla. */
	static final Ability DRAGON_RAM = context -> {
		AbilitySupport.dash(context, RAM_SPEED);
		AbilitySupport.sound(context, SoundEvents.ENTITY_ENDER_DRAGON_GROWL);
		return true;
	};

	static final Ability DRAGON_WINGS = context -> {
		MobEntity mob = context.mob();
		AbilitySupport.sound(context, SoundEvents.ENTITY_ENDER_DRAGON_FLAP);
		for (LivingEntity target : targets(context, WING_RANGE)) {
			Vec3d away = target.getPos().subtract(mob.getPos()).multiply(1.0, 0.0, 1.0);
			Vec3d push = away.lengthSquared() > 0.0 ? away.normalize().multiply(WING_PUSH) : Vec3d.ZERO;
			target.addVelocity(push.x, WING_LIFT, push.z);
			target.velocityModified = true;
			target.damage(context.world(), mob.getDamageSources().mobAttack(mob), WING_DAMAGE);
		}
		return true;
	};

	private BossAbilities() {
	}

	private static void boom(AbilityContext context, WardenEntity warden) {
		Vec3d chest = warden.getPos().add(warden.getAttachments().getPoint(EntityAttachmentType.WARDEN_CHEST, 0, warden.getYaw()));
		LivingEntity target = AbilitySupport.lookedAt(context, BOOM_RANGE);
		Vec3d line = target != null ? target.getEyePos().subtract(chest) : context.look().multiply(BOOM_RANGE);
		Vec3d direction = line.normalize();
		for (int step = 1; step < MathHelper.floor(line.length()) + 2; step++) {
			Vec3d point = chest.add(direction.multiply(step));
			context.world().spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		warden.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3.0F, 1.0F);
		if (target != null) {
			DamageSource source = context.world().getDamageSources().sonicBoom(warden);
			if (target.damage(context.world(), source, BOOM_DAMAGE)) {
				double kept = 1.0 - target.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE);
				target.addVelocity(direction.x * 2.5 * kept, direction.y * 0.5 * kept, direction.z * 2.5 * kept);
			}
		}
	}

	private static void stand(AbilityContext context, WardenEntity warden) {
		warden.setPose(EntityPose.STANDING);
		AbilitySupport.freeze(context, false);
	}

	private static boolean skull(AbilityContext context, boolean charged) {
		MobEntity mob = context.mob();
		WitherSkullEntity skull = new WitherSkullEntity(context.world(), mob, context.look());
		skull.setCharged(charged);
		skull.setPosition(context.eyes().add(context.look().multiply(SKULL_MUZZLE)));
		context.world().spawnEntity(skull);
		context.world().syncWorldEvent(null, WorldEvents.WITHER_SHOOTS, mob.getBlockPos(), 0);
		return true;
	}

	private static List<LivingEntity> targets(AbilityContext context, double range) {
		return context.world().getEntitiesByClass(LivingEntity.class, context.mob().getBoundingBox().expand(range), entity -> AbilitySupport.canTarget(context, entity));
	}

	/** Does mobTick's work, skipped in a session. Stale head targets would pull tickMovement. */
	static void tickWither(WitherEntity wither) {
		for (int head = 0; head < 3; head++) {
			wither.setTrackedEntityId(head, 0);
		}
		if (wither.getInvulnerableTimer() > 0) {
			charge(wither);
			return;
		}
		if (wither.age % 20 == 0) {
			wither.heal(1.0F);
		}
		((WitherEntityAccessor) wither).neurallink$bossBar().setPercent(wither.getHealth() / wither.getMaxHealth());
	}

	private static void charge(WitherEntity wither) {
		int left = wither.getInvulnerableTimer() - 1;
		((WitherEntityAccessor) wither).neurallink$bossBar().setPercent(1.0F - left / WITHER_CHARGE_TICKS);
		if (left <= 0) {
			wither.getWorld().createExplosion(wither, wither.getX(), wither.getEyeY(), wither.getZ(), WITHER_BLAST_POWER, false, World.ExplosionSourceType.MOB);
			if (!wither.isSilent()) {
				wither.getWorld().syncGlobalEvent(WorldEvents.WITHER_SPAWNS, wither.getBlockPos(), 0);
			}
		}
		wither.setInvulTimer(left);
		if (wither.age % 10 == 0) {
			wither.heal(10.0F);
		}
	}
}
