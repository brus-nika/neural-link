package net.neurallink.mixin;

import net.minecraft.entity.passive.FoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FoxEntity.class)
public interface FoxEntityInvoker {
	@Invoker("stopSleeping")
	void neurallink$stopSleeping();

	@Accessor("eatingTime")
	void neurallink$setEatingTime(int ticks);
}
