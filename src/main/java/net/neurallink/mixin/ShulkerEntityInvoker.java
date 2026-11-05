package net.neurallink.mixin;

import net.minecraft.entity.mob.ShulkerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShulkerEntity.class)
public interface ShulkerEntityInvoker {
	@Invoker("getPeekAmount")
	int neurallink$getPeekAmount();

	@Invoker("setPeekAmount")
	void neurallink$setPeekAmount(int amount);

	@Invoker("tryTeleport")
	boolean neurallink$tryTeleport();
}
