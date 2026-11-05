package net.neurallink.control;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.CamelEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.neurallink.mixin.AbstractPiglinEntityAccessor;
import net.neurallink.mixin.HoglinEntityAccessor;
import net.neurallink.network.HudStatePayload;
import net.neurallink.network.VisorEventPayload;

/** The server checks cooldowns, the client only sends keys. */
public final class Control {
	private static final int BEE_LIFE_AFTER_STING = 600;
	private static final int BEE_WATER_TICKS = 20;
	private static final int ZOMBIFY_AFTER = 300;
	/** custom entity status for illusioner copies, above vanilla ones. */
	public static final byte MIRROR_STATUS = 100;

	private Control() {
	}

	public static boolean vertical(MobEntity mob, ControlState state) {
		return !state.frozen && MobProfiles.of(mob).movement().vertical(mob, state);
	}

	/** Runs instead of the mob's AI. */
	public static void tick(MobEntity mob, ControlState state) {
		if (state.sneak && mob.hasVehicle()) {
			mob.stopRiding(); // boats and minecarts seat any mob and nothing gets it out
		}
		if (state.frozen && mob instanceof CamelEntity camel && !camel.isSitting()) {
			state.frozen = false; // water and damage stand a camel up
		}
		MobProfile profile = MobProfiles.of(mob);
		if (profile.movement().flies()) {
			mob.setNoGravity(true);
		}
		if (state.frozen) {
			Movement.hold(mob, state);
		} else {
			profile.movement().apply(mob, state);
		}
		if (state.attackPoseTicks > 0 && --state.attackPoseTicks == 0) {
			mob.setAttacking(false);
		}
		if (state.collecting && mob instanceof AllayEntity allay) {
			PeacefulAbilities.collectItems(allay, state);
		}
		if (mob instanceof BeeEntity bee) {
			state.waterTicks = bee.isInsideWaterOrBubbleColumn() ? state.waterTicks + 1 : 0;
			if (state.waterTicks > BEE_WATER_TICKS) {
				bee.damage((ServerWorld) bee.getWorld(), bee.getDamageSources().drown(), 1.0F); // also vanilla mobTick, skipped under control
			}
		}
		if (mob instanceof BeeEntity bee && bee.hasStung() && ++state.stungTicks > BEE_LIFE_AFTER_STING) {
			bee.kill((ServerWorld) bee.getWorld()); // its AI normally counts this
		}
		tickZombification(mob);
	}

	/** Vanilla does this in mobTick; the vanilla field lets a released mob continue. */
	private static void tickZombification(MobEntity mob) {
		if (mob instanceof AbstractPiglinEntity piglin) {
			AbstractPiglinEntityAccessor accessor = (AbstractPiglinEntityAccessor) piglin;
			piglin.setTimeInOverworld(piglin.shouldZombify() ? accessor.neurallink$timeInOverworld() + 1 : 0);
			if (accessor.neurallink$timeInOverworld() > ZOMBIFY_AFTER) {
				accessor.neurallink$playZombificationSound();
				accessor.neurallink$zombify((ServerWorld) piglin.getWorld());
			}
		} else if (mob instanceof HoglinEntity hoglin) {
			HoglinEntityAccessor accessor = (HoglinEntityAccessor) hoglin;
			hoglin.setTimeInOverworld(hoglin.canConvert() ? accessor.neurallink$timeInOverworld() + 1 : 0);
			if (accessor.neurallink$timeInOverworld() > ZOMBIFY_AFTER) {
				hoglin.playSound(SoundEvents.ENTITY_HOGLIN_CONVERTED_TO_ZOMBIFIED);
				accessor.neurallink$zombify();
			}
		}
	}

	public static void sessionEnded(ServerPlayerEntity operator, MobEntity mob, ControlState state) {
		ServerWorld world = (ServerWorld) mob.getWorld();
		AbilityContext context = new AbilityContext(operator, mob, world, state);
		for (AbilitySlot slot : MobProfiles.of(mob).slots()) {
			if (slot != null) {
				slot.ability().sessionEnded(context);
			}
		}
	}

	public static void onSlotInput(ServerPlayerEntity operator, MobEntity mob, ControlState state, int slot, boolean pressed) {
		AbilitySlot entry = MobProfiles.of(mob).slot(slot);
		if (entry == null) {
			return;
		}
		ServerWorld world = (ServerWorld) mob.getWorld();
		int now = operator.getServer().getTicks();
		AbilityContext context = new AbilityContext(operator, mob, world, state);
		if (pressed) {
			if (state.held[slot] || state.lastPress[slot] == now) {
				return;
			}
			state.lastPress[slot] = now;
			boolean used = now >= state.readyAt[slot] && entry.ability().press(context);
			if (used && entry.ability().charges()) {
				state.held[slot] = true;
				state.heldSince[slot] = now;
			} else if (used) {
				startCooldown(state, slot, entry, now);
			}
			event(operator, used ? VisorEventPayload.Kind.SLOT_USED : VisorEventPayload.Kind.SLOT_REJECTED, slot);
		} else if (state.held[slot]) {
			state.held[slot] = false;
			if (entry.ability().release(context, now - state.heldSince[slot])) {
				startCooldown(state, slot, entry, now);
			}
		}
	}

	private static void startCooldown(ControlState state, int slot, AbilitySlot entry, int now) {
		state.readyAt[slot] = now + (long) entry.cooldown();
	}

	private static void event(ServerPlayerEntity operator, VisorEventPayload.Kind kind, int slot) {
		if (ServerPlayNetworking.canSend(operator, VisorEventPayload.ID)) {
			ServerPlayNetworking.send(operator, new VisorEventPayload(kind, slot));
		}
	}

	public static List<HudStatePayload.Slot> slotViews(MobEntity mob, ControlState state, int now) {
		List<HudStatePayload.Slot> views = new ArrayList<>();
		MobProfile profile = MobProfiles.of(mob);
		for (int slot = 0; slot < ControlState.SLOTS; slot++) {
			AbilitySlot entry = profile.slot(slot);
			if (entry != null) {
				int left = Math.max(0, (int) (state.readyAt[slot] - now));
				views.add(new HudStatePayload.Slot(slot, entry.icon(), left, entry.cooldown(), state.held[slot] || entry.ability().active(mob, state)));
			}
		}
		return views;
	}
}
