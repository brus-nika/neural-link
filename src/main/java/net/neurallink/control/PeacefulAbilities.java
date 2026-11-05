package net.neurallink.control;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.PandaEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.passive.SnifferEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.world.GameRules;
import net.neurallink.mixin.FoxEntityInvoker;
import net.neurallink.mixin.PufferfishEntityAccessor;
import net.neurallink.mixin.SquidEntityAccessor;

/** Calls vanilla logic wherever it exists. */
final class PeacefulAbilities {
	private static final int UNROLL_TICKS = 30;
	private static final double GRACE_RANGE = 16.0;
	private static final double PICKUP_RANGE = 2.0;
	private static final int FOX_DROP_DELAY = 40;
	private static final int SQUID_JET_TICKS = 6;
	/** vanilla drops seeds at +120 after scheduled tasks, so 120 would cut it off. */
	private static final int SNIFFER_DIG_TICKS = 121;
	private static final double COLLECT_RANGE = 1.5;

	static final Ability ARMADILLO_ROLL = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof ArmadilloEntity armadillo)) {
				return false;
			}
			if (armadillo.getState() == ArmadilloEntity.State.UNROLLING) {
				return false;
			}
			if (armadillo.isNotIdle()) {
				armadillo.setState(ArmadilloEntity.State.UNROLLING);
				context.state().schedule(UNROLL_TICKS, () -> {
					if (armadillo.getState() == ArmadilloEntity.State.UNROLLING) {
						armadillo.unroll();
					}
				});
			} else {
				armadillo.startRolling();
			}
			return true;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof ArmadilloEntity armadillo && armadillo.isNotIdle();
		}
	};

	static final Ability PUFF = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (context.mob() instanceof PufferfishEntity pufferfish) {
				((PufferfishEntityAccessor) pufferfish).neurallink$setDeflateTicks(0); // only InflateGoal resets it
				pufferfish.setPuffState(2);
				AbilitySupport.sound(context, SoundEvents.ENTITY_PUFFER_FISH_BLOW_UP);
				return true;
			}
			return false;
		}

		@Override
		public boolean active(MobEntity mob, ControlState state) {
			return mob instanceof PufferfishEntity pufferfish && pufferfish.getPuffState() > 0;
		}
	};

	/** Ink and dash on one key; the dash lasts a few ticks to beat the tentacle stroke. */
	static final Ability SQUID_JET = context -> {
		if (!(context.mob() instanceof SquidEntity squid)) {
			return false;
		}
		((SquidEntityAccessor) squid).neurallink$squirt();
		if (squid.isTouchingWater()) {
			context.state().jetTicks = SQUID_JET_TICKS;
		}
		return true;
	};

	static final Ability AXOLOTL_PLAY_DEAD = new ToggleAbility(context -> {
		context.state().frozen = true;
		((AxolotlEntity) context.mob()).setPlayingDead(true);
	}, context -> {
		context.state().frozen = false;
		((AxolotlEntity) context.mob()).setPlayingDead(false);
	}, (mob, state) -> mob instanceof AxolotlEntity axolotl && axolotl.isPlayingDead());

	static final Ability SWIM_DASH = context -> {
		AbilitySupport.dash(context, 1.0);
		return true;
	};

	static final Ability DOLPHIN_GRACE = context -> {
		MobEntity mob = context.mob();
		List<PlayerEntity> players = context.world().getEntitiesByClass(PlayerEntity.class, mob.getBoundingBox().expand(GRACE_RANGE), player -> !player.isSpectator());
		players.forEach(player -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 100)));
		return !players.isEmpty();
	};

	static final Ability PANDA_SNEEZE = context -> {
		if (!(context.mob() instanceof PandaEntity panda)) {
			return false;
		}
		panda.setSneezing(true);
		return true;
	};

	static final Ability PANDA_ROLL = context -> {
		if (!(context.mob() instanceof PandaEntity panda)) {
			return false;
		}
		panda.setPlaying(true);
		AbilitySupport.dash(context, 0.6);
		context.state().schedule(30, () -> panda.setPlaying(false));
		return true;
	};

	static final Ability FOX_POUNCE = context -> {
		if (!AbilitySupport.leap(context, 0.7, 0.5)) {
			return false;
		}
		AbilitySupport.strikeLater(context, 8, 3.0, true, target -> {});
		return true;
	};

	static final Ability FOX_ITEM = context -> {
		if (!(context.mob() instanceof FoxEntity fox)) {
			return false;
		}
		ItemStack held = fox.getEquippedStack(EquipmentSlot.MAINHAND);
		if (!held.isEmpty()) {
			ItemEntity dropped = fox.dropStack(context.world(), held.copy());
			if (dropped != null) {
				dropped.setPickupDelay(FOX_DROP_DELAY);
			}
			fox.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			return true;
		}
		if (!AbilitySupport.griefing(context)) {
			return false;
		}
		List<ItemEntity> items = context.world().getEntitiesByClass(ItemEntity.class, fox.getBoundingBox().expand(PICKUP_RANGE), item -> item.isAlive() && !item.cannotPickup());
		if (items.isEmpty()) {
			return false;
		}
		fox.equipStack(EquipmentSlot.MAINHAND, items.get(0).getStack().split(1));
		fox.updateDropChances(EquipmentSlot.MAINHAND);
		((FoxEntityInvoker) fox).neurallink$setEatingTime(0); // as vanilla looting does, or food goes down next tick
		if (items.get(0).getStack().isEmpty()) {
			items.get(0).discard();
		}
		return true;
	};

	static final Ability FROG_TONGUE = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof FrogEntity frog)) {
				return false;
			}
			LivingEntity target = AbilitySupport.lookedAt(context, 8.0);
			if (target == null || !FrogEntity.isValidFrogFood(target)) {
				return false;
			}
			frog.setFrogTarget(target);
			frog.setPose(EntityPose.USING_TONGUE);
			AbilitySupport.sound(context, SoundEvents.ENTITY_FROG_TONGUE);
			context.state().schedule(6, () -> {
				if (target.isAlive()) {
					frog.tryAttack(context.world(), target); // the froglight loot table wants the frog as killer
					if (!target.isAlive()) {
						target.remove(Entity.RemovalReason.KILLED);
					}
				}
				frog.clearFrogTarget();
				frog.setPose(EntityPose.STANDING);
				AbilitySupport.sound(context, SoundEvents.ENTITY_FROG_EAT);
			});
			return true;
		}

		@Override
		public void sessionEnded(AbilityContext context) {
			if (context.mob() instanceof FrogEntity frog && frog.getPose() == EntityPose.USING_TONGUE) {
				frog.clearFrogTarget();
				frog.setPose(EntityPose.STANDING);
			}
		}
	};

	static final Ability FROG_LONG_JUMP = context -> {
		if (!AbilitySupport.leap(context, 0.9, 0.6)) {
			return false;
		}
		AbilitySupport.sound(context, SoundEvents.ENTITY_FROG_LONG_JUMP);
		return true;
	};

	static final Ability CAMEL_DASH = context -> {
		if (!(context.mob() instanceof CamelEntity camel)) {
			return false;
		}
		camel.setDashing(true);
		AbilitySupport.dash(context, 1.3);
		AbilitySupport.sound(context, SoundEvents.ENTITY_CAMEL_DASH);
		context.state().schedule(12, () -> camel.setDashing(false));
		return true;
	};

	static final Ability CAMEL_SIT = new ToggleAbility(context -> {
		context.state().frozen = true;
		((CamelEntity) context.mob()).startSitting();
	}, context -> {
		context.state().frozen = false;
		((CamelEntity) context.mob()).startStanding();
	}, (mob, state) -> mob instanceof CamelEntity camel && camel.isSitting());

	static final Ability SNIFFER_DIG = new Ability() {
		@Override
		public boolean press(AbilityContext context) {
			if (!(context.mob() instanceof SnifferEntity sniffer)) {
				return false;
			}
			sniffer.startState(SnifferEntity.State.DIGGING);
			context.state().schedule(SNIFFER_DIG_TICKS, () -> sniffer.startState(SnifferEntity.State.IDLING));
			return true;
		}

		@Override
		public void sessionEnded(AbilityContext context) {
			if (context.mob() instanceof SnifferEntity sniffer && sniffer.isDiggingOrSearching()) {
				sniffer.startState(SnifferEntity.State.IDLING);
			}
		}
	};

	static final Ability ALLAY_COLLECT = new ToggleAbility(context -> context.state().collecting = true, context -> {
		context.state().collecting = false;
		Entity.RemovalReason removal = context.mob().getRemovalReason();
		// a portaled mob took its stacks; dropping them would duplicate.
		if (context.mob() instanceof AllayEntity allay && (removal == null || removal.shouldDestroy())) {
			allay.getInventory().clearToList().forEach(stack -> allay.dropStack(context.world(), stack));
			ItemStack collected = context.state().collectedHand;
			if (allay.getMainHandStack() == collected) {
				allay.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
				allay.dropStack(context.world(), collected);
			}
		}
		context.state().collectedHand = null;
	}, (mob, state) -> state.collecting);

	private PeacefulAbilities() {
	}

	static void collectItems(AllayEntity allay, ControlState state) {
		ServerWorld world = (ServerWorld) allay.getWorld();
		if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			return;
		}
		for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, allay.getBoundingBox().expand(COLLECT_RANGE), candidate -> candidate.isAlive() && !candidate.cannotPickup())) {
			ItemStack rest = allay.getInventory().addStack(item.getStack());
			if (rest.isEmpty()) {
				item.discard();
			} else if (rest.getCount() != item.getStack().getCount()) {
				item.setStack(rest); // a new stack marks the tracker dirty every tick
			}
			if (allay.getMainHandStack().isEmpty()) {
				state.collectedHand = allay.getInventory().removeStack(0, 1);
				allay.setStackInHand(Hand.MAIN_HAND, state.collectedHand);
			}
		}
	}
}
