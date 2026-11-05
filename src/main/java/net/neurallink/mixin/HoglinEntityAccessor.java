package net.neurallink.mixin;

import net.minecraft.entity.mob.HoglinEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HoglinEntity.class)
public interface HoglinEntityAccessor {
	@Accessor("timeInOverworld")
	int neurallink$timeInOverworld();

	@Invoker("zombify")
	void neurallink$zombify();
}
