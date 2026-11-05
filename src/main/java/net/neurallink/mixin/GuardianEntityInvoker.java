package net.neurallink.mixin;

import net.minecraft.entity.mob.GuardianEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuardianEntity.class)
public interface GuardianEntityInvoker {
	@Invoker("setBeamTarget")
	void neurallink$setBeamTarget(int entityId);

	@Invoker("setSpikesRetracted")
	void neurallink$setSpikesRetracted(boolean retracted);
}
