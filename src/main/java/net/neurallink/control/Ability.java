package net.neurallink.control;

import net.minecraft.entity.mob.MobEntity;

/** Only bow and crossbow hold, the rest fire on press. */
public interface Ability {
	/** true if fired, which starts the cooldown or hold. */
	boolean press(AbilityContext context);

	default boolean charges() {
		return false;
	}

	/** Toggle or fuse: keeps the HUD frame lit. */
	default boolean active(MobEntity mob, ControlState state) {
		return false;
	}

	/** the mob must not be left in a stance, beam or invulnerable. */
	default void sessionEnded(AbilityContext context) {
	}

	/** True if shot; a short press costs no cooldown. */
	default boolean release(AbilityContext context, int heldTicks) {
		return false;
	}
}
