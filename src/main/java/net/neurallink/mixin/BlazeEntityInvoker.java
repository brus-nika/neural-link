package net.neurallink.mixin;

import net.minecraft.entity.mob.BlazeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlazeEntity.class)
public interface BlazeEntityInvoker {
	@Invoker("setFireActive")
	void neurallink$setFireActive(boolean active);
}
