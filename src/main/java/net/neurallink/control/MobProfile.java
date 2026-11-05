package net.neurallink.control;

import java.util.List;

/** movement type and LMB, R, F slots (null = empty). */
public record MobProfile(Movement movement, List<AbilitySlot> slots) {
	public static MobProfile of(Movement movement, AbilitySlot... slots) {
		return new MobProfile(movement, java.util.Arrays.asList(slots));
	}

	public AbilitySlot slot(int index) {
		return index >= 0 && index < slots.size() ? slots.get(index) : null;
	}
}
