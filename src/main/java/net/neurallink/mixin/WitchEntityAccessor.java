package net.neurallink.mixin;

import net.minecraft.entity.mob.WitchEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WitchEntity.class)
public interface WitchEntityAccessor {
	@Accessor("drinkTimeLeft")
	void neurallink$setDrinkTimeLeft(int ticks);
}
