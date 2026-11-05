package net.neurallink.mixin;

import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractPiglinEntity.class)
public interface AbstractPiglinEntityAccessor {
	@Accessor("timeInOverworld")
	int neurallink$timeInOverworld();

	@Invoker("zombify")
	void neurallink$zombify(ServerWorld world);

	@Invoker("playZombificationSound")
	void neurallink$playZombificationSound();
}
