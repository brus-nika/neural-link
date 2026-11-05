package net.neurallink.control;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

import net.minecraft.entity.mob.MobEntity;

final class ToggleAbility implements Ability {
	private final Consumer<AbilityContext> on;
	private final Consumer<AbilityContext> off;
	private final BiPredicate<MobEntity, ControlState> isOn;

	ToggleAbility(Consumer<AbilityContext> on, Consumer<AbilityContext> off, BiPredicate<MobEntity, ControlState> isOn) {
		this.on = on;
		this.off = off;
		this.isOn = isOn;
	}

	@Override
	public boolean press(AbilityContext context) {
		if (isOn.test(context.mob(), context.state())) {
			off.accept(context);
		} else {
			on.accept(context);
		}
		return true;
	}

	@Override
	public boolean active(MobEntity mob, ControlState state) {
		return isOn.test(mob, state);
	}

	@Override
	public void sessionEnded(AbilityContext context) {
		if (isOn.test(context.mob(), context.state())) {
			off.accept(context);
		}
	}
}
