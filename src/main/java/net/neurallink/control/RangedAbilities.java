package net.neurallink.control;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.projectile.BreezeWindChargeEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.neurallink.mixin.AbstractSkeletonEntityInvoker;
import net.neurallink.mixin.BlazeEntityInvoker;
import net.neurallink.mixin.GuardianEntityInvoker;

/** Vanilla projectiles and beams. */
final class RangedAbilities {
	private static final int FULL_DRAW_TICKS = 20;
	private static final int MIN_DRAW_TICKS = 5;
	private static final double LASER_RANGE = 15.0;
	private static final int LASER_WARMUP_TICKS = 40;
	private static final float LASER_DAMAGE = 6.0F;
	private static final int FATIGUE_TICKS = 1200;
	private static final double BLAZE_LIFT = 0.45;
	private static final int CROSSBOW_MARGIN_TICKS = 2;
	private static final float CROSSBOW_ARROW_SPEED = 3.15F;
	private static final float CROSSBOW_FIREWORK_SPEED = 1.6F;

	static final Ability BOW = new Ability() {
		@Override
		public boolean charges() {
			return true;
		}

		@Override
		public boolean press(AbilityContext context) {
			context.mob().setAttacking(true);
			context.mob().setCurrentHand(Hand.MAIN_HAND);
			return true;
		}

		@Override
		public boolean release(AbilityContext context, int heldTicks) {
			MobEntity mob = context.mob();
			mob.clearActiveItem();
			mob.setAttacking(false);
			if (heldTicks < MIN_DRAW_TICKS) {
				return false;
			}
			float pull = Math.min(1.0F, (float) heldTicks / FULL_DRAW_TICKS);
			ItemStack bow = mob.getMainHandStack().isOf(Items.BOW) ? mob.getMainHandStack() : new ItemStack(Items.BOW);
			ItemStack ammo = mob.getProjectileType(bow);
			ItemStack arrowStack = ammo.isEmpty() ? new ItemStack(Items.ARROW) : ammo;
			// skeletons build arrows so strays slow and bogged poison.
			PersistentProjectileEntity arrow = mob instanceof AbstractSkeletonEntity skeleton
					? ((AbstractSkeletonEntityInvoker) skeleton).neurallink$createArrow(arrowStack, pull, bow)
					: ProjectileUtil.createArrowProjectile(mob, arrowStack, pull, bow);
			AbilitySupport.launch(context, arrow, arrowStack, 1.6F + 1.4F * pull);
			AbilitySupport.sound(context, SoundEvents.ENTITY_SKELETON_SHOOT);
			return true;
		}
	};

	/** Like a player: hold to charge, release early to cancel, click to fire. */
	static final Ability CROSSBOW = new Ability() {
		@Override
		public boolean charges() {
			return true;
		}

		@Override
		public boolean press(AbilityContext context) {
			MobEntity mob = context.mob();
			ItemStack crossbow = mob.getMainHandStack();
			if (CrossbowItem.isCharged(crossbow)) {
				ChargedProjectilesComponent loaded = crossbow.getOrDefault(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);
				float speed = loaded.contains(Items.FIREWORK_ROCKET) ? CROSSBOW_FIREWORK_SPEED : CROSSBOW_ARROW_SPEED;
				// shootAll aims by the head, which Movement turns next tick.
				mob.setHeadYaw(context.state().yaw);
				mob.setPitch(context.state().pitch);
				((CrossbowItem) crossbow.getItem()).shootAll(context.world(), mob, Hand.MAIN_HAND, crossbow, speed, 1.0F, null);
				context.state().crossbowShot = true;
				return true;
			}
			int pull = CrossbowItem.getPullTime(crossbow, mob);
			mob.setCurrentHand(Hand.MAIN_HAND);
			charging(mob, true);
			context.state().schedule(pull + CROSSBOW_MARGIN_TICKS, () -> {
				if (mob.isUsingItem() && mob.getItemUseTime() >= pull) {
					mob.stopUsingItem(); // the crossbow loads itself when pulling stops
					charging(mob, false);
				}
			});
			return true;
		}

		@Override
		public boolean release(AbilityContext context, int heldTicks) {
			if (context.mob().isUsingItem()) {
				context.mob().clearActiveItem();
			}
			charging(context.mob(), false);
			boolean shot = context.state().crossbowShot;
			context.state().crossbowShot = false;
			return shot;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return CrossbowItem.isCharged(mob.getMainHandStack());
		}
	};

	/** as in vanilla an enchanted copy flies, so throws are unlimited. */
	static final Ability TRIDENT = context -> {
		ItemStack thrown = context.mob().getMainHandStack().copyWithCount(1);
		TridentEntity trident = new TridentEntity(context.world(), context.mob(), thrown);
		trident.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		AbilitySupport.launch(context, trident, thrown, 2.5F);
		AbilitySupport.sound(context, SoundEvents.ENTITY_DROWNED_SHOOT);
		return true;
	};

	static final Ability SNOWBALL = context -> {
		SnowballEntity snowball = new SnowballEntity(context.world(), context.mob(), new ItemStack(Items.SNOWBALL));
		snowball.setVelocity(context.look().x, context.look().y, context.look().z, 1.6F, 1.0F);
		context.world().spawnEntity(snowball);
		AbilitySupport.sound(context, SoundEvents.ENTITY_SNOW_GOLEM_SHOOT);
		return true;
	};

	static final Ability LLAMA_SPIT = context -> {
		if (!(context.mob() instanceof LlamaEntity llama)) {
			return false;
		}
		LlamaSpitEntity spit = new LlamaSpitEntity(context.world(), llama);
		spit.setVelocity(context.look().x, context.look().y, context.look().z, 1.5F, 1.0F);
		context.world().spawnEntity(spit);
		AbilitySupport.sound(context, SoundEvents.ENTITY_LLAMA_SPIT);
		return true;
	};

	static final Ability GHAST_FIREBALL = context -> {
		FireballEntity fireball = new FireballEntity(context.world(), context.mob(), context.look(), 1);
		fireball.setPosition(context.eyes().add(context.look().multiply(2.0)));
		context.world().spawnEntity(fireball);
		AbilitySupport.sound(context, SoundEvents.ENTITY_GHAST_SHOOT);
		return true;
	};

	static final Ability BLAZE_VOLLEY = context -> {
		for (int shot = 0; shot < 3; shot++) {
			context.state().schedule(shot * 6, () -> {
				SmallFireballEntity fireball = new SmallFireballEntity(context.world(), context.mob(), context.look());
				fireball.setPosition(context.eyes().add(context.look()));
				context.world().spawnEntity(fireball);
				AbilitySupport.sound(context, SoundEvents.ENTITY_BLAZE_SHOOT);
			});
		}
		return true;
	};

	/** Lifts and lights up at once so toggling is noticeable. */
	static final Ability BLAZE_FLIGHT = new ToggleAbility(context -> {
		context.state().flying = true;
		context.mob().setVelocity(context.mob().getVelocity().add(0.0, BLAZE_LIFT, 0.0));
		context.mob().velocityModified = true;
		blazeFire(context, true);
		AbilitySupport.sound(context, SoundEvents.ENTITY_BLAZE_BURN);
	}, context -> {
		context.state().flying = false;
		blazeFire(context, false);
	}, (mob, state) -> state.flying);

	static final Ability WIND_CHARGE = context -> {
		if (!(context.mob() instanceof BreezeEntity breeze)) {
			return false;
		}
		BreezeWindChargeEntity charge = new BreezeWindChargeEntity(breeze, context.world());
		charge.setPosition(context.eyes().add(context.look()));
		charge.setVelocity(context.look().x, context.look().y, context.look().z, 1.0F, 1.0F);
		context.world().spawnEntity(charge);
		AbilitySupport.sound(context, SoundEvents.ENTITY_BREEZE_SHOOT);
		return true;
	};

	static final Ability HIGH_JUMP = context -> {
		if (!AbilitySupport.leap(context, 0.5, 1.0)) {
			return false;
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_BREEZE_JUMP);
		return true;
	};

	static final Ability GUARDIAN_LASER = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			LivingEntity target = AbilitySupport.lookedAt(context, LASER_RANGE);
			if (target == null || !(context.mob() instanceof GuardianEntity guardian)) {
				return false;
			}
			((GuardianEntityInvoker) guardian).neurallink$setBeamTarget(target.getId());
			if (!guardian.isSilent()) {
				context.world().sendEntityStatus(guardian, EntityStatuses.PLAY_GUARDIAN_ATTACK_SOUND);
			}
			context.state().schedule(LASER_WARMUP_TICKS, () -> {
				((GuardianEntityInvoker) guardian).neurallink$setBeamTarget(0);
				if (target.isAlive() && guardian.isAlive() && guardian.canSee(target) && guardian.squaredDistanceTo(target) <= LASER_RANGE * LASER_RANGE) {
					target.damage(context.world(), guardian.getDamageSources().indirectMagic(guardian, guardian), LASER_DAMAGE);
				}
			});
			return true;
		}

		@Override
		public void sessionEnded(AbilityContext context) {
			if (context.mob() instanceof GuardianEntity guardian) {
				((GuardianEntityInvoker) guardian).neurallink$setBeamTarget(0);
			}
		}
	};

	/** spikes out: hits bounce back. Vanilla stores an inverted flag. */
	static final Ability GUARDIAN_SPIKES = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (context.mob() instanceof GuardianEntity guardian) {
				((GuardianEntityInvoker) guardian).neurallink$setSpikesRetracted(!guardian.areSpikesRetracted());
				return true;
			}
			return false;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof GuardianEntity guardian && !guardian.areSpikesRetracted();
		}
	};

	static final Ability ELDER_FATIGUE = context -> {
		if (!(context.mob() instanceof ElderGuardianEntity elder)) {
			return false;
		}
		StatusEffectInstance effect = new StatusEffectInstance(StatusEffects.MINING_FATIGUE, FATIGUE_TICKS, 2);
		StatusEffectUtil.addEffectToPlayersWithinDistance(context.world(), elder, elder.getPos(), 50.0, effect, FATIGUE_TICKS);
		AbilitySupport.sound(context, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE);
		return true;
	};

	private RangedAbilities() {
	}

	private static void charging(MobEntity mob, boolean charging) {
		if (mob instanceof CrossbowUser user) {
			user.setCharging(charging);
		}
	}

	private static void blazeFire(AbilityContext context, boolean active) {
		if (context.mob() instanceof BlazeEntity blaze) {
			((BlazeEntityInvoker) blaze).neurallink$setFireActive(active);
		}
	}
}
